package com.zhixuewuli.common.redis;

import com.zhixuewuli.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.UUID;

/** Sorted-set scores are lease deadlines; all checks use Redis time and run atomically. */
@Component
public class RedisConnectionLeases {
    private static final String NOW = """
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            """;
    private static final DefaultRedisScript<Long> ACQUIRE = script(NOW + """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
            redis.call('ZADD', KEYS[1], now + ARGV[3], ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3] * 2)
            return 1
            """);
    private static final DefaultRedisScript<Long> CLAIM = script(NOW + """
            local deadline = tonumber(redis.call('ZSCORE', KEYS[1], ARGV[1]) or '0')
            if deadline <= now or redis.call('EXISTS', KEYS[2]) == 0 then return -1 end
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)
            if not redis.call('ZSCORE', KEYS[3], ARGV[1])
                and redis.call('ZCARD', KEYS[3]) >= tonumber(ARGV[2]) then return 0 end
            redis.call('ZADD', KEYS[3], deadline, ARGV[1])
            redis.call('PEXPIRE', KEYS[3], ARGV[3] * 2)
            return 1
            """);
    private static final DefaultRedisScript<Long> RENEW = script(NOW + """
            if tonumber(redis.call('ZSCORE', KEYS[1], ARGV[1]) or '0') <= now
                or redis.call('EXISTS', KEYS[2]) == 0 then return 0 end
            if ARGV[3] == '1' then
                if tonumber(redis.call('ZSCORE', KEYS[3], ARGV[1]) or '0') <= now then return 0 end
                redis.call('ZADD', KEYS[3], now + ARGV[2], ARGV[1])
                redis.call('PEXPIRE', KEYS[3], ARGV[2] * 2)
            end
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            redis.call('ZADD', KEYS[1], now + ARGV[2], ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2] * 2)
            return 1
            """);
    private static final DefaultRedisScript<Long> RELEASE = script("""
            redis.call('DEL', KEYS[2])
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[3], ARGV[1])
            return 1
            """);
    private static final DefaultRedisScript<Long> COUNT = script(NOW + """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            return redis.call('ZCARD', KEYS[1])
            """);
    private final StringRedisTemplate redis;
    private final String prefix;
    private final long ttlMillis;

    @Autowired
    public RedisConnectionLeases(StringRedisTemplate redis, @Value("${app.redis.namespace:zxwl:}") String namespace) {
        this(redis, namespace, 60_000);
    }
    public RedisConnectionLeases(StringRedisTemplate redis, String namespace, long ttlMillis) {
        this.redis = redis;
        this.prefix = namespace + "sse:{sse}:";
        if (ttlMillis < 1) throw new IllegalArgumentException("Lease TTL must be positive");
        this.ttlMillis = ttlMillis;
    }
    private static DefaultRedisScript<Long> script(String source) { return new DefaultRedisScript<>(source, Long.class); }
    private List<String> keys(String lease, Long user) {
        return List.of(prefix + "global", prefix + "lease:" + lease, prefix + "user:" + user);
    }
    public String acquire(int limit) {
        String lease = UUID.randomUUID().toString();
        if (!Long.valueOf(1).equals(redis.execute(ACQUIRE, keys(lease, null).subList(0, 2),
                lease, Integer.toString(limit), Long.toString(ttlMillis))))
            throw new BusinessException("系统繁忙，连接数已达上限，请稍后重试");
        return lease;
    }
    public boolean claimUser(String lease, Long user, int limit) {
        Long result = redis.execute(CLAIM, keys(lease, user), lease, Integer.toString(limit), Long.toString(ttlMillis));
        if (Long.valueOf(-1).equals(result)) throw new BusinessException("请求已取消");
        return Long.valueOf(1).equals(result);
    }
    public boolean renew(String lease, Long user) {
        return Long.valueOf(1).equals(redis.execute(RENEW, keys(lease, user), lease,
                Long.toString(ttlMillis), user == null ? "0" : "1"));
    }
    public void release(String lease, Long user) { redis.execute(RELEASE, keys(lease, user), lease); }
    public int count() {
        Long count = redis.execute(COUNT, List.of(prefix + "global"));
        if (count == null) throw new IllegalStateException("Missing Redis connection count");
        return Math.toIntExact(count);
    }
}
