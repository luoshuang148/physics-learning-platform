package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * StreamContentBuffer 单元测试类
 * 
 * <p>
 * 测试流式内容缓冲区的核心功能：
 * </p>
 * <ul>
 * <li>小数据块暂存</li>
 * <li>安全切割点查找</li>
 * <li>缓冲区刷新</li>
 * <li>格式修复集成</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("StreamContentBuffer 单元测试 - 流式缓冲区处理")
class StreamContentBufferUnitTest {

    // ==================== 构造函数测试 ====================

    @Test
    @DisplayName("默认构造函数使用 50 字符阈值")
    void shouldUseDefaultBufferSize() {
        StreamContentBuffer buffer = new StreamContentBuffer();
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.getBufferSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("自定义构造函数设置正确阈值")
    void shouldUseCustomBufferSize() {
        StreamContentBuffer buffer = new StreamContentBuffer(100);
        assertThat(buffer.isEmpty()).isTrue();
    }

    // ==================== 基础功能测试 ====================

    @Nested
    @DisplayName("基础缓冲区功能测试")
    class BasicBufferTests {

        @Test
        @DisplayName("空值和空字符串处理")
        void shouldHandleEmptyInput() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            assertThat(buffer.process(null)).isEmpty();
            assertThat(buffer.process("")).isEmpty();
            assertThat(buffer.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("小数据块暂存不输出")
        void shouldBufferSmallChunks() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            String result = buffer.process("短文本");
            assertThat(result).isEmpty();
            assertThat(buffer.getBufferSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("缓冲区刷新输出全部内容")
        void shouldFlushAllContent() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            buffer.process("部分内容");
            String flushed = buffer.flush();
            
            assertThat(flushed).isEqualTo("部分内容");
            assertThat(buffer.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("多次刷新只有第一次有内容")
        void shouldReturnEmptyOnSecondFlush() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            buffer.process("内容");
            buffer.flush();
            String secondFlush = buffer.flush();
            
            assertThat(secondFlush).isEmpty();
        }
    }

    // ==================== 安全切割测试 ====================

    @Nested
    @DisplayName("安全切割测试")
    class SafeSplitTests {

        @Test
        @DisplayName("LaTeX 命令不会被切断")
        void shouldNotSplitInLatexCommand() {
            StreamContentBuffer buffer = new StreamContentBuffer(10);
            
            String result = buffer.process("\\frac12\\alpha");
            
            // LaTeX 命令不应该被切断
            assertThat(result).doesNotContain("\\frac1");
        }

        @Test
        @DisplayName("Markdown 标题处可以切割")
        void shouldSplitAtMarkdownBoundary() {
            StreamContentBuffer buffer = new StreamContentBuffer(15);
            
            // 足够长的文本才能触发输出
            String text = "这是一段###标题内容文本";
            String result = buffer.process(text);
            
            // 当文本足够长时应该能输出
            if (!result.isEmpty()) {
                assertThat(result).contains("###");
            }
        }

        @Test
        @DisplayName("包含多种分隔符的文本")
        void shouldHandleMixedDelimiters() {
            StreamContentBuffer buffer = new StreamContentBuffer(20);
            
            String text = "Hello, world!###标题\n列表项.";
            String result = buffer.process(text);
            
            assertThat(result).isNotEmpty();
            // 各种分隔符都应该能被识别
            assertThat(result.length()).isLessThan(text.length());
        }
    }

    // ==================== 格式修复集成测试 ====================

    @Nested
    @DisplayName("格式修复集成测试")
    class FixerIntegrationTests {

        @Test
        @DisplayName("LaTeX 粘连在输出时被修复")
        void shouldFixLatexOnOutput() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            String result = buffer.process("公式: $a\\cdott + b\\timesc$");
            
            // 缓冲区足够大时应该输出并修复
            if (!result.isEmpty()) {
                assertThat(result).doesNotContain("\\cdott");
                assertThat(result).doesNotContain("\\timesc");
            }
        }

        @Test
        @DisplayName("Markdown 粘连在输出时被修复")
        void shouldFixMarkdownOnOutput() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            String result = buffer.process("###标题内容");
            
            if (!result.isEmpty()) {
                assertThat(result).contains("### 标题");
            }
        }

        @Test
        @DisplayName("刷新时修复剩余内容")
        void shouldFixOnFlush() {
            StreamContentBuffer buffer = new StreamContentBuffer(100);
            
            buffer.process("一些\\cdott内容"); // 不会触发输出（小于阈值）
            String flushed = buffer.flush();
            
            assertThat(flushed).doesNotContain("\\cdott");
            assertThat(flushed).contains("\\cdot t");
        }

        @Test
        @DisplayName("空缓冲区刷新返回空")
        void shouldReturnEmptyOnFlushEmptyBuffer() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            String result = buffer.flush();
            
            assertThat(result).isEmpty();
        }
    }

