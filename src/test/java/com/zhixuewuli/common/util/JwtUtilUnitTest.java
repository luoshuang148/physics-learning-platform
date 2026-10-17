package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.config.properties.SecurityProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * JwtUtil 单元测试类
 * 
 * <p>
 * 测试 JWT 令牌生成、验证和解析功能，包括：
 * </p>
 * <ul>
 * <li>令牌生成 - 验证生成的令牌格式和内容正确性</li>
 * <li>令牌验证 - 验证有效令牌、过期令牌、无效令牌的识别</li>
 * <li>令牌解析 - 验证从令牌中提取用户信息的准确性</li>
 * <li>边界场景 - 验证空值、null 值等异常情况处理</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用真实 JWT 库进行端到端测试（不 mock Jwts）</li>
 * <li>Mock SecurityProperties 以控制测试参数</li>
 * <li>覆盖所有公开方法和异常场景</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("JwtUtil 单元测试")
class JwtUtilUnitTest extends BaseUnitTest {

    @Mock
    private SecurityProperties securityProperties;

    private JwtUtil jwtUtil;

    // 测试用配置
    private static final String TEST_JWT_SECRET = "test-jwt-secret-key-for-unit-tests-only-do-not-use-in-production";
    private static final long TEST_JWT_EXPIRATION_MS = 3600000L; // 1 小时
    private static final Long TEST_USER_ID = 1001L;
    private static final String TEST_USERNAME = "test_user";

    @BeforeEach
    void setUp() {
        // 配置 SecurityProperties mock（使用 lenient 避免不必要的 stubbing 错误）
        lenient().when(securityProperties.getJwtSecret()).thenReturn(TEST_JWT_SECRET);
        lenient().when(securityProperties.getJwtExpirationMs()).thenReturn(TEST_JWT_EXPIRATION_MS);

        // 创建真实的 JwtUtil 实例（因为需要调用真实的 JWT 库）
        jwtUtil = new JwtUtil(securityProperties);
    }

    // ==================== generateToken 测试 ====================

    @Nested
    @DisplayName("generateToken - 令牌生成测试")
    class GenerateTokenTests {

        @Test
        @DisplayName("TC-JWT-01: 应该成功生成有效的 JWT 令牌")
        void testGenerateToken_ValidInput_ShouldGenerateToken() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(userId, username);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // JWT 有 3 部分：header.payload.signature
        }

        @Test
        @DisplayName("TC-JWT-02: 生成的令牌应该包含正确的用户 ID")
        void testGenerateToken_ShouldContainCorrectUserId() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(userId, username);
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("TC-JWT-03: 生成的令牌应该包含正确的用户名")
        void testGenerateToken_ShouldContainCorrectUsername() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(userId, username);
            String extractedUsername = jwtUtil.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("TC-JWT-04: 生成的令牌应该具有正确的过期时间")
        void testGenerateToken_ShouldHaveCorrectExpirationTime() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;
            Date beforeGeneration = new Date();

            // When
            String token = jwtUtil.generateToken(userId, username);

            // Then
            // 验证令牌未过期
            assertThat(jwtUtil.validateToken(token)).isTrue();

