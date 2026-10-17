package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.BusinessException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * OptionMappingUtil 单元测试类
 * 
 * <p>
 * 测试选项和视频映射的 JSON 序列化/反序列化功能，包括：
 * </p>
 * <ul>
 * <li>JSON 序列化 - 验证 Map 到 JSON 字符串的转换和异常处理</li>
 * <li>JSON 反序列化 - 验证 JSON 字符串到 Map 的解析和异常处理</li>
 * <li>数据验证 - 验证选项格式校验逻辑</li>
 * <li>边界场景 - 验证 null 值、空字符串、无效 JSON 等异常场景</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用真实的 ObjectMapper 进行端到端测试</li>
 * <li>覆盖所有公开方法和异常场景</li>
 * <li>验证 JSON 序列化/反序列化的完整性和健壮性</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("OptionMappingUtil 单元测试")
class OptionMappingUtilUnitTest extends BaseUnitTest {

    private OptionMappingUtil optionMappingUtil;

    // 测试用标准选项
    private static final Map<String, String> STANDARD_OPTIONS = Map.of(
            OptionMappingUtil.OPTION_A, "选项A",
            OptionMappingUtil.OPTION_B, "选项B",
            OptionMappingUtil.OPTION_C, "选项C",
            OptionMappingUtil.OPTION_D, "选项D");

    // 测试用标准视频映射
    private static final Map<String, String> STANDARD_VIDEO_MAPPING = Map.of(
            OptionMappingUtil.OPTION_A, "视频A",
            OptionMappingUtil.OPTION_B, "视频B",
            OptionMappingUtil.OPTION_C, "视频C",
            OptionMappingUtil.OPTION_D, "视频D");

    @BeforeEach
    void setUp() {
        optionMappingUtil = new OptionMappingUtil(new ObjectMapper());
    }

    // ==================== optionsToJson - 选项序列化测试 ====================

    @Nested
    @DisplayName("optionsToJson - 选项序列化测试")
    class OptionsToJsonTests {

        @Test
        @DisplayName("TC-OPT-01: 应该成功将标准选项 Map 转换为 JSON")
        void testOptionsToJson_ValidMap_ShouldConvertSuccessfully() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);