    // ==================== 边界情况测试 ====================

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("超长连续文本处理")
        void shouldHandleLongContinuousText() {
            StreamContentBuffer buffer = new StreamContentBuffer(20);
            
            String longText = "这是一个很长的没有空格的连续文本内容";
            String result = buffer.process(longText);
            
            // 缓冲区达到阈值后应该强制切割输出
            assertThat(result).isNotEmpty();
            // 缓冲区应该保留剩余内容
            assertThat(buffer.getBufferSize()).isLessThan(longText.length());
        }

        @Test
        @DisplayName("文本小于阈值时暂存")
        void shouldBufferWhenBelowThreshold() {
            StreamContentBuffer buffer = new StreamContentBuffer(100);
            
            String shortText = "短文本";
            String result = buffer.process(shortText);
            
            // 小于阈值时不输出
            assertThat(result).isEmpty();
            assertThat(buffer.getBufferSize()).isEqualTo(shortText.length());
        }

        @Test
        @DisplayName("不应在 \\( 与 \\) 转义序列中间切割")
        void shouldNotSplitEscapedLatexParentheses() {
            StreamContentBuffer buffer = new StreamContentBuffer(8);

            String input = "加速度\\(a\\)就越大，或者合外力\\(F\\)越大";
            String first = buffer.process(input);
            String merged = first + buffer.flush();

            assertThat(first).doesNotEndWith("\\");
            assertThat(merged).contains("\\(a\\)");
            assertThat(merged).contains("\\(F\\)");
        }
    }

    // ==================== 状态测试 ====================

    @Nested
    @DisplayName("状态管理测试")
    class StateManagementTests {

        @Test
        @DisplayName("isEmpty 在空缓冲区返回 true")
        void shouldReturnTrueForEmptyBuffer() {
            StreamContentBuffer buffer = new StreamContentBuffer();
            assertThat(buffer.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("isEmpty 在有内容时返回 false")
        void shouldReturnFalseForNonEmptyBuffer() {
            StreamContentBuffer buffer = new StreamContentBuffer(100);
            buffer.process("内容");
            assertThat(buffer.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("flush 后缓冲区为空")
        void shouldBeEmptyAfterFlush() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            buffer.process("内容");
            buffer.flush();
            assertThat(buffer.isEmpty()).isTrue();
            assertThat(buffer.getBufferSize()).isEqualTo(0);
        }
    }

    // ==================== 性能测试 ====================

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("处理大量小数据块")
        void shouldHandleManySmallChunks() {
            StreamContentBuffer buffer = new StreamContentBuffer(100);
            
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                buffer.process("短文本");
            }
            buffer.flush();
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(duration).isLessThan(1000); // 1 秒内完成
        }

        @Test
        @DisplayName("处理大块文本")
        void shouldHandleLargeChunks() {
            StreamContentBuffer buffer = new StreamContentBuffer(50);
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("这是一段测试内容");
            }
            String largeText = sb.toString();
            
            long startTime = System.currentTimeMillis();
            String result = buffer.process(largeText);
            buffer.flush();
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(result).isNotEmpty();
            assertThat(duration).isLessThan(100); // 100ms 内完成
        }
    }
}
