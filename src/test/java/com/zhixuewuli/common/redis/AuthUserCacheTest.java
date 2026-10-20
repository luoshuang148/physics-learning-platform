package com.zhixuewuli.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.security.AuthUserCache;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration-controller")
class AuthUserCacheTest extends RedisTestSupport {
    LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    UserRepository users;
    AuthUserCache first, second;
    String namespace;
    @BeforeEach void setUp() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet(); redis = new StringRedisTemplate(factory);
        users = mock(UserRepository.class); namespace = "test:" + UUID.randomUUID() + ":";
        first = new AuthUserCache(users, redis, new ObjectMapper(), namespace);
        second = new AuthUserCache(users, redis, new ObjectMapper(), namespace);
    }
    @AfterEach void close() { factory.destroy(); }
    User user(long version) {
        var user = new User(); user.setId(1L); user.setUsername("user"); user.setActive(true);
        user.setTokenVersion(version); user.setUserType(User.UserType.STUDENT); return user;
    }
    void commitInvalidation() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            second.invalidateAfterCommit(1L);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.beforeCommit(false));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void snapshotsAreSharedWithFiveMinuteTtl() {
        when(users.findById(1L)).thenReturn(Optional.of(user(0)));
        assertThat(first.get(1L).tokenVersion()).isZero();
        assertThat(second.get(1L).userType()).isEqualTo(User.UserType.STUDENT);
        verify(users, times(1)).findById(1L);
        assertThat(redis.getExpire(namespace + "auth:{1}:snapshot")).isBetween(290L, 300L);
    }
    @Test void invalidationWaitsForCommitAndRollbackKeepsTheSnapshot() {
        when(users.findById(1L)).thenReturn(Optional.of(user(0)));
        first.get(1L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            first.invalidateAfterCommit(1L);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.beforeCommit(false));
            assertThat(redis.hasKey(namespace + "auth:{1}:snapshot")).isTrue();
            assertThat(redis.hasKey(namespace + "auth:{1}:pending")).isTrue();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertThat(redis.hasKey(namespace + "auth:{1}:snapshot")).isTrue();
        assertThat(redis.hasKey(namespace + "auth:{1}:pending")).isFalse();
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        commitInvalidation();
        assertThat(first.get(1L).tokenVersion()).isEqualTo(1);
    }
    @Test void readStartedBeforeCommitCannotRefillAStaleSnapshotAfterEviction() throws Exception {
        var read = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(users.findById(1L)).thenAnswer(call -> {
            read.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return Optional.of(user(0));
        }).thenReturn(Optional.of(user(1)));
        try (var worker = Executors.newSingleThreadExecutor()) {
            var pending = worker.submit(() -> first.get(1L));
            try { assertThat(read.await(10, TimeUnit.SECONDS)).isTrue(); commitInvalidation(); }
            finally { release.countDown(); }
            assertThat(pending.get(10, TimeUnit.SECONDS).tokenVersion()).isEqualTo(1);
        }
        assertThat(second.get(1L).tokenVersion()).isEqualTo(1);
        verify(users, times(2)).findById(1L);
    }

    @Test void failedPostCommitEvictionBypassesStaleCacheAfterRecoveryAndRetries() {
        when(users.findById(1L)).thenReturn(Optional.of(user(0)));
        first.get(1L);
        var failingRedis = spy(redis);
        var cache = new AuthUserCache(users, failingRedis, new ObjectMapper(), namespace);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.invalidateAfterCommit(1L);
            var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
            synchronization.beforeCommit(false);
            when(users.findById(1L)).thenReturn(Optional.of(user(1)));
            doThrow(new org.springframework.data.redis.RedisConnectionFailureException("simulated outage"))
                    .when(failingRedis).execute(org.mockito.ArgumentMatchers.<org.springframework.data.redis.core.script.RedisScript<Long>>any(),
                            org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(Object[].class));
            synchronization.afterCommit();
            assertThat(redis.hasKey(namespace + "auth:{1}:snapshot")).isTrue();
            // Another process after Redis recovery must ignore the surviving warm snapshot.
            assertThat(second.get(1L).tokenVersion()).isEqualTo(1);
            assertThat(redis.hasKey(namespace + "auth:{1}:pending")).isTrue();
            reset(failingRedis);
            cache.retryInvalidations();
            assertThat(redis.hasKey(namespace + "auth:{1}:pending")).isFalse();
            assertThat(second.get(1L).tokenVersion()).isEqualTo(1);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void aTransactionalReadNeverPublishesUncommittedSecurityState() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThat(first.get(1L).tokenVersion()).isEqualTo(1); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(redis.hasKey(namespace + "auth:{1}:snapshot")).isFalse();
    }
}
