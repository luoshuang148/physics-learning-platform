package com.zhixuewuli.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.UUID;

/** Five-minute auth snapshots. Revision fencing prevents a pre-commit reader refilling after eviction. */
@lombok.extern.slf4j.Slf4j
@Component
public class AuthUserCache {
    public record Snapshot(Long id, String username, Long tokenVersion, Boolean active, User.UserType userType) {
        static Snapshot from(User user) {
            return new Snapshot(user.getId(), user.getUsername(), user.getTokenVersion(), user.getActive(), user.getUserType());
        }
        AuthenticatedUser principal() { return new AuthenticatedUser(id, username, userType); }
    }
    private static final DefaultRedisScript<Long> FILL = new DefaultRedisScript<>("""
            if (redis.call('GET', KEYS[1]) or '') ~= ARGV[1]
                or redis.call('SCARD', KEYS[3]) > 0 then return 0 end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', 300)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('DEL', KEYS[2])
            redis.call('SREM', KEYS[3], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> GUARD = new DefaultRedisScript<>("""
            redis.call('SADD', KEYS[1], ARGV[1])
            return 1
            """, Long.class);
    private final java.util.Map<String, Long> pendingEvictions = new java.util.concurrent.ConcurrentHashMap<>();
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String prefix;

    public AuthUserCache(UserRepository users, StringRedisTemplate redis, ObjectMapper mapper,
                         @Value("${app.redis.namespace:zxwl:}") String namespace) {
        this.users = users; this.redis = redis; this.mapper = mapper; this.prefix = namespace + "auth:";
    }
    private List<String> keys(Long id) {
        String base = prefix + "{" + id + "}:";
        return List.of(base + "revision", base + "snapshot", base + "pending");
    }
    public Snapshot get(Long id) {
        // A caller inside a transaction must observe its own writes and must never publish uncommitted state.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return load(id);
        List<String> keys = keys(id);
        while (true) {
            String revision = redis.opsForValue().get(keys.getFirst());
            if (Boolean.TRUE.equals(redis.hasKey(keys.get(2)))) return load(id);
            String cached = redis.opsForValue().get(keys.get(1));
            if (cached != null) return decode(cached);
            Snapshot snapshot = load(id);
            if (Long.valueOf(1).equals(redis.execute(FILL, keys, revision == null ? "" : revision, encode(snapshot))))
                return snapshot;
        }
    }
    private Snapshot load(Long id) { return Snapshot.from(users.findById(id).orElseThrow(UserNotFoundException::new)); }
    private String encode(Snapshot snapshot) {
        try { return mapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode auth snapshot", e); }
    }
    private Snapshot decode(String value) {
        try { return mapper.readValue(value, Snapshot.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot decode auth snapshot", e); }
    }
    private void evictCommitted(Long id, String mutation) {
        try {
            redis.execute(INVALIDATE, keys(id), UUID.randomUUID().toString(), mutation);
            pendingEvictions.remove(mutation);
        } catch (RuntimeException error) {
            log.warn("提交后认证缓存失效失败，将重试；保护标记使该用户继续回库校验", error);
        }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5000)
    public void retryInvalidations() { pendingEvictions.forEach((mutation, id) -> evictCommitted(id, mutation)); }

    public void invalidateAfterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Auth cache invalidation requires a transaction");
        String mutation = UUID.randomUUID().toString();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                // Do not evict before commit. A guard blocks cache hits/refills if the later eviction fails.
                // Guards do not expire while a transaction or failed eviction may still be outstanding.
                redis.execute(GUARD, List.of(keys(id).get(2)), mutation);
            }
            @Override public void afterCommit() {
                pendingEvictions.put(mutation, id);
                evictCommitted(id, mutation);
            }
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { redis.opsForSet().remove(keys(id).get(2), mutation); }
                    catch (RuntimeException error) { log.warn("回滚后清理认证缓存保护标记失败，将继续回库校验", error); }
                }
            }
        });
    }
}
