package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhixuewuli.common.base.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * PasswordUtil 单元测试类
 * 
 * <p>
 * 测试密码加密、验证和强度校验功能，包括：
 * </p>
 * <ul>
 * <li>密码加密 - 验证 BCrypt 加密的正确性和唯一性</li>
 * <li>密码验证 - 验证加密密码与原密码的匹配性</li>
 * <li>密码强度验证 - 验证密码复杂度规则（数字、字母、特殊字符、最小长度）</li>
 * <li>安全比较 - 验证防止时序攻击的安全字符串比较</li>
 * <li>边界场景 - 验证 null 值、空字符串、异常输入的处理</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用真实的 BCryptPasswordEncoder 进行端到端测试</li>
 * <li>覆盖所有公开方法和异常场景</li>
 * <li>验证密码强度规则的完整性</li>
 * <li>测试安全比较函数的时序攻击防护</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("PasswordUtil 单元测试")
class PasswordUtilUnitTest extends BaseUnitTest {

    private PasswordUtil passwordUtil;

    // 测试用密码
    private static final String VALID_PASSWORD = "Test@1234";
    private static final String ANOTHER_PASSWORD = "Pass#5678";

    @BeforeEach
    void setUp() {
        // 创建真实的 PasswordUtil 实例（因为需要调用真实的 BCrypt 加密库）
        passwordUtil = new PasswordUtil();
    }

    // ==================== encode - 密码加密测试 ====================

    @Nested
    @DisplayName("encode - 密码加密测试")
    class EncodeTests {

        @Test
        @DisplayName("TC-PWD-01: 应该成功加密有效的密码")
        void testEncode_ValidPassword_ShouldEncrypt() {
            // Given
            String rawPassword = VALID_PASSWORD;

            // When
            String encodedPassword = passwordUtil.encode(rawPassword);

            // Then
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).isNotEmpty();
            assertThat(encodedPassword).startsWith("$2a$"); // BCrypt 格式前缀
        }

        @Test
        @DisplayName("TC-PWD-02: 同一密码多次加密应该生成不同的结果（BCrypt 随机盐）")
        void testEncode_SamePasswordMultipleTimes_ShouldGenerateDifferentHashes() {
            // Given
            String rawPassword = VALID_PASSWORD;

            // When
            String encoded1 = passwordUtil.encode(rawPassword);
            String encoded2 = passwordUtil.encode(rawPassword);

            // Then
            assertThat(encoded1).isNotEqualTo(encoded2); // BCrypt 每次生成不同的哈希
        }

        @Test
        @DisplayName("TC-PWD-03: 不同密码应该生成不同的加密结果")
        void testEncode_DifferentPasswords_ShouldGenerateDifferentHashes() {
            // Given
            String password1 = VALID_PASSWORD;
            String password2 = ANOTHER_PASSWORD;

            // When
            String encoded1 = passwordUtil.encode(password1);
            String encoded2 = passwordUtil.encode(password2);

            // Then
            assertThat(encoded1).isNotEqualTo(encoded2);
        }

        @Test
        @DisplayName("TC-PWD-04: 加密后的密码长度应该符合 BCrypt 标准（60 个字符）")
        void testEncode_EncryptedPasswordLength_ShouldBeStandard() {
            // Given
            String rawPassword = VALID_PASSWORD;

            // When
            String encodedPassword = passwordUtil.encode(rawPassword);

            // Then
            assertThat(encodedPassword).hasSize(60); // BCrypt 哈希长度固定为 60
        }

        @Test
        @DisplayName("TC-PWD-05: 密码为 null 时应该抛出 IllegalArgumentException")
        void testEncode_NullPassword_ShouldThrowException() {
            // Given
            String nullPassword = null;

            // When & Then
            assertThatThrownBy(() -> passwordUtil.encode(nullPassword))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密码不能为空");
        }

        @Test
        @DisplayName("TC-PWD-06: 密码为空字符串时应该抛出 IllegalArgumentException")
        void testEncode_EmptyPassword_ShouldThrowException() {
            // Given
            String emptyPassword = "";

            // When & Then
            assertThatThrownBy(() -> passwordUtil.encode(emptyPassword))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("密码不能为空");
        }

