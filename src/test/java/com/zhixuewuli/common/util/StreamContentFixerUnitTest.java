package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * StreamContentFixer 单元测试类
 * 
 * <p>
 * 测试 LaTeX/Markdown 格式修复功能，包括：
 * </p>
 * <ul>
 * <li>LaTeX 运算符粘连修复</li>
 * <li>希腊字母粘连修复</li>
 * <li>Markdown 标题粘连修复</li>
 * <li>Markdown 列表粘连修复</li>
 * <li>Markdown 粗体/斜体粘连修复</li>
 * <li>空值和边界情况处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("StreamContentFixer 单元测试 - LaTeX/Markdown 格式修复")
class StreamContentFixerUnitTest {

    @Test
    void preservesPhysicalNumbersEquationsAndUrls() {
        for (String input : new String[]{"加速度 1.5 m/s²", "0.25 A", "-1.5", "+0.25", "$v=1.5$", "版本 v1.2.3", "https://example.com/v1.2", "量值为 1.5，误差 -0.25"}) {
            assertThat(StreamContentFixer.fix(input)).as(input).isEqualTo(input);
            for (int i = 1; i < input.length(); i++) {
                assertThat(StreamContentFixer.fix(input.substring(0, i)) + StreamContentFixer.fix(input.substring(i)))
                        .as("split %s at %s", input, i).isEqualTo(input);
            }
        }
    }

    @Test
    void repairsOrderedListsOnlyAtLineOrHeadingStart() {
        assertThat(StreamContentFixer.fix("说明\n1.第一步\n  2.第二步")).isEqualTo("说明\n1. 第一步\n  2. 第二步");
        assertThat(StreamContentFixer.fix("见 1.第一步")).isEqualTo("见 1.第一步");
    }

    // ==================== 空值和边界测试 ====================

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullWhenInputIsNull() {
        assertThat(StreamContentFixer.fix(null)).isNull();
    }

    @Test
    @DisplayName("空字符串输入应返回空字符串")
    void shouldReturnEmptyStringWhenInputIsEmpty() {
        assertThat(StreamContentFixer.fix("")).isEmpty();
    }

    @Test
    @DisplayName("正常文本无需修复")
    void shouldNotModifyNormalText() {
        String input = "这是一个正常的物理问题。";
        assertThat(StreamContentFixer.fix(input)).isEqualTo(input);
    }

    // ==================== LaTeX 运算符测试 ====================

    @Nested
    @DisplayName("LaTeX 运算符粘连修复")
    class LatexOperatorTests {

        @Test
        @DisplayName("\\cdot 运算符粘连修复")
        void shouldFixDotOperatorGlue() {
            assertThat(StreamContentFixer.fix("a\\cdott")).isEqualTo("a\\cdot t");
            assertThat(StreamContentFixer.fix("x\\cdoty")).isEqualTo("x\\cdot y");
        }

        @Test
        @DisplayName("\\times 运算符粘连修复")
        void shouldFixTimesOperatorGlue() {
            assertThat(StreamContentFixer.fix("v\\timesm")).isEqualTo("v\\times m");
            assertThat(StreamContentFixer.fix("a\\times10")).isEqualTo("a\\times 10");
        }

        @Test
        @DisplayName("\\div 运算符粘连修复")
        void shouldFixDivOperatorGlue() {
            assertThat(StreamContentFixer.fix("x\\divy")).isEqualTo("x\\div y");
        }

        @Test
        @DisplayName("\\pm 运算符粘连修复")
        void shouldFixPmOperatorGlue() {
            assertThat(StreamContentFixer.fix("a\\pmx")).isEqualTo("a\\pm x");
        }

        @Test
        @DisplayName("\\leq 运算符粘连修复")
        void shouldFixLeqOperatorGlue() {
            assertThat(StreamContentFixer.fix("a\\leqb")).isEqualTo("a\\leq b");
        }

        @Test
        @DisplayName("\\geq 运算符粘连修复")
        void shouldFixGeqOperatorGlue() {
            assertThat(StreamContentFixer.fix("a\\geqb")).isEqualTo("a\\geq b");
        }

        @Test
        @DisplayName("\\neq 运算符粘连修复")
        void shouldFixNeqOperatorGlue() {
            assertThat(StreamContentFixer.fix("a\\neqb")).isEqualTo("a\\neq b");
        }

        @Test
        @DisplayName("\\Rightarrow 运算符粘连修复")
        void shouldFixRightarrowOperatorGlue() {
            assertThat(StreamContentFixer.fix("x\\Rightarrowy")).isEqualTo("x\\Rightarrow y");
        }

