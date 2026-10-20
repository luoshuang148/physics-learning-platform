package com.zhixuewuli.common.redis;

import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.security.LoginAttemptLimiter;
import com.zhixuewuli.common.util.StreamUtils;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@Tag("integration-controller")
class DistributedStateTest extends RedisTestSupport {
    LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    String namespace;
    @BeforeEach void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        namespace = "test:" + UUID.randomUUID() + ":";
    }
    @AfterEach void close() { factory.destroy(); }

    @Test void independentLimitersShareAtomicAdmissionAndSuccessReset() throws Exception {
        var first = new LoginAttemptLimiter(redis, namespace);
        var second = new LoginAttemptLimiter(redis, namespace);
        var accepted = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(12)) {
            var start = new CountDownLatch(1);
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int n = 0; n < 24; n++) {
                var limiter = n % 2 == 0 ? first : second;
                jobs.add(workers.submit(() -> {
                    try { start.await(); limiter.check("peer", " User "); accepted.incrementAndGet(); }
                    catch (LoginAttemptLimiter.LoginThrottledException expected) { }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }));
            }
            start.countDown();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertThat(accepted.get()).isEqualTo(5);
        second.succeeded("peer", "user");
        assertThatCode(() -> first.check("peer", "USER")).doesNotThrowAnyException();
        assertThat(redis.keys(namespace + "login:*")).allSatisfy(key ->
                assertThat(redis.getExpire(key)).isBetween(1L, 60L));
    }

    @Test void peerLimitIsSharedAcrossDifferentAccounts() {
        var first = new LoginAttemptLimiter(redis, namespace);
        var second = new LoginAttemptLimiter(redis, namespace);
        for (int i = 0; i < 200; i++) first.check("peer", "user" + i);
        assertThatThrownBy(() -> second.check("peer", "another")).isInstanceOf(LoginAttemptLimiter.LoginThrottledException.class);
        assertThatCode(() -> second.check("other-peer", "another")).doesNotThrowAnyException();
    }

    @Test void globalAndUserConnectionsAreSharedAndReleaseIsIdempotent() {
        var leases1 = new RedisConnectionLeases(redis, namespace);
        var leases2 = new RedisConnectionLeases(redis, namespace);
        var first = new StreamUtils(new AIStreamingProperties(), leases1);
        var second = new StreamUtils(new AIStreamingProperties(), leases2);
        try {
            first.setMaxConnections(3); second.setMaxConnections(3);
            var a = first.createConfiguredEmitterWithLimit(10000, 1000);
            var b = second.createConfiguredEmitterWithLimit(10000, 1000);
            first.claimUser(a, 1L); second.claimUser(b, 1L);
            assertThat(second.getActiveConnectionCount()).isEqualTo(2);
            var c = second.createConfiguredEmitterWithLimit(10000, 1000);
            assertThatThrownBy(() -> second.claimUser(c, 1L)).hasMessageContaining("同时生成");
            assertThatThrownBy(() -> first.createConfiguredEmitterWithLimit(10000, 1000)).hasMessageContaining("系统繁忙");
            first.safeCompleteEmitter(a); first.safeCompleteEmitter(a);
            second.claimUser(c, 1L);
            assertThat(first.getActiveConnectionCount()).isEqualTo(2);
            second.safeCompleteEmitter(b); second.safeCompleteEmitter(c);
            assertThat(first.getActiveConnectionCount()).isZero();
        } finally { first.destroy(); second.destroy(); }
    }

    @Test void concurrentInstancesCannotExceedGlobalOrUserLimits() throws Exception {
        var first = new RedisConnectionLeases(redis, namespace);
        var second = new RedisConnectionLeases(redis, namespace);
        var admitted = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var claimed = new AtomicInteger();
        try (var workers = Executors.newFixedThreadPool(12)) {
            var start = new CountDownLatch(1);
            var jobs = new java.util.ArrayList<Future<?>>();
            for (int n = 0; n < 24; n++) {
                var instance = n % 2 == 0 ? first : second;
                jobs.add(workers.submit(() -> {
                    try {
                        start.await();
                        String lease = instance.acquire(5);
                        admitted.add(lease);
                        if (instance.claimUser(lease, 1L, 2)) claimed.incrementAndGet();
                    } catch (com.zhixuewuli.common.exception.BusinessException expected) { }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }));
            }
            start.countDown();
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertThat(admitted).hasSize(5);
        assertThat(claimed.get()).isEqualTo(2);
        assertThat(second.count()).isEqualTo(5);
        admitted.forEach(lease -> first.release(lease, 1L));
        assertThat(second.count()).isZero();
    }

    @Test void servletCallbacksReleaseBothSlots() {
        var streams = new StreamUtils(new AIStreamingProperties(), new RedisConnectionLeases(redis, namespace));
        try {
            for (String callback : java.util.List.of("completionCallback", "timeoutCallback", "errorCallback")) {
                var emitter = streams.createConfiguredEmitterWithLimit(10000, 1000);
                streams.claimUser(emitter, 1L);
                Object action = org.springframework.test.util.ReflectionTestUtils.getField(emitter, callback);
                if (action instanceof Runnable runnable) runnable.run();
                else {
                    @SuppressWarnings("unchecked")
                    var consumer = (java.util.function.Consumer<Throwable>) action;
                    consumer.accept(new java.io.IOException("disconnect"));
                }
                assertThat(streams.isCancelled(emitter)).isTrue();
                assertThat(streams.getActiveConnectionCount()).isZero();
                streams.safeCompleteEmitter(emitter);
            }
        } finally { streams.destroy(); }
    }

    @Test void abandonedLeasesExpireAndCannotBeResurrected() throws Exception {
        var first = new RedisConnectionLeases(redis, namespace, 150);
        var second = new RedisConnectionLeases(redis, namespace, 150);
        String old = first.acquire(1);
        assertThat(first.claimUser(old, 1L, 1)).isTrue();
        Thread.sleep(250);
        String next = second.acquire(1);
        assertThat(second.claimUser(next, 1L, 1)).isTrue();
        assertThat(first.renew(old, 1L)).isFalse();
        first.release(old, 1L);
        assertThat(second.count()).isEqualTo(1);
        second.release(next, 1L);
        assertThat(second.count()).isZero();
    }

    @Test void renewingKeepsLiveLeaseAndUserSlot() throws Exception {
        var first = new RedisConnectionLeases(redis, namespace, 2000);
        var second = new RedisConnectionLeases(redis, namespace, 2000);
        String lease = first.acquire(2);
        first.claimUser(lease, 1L, 1);
        Thread.sleep(1100);
        assertThat(first.renew(lease, 1L)).isTrue();
        Thread.sleep(1100);
        String other = second.acquire(2);
        assertThat(second.claimUser(other, 1L, 1)).isFalse();
        assertThat(second.count()).isEqualTo(2);
        first.release(lease, 1L); second.release(other, null);
    }
}
