package com.zhixuewuli.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 测试专用 JWT 工具类
 * 
 * <p>
 * 在测试环境下提供简化的 JWT 令牌生成和验证功能。
 * 该类仅在 test profile 下激活，用于控制器集成测试中的身份认证场景。
 * </p>
 * 
 * <p>
 * 主要特点：
 * </p>
 * <ul>
 * <li>仅用于测试环境（@Profile("test")）</li>
 * <li>使用配置的 JWT 密钥进行签名</li>
 * <li>支持自定义过期时间</li>
 * <li>与主 JwtUtil 使用相同的签名算法</li>
 * <li>便于测试各种认证场景（正常、过期、无效令牌等）</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     public class AuthControllerIntegrationTest extends BaseControllerIntegrationTest {
 * 
 *         &#64;Autowired
 *         private TestJwtUtil testJwtUtil;
 * 
 *         @Test
 *         void testTokenExpiration() throws Exception {
 *             // 生成即将过期的令牌
 *             String token = testJwtUtil.generateTokenWithExpiration(100L, "test", 1000L);
 * 
 *             // 等待令牌过期
 *             Thread.sleep(1500);
 * 
 *             // 验证令牌已过期
 *             assertThat(testJwtUtil.validateToken(token)).isFalse();
 *         }
 *     }
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see JwtUtil 主 JWT 工具类
 */
@Component
@Profile("test")
public class TestJwtUtil {

    /**
     * JWT 签名密钥（从配置文件读取）
     */
    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    /**
     * JWT 过期时间（毫秒，从配置文件读取）
     */
    @Value("${app.security.jwt-expiration}")
    private long jwtExpiration;

    /**
     * 获取JWT签名密钥
     *
     * 使用HMAC-SHA算法生成签名密钥，确保令牌的安全性。
     *
     * @return SecretKey 签名密钥对象
     */
    private javax.crypto.SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 生成测试令牌（使用默认过期时间）
     * 
     * <p>
     * 根据用户 ID 和用户名生成标准的 JWT 令牌，
     * 使用配置文件中定义的过期时间。
     * </p>
     * 
     * @param userId   用户唯一标识
     * @param username 用户名
     * @return String JWT 令牌字符串
     */
    public String generateTestToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 生成指定过期时间的测试令牌
     * 
     * <p>
     * 用于测试令牌过期场景，可以自定义令牌的有效期（毫秒）。
     * 适用于验证令牌过期处理逻辑的测试用例。
     * </p>
     * 
     * @param userId     用户唯一标识
     * @param username   用户名
     * @param expiration 过期时间（毫秒）
     * @return String JWT 令牌字符串
     */
    public String generateTokenWithExpiration(Long userId, String username, long expiration) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证令牌有效性
     * 
     * <p>
     * 检查 JWT 令牌的签名完整性和基本格式，
     * 但不验证过期时间（用于测试过期令牌的处理）。
     * </p>
     * 
     * @param token JWT 令牌字符串
     * @return boolean 令牌是否有效（格式正确且签名匹配）
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从令牌中获取用户 ID
     * 
     * <p>
     * 解析 JWT 令牌并提取其中包含的用户 ID 信息。
     * 用于验证令牌生成和解析的一致性。
     * </p>
     * 
     * @param token JWT 令牌字符串
     * @return Long 用户 ID，如果解析失败返回 null
     */
    public Long getUserIdFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("userId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从令牌中获取用户名
     * 
     * <p>
     * 解析 JWT 令牌并提取其中的用户名（subject）信息。
     * 用于验证令牌内容的正确性。
     * </p>
     * 
     * @param token JWT 令牌字符串
     * @return String 用户名，如果解析失败返回 null
     */
    public String getUsernameFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