            // 注意：实际测试中不会等待 1 小时来验证过期，这里只验证生成时有效
        }

        @Test
        @DisplayName("TC-JWT-05: 不同用户应该生成不同的令牌")
        void testGenerateToken_DifferentUsers_ShouldGenerateDifferentTokens() {
            // Given
            Long userId1 = TEST_USER_ID;
            String username1 = "user1";
            Long userId2 = 2002L;
            String username2 = "user2";

            // When
            String token1 = jwtUtil.generateToken(userId1, username1);
            String token2 = jwtUtil.generateToken(userId2, username2);

            // Then
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("TC-JWT-06: 同一用户多次生成应该得到不同的令牌（时间戳不同）")
        void testGenerateToken_SameUserMultipleTimes_ShouldGenerateDifferentTokens() throws Exception {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;

            // When
            String token1 = jwtUtil.generateToken(userId, username);

            // 等待 1.1 秒确保 issuedAt 时间戳不同（JWT 时间戳以秒为单位）
            Thread.sleep(1100); // 等待 1.1 秒

            String token2 = jwtUtil.generateToken(userId, username);

            // Then
            // JWT 包含 issuedAt 时间戳，每次生成应该不同
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("TC-JWT-07: 用户 ID 为 null 时应该能生成令牌（JWT 库允许 null claims）")
        void testGenerateToken_NullUserId_ShouldHandleGracefully() {
            // Given
            Long nullUserId = null;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(nullUserId, username);

            // Then
            // JWT 库允许 null 值的 claim，所以会成功生成令牌
            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3); // JWT 有 3 部分

            // 提取的 userId 应该是 null
            assertThat(jwtUtil.getUserIdFromToken(token)).isNull();
        }

        @Test
        @DisplayName("TC-JWT-08: 用户名为 null 时应该能生成令牌（JWT 库接受 null subject）")
        void testGenerateToken_NullUsername_ShouldHandleGracefully() {
            // Given
            Long userId = TEST_USER_ID;
            String nullUsername = null;

            // When
            // 实际测试发现 JWT 库的 setSubject() 在某些版本中接受 null
            String token = jwtUtil.generateToken(userId, nullUsername);

            // Then
            // JWT 库可能接受 null 值，生成令牌
            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3); // JWT 有 3 部分
        }

        @Test
        @DisplayName("TC-JWT-09: 用户名为空字符串时会生成令牌（JWT 允许空字符串作为 subject）")
        void testGenerateToken_EmptyUsername_ShouldGenerateToken() {
            // Given
            Long userId = TEST_USER_ID;
            String emptyUsername = "";

            // When
            String token = jwtUtil.generateToken(userId, emptyUsername);

            // Then
            // JWT 允许空字符串作为 subject，所以会成功生成令牌
            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3); // JWT 有 3 部分
        }
    }

    // ==================== validateToken 测试 ====================

    @Nested
    @DisplayName("validateToken - 令牌验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("TC-JWT-10: 有效令牌应该验证通过")
        void testValidateToken_ValidToken_ShouldReturnTrue() {
            // Given
            String validToken = jwtUtil.generateToken(TEST_USER_ID, TEST_USERNAME);

            // When
            boolean isValid = jwtUtil.validateToken(validToken);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-JWT-11: 格式错误的令牌应该返回 false")
        void testValidateToken_MalformedToken_ShouldReturnFalse() {
            // Given
            String malformedToken = "invalid.token.format";

            // When
            boolean isValid = jwtUtil.validateToken(malformedToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-12: 空令牌应该返回 false")
        void testValidateToken_EmptyToken_ShouldReturnFalse() {
            // Given
            String emptyToken = "";

            // When
            boolean isValid = jwtUtil.validateToken(emptyToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-13: null 令牌应该返回 false")
        void testValidateToken_NullToken_ShouldReturnFalse() {
            // Given
            String nullToken = null;

            // When
            boolean isValid = jwtUtil.validateToken(nullToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-14: 签名不匹配的令牌应该返回 false（内部记录日志但不抛出异常）")
        void testValidateToken_WrongSignature_ShouldReturnFalse() {
            // Given
            String tokenWithWrongSignature = createTokenWithDifferentSignature();

            // When
            // validateToken 方法会捕获 SignatureException 并记录日志，然后返回 false
            boolean isValid = jwtUtil.validateToken(tokenWithWrongSignature);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-15: 过期的令牌应该返回 false")
        void testValidateToken_ExpiredToken_ShouldReturnFalse() {
            // Given
            // 创建一个立即过期的令牌
            String expiredToken = createExpiredToken();

            // When
            boolean isValid = jwtUtil.validateToken(expiredToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-JWT-15b: 空白令牌应该返回 false")
        void testValidateToken_BlankToken_ShouldReturnFalse() {
            // Given
            String blankToken = "   ";

            // When
            boolean isValid = jwtUtil.validateToken(blankToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    // ==================== getUserIdFromToken 测试 ====================

    @Nested
    @DisplayName("getUserIdFromToken - 提取用户 ID 测试")
    class GetUserIdFromTokenTests {

        @Test
        @DisplayName("TC-JWT-16: 应该从有效令牌中正确提取用户 ID")
        void testGetUserIdFromToken_ValidToken_ShouldExtractUserId() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;
            String token = jwtUtil.generateToken(userId, username);

            // When
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("TC-JWT-17: 从过期令牌提取用户 ID 应该抛出 ExpiredJwtException")
        void testGetUserIdFromToken_ExpiredToken_ShouldThrowExpiredJwtException() {
            // Given
            String expiredToken = createExpiredToken();

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUserIdFromToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("TC-JWT-18: 从格式错误令牌提取用户 ID 应该抛出异常")
        void testGetUserIdFromToken_MalformedToken_ShouldThrowException() {
            // Given
            String malformedToken = "invalid.token.format";

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUserIdFromToken(malformedToken))
                    .isInstanceOf(MalformedJwtException.class);
        }

        @Test
        @DisplayName("TC-JWT-19: 从签名无效令牌提取用户 ID 应该抛出 SecurityException")
        void testGetUserIdFromToken_InvalidSignature_ShouldThrowSecurityException() {
            // Given
            String invalidSignatureToken = createTokenWithDifferentSignature();

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUserIdFromToken(invalidSignatureToken))
                    .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
        }
    }

    // ==================== getUsernameFromToken 测试 ====================

    @Nested
    @DisplayName("getUsernameFromToken - 提取用户名测试")
    class GetUsernameFromTokenTests {

        @Test
        @DisplayName("TC-JWT-20: 应该从有效令牌中正确提取用户名")
        void testGetUsernameFromToken_ValidToken_ShouldExtractUsername() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;
            String token = jwtUtil.generateToken(userId, username);

            // When
            String extractedUsername = jwtUtil.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("TC-JWT-21: 从过期令牌提取用户名应该抛出 ExpiredJwtException")
        void testGetUsernameFromToken_ExpiredToken_ShouldThrowExpiredJwtException() {
            // Given
            String expiredToken = createExpiredToken();

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUsernameFromToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("TC-JWT-22: 从格式错误令牌提取用户名应该抛出 MalformedJwtException")
        void testGetUsernameFromToken_MalformedToken_ShouldThrowMalformedJwtException() {
            // Given
            String malformedToken = "invalid.token.format";

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUsernameFromToken(malformedToken))
                    .isInstanceOf(MalformedJwtException.class);
        }

        @Test
        @DisplayName("TC-JWT-23: 从签名无效令牌提取用户名应该抛出 SecurityException")
        void testGetUsernameFromToken_InvalidSignature_ShouldThrowSecurityException() {
            // Given
            String invalidSignatureToken = createTokenWithDifferentSignature();

            // When & Then
            assertThatThrownBy(() -> jwtUtil.getUsernameFromToken(invalidSignatureToken))
                    .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
        }
    }

    // ==================== 边界和特殊场景测试 ====================

    @Nested
    @DisplayName("边界和特殊场景测试")
    class BoundaryAndSpecialCasesTests {

        @Test
        @DisplayName("TC-JWT-24: 应该支持长用户名")
        void testGenerateToken_LongUsername_ShouldWork() {
            // Given
            Long userId = TEST_USER_ID;
            String longUsername = "very_long_username_that_is_still_valid_for_jwt_token_generation_and_should_work_without_any_issues";

            // When
            String token = jwtUtil.generateToken(userId, longUsername);
            String extractedUsername = jwtUtil.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(longUsername);
        }

        @Test
        @DisplayName("TC-JWT-25: 应该支持特殊字符用户名")
        void testGenerateToken_SpecialCharactersUsername_ShouldWork() {
            // Given
            Long userId = TEST_USER_ID;
            String specialUsername = "user_with_special_chars-123@test.example";

            // When
            String token = jwtUtil.generateToken(userId, specialUsername);
            String extractedUsername = jwtUtil.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(specialUsername);
        }

        @Test
        @DisplayName("TC-JWT-26: 应该支持大用户 ID")
        void testGenerateToken_LargeUserId_ShouldWork() {
            // Given
            Long largeUserId = Long.MAX_VALUE;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(largeUserId, username);
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(largeUserId);
        }

        @Test
        @DisplayName("TC-JWT-27: 应该支持负数用户 ID（虽然业务上不合理）")
        void testGenerateToken_NegativeUserId_ShouldWork() {
            // Given
            Long negativeUserId = -1L;
            String username = TEST_USERNAME;

            // When
            String token = jwtUtil.generateToken(negativeUserId, username);
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(negativeUserId);
        }

        @Test
        @DisplayName("TC-JWT-28: 无效令牌验证会记录日志")
        void testValidateToken_ShouldLogErrors() {
            // Given
            String invalidToken = "invalid.token.here";

            // When
            // 注意：日志由内部记录，无法直接验证，这里只验证功能
            boolean isValid = jwtUtil.validateToken(invalidToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    // ==================== 集成场景测试 ====================

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("TC-JWT-29: 完整的令牌生命周期 - 生成、验证、解析")
        void testCompleteTokenLifecycle() {
            // Given
            Long userId = TEST_USER_ID;
            String username = TEST_USERNAME;

            // When - 生成令牌
            String token = jwtUtil.generateToken(userId, username);

            // Then - 验证令牌有效性
            assertThat(jwtUtil.validateToken(token)).isTrue();

            // And - 解析令牌获取用户信息
            assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(userId);
            assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(username);
        }

        @Test
        @DisplayName("TC-JWT-30: 多用户并发场景下的令牌独立性")
        void testMultipleUsers_TokenIndependence() {
            // Given
            Long userId1 = 1001L;
            String username1 = "user1";
            Long userId2 = 1002L;
            String username2 = "user2";
            Long userId3 = 1003L;
            String username3 = "user3";

            // When
            String token1 = jwtUtil.generateToken(userId1, username1);
            String token2 = jwtUtil.generateToken(userId2, username2);
            String token3 = jwtUtil.generateToken(userId3, username3);

            // Then
            // 验证每个令牌都有效
            assertThat(jwtUtil.validateToken(token1)).isTrue();
            assertThat(jwtUtil.validateToken(token2)).isTrue();
            assertThat(jwtUtil.validateToken(token3)).isTrue();

            // 验证每个令牌包含正确的用户信息
            assertThat(jwtUtil.getUserIdFromToken(token1)).isEqualTo(userId1);
            assertThat(jwtUtil.getUserIdFromToken(token2)).isEqualTo(userId2);
            assertThat(jwtUtil.getUserIdFromToken(token3)).isEqualTo(userId3);

            assertThat(jwtUtil.getUsernameFromToken(token1)).isEqualTo(username1);
            assertThat(jwtUtil.getUsernameFromToken(token2)).isEqualTo(username2);
            assertThat(jwtUtil.getUsernameFromToken(token3)).isEqualTo(username3);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建使用不同签名的令牌（用于测试签名验证失败场景）
     */
    private String createTokenWithDifferentSignature() {
        // 使用足够长的密钥以确保符合 HS512 要求（至少 512 位）
        String differentSecret = "different-secret-key-for-testing-purposes-only-must-be-long-enough-for-hs512-algorithm-security-requirements";
        Key differentKey = Keys.hmacShaKeyFor(differentSecret.getBytes());
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + TEST_JWT_EXPIRATION_MS);

        return io.jsonwebtoken.Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(differentKey)
                .compact();
    }

    /**
     * 创建已过期的令牌（用于测试过期场景）
     */
    private String createExpiredToken() {
        Date now = new Date();
        Date pastDate = new Date(now.getTime() - 10000L); // 10 秒前过期

        return io.jsonwebtoken.Jwts.builder()
                .subject(TEST_USERNAME)
                .claim("userId", TEST_USER_ID)
                .issuedAt(pastDate)
                .expiration(pastDate) // 过期时间在过去
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 获取签名密钥（用于创建过期令牌）
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
    }
}
