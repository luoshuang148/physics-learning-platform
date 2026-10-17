package com.zhixuewuli.common.util;

import com.zhixuewuli.common.config.properties.SecurityProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT工具类
 * 
 * 提供JSON Web Token的生成、验证和解析功能。
 * 用于用户身份认证和授权管理。
 * 
 * <p>
 * 主要功能包括：
 * </p>
 * <ul>
 * <li>生成包含用户信息的JWT令牌</li>
 * <li>验证JWT令牌的有效性和完整性</li>
 * <li>从JWT令牌中提取用户标识信息</li>
 * </ul>
 * 
 * <p>
 * 安全特性：
 * </p>
 * <ul>
 * <li>使用HS512算法进行签名</li>
 * <li>支持令牌过期时间控制</li>
 * <li>具备完善的异常处理机制</li>
 * <li>日志记录安全相关事件</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see <a href="https://jwt.io/">JWT官方网站</a>
 * @see SecurityProperties 安全配置属性类
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtil {

    private final SecurityProperties securityProperties;

    /**
     * 获取JWT签名密钥
     * 
     * 使用HMAC-SHA算法生成签名密钥，确保令牌的安全性。
     * 密钥从SecurityProperties配置中获取。
     * 
     * @return SecretKey 签名密钥对象
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(securityProperties.getJwtSecret().getBytes());
    }

    /**
     * 生成JWT令牌
     * 
     * 创建包含用户标识信息的JWT令牌，用于后续的身份验证。
     * 
     * @param userId   用户唯一标识
     * @param username 用户名
     * @return String 生成的JWT令牌字符串
     * @throws IllegalArgumentException 当参数为空或无效时抛出
     * @throws RuntimeException         当令牌生成失败时抛出
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + securityProperties.getJwtExpirationMs());

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 验证 JWT 令牌有效性
     * 
     * 检查 JWT 令牌的签名完整性、有效性和过期状态。
     * 
     * @param token 待验证的 JWT 令牌
     * @return boolean 令牌是否有效
     * @throws IllegalArgumentException 当令牌格式不正确时抛出
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (JwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT token is null or empty");
        }
        return false;
    }

    /**
     * 从JWT令牌中获取用户ID
     * 
     * 解析JWT令牌并提取其中包含的用户唯一标识。
     * 
     * @param token JWT令牌字符串
     * @return Long 用户唯一标识
     * @throws ExpiredJwtException     当令牌已过期时抛出
     * @throws MalformedJwtException   当令牌格式错误时抛出
     * @throws SecurityException       当签名验证失败时抛出
     * @throws UnsupportedJwtException 当令牌版本不支持时抛出
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }

    /**
     * 从JWT令牌中获取用户名
     * 
     * 解析JWT令牌并提取其中包含的用户名信息。
     * 
     * @param token JWT令牌字符串
     * @return String 用户名
     * @throws ExpiredJwtException     当令牌已过期时抛出
     * @throws MalformedJwtException   当令牌格式错误时抛出
     * @throws SecurityException       当签名验证失败时抛出
     * @throws UnsupportedJwtException 当令牌版本不支持时抛出
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