        @Test
        @DisplayName("\\rightarrow 运算符粘连修复")
        void shouldFixrightarrowOperatorGlue() {
            assertThat(StreamContentFixer.fix("x\\rightarrowy")).isEqualTo("x\\rightarrow y");
        }

        @Test
        @DisplayName("\\dots 不应被误改为 \\dot s")
        void shouldNotBreakDotsCommand() {
            assertThat(StreamContentFixer.fix(" \\dots$，")).isEqualTo(" \\dots$，");
        }

        @Test
        @DisplayName("\\dot 与变量粘连应修复")
        void shouldFixDotModifierGlue() {
            assertThat(StreamContentFixer.fix("\\dotx")).isEqualTo("\\dot x");
        }
    }

    // ==================== LaTeX 希腊字母测试 ====================

    @Nested
    @DisplayName("LaTeX 希腊字母粘连修复")
    class LatexGreekLetterTests {

        @Test
        @DisplayName("常见希腊字母粘连修复")
        void shouldFixGreekLetterGlue() {
            assertThat(StreamContentFixer.fix("\\alphax")).isEqualTo("\\alpha x");
            assertThat(StreamContentFixer.fix("\\beta1")).isEqualTo("\\beta 1");
            assertThat(StreamContentFixer.fix("\\gamma2")).isEqualTo("\\gamma 2");
            assertThat(StreamContentFixer.fix("\\theta3")).isEqualTo("\\theta 3");
            assertThat(StreamContentFixer.fix("\\lambda4")).isEqualTo("\\lambda 4");
            assertThat(StreamContentFixer.fix("\\pi5")).isEqualTo("\\pi 5");
            assertThat(StreamContentFixer.fix("\\omega6")).isEqualTo("\\omega 6");
        }

        @Test
        @DisplayName("希腊字母后面是空格或标点不应修改")
        void shouldNotModifyWhenFollowedBySpace() {
            assertThat(StreamContentFixer.fix("\\alpha x")).isEqualTo("\\alpha x");
            assertThat(StreamContentFixer.fix("\\beta, x")).isEqualTo("\\beta, x");
        }
    }

    // ==================== LaTeX 数学函数测试 ====================

    @Nested
    @DisplayName("LaTeX 数学函数粘连修复")
    class LatexMathFunctionTests {

        @Test
        @DisplayName("三角函数和常用函数粘连修复")
        void shouldFixMathFunctionGlue() {
            assertThat(StreamContentFixer.fix("\\sinx")).isEqualTo("\\sin x");
            assertThat(StreamContentFixer.fix("\\cosx")).isEqualTo("\\cos x");
            assertThat(StreamContentFixer.fix("\\tanx")).isEqualTo("\\tan x");
            assertThat(StreamContentFixer.fix("\\logx")).isEqualTo("\\log x");
            assertThat(StreamContentFixer.fix("\\lnx")).isEqualTo("\\ln x");
            assertThat(StreamContentFixer.fix("\\expx")).isEqualTo("\\exp x");
        }
    }

    // ==================== Markdown 标题测试 ====================

    @Nested
    @DisplayName("Markdown 标题粘连修复")
    class MarkdownHeadingTests {

        @Test
        @DisplayName("标题标记粘连修复")
        void shouldFixHeadingGlue() {
            assertThat(StreamContentFixer.fix("###错误原因分析")).isEqualTo("### 错误原因分析");
            assertThat(StreamContentFixer.fix("##解题思路")).isEqualTo("## 解题思路");
            assertThat(StreamContentFixer.fix("#快速概览")).isEqualTo("# 快速概览");
            assertThat(StreamContentFixer.fix("######小标题")).isEqualTo("###### 小标题");
        }

        @Test
        @DisplayName("标题后紧跟有序列表标记应修复")
        void shouldFixHeadingBeforeOrderedList() {
            // 关键修复：###1.标题 -> ### 1. 标题
            // 注意：由于有序列表规则也会在 1. 后添加空格，最终会多一个空格
            assertThat(StreamContentFixer.fix("###1.错误原因分析")).isEqualTo("### 1. 错误原因分析");
            assertThat(StreamContentFixer.fix("##2.解题思路")).isEqualTo("## 2. 解题思路");
            assertThat(StreamContentFixer.fix("#3.重点总结")).isEqualTo("# 3. 重点总结");
            // 多级标题也适用
            assertThat(StreamContentFixer.fix("####4.延伸阅读")).isEqualTo("#### 4. 延伸阅读");
        }

        @Test
        @DisplayName("标题后面已有空格不应修改")
        void shouldNotModifyWhenAlreadySpaced() {
            assertThat(StreamContentFixer.fix("### 正确格式")).isEqualTo("### 正确格式");
        }
    }