            // When
            String json = optionMappingUtil.optionsToJson(options);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"A\":\"选项A\"");
            assertThat(json).contains("\"B\":\"选项B\"");
            assertThat(json).contains("\"C\":\"选项C\"");
            assertThat(json).contains("\"D\":\"选项D\"");
        }

        @Test
        @DisplayName("TC-OPT-02: 当 Map 包含 null Key 时应该抛出业务异常")
        void testOptionsToJson_WithNullKey_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>();
            options.put(null, "无效选项");
            options.put(OptionMappingUtil.OPTION_A, "选项A");
            options.put(OptionMappingUtil.OPTION_B, "选项B");
            options.put(OptionMappingUtil.OPTION_C, "选项C");
            options.put(OptionMappingUtil.OPTION_D, "选项D");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项格式转换失败");
        }

        @Test
        @DisplayName("TC-OPT-03: 当 Map 为 null 时应该抛出业务异常")
        void testOptionsToJson_NullMap_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = null;

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不能为空");
        }

        @Test
        @DisplayName("TC-OPT-04: 当 Map 为空时应该抛出业务异常")
        void testOptionsToJson_EmptyMap_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>();

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不能为空");
        }

        @Test
        @DisplayName("TC-OPT-05: 当缺少必需选项时应该抛出业务异常")
        void testOptionsToJson_MissingRequiredOption_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>();
            options.put(OptionMappingUtil.OPTION_A, "选项A");
            options.put(OptionMappingUtil.OPTION_B, "选项B");
            options.put(OptionMappingUtil.OPTION_C, "选项C");
            // 缺少选项 D

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缺少选项: D");
        }

        @Test
        @DisplayName("TC-OPT-06: 当选项内容为空字符串时应该抛出业务异常")
        void testOptionsToJson_EmptyOptionContent_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            options.put(OptionMappingUtil.OPTION_A, "");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项内容不能为空: A");
        }

        @Test
        @DisplayName("TC-OPT-07: 当选项内容为 null 时应该抛出业务异常")
        void testOptionsToJson_NullOptionContent_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            options.put(OptionMappingUtil.OPTION_A, null);

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项内容不能为空: A");
        }

        @Test
        @DisplayName("TC-OPT-08: 当选项数量超过 4 个时应该抛出业务异常")
        void testOptionsToJson_TooManyOptions_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            options.put("E", "额外选项");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.optionsToJson(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项数量必须为4个(A/B/C/D)");
        }
    }

    // ==================== jsonToOptions - 选项反序列化测试 ====================

    @Nested
    @DisplayName("jsonToOptions - 选项反序列化测试")
    class JsonToOptionsTests {

        @Test
        @DisplayName("TC-OPT-09: 应该成功将有效 JSON 转换为选项 Map")
        void testJsonToOptions_ValidJson_ShouldConvertSuccessfully() {
            // Given
            String json = "{\"A\":\"选项A\",\"B\":\"选项B\",\"C\":\"选项C\",\"D\":\"选项D\"}";

            // When
            Map<String, String> options = optionMappingUtil.jsonToOptions(json);

            // Then
            assertThat(options).isNotNull();
            assertThat(options).hasSize(4);
            assertThat(options.get(OptionMappingUtil.OPTION_A)).isEqualTo("选项A");
            assertThat(options.get(OptionMappingUtil.OPTION_B)).isEqualTo("选项B");
            assertThat(options.get(OptionMappingUtil.OPTION_C)).isEqualTo("选项C");
            assertThat(options.get(OptionMappingUtil.OPTION_D)).isEqualTo("选项D");
        }

        @Test
        @DisplayName("TC-OPT-10: 当 JSON 为 null 时应该返回默认选项")
        void testJsonToOptions_NullJson_ShouldReturnDefaultOptions() {
            // Given
            String json = null;

            // When
            Map<String, String> options = optionMappingUtil.jsonToOptions(json);

            // Then
            assertThat(options).isNotNull();
            assertThat(options).isEqualTo(optionMappingUtil.getDefaultOptions());
        }

        @Test
        @DisplayName("TC-OPT-11: 当 JSON 为空字符串时应该返回默认选项")
        void testJsonToOptions_EmptyJson_ShouldReturnDefaultOptions() {
            // Given
            String json = "";

            // When
            Map<String, String> options = optionMappingUtil.jsonToOptions(json);

            // Then
            assertThat(options).isNotNull();
            assertThat(options).isEqualTo(optionMappingUtil.getDefaultOptions());
        }

        @Test
        @DisplayName("TC-OPT-12: 当 JSON 为空白字符串时应该返回默认选项")
        void testJsonToOptions_BlankJson_ShouldReturnDefaultOptions() {
            // Given
            String json = "   \t\n  ";

            // When
            Map<String, String> options = optionMappingUtil.jsonToOptions(json);

            // Then
            assertThat(options).isNotNull();
            assertThat(options).isEqualTo(optionMappingUtil.getDefaultOptions());
        }

        @Test
        @DisplayName("TC-OPT-13: 当 JSON 格式无效时应该抛出业务异常")
        void testJsonToOptions_InvalidJsonFormat_ShouldThrowBusinessException() {
            // Given
            String json = "{invalid json format}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项解析失败");
        }

        @Test
        @DisplayName("TC-OPT-14: 当 JSON 不是对象格式时应该抛出业务异常")
        void testJsonToOptions_NotObjectFormat_ShouldThrowBusinessException() {
            // Given
            String json = "[\"A\", \"B\", \"C\", \"D\"]";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项解析失败");
        }

        @Test
        @DisplayName("TC-OPT-15: 当 JSON 缺少必需选项时应该抛出业务异常")
        void testJsonToOptions_MissingRequiredOption_ShouldThrowBusinessException() {
            // Given
            String json = "{\"A\":\"选项A\",\"B\":\"选项B\",\"C\":\"选项C\"}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缺少选项: D");
        }

        @Test
        @DisplayName("TC-OPT-16: 当 JSON 包含额外字段时应该抛出业务异常")
        void testJsonToOptions_ExtraFields_ShouldThrowBusinessException() {
            // Given
            String json = "{\"A\":\"选项A\",\"B\":\"选项B\",\"C\":\"选项C\",\"D\":\"选项D\",\"E\":\"额外\"}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项数量必须为4个(A/B/C/D)");
        }

        @Test
        @DisplayName("TC-OPT-17: 当 JSON 值为 null 时应该抛出业务异常")
        void testJsonToOptions_NullValue_ShouldThrowBusinessException() {
            // Given
            String json = "{\"A\":null,\"B\":\"选项B\",\"C\":\"选项C\",\"D\":\"选项D\"}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项内容不能为空: A");
        }

        @Test
        @DisplayName("TC-OPT-18: 当 JSON 值为空字符串时应该抛出业务异常")
        void testJsonToOptions_EmptyValue_ShouldThrowBusinessException() {
            // Given
            String json = "{\"A\":\"\",\"B\":\"选项B\",\"C\":\"选项C\",\"D\":\"选项D\"}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToOptions(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项内容不能为空: A");
        }
    }

    // ==================== videoMappingToJson - 视频映射序列化测试 ====================

    @Nested
    @DisplayName("videoMappingToJson - 视频映射序列化测试")
    class VideoMappingToJsonTests {

        @Test
        @DisplayName("TC-VID-01: 应该成功将标准视频映射 Map 转换为 JSON")
        void testVideoMappingToJson_ValidMap_ShouldConvertSuccessfully() {
            // Given
            Map<String, String> videoMapping = new HashMap<>(STANDARD_VIDEO_MAPPING);

            // When
            String json = optionMappingUtil.videoMappingToJson(videoMapping);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"A\":\"视频A\"");
            assertThat(json).contains("\"B\":\"视频B\"");
            assertThat(json).contains("\"C\":\"视频C\"");
            assertThat(json).contains("\"D\":\"视频D\"");
        }

        @Test
        @DisplayName("TC-VID-02: 当 Map 为 null 时应该返回空对象 JSON")
        void testVideoMappingToJson_NullMap_ShouldAllow() {
            // Given
            Map<String, String> videoMapping = null;

            // When
            String json = optionMappingUtil.videoMappingToJson(videoMapping);

            // Then - 应该返回 "{}" 表示空映射
            assertThat(json).isNotNull();
            assertThat(json).isEqualTo("{}");
        }

        @Test
        @DisplayName("TC-VID-03: 当 Map 包含 null Key 时应该抛出业务异常")
        void testVideoMappingToJson_WithNullKey_ShouldThrowBusinessException() {
            // Given
            Map<String, String> videoMapping = new HashMap<>();
            videoMapping.put(null, "无效视频");
            videoMapping.put(OptionMappingUtil.OPTION_A, "视频A");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.videoMappingToJson(videoMapping))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("视频映射格式转换失败");
        }

        @Test
        @DisplayName("TC-VID-04: 应该支持空 Map")
        void testVideoMappingToJson_EmptyMap_ShouldConvertSuccessfully() {
            // Given
            Map<String, String> videoMapping = new HashMap<>();

            // When
            String json = optionMappingUtil.videoMappingToJson(videoMapping);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).isEqualTo("{}");
        }
    }

    // ==================== jsonToVideoMapping - 视频映射反序列化测试 ====================

    @Nested
    @DisplayName("jsonToVideoMapping - 视频映射反序列化测试")
    class JsonToVideoMappingTests {

        @Test
        @DisplayName("TC-VID-05: 应该成功将有效 JSON 转换为视频映射 Map")
        void testJsonToVideoMapping_ValidJson_ShouldConvertSuccessfully() {
            // Given
            String json = "{\"A\":123,\"B\":456,\"C\":789}";

            // When
            Map<String, Long> videoMapping = optionMappingUtil.jsonToVideoMapping(json);

            // Then
            assertThat(videoMapping).isNotNull();
            assertThat(videoMapping).hasSize(3);
            assertThat(videoMapping.get(OptionMappingUtil.OPTION_A)).isEqualTo(123L);
            assertThat(videoMapping.get(OptionMappingUtil.OPTION_B)).isEqualTo(456L);
            assertThat(videoMapping.get(OptionMappingUtil.OPTION_C)).isEqualTo(789L);
        }

        @Test
        @DisplayName("TC-VID-06: 当 JSON 为 null 时应该返回空 Map")
        void testJsonToVideoMapping_NullJson_ShouldReturnEmptyMap() {
            // Given
            String json = null;

            // When
            Map<String, Long> videoMapping = optionMappingUtil.jsonToVideoMapping(json);

            // Then
            assertThat(videoMapping).isNotNull();
            assertThat(videoMapping).isEmpty();
        }

        @Test
        @DisplayName("TC-VID-07: 当 JSON 为空字符串时应该返回空 Map")
        void testJsonToVideoMapping_EmptyJson_ShouldReturnEmptyMap() {
            // Given
            String json = "";

            // When
            Map<String, Long> videoMapping = optionMappingUtil.jsonToVideoMapping(json);

            // Then
            assertThat(videoMapping).isNotNull();
            assertThat(videoMapping).isEmpty();
        }

        @Test
        @DisplayName("TC-VID-08: 当 JSON 格式无效时应该抛出业务异常")
        void testJsonToVideoMapping_InvalidJsonFormat_ShouldThrowBusinessException() {
            // Given
            String json = "{not valid json}";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToVideoMapping(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("视频映射解析失败");
        }

        @Test
        @DisplayName("TC-VID-09: 当 JSON 不是对象格式时应该抛出业务异常")
        void testJsonToVideoMapping_NotObjectFormat_ShouldThrowBusinessException() {
            // Given
            String json = "[\"视频A\", \"视频B\"]";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.jsonToVideoMapping(json))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("视频映射解析失败");
        }

        @Test
        @DisplayName("TC-VID-10: 应该支持只有部分选项的视频映射")
        void testJsonToVideoMapping_PartialMapping_ShouldConvertSuccessfully() {
            // Given
            String json = "{\"A\":101,\"D\":104}";

            // When
            Map<String, Long> videoMapping = optionMappingUtil.jsonToVideoMapping(json);

            // Then
            assertThat(videoMapping).isNotNull();
            assertThat(videoMapping).hasSize(2);
            assertThat(videoMapping.get(OptionMappingUtil.OPTION_A)).isEqualTo(101L);
            assertThat(videoMapping.get(OptionMappingUtil.OPTION_D)).isEqualTo(104L);
        }
    }

    // ==================== validateOptions - 选项验证测试 ====================

    @Nested
    @DisplayName("validateOptions - 选项验证测试")
    class ValidateOptionsTests {

        @Test
        @DisplayName("TC-VAL-01: 应该成功验证标准选项")
        void testValidateOptions_StandardOptions_ShouldPass() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);

            // When & Then - 不应该抛出异常
            optionMappingUtil.validateOptions(options); // 不抛异常即通过
        }

        @Test
        @DisplayName("TC-VAL-02: 当选项为 null 时应该抛出业务异常")
        void testValidateOptions_NullOptions_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = null;

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.validateOptions(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不能为空");
        }

        @Test
        @DisplayName("TC-VAL-03: 当选项为空时应该抛出业务异常")
        void testValidateOptions_EmptyOptions_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>();

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.validateOptions(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不能为空");
        }

        @Test
        @DisplayName("TC-VAL-04: 当缺少选项 A 时应该抛出业务异常")
        void testValidateOptions_MissingOptionA_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>();
            options.put(OptionMappingUtil.OPTION_B, "选项B");
            options.put(OptionMappingUtil.OPTION_C, "选项C");
            options.put(OptionMappingUtil.OPTION_D, "选项D");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.validateOptions(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缺少选项: A");
        }

        @Test
        @DisplayName("TC-VAL-05: 当选项内容为空字符串时应该抛出业务异常")
        void testValidateOptions_EmptyContent_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            options.put(OptionMappingUtil.OPTION_B, "  ");

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.validateOptions(options))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项内容不能为空: B");
        }
    }

    // ==================== getDefaultOptions - 获取默认选项测试 ====================

    @Nested
    @DisplayName("getDefaultOptions - 默认选项测试")
    class GetDefaultOptionsTests {

        @Test
        @DisplayName("TC-DEF-01: 应该返回包含 4 个标准选项的 Map")
        void testGetDefaultOptions_ShouldReturnFourOptions() {
            // When
            Map<String, String> defaultOptions = optionMappingUtil.getDefaultOptions();

            // Then
            assertThat(defaultOptions).isNotNull();
            assertThat(defaultOptions).hasSize(4);
            assertThat(defaultOptions.containsKey(OptionMappingUtil.OPTION_A)).isTrue();
            assertThat(defaultOptions.containsKey(OptionMappingUtil.OPTION_B)).isTrue();
            assertThat(defaultOptions.containsKey(OptionMappingUtil.OPTION_C)).isTrue();
            assertThat(defaultOptions.containsKey(OptionMappingUtil.OPTION_D)).isTrue();
        }

        @Test
        @DisplayName("TC-DEF-02: 每次调用应该返回新的 Map 实例")
        void testGetDefaultOptions_MultipleCalls_ShouldReturnNewInstances() {
            // When
            Map<String, String> options1 = optionMappingUtil.getDefaultOptions();
            Map<String, String> options2 = optionMappingUtil.getDefaultOptions();

            // Then
            assertThat(options1).isNotSameAs(options2);
        }

        @Test
        @DisplayName("TC-DEF-03: 修改返回的 Map 不应该影响后续调用")
        void testGetDefaultOptions_ModifyingReturnedMap_ShouldNotAffectNextCall() {
            // Given
            Map<String, String> options = optionMappingUtil.getDefaultOptions();
            options.put(OptionMappingUtil.OPTION_A, "修改后的选项A");

            // When
            Map<String, String> freshOptions = optionMappingUtil.getDefaultOptions();

            // Then
            assertThat(freshOptions.get(OptionMappingUtil.OPTION_A)).isEqualTo("选项A");
        }
    }

    // ==================== getOptionContent - 获取选项内容测试 ====================

    @Nested
    @DisplayName("getOptionContent - 获取选项内容测试")
    class GetOptionContentTests {

        @Test
        @DisplayName("TC-GET-01: 应该成功获取存在的选项内容")
        void testGetOptionContent_ExistingOption_ShouldReturnContent() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            String optionKey = OptionMappingUtil.OPTION_A;

            // When
            String content = optionMappingUtil.getOptionContent(options, optionKey);

            // Then
            assertThat(content).isEqualTo("选项A");
        }

        @Test
        @DisplayName("TC-GET-02: 当选项 Map 为 null 时应该抛出业务异常")
        void testGetOptionContent_NullMap_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = null;
            String optionKey = OptionMappingUtil.OPTION_A;

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.getOptionContent(options, optionKey))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不存在");
        }

        @Test
        @DisplayName("TC-GET-03: 当选项不存在时应该抛出业务异常")
        void testGetOptionContent_NonExistentOption_ShouldThrowBusinessException() {
            // Given
            Map<String, String> options = new HashMap<>(STANDARD_OPTIONS);
            String optionKey = "X";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.getOptionContent(options, optionKey))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("选项不存在");
        }
    }

    // ==================== getVideoTitleForOption - 获取视频标题测试 ====================

    @Nested
    @DisplayName("getVideoTitleForOption - 获取视频标题测试")
    class GetVideoTitleForOptionTests {

        @Test
        @DisplayName("TC-VGET-01: 应该成功获取存在的视频标题")
        void testGetVideoTitleForOption_ExistingOption_ShouldReturnTitle() {
            // Given
            Map<String, String> videoMapping = new HashMap<>(STANDARD_VIDEO_MAPPING);
            String optionKey = OptionMappingUtil.OPTION_A;

            // When
            String title = optionMappingUtil.getVideoTitleForOption(videoMapping, optionKey);

            // Then
            assertThat(title).isEqualTo("视频A");
        }

        @Test
        @DisplayName("TC-VGET-02: 当视频映射为 null 时应该返回 null")
        void testGetVideoTitleForOption_NullMapping_ShouldReturnNull() {
            // Given
            Map<String, String> videoMapping = null;
            String optionKey = OptionMappingUtil.OPTION_A;

            // When
            String title = optionMappingUtil.getVideoTitleForOption(videoMapping, optionKey);

            // Then
            assertThat(title).isNull();
        }

        @Test
        @DisplayName("TC-VGET-03: 当选项不存在时应该返回 null")
        void testGetVideoTitleForOption_NonExistentOption_ShouldReturnNull() {
            // Given
            Map<String, String> videoMapping = new HashMap<>();
            videoMapping.put(OptionMappingUtil.OPTION_A, "视频A");
            String optionKey = "Z"; // 使用不存在的选项标识

            // When
            String title = optionMappingUtil.getVideoTitleForOption(videoMapping, optionKey);

            // Then
            assertThat(title).isNull();
        }
    }

    // ==================== setVideoTitleForOption - 设置视频标题测试 ====================

    @Nested
    @DisplayName("setVideoTitleForOption - 设置视频标题测试")
    class SetVideoTitleForOptionTests {

        @Test
        @DisplayName("TC-VSET-01: 应该成功设置视频标题")
        void testSetVideoTitleForOption_ValidParameters_ShouldSetTitle() {
            // Given
            Map<String, String> videoMapping = new HashMap<>();
            String optionKey = OptionMappingUtil.OPTION_A;
            String videoTitle = "测试视频";

            // When
            optionMappingUtil.setVideoTitleForOption(videoMapping, optionKey, videoTitle);

            // Then
            assertThat(videoMapping.get(optionKey)).isEqualTo(videoTitle);
        }

        @Test
        @DisplayName("TC-VSET-02: 当视频映射为 null 时应该抛出业务异常")
        void testSetVideoTitleForOption_NullMapping_ShouldThrowBusinessException() {
            // Given
            Map<String, String> videoMapping = null;
            String optionKey = OptionMappingUtil.OPTION_A;
            String videoTitle = "测试视频";

            // When & Then
            assertThatThrownBy(() -> optionMappingUtil.setVideoTitleForOption(videoMapping, optionKey, videoTitle))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("视频映射不能为空");
        }

        @Test
        @DisplayName("TC-VSET-03: 应该可以覆盖已存在的视频标题")
        void testSetVideoTitleForOption_ExistingOption_ShouldOverride() {
            // Given
            Map<String, String> videoMapping = new HashMap<>();
            videoMapping.put(OptionMappingUtil.OPTION_A, "原视频");
            String optionKey = OptionMappingUtil.OPTION_A;
            String newVideoTitle = "新视频";

            // When
            optionMappingUtil.setVideoTitleForOption(videoMapping, optionKey, newVideoTitle);

            // Then
            assertThat(videoMapping.get(optionKey)).isEqualTo(newVideoTitle);
        }
    }
}
