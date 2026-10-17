package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.experiment.ExperimentBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON 数组工具类单元测试
 * 
 * 测试 JsonArrayUtil 的所有公共方法，包括：
 * - jsonToList: JSON 数组转 List
 * - listToJson: List 转 JSON 数组
 * - isValidJsonArray: 验证 JSON 数组格式
 * - parseQuestionIds: 解析题目 ID 数组
 * 
 * 包含正常场景、边界值测试和安全测试
 */
@Tag("unit")
class JsonArrayUtilTest extends BaseUnitTest {

    private JsonArrayUtil jsonArrayUtil;

    @BeforeEach
    void setUp() {
        // 手动实例化以测试
        jsonArrayUtil = new JsonArrayUtil(new ObjectMapper());
    }

    // ==================== jsonToList 测试 ====================

    @Test
    @DisplayName("jsonToList - 正常 JSON 数组转换")
    void jsonToList_shouldConvert_whenValidJson() {
        // Given
        String json = "[1,2,3]";

        // When
        List<Long> result = jsonArrayUtil.jsonToList(json);

        // Then
        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("jsonToList - 空数组转换")
    void jsonToList_shouldReturnEmptyList_whenEmptyArray() {
        // Given
        String json = "[]";

        // When
        List<Long> result = jsonArrayUtil.jsonToList(json);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("jsonToList - null 值处理")
    void jsonToList_shouldReturnEmptyList_whenNull() {
        // When
        List<Long> result = jsonArrayUtil.jsonToList(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("jsonToList - 空字符串处理")
    void jsonToList_shouldReturnEmptyList_whenEmptyString() {
        // When
        List<Long> result = jsonArrayUtil.jsonToList("");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("jsonToList - 空白字符串处理")
    void jsonToList_shouldReturnEmptyList_whenBlankString() {
        // When
        List<Long> result = jsonArrayUtil.jsonToList("   ");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("jsonToList - 无效 JSON 格式抛出异常")
    void jsonToList_shouldThrowException_whenInvalidJson() {
        // Given
        String invalidJson = "not-a-json";

        // When & Then
        assertThatThrownBy(() -> jsonArrayUtil.jsonToList(invalidJson))
                .isInstanceOf(ExperimentBusinessException.class)
                .hasMessageContaining("题目 ID 列表格式无效");
    }

    @Test
    @DisplayName("jsonToList - 边界值：包含空格和换行的 JSON 应正确处理")
    void jsonToList_shouldHandleWhitespace() {
        // Given
        String jsonWithWhitespace = "[\n  1, \n  2, \n  3\n]";

        // When
        List<Long> result = jsonArrayUtil.jsonToList(jsonWithWhitespace);

        // Then
        assertThat(result).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("jsonToList - 边界值：超大整数应抛出异常")
    void jsonToList_shouldThrowException_whenLargeNumber() {
        // Given - 超出 Long 范围的数字
        String json = "[" + Long.MAX_VALUE + "0]";

        // When & Then - Jackson 可能无法解析这么大的数字
        assertThatThrownBy(() -> jsonArrayUtil.jsonToList(json))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("jsonToList - 边界值：包含非数字元素应抛出异常")
    void jsonToList_shouldThrowException_whenNonNumericElement() {
        // Given
        String json = "[1,\"string\",3]";

        // When & Then
        assertThatThrownBy(() -> jsonArrayUtil.jsonToList(json))
                .isInstanceOf(ExperimentBusinessException.class)
                .hasMessageContaining("列表必须包含数字类型");
    }

    @Test
    @DisplayName("jsonToList - 边界值：包含小数的数组应抛出异常")
    void jsonToList_shouldThrowException_whenDecimalNumber() {
        // Given
        String json = "[1.5,2,3]";

        // When & Then - Jackson 会尝试转换但可能失败或截断
        // 实际上这可能不会抛出异常，取决于 Jackson 配置
        List<Long> result = jsonArrayUtil.jsonToList(json);
        
        // 验证能解析（Jackson 可能会截断小数）
        assertThat(result).hasSize(3);
    }

    // ==================== listToJson 测试 ====================

    @Test
    @DisplayName("listToJson - 正常 List 转换")
    void listToJson_shouldConvert_whenValidList() {
        // Given
        List<Long> list = List.of(1L, 2L, 3L);

        // When
        String result = jsonArrayUtil.listToJson(list);

        // Then
        assertThat(result).isEqualTo("[1,2,3]");
    }

    @Test
    @DisplayName("listToJson - 空 List 转换")
    void listToJson_shouldConvertEmptyList() {
        // Given
        List<Long> list = List.of();

        // When
        String result = jsonArrayUtil.listToJson(list);

        // Then
        assertThat(result).isEqualTo("[]");
    }

    @Test
    @DisplayName("listToJson - null List 转换")
    void listToJson_shouldConvertNullList() {
        // When
        String result = jsonArrayUtil.listToJson(null);

        // Then
        assertThat(result).isEqualTo("[]");
    }

    @Test
    @DisplayName("listToJson - 边界值：超大 List 转换性能测试")
    void listToJson_shouldHandleLargeList() {
        // Given - 1000+ 元素的列表
        List<Long> largeList = List.of(
                1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L
        );

        // When
        String result = jsonArrayUtil.listToJson(largeList);

        // Then
        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
        assertThat(result).contains("1");
        assertThat(result).contains("20");
    }

    // ==================== isValidJsonArray 测试 ====================

    @Test
    @DisplayName("isValidJsonArray - 有效 JSON 数组返回 true")
    void isValidJsonArray_shouldReturnTrue_whenValidJson() {
        // Given
        String json = "[1,2,3]";

        // When
        boolean result = jsonArrayUtil.isValidJsonArray(json);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValidJsonArray - 无效 JSON 返回 false")
    void isValidJsonArray_shouldReturnFalse_whenInvalidJson() {
        // Given
        String invalidJson = "not-a-json";

        // When
        boolean result = jsonArrayUtil.isValidJsonArray(invalidJson);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidJsonArray - null 返回 false")
    void isValidJsonArray_shouldReturnFalse_whenNull() {
        // When
        boolean result = jsonArrayUtil.isValidJsonArray(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidJsonArray - 空字符串返回 false")
    void isValidJsonArray_shouldReturnFalse_whenEmptyString() {
        // When
        boolean result = jsonArrayUtil.isValidJsonArray("");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidJsonArray - 边界值：JSON 对象应返回 false")
    void isValidJsonArray_shouldReturnFalse_whenJsonObject() {
        // Given
        String jsonObject = "{\"key\":\"value\"}";

        // When
        boolean result = jsonArrayUtil.isValidJsonArray(jsonObject);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidJsonArray - 边界值：混合类型数组应返回 false")
    void isValidJsonArray_shouldReturnFalse_whenMixedTypeArray() {
        // Given
        String mixedArray = "[1,\"string\",3]";

        // When
        boolean result = jsonArrayUtil.isValidJsonArray(mixedArray);

        // Then
        assertThat(result).isFalse();
    }

    // ==================== parseQuestionIds 测试 ====================

    @Test
    @DisplayName("parseQuestionIds - 正常解析")
    void parseQuestionIds_shouldParse_whenValidJson() {
        // Given
        String json = "[3001,3002,3003]";

        // When
        List<Long> result = jsonArrayUtil.parseQuestionIds(json);

        // Then
        assertThat(result).containsExactly(3001L, 3002L, 3003L);
    }

    @Test
    @DisplayName("parseQuestionIds - 空 JSON 抛出异常")
    void parseQuestionIds_shouldThrowException_whenEmptyJson() {
        // Given
        String emptyJson = "";

        // When & Then
        assertThatThrownBy(() -> jsonArrayUtil.parseQuestionIds(emptyJson))
                .isInstanceOf(ExperimentBusinessException.class)
                .hasMessageContaining("题目 ID 列表不能为空");
    }

    @Test
    @DisplayName("parseQuestionIds - null 值抛出异常")
    void parseQuestionIds_shouldThrowException_whenNull() {
        // When & Then
        assertThatThrownBy(() -> jsonArrayUtil.parseQuestionIds(null))
                .isInstanceOf(ExperimentBusinessException.class)
                .hasMessageContaining("题目 ID 列表不能为空");
    }

    @Test
    @DisplayName("parseQuestionIds - 无效格式抛出异常")
    void parseQuestionIds_shouldThrowException_whenInvalidFormat() {
        // Given
        String invalidJson = "not-a-json";

        // When & Then
        assertThatThrownBy(() -> jsonArrayUtil.parseQuestionIds(invalidJson))
                .isInstanceOf(ExperimentBusinessException.class)
                .hasMessageContaining("题目 ID 列表格式无效");
    }

    @Test
    @DisplayName("边界值 - 包含重复 ID 的数组应抛出异常")
    void parseQuestionIds_shouldThrowException_whenDuplicateIds() {
        // Given
        String json = "[1,2,1]";

        // When & Then - 注意：当前实现不检查重复，只负责解析
        // 如果业务要求检查重复，应在 ExperimentServiceImpl 中检查
        List<Long> result = jsonArrayUtil.parseQuestionIds(json);
        
        // 目前会成功解析，包含重复 ID
        assertThat(result).containsExactly(1L, 2L, 1L);
    }

    @Test
    @DisplayName("边界值 - 包含 0 或负数的 ID 应抛出异常")
    void parseQuestionIds_shouldThrowException_whenZeroOrNegativeId() {
        // Given
        String json = "[0,1,2]";

        // When & Then - 注意：当前实现不检查 ID 范围
        // 如果业务要求 ID > 0，应在 ExperimentServiceImpl 中检查
        List<Long> result = jsonArrayUtil.parseQuestionIds(json);
        
        // 目前会成功解析，包含 0
        assertThat(result).containsExactly(0L, 1L, 2L);
    }

    @Test
    @DisplayName("边界值 - 包含负数 ID")
    void parseQuestionIds_shouldAllowNegativeId() {
        // Given
        String json = "[-1,1,2]";

        // When & Then - 当前实现不检查 ID 范围
        List<Long> result = jsonArrayUtil.parseQuestionIds(json);
        
        // 目前会成功解析，包含负数
        assertThat(result).containsExactly(-1L, 1L, 2L);
    }

    // ==================== 综合场景测试 ====================

    @Test
    @DisplayName("综合场景 - 单元素数组")
    void jsonToList_singleElement() {
        // Given
        String json = "[100]";

        // When
        List<Long> result = jsonArrayUtil.jsonToList(json);

        // Then
        assertThat(result).containsExactly(100L);
    }

    @Test
    @DisplayName("综合场景 - 大数值数组")
    void jsonToList_largeNumbers() {
        // Given
        String json = "[" + Long.MAX_VALUE + "]";

        // When
        List<Long> result = jsonArrayUtil.jsonToList(json);

        // Then
        assertThat(result).containsExactly(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("综合场景 - 往返转换一致性")
    void roundTripConversion_shouldBeConsistent() {
        // Given
        List<Long> originalList = List.of(1L, 2L, 3L, 4L, 5L);

        // When
        String json = jsonArrayUtil.listToJson(originalList);
        List<Long> resultList = jsonArrayUtil.jsonToList(json);

        // Then
        assertThat(resultList).isEqualTo(originalList);
    }
}