    // ==================== Markdown 列表测试 ====================

    @Nested
    @DisplayName("Markdown 列表粘连修复")
    class MarkdownListTests {

        @Test
        @DisplayName("无序列表标记粘连修复")
        void shouldFixUnorderedListGlue() {
            assertThat(StreamContentFixer.fix("-列表项1")).isEqualTo("- 列表项1");
            assertThat(StreamContentFixer.fix("+列表项3")).isEqualTo("+ 列表项3");
        }

        @Test
        @DisplayName("有序列表标记粘连修复")
        void shouldFixOrderedListGlue() {
            assertThat(StreamContentFixer.fix("1.第一步")).isEqualTo("1. 第一步");
            assertThat(StreamContentFixer.fix("2.第二步")).isEqualTo("2. 第二步");
            assertThat(StreamContentFixer.fix("10.第十步")).isEqualTo("10. 第十步");
        }

        @Test
        @DisplayName("列表标记后面已有空格不应修改")
        void shouldNotModifyWhenAlreadySpaced() {
            assertThat(StreamContentFixer.fix("- 正常列表项")).isEqualTo("- 正常列表项");
            assertThat(StreamContentFixer.fix("1. 正常列表项")).isEqualTo("1. 正常列表项");
        }

        @Test
        @DisplayName("不应修改 Markdown 表格分隔线")
        void shouldNotModifyMarkdownTableSeparator() {
            String input = "|------|--------|----------|";
            assertThat(StreamContentFixer.fix(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("分片从短横线开始时不应插入空格")
        void shouldNotInsertSpaceWhenChunkStartsWithHyphenRun() {
            assertThat(StreamContentFixer.fix("-----|--------|-"))
                    .isEqualTo("-----|--------|-");
        }

        @Test
        @DisplayName("仍应修复无序列表粘连")
        void shouldStillFixUnorderedListGlue() {
            assertThat(StreamContentFixer.fix("-列表项")).isEqualTo("- 列表项");
            assertThat(StreamContentFixer.fix("+列表项")).isEqualTo("+ 列表项");
        }

        @Test
        @DisplayName("星号开头分片不应被当作列表误改")
        void shouldNotTreatAsteriskChunkAsListItem() {
            assertThat(StreamContentFixer.fix("*“")).isEqualTo("*“");
            assertThat(StreamContentFixer.fix("*斜体")).isEqualTo("*斜体");
        }
    }

    // ==================== Markdown 粗体斜体测试 ====================

    @Nested
    @DisplayName("Markdown 粗体/斜体粘连修复")
    class MarkdownEmphasisTests {

        @Test
        @DisplayName("星号粗体标记后粘连修复")
        void shouldFixAsteriskBoldGlue() {
            assertThat(StreamContentFixer.fix("**粗体**文字")).isEqualTo("**粗体** 文字");
            assertThat(StreamContentFixer.fix("**多字粗体**内容")).isEqualTo("**多字粗体** 内容");
        }

        @Test
        @DisplayName("下划线粗体标记后粘连修复")
        void shouldFixUnderscoreBoldGlue() {
            assertThat(StreamContentFixer.fix("__粗体__文字")).isEqualTo("__粗体__ 文字");
        }

        @Test
        @DisplayName("星号斜体标记后粘连修复")
        void shouldFixAsteriskItalicGlue() {
            assertThat(StreamContentFixer.fix("*斜体*文字")).isEqualTo("*斜体* 文字");
            assertThat(StreamContentFixer.fix("*多字斜体*内容")).isEqualTo("*多字斜体* 内容");
        }

        @Test
        @DisplayName("下划线斜体标记后粘连修复")
        void shouldFixUnderscoreItalicGlue() {
            assertThat(StreamContentFixer.fix("_斜体_文字")).isEqualTo("_斜体_ 文字");
        }

        @Test
        @DisplayName("粗体后已有空格不应修改")
        void shouldNotModifyBoldWhenSpaced() {
            assertThat(StreamContentFixer.fix("**粗体** 正常")).isEqualTo("**粗体** 正常");
        }

        @Test
        @DisplayName("仅标点粗体后不应强制补空格")
        void shouldNotInsertSpaceAfterPunctuationOnlyBold() {
            assertThat(StreamContentFixer.fix("程度**：**优秀")).isEqualTo("程度**：**优秀");
        }

        @Test
        @DisplayName("正常粗体后紧跟文字仍应补空格")
        void shouldStillInsertSpaceAfterNormalBold() {
            assertThat(StreamContentFixer.fix("**绝不是**匀速")).isEqualTo("**绝不是** 匀速");
        }
        
        @Test
        @DisplayName("不应错误匹配部分粗体标记")
        void shouldNotMatchPartialBold() {
            // `*text*` 作为一个整体的斜体标记，应该被正确处理
            String result = StreamContentFixer.fix("*text*后");
            // 斜体后应该添加空格
            assertThat(result).contains("*text*");
        }
    }

    // ==================== Markdown 代码块测试 ====================

    @Nested
    @DisplayName("Markdown 代码块修复")
    class MarkdownCodeBlockTests {

        @Test
        @DisplayName("行内代码后粘连修复")
        void shouldFixInlineCodeGlue() {
            assertThat(StreamContentFixer.fix("`code`文字")).isEqualTo("`code` 文字");
        }

        @Test
        @DisplayName("代码块语言标识符粘连修复")
        void shouldFixCodeBlockLanguageGlue() {
            assertThat(StreamContentFixer.fix("```java代码")).isEqualTo("```java\n代码");
            assertThat(StreamContentFixer.fix("```python代码")).isEqualTo("```python\n代码");
        }
    }

    // ==================== 组合场景测试 ====================

    @Nested
    @DisplayName("组合场景测试")
    class CombinedScenarioTests {

        @Test
        @DisplayName("物理公式组合场景")
        void shouldFixPhysicsFormula() {
            String input = "根据公式 $F\\cdott=m\\timesa$，可求得结果。";
            String output = StreamContentFixer.fix(input);
            assertThat(output).contains("\\cdot t");
            assertThat(output).contains("\\times a");
        }

        @Test
        @DisplayName("AI 分析报告组合场景")
        void shouldFixAIAnalysisReport() {
            String input = "###错误原因分析\n1.概念混淆：$\\alpha+\\beta=\\gamma$\n-列表项一\n**重点**：理解基本概念";
            String output = StreamContentFixer.fix(input);
            assertThat(output).contains("### 错误原因分析");
            assertThat(output).contains("1. 概念混淆");
            // 换行后的无序列表项也应该被修复
            assertThat(output).contains("- 列表项一");
            // 验证希腊字母后的空格修复
            assertThat(output).contains("\\alpha + ");
        }

        @Test
        @DisplayName("数学题解答场景")
        void shouldFixMathSolution() {
            String input = "解方程：$a\\timesx + b\\divy = c$";
            String output = StreamContentFixer.fix(input);
            assertThat(output).contains("\\times x");
            assertThat(output).contains("\\div y");
        }

        @Test
        @DisplayName("LaTeX 命令与加减等号粘连应修复")
        void shouldFixLatexCommandPlusEqualsGlue() {
            String input = "\\alpha+\\beta=\\gamma";
            assertThat(StreamContentFixer.fix(input)).isEqualTo("\\alpha + \\beta = \\gamma");
        }

        @Test
        @DisplayName("LaTeX 已有空格的加减等号不应重复修改")
        void shouldNotModifyAlreadySpacedLatexPlusEquals() {
            String input = "\\alpha + \\beta = \\gamma";
            assertThat(StreamContentFixer.fix(input)).isEqualTo(input);
        }

        @Test
        @DisplayName("粗体与中文引号在分片拼接后不应出现内部空格")
        void shouldKeepBoldQuoteWithoutInnerSpacesAfterChunkWiseFix() {
            String[] chunks = { "*", "*“", "分解运动+受力分析", "”*", "*" };
            StringBuilder merged = new StringBuilder();
            for (String chunk : chunks) {
                merged.append(StreamContentFixer.fix(chunk));
            }
            assertThat(merged.toString()).isEqualTo("**“分解运动+受力分析”**");
        }

        @Test
        @DisplayName("LaTeX 小括号转义不应被误改")
        void shouldNotModifyEscapedLatexParentheses() {
            String input = "加速度\\(a\\)就越大，或者合外力\\(F\\)越大";
            assertThat(StreamContentFixer.fix(input)).isEqualTo(input);
        }
    }

    // ==================== 性能测试 ====================

    @Nested
    @DisplayName("性能测试")
    class PerformanceTests {

        @Test
        @DisplayName("大量文本处理性能")
        void shouldHandleLargeTextEfficiently() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("这是一段物理内容，包含 \\alpha+\\beta=\\gamma 公式。");
                sb.append("###标题").append("\n");
                sb.append("-列表项\n");
            }
            String input = sb.toString();
            
            long startTime = System.currentTimeMillis();
            String output = StreamContentFixer.fix(input);
            long duration = System.currentTimeMillis() - startTime;
            
            assertThat(output).isNotNull();
            assertThat(duration).isLessThan(100); // 100ms 内完成
        }
    }
}
