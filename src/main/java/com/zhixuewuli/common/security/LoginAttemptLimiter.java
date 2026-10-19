package com.zhixuewuli.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Shared fixed-window admission; identity is the direct peer, never a forwarding header. */
@Component
public class LoginAttemptLimiter {
    private static final DefaultRedisScript<Long> ADMIT = new DefaultRedisScript<>("""
            if tonumber(redis.call('GET', KEYS[1]) or '0') >= 5
                or tonumber(redis.call('GET', KEYS[2]) or '0') >= 200 then return 0 end
            for _, key in ipairs(KEYS) do
                if redis.call('INCR', key) == 1 then redis.call('EXPIRE', key, 60) end
            end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final String prefix;

    public LoginAttemptLimiter(StringRedisTemplate redis, @Value("${app.redis.namespace:zxwl:}") String namespace) {
        this.redis = redis;
        this.prefix = namespace + "login:{login}:";
    }
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private String accountKey(String ip, String username) {
        return prefix + "account:" + encode(ip) + ":" + encode(username.trim().toLowerCase(Locale.ROOT));
    }
    public void check(String ip, String username) {
        Long admitted = redis.execute(ADMIT, List.of(accountKey(ip, username), prefix + "peer:" + encode(ip)));
        if (!Long.valueOf(1).equals(admitted)) throw new LoginThrottledException();
    }
    public void succeeded(String ip, String username) { redis.delete(accountKey(ip, username)); }

    public static final class LoginThrottledException extends RuntimeException {
        public LoginThrottledException() { super("登录尝试过多，请一分钟后重试"); }
    }
}