        @Test
        @DisplayName("TC-PWD-07: 应该支持包含特殊字符的密码")
        void testEncode_SpecialCharacters_ShouldWork() {
            // Given
            String passwordWithSpecialChars = "Test@#$%123!";

            // When
            String encodedPassword = passwordUtil.encode(passwordWithSpecialChars);

            // Then
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).startsWith("$2a$");
        }

        @Test
        @DisplayName("TC-PWD-08: 应该支持包含中文字符的密码")
        void testEncode_ChineseCharacters_ShouldWork() {
            // Given
            String chinesePassword = "测试密码 123@abc";

            // When
            String encodedPassword = passwordUtil.encode(chinesePassword);

            // Then
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).startsWith("$2a$");
        }

        @Test
        @DisplayName("TC-PWD-09: 应该支持长密码")
        void testEncode_LongPassword_ShouldWork() {
            // Given
            String longPassword = "ThisIsAVeryLongPassword123!@#ThatShouldStillWorkCorrectlyWithBCryptEncryption";

            // When
            String encodedPassword = passwordUtil.encode(longPassword);

            // Then
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).startsWith("$2a$");
        }

        @Test
        @DisplayName("TC-PWD-10: 应该支持最短有效密码（8 位）")
        void testEncode_MinimumLengthPassword_ShouldWork() {
            // Given
            String minPassword = "Abc@1234"; // 刚好 8 位

            // When
            String encodedPassword = passwordUtil.encode(minPassword);

            // Then
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).startsWith("$2a$");
        }
    }

    // ==================== matches - 密码验证测试 ====================

    @Nested
    @DisplayName("matches - 密码匹配测试")
    class MatchesTests {

        @Test
        @DisplayName("TC-PWD-11: 正确的密码应该匹配成功")
        void testMatches_CorrectPassword_ShouldReturnTrue() {
            // Given
            String rawPassword = VALID_PASSWORD;
            String encodedPassword = passwordUtil.encode(rawPassword);

            // When
            boolean isMatch = passwordUtil.matches(rawPassword, encodedPassword);

            // Then
            assertThat(isMatch).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-12: 错误的密码应该匹配失败")
        void testMatches_WrongPassword_ShouldReturnFalse() {
            // Given
            String rawPassword = VALID_PASSWORD;
            String wrongPassword = "Wrong@1234";
            String encodedPassword = passwordUtil.encode(rawPassword);

            // When
            boolean isMatch = passwordUtil.matches(wrongPassword, encodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-13: 原始密码为 null 时应该返回 false")
        void testMatches_NullRawPassword_ShouldReturnFalse() {
            // Given
            String nullRawPassword = null;
            String encodedPassword = passwordUtil.encode(VALID_PASSWORD);

            // When
            boolean isMatch = passwordUtil.matches(nullRawPassword, encodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-14: 加密密码为 null 时应该返回 false")
        void testMatches_NullEncodedPassword_ShouldReturnFalse() {
            // Given
            String rawPassword = VALID_PASSWORD;
            String nullEncodedPassword = null;

            // When
            boolean isMatch = passwordUtil.matches(rawPassword, nullEncodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-15: 两个都为 null 时应该返回 false")
        void testMatches_BothNull_ShouldReturnFalse() {
            // Given
            String nullRawPassword = null;
            String nullEncodedPassword = null;

            // When
            boolean isMatch = passwordUtil.matches(nullRawPassword, nullEncodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-16: 格式无效的加密密码应该返回 false")
        void testMatches_InvalidEncodedPassword_ShouldReturnFalse() {
            // Given
            String rawPassword = VALID_PASSWORD;
            String invalidEncodedPassword = "invalid.hash.format";

            // When
            boolean isMatch = passwordUtil.matches(rawPassword, invalidEncodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-17: 使用不同密码加密的哈希应该匹配失败")
        void testMatches_DifferentPasswordHash_ShouldReturnFalse() {
            // Given
            String password1 = VALID_PASSWORD;
            String password2 = ANOTHER_PASSWORD;
            String encodedPassword1 = passwordUtil.encode(password1);

            // When
            boolean isMatch = passwordUtil.matches(password2, encodedPassword1);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-18: 空字符串密码应该返回 false")
        void testMatches_EmptyRawPassword_ShouldReturnFalse() {
            // Given
            String emptyRawPassword = "";
            String encodedPassword = passwordUtil.encode(VALID_PASSWORD);

            // When
            boolean isMatch = passwordUtil.matches(emptyRawPassword, encodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-19: 大小写敏感验证")
        void testMatches_CaseSensitive_ShouldReturnFalseForWrongCase() {
            // Given
            String rawPassword = "Test@1234";
            String wrongCasePassword = "test@1234"; // 首字母小写
            String encodedPassword = passwordUtil.encode(rawPassword);

            // When
            boolean isMatch = passwordUtil.matches(wrongCasePassword, encodedPassword);

            // Then
            assertThat(isMatch).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-20: 包含空格的密码应该精确匹配")
        void testMatches_PasswordWithSpaces_ShouldMatchExactly() {
            // Given
            String rawPassword = "Test @1234";
            String encodedPassword = passwordUtil.encode(rawPassword);

            // When
            boolean isMatch = passwordUtil.matches(rawPassword, encodedPassword);

            // Then
            assertThat(isMatch).isTrue();

            // And - 没有空格的版本应该不匹配
            assertThat(passwordUtil.matches("Test@1234", encodedPassword)).isFalse();
        }
    }

    // ==================== isValidPassword - 密码强度验证测试 ====================

    @Nested
    @DisplayName("isValidPassword - 密码强度验证测试")
    class IsValidPasswordTests {

        @Test
        @DisplayName("TC-PWD-21: 符合强度要求的密码应该验证通过")
        void testIsValidPassword_ValidPassword_ShouldReturnTrue() {
            // Given
            String validPassword = VALID_PASSWORD; // "Test@1234" - 包含数字、字母、特殊字符，长度>=8

            // When
            boolean isValid = passwordUtil.isValidPassword(validPassword);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-22: 缺少数字的密码应该验证失败")
        void testIsValidPassword_NoDigits_ShouldReturnFalse() {
            // Given
            String passwordWithoutDigits = "Test@abc!"; // 没有数字

            // When
            boolean isValid = passwordUtil.isValidPassword(passwordWithoutDigits);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-23: 缺少字母的密码应该验证失败")
        void testIsValidPassword_NoLetters_ShouldReturnFalse() {
            // Given
            String passwordWithoutLetters = "123@#!"; // 没有字母

            // When
            boolean isValid = passwordUtil.isValidPassword(passwordWithoutLetters);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-24: 缺少特殊字符的密码应该验证失败")
        void testIsValidPassword_NoSpecialChars_ShouldReturnFalse() {
            // Given
            String passwordWithoutSpecialChars = "Test1234"; // 没有特殊字符

            // When
            boolean isValid = passwordUtil.isValidPassword(passwordWithoutSpecialChars);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-25: 长度小于 8 位的密码应该验证失败")
        void testIsValidPassword_TooShort_ShouldReturnFalse() {
            // Given
            String shortPassword = "Abc@123"; // 只有 7 位

            // When
            boolean isValid = passwordUtil.isValidPassword(shortPassword);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-26: null 密码应该验证失败")
        void testIsValidPassword_NullPassword_ShouldReturnFalse() {
            // Given
            String nullPassword = null;

            // When
            boolean isValid = passwordUtil.isValidPassword(nullPassword);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-27: 空字符串密码应该验证失败")
        void testIsValidPassword_EmptyPassword_ShouldReturnFalse() {
            // Given
            String emptyPassword = "";

            // When
            boolean isValid = passwordUtil.isValidPassword(emptyPassword);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-28: 只包含字母的密码应该验证失败")
        void testIsValidPassword_OnlyLetters_ShouldReturnFalse() {
            // Given
            String onlyLetters = "abcdefgh";

            // When
            boolean isValid = passwordUtil.isValidPassword(onlyLetters);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-29: 只包含数字的密码应该验证失败")
        void testIsValidPassword_OnlyDigits_ShouldReturnFalse() {
            // Given
            String onlyDigits = "12345678";

            // When
            boolean isValid = passwordUtil.isValidPassword(onlyDigits);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-30: 只包含特殊字符的密码应该验证失败")
        void testIsValidPassword_OnlySpecialChars_ShouldReturnFalse() {
            // Given
            String onlySpecialChars = "!@#$%^&*";

            // When
            boolean isValid = passwordUtil.isValidPassword(onlySpecialChars);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("TC-PWD-31: 刚好 8 位且符合所有要求应该验证通过")
        void testIsValidPassword_Exactly8Chars_ShouldReturnTrue() {
            // Given
            String exactly8Chars = "Abc@1234";

            // When
            boolean isValid = passwordUtil.isValidPassword(exactly8Chars);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-32: 支持多种特殊字符")
        void testIsValidPassword_MultipleSpecialChars_ShouldReturnTrue() {
            // Given
            String passwordWithVariousSpecialChars = "Test123!@#$%^&*()";

            // When
            boolean isValid = passwordUtil.isValidPassword(passwordWithVariousSpecialChars);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-33: 大小写字母组合应该满足字母要求")
        void testIsValidPassword_MixedCase_ShouldReturnTrue() {
            // Given
            String mixedCasePassword = "TESTtest@1";

            // When
            boolean isValid = passwordUtil.isValidPassword(mixedCasePassword);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-34: 超长密码且符合要求应该验证通过")
        void testIsValidPassword_VeryLongPassword_ShouldReturnTrue() {
            // Given
            String veryLongPassword = "ThisIsAVeryLongAndSecurePassword123!@#";

            // When
            boolean isValid = passwordUtil.isValidPassword(veryLongPassword);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("TC-PWD-35: 空白字符串应该验证失败")
        void testIsValidPassword_BlankPassword_ShouldReturnFalse() {
            // Given
            String blankPassword = "   ";

            // When
            boolean isValid = passwordUtil.isValidPassword(blankPassword);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    // ==================== secureEquals - 安全字符串比较测试 ====================

    @Nested
    @DisplayName("secureEquals - 安全字符串比较测试")
    class SecureEqualsTests {

        @Test
        @DisplayName("TC-SEC-01: 相同的字符串应该返回 true")
        void testSecureEquals_SameStrings_ShouldReturnTrue() {
            // Given
            String str1 = "test_string";
            String str2 = "test_string";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-02: 不同的字符串应该返回 false")
        void testSecureEquals_DifferentStrings_ShouldReturnFalse() {
            // Given
            String str1 = "test_string1";
            String str2 = "test_string2";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TC-SEC-03: 两个 null 应该返回 true")
        void testSecureEquals_BothNull_ShouldReturnTrue() {
            // Given
            String nullStr1 = null;
            String nullStr2 = null;

            // When
            boolean result = passwordUtil.secureEquals(nullStr1, nullStr2);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-04: 一个 null 一个非 null 应该返回 false")
        void testSecureEquals_OneNullOneNotNull_ShouldReturnFalse() {
            // Given
            String nullStr = null;
            String nonNullStr = "test";

            // When
            boolean result1 = passwordUtil.secureEquals(nullStr, nonNullStr);
            boolean result2 = passwordUtil.secureEquals(nonNullStr, nullStr);

            // Then
            assertThat(result1).isFalse();
            assertThat(result2).isFalse();
        }

        @Test
        @DisplayName("TC-SEC-05: 长度不同的字符串应该返回 false")
        void testSecureEquals_DifferentLength_ShouldReturnFalse() {
            // Given
            String str1 = "short";
            String str2 = "much_longer_string";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TC-SEC-06: 只有一个字符不同应该返回 false")
        void testSecureEquals_OneCharDifference_ShouldReturnFalse() {
            // Given
            String str1 = "test_string";
            String str2 = "test_strinG";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TC-SEC-07: 空字符串应该返回 true")
        void testSecureEquals_EmptyStrings_ShouldReturnTrue() {
            // Given
            String emptyStr1 = "";
            String emptyStr2 = "";

            // When
            boolean result = passwordUtil.secureEquals(emptyStr1, emptyStr2);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-08: 包含特殊字符的相同字符串应该返回 true")
        void testSecureEquals_SameStringsWithSpecialChars_ShouldReturnTrue() {
            // Given
            String str1 = "test@#$string!";
            String str2 = "test@#$string!";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("TC-SEC-09: 第一个字符不同应该返回 false")
        void testSecureEquals_FirstCharDifferent_ShouldReturnFalse() {
            // Given
            String str1 = "abcdef";
            String str2 = "xbcdef";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TC-SEC-10: 最后一个字符不同应该返回 false")
        void testSecureEquals_LastCharDifferent_ShouldReturnFalse() {
            // Given
            String str1 = "abcdef";
            String str2 = "abcdeX";

            // When
            boolean result = passwordUtil.secureEquals(str1, str2);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ==================== 集成场景测试 ====================

    @Nested
    @DisplayName("集成场景测试")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("TC-INT-01: 完整的密码处理流程 - 加密、验证、强度检查")
        void testCompletePasswordWorkflow() {
            // Given
            String rawPassword = "SecureP@ss123";

            // When - 验证密码强度
            boolean isValidStrength = passwordUtil.isValidPassword(rawPassword);

            // Then - 强度应该符合要求
            assertThat(isValidStrength).isTrue();

            // When - 加密密码
            String encodedPassword = passwordUtil.encode(rawPassword);

            // Then - 加密后的密码应该有效
            assertThat(encodedPassword).isNotNull();
            assertThat(encodedPassword).startsWith("$2a$");

            // When - 验证密码匹配
            boolean isMatch = passwordUtil.matches(rawPassword, encodedPassword);

            // Then - 应该匹配成功
            assertThat(isMatch).isTrue();
        }

        @Test
        @DisplayName("TC-INT-02: 弱密码无法通过强度验证但仍可加密")
        void testWeakPassword_FailsValidationButCanStillEncrypt() {
            // Given
            String weakPassword = "weak"; // 太短，无特殊字符

            // When - 强度验证
            boolean isValidStrength = passwordUtil.isValidPassword(weakPassword);

            // Then - 强度验证失败
            assertThat(isValidStrength).isFalse();

            // When - 但仍然可以加密（工具类不阻止加密弱密码）
            String encodedPassword = passwordUtil.encode(weakPassword);

            // Then - 加密成功
            assertThat(encodedPassword).isNotNull();

            // And - 匹配仍然有效
            assertThat(passwordUtil.matches(weakPassword, encodedPassword)).isTrue();
        }

        @Test
        @DisplayName("TC-INT-03: 多用户密码独立性验证")
        void testMultipleUsers_PasswordIndependence() {
            // Given
            String user1Password = "User1@Pass1";
            String user2Password = "User2@Pass2";
            String user3Password = "User3@Pass3";

            // When
            String encoded1 = passwordUtil.encode(user1Password);
            String encoded2 = passwordUtil.encode(user2Password);
            String encoded3 = passwordUtil.encode(user3Password);

            // Then
            // 每个密码只能匹配自己的哈希
            assertThat(passwordUtil.matches(user1Password, encoded1)).isTrue();
            assertThat(passwordUtil.matches(user1Password, encoded2)).isFalse();
            assertThat(passwordUtil.matches(user1Password, encoded3)).isFalse();

            assertThat(passwordUtil.matches(user2Password, encoded1)).isFalse();
            assertThat(passwordUtil.matches(user2Password, encoded2)).isTrue();
            assertThat(passwordUtil.matches(user2Password, encoded3)).isFalse();

            assertThat(passwordUtil.matches(user3Password, encoded1)).isFalse();
            assertThat(passwordUtil.matches(user3Password, encoded2)).isFalse();
            assertThat(passwordUtil.matches(user3Password, encoded3)).isTrue();
        }

        @Test
        @DisplayName("TC-INT-04: 密码修改场景 - 旧密码失效，新密码生效")
        void testPasswordChangeScenario() {
            // Given
            String oldPassword = "Old@Pass123";
            String newPassword = "New@Pass456";

            // When - 加密旧密码
            String oldEncoded = passwordUtil.encode(oldPassword);

            // Then - 旧密码匹配
            assertThat(passwordUtil.matches(oldPassword, oldEncoded)).isTrue();

            // When - 加密新密码
            String newEncoded = passwordUtil.encode(newPassword);

            // Then - 新密码匹配
            assertThat(passwordUtil.matches(newPassword, newEncoded)).isTrue();

            // And - 旧密码不匹配新哈希
            assertThat(passwordUtil.matches(oldPassword, newEncoded)).isFalse();

            // And - 新密码不匹配旧哈希
            assertThat(passwordUtil.matches(newPassword, oldEncoded)).isFalse();
        }

        @Test
        @DisplayName("TC-INT-05: 中文字符密码的完整处理流程")
        void testChinesePasswordWorkflow() {
            // Given
            String chinesePassword = "中文密码 123@ABC";

            // When - 强度验证
            boolean isValidStrength = passwordUtil.isValidPassword(chinesePassword);

            // Then - 中文字符不影响强度验证（只要有数字、字母、特殊字符）
            assertThat(isValidStrength).isTrue();

            // When - 加密
            String encodedPassword = passwordUtil.encode(chinesePassword);

            // Then - 加密成功
            assertThat(encodedPassword).isNotNull();

            // And - 匹配成功
            assertThat(passwordUtil.matches(chinesePassword, encodedPassword)).isTrue();
        }
    }
}
