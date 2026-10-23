package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Part;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchUnit;
import com.zhixuewuli.entity.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QuestionSourceBuilder 单元测试：分段、LaTeX/中文、空解析、纯图片题。
 */
@Tag("unit")
@DisplayName("题目检索单元构建")
class QuestionSourceBuilderTest {

    private final QuestionSourceBuilder builder = new QuestionSourceBuilder();

    private Question question(String content, String options, String explanation) {
        Question q = new Question();
        q.setId(101L);
        q.setContent(content);
        q.setOptions(options);
        q.setExplanation(explanation);
        q.setDifficulty(2);
        q.setAiContentRevision(3L);
        q.setKnowledgePointCodes(List.of("mechanics.newton"));
        return q;
    }

    @Test
    @DisplayName("短题整体保留为一个 QUESTION 单元，空解析无 EXPLANATION")
    void shortQuestionWhole() {
        var units = builder.build(question(
                "质量为$2\\,\\mathrm{kg}$的物体受$10\\,\\mathrm{N}$力，求加速度。",
                "{\"A\":\"$2\\\\,\\\\mathrm{m/s^2}$\",\"B\":\"$5\\\\,\\\\mathrm{m/s^2}$\"}",
                null));
        assertEquals(1, units.size());
        SearchUnit unit = units.get(0);
        assertEquals(Part.QUESTION, unit.part());
        assertEquals(0, unit.ordinal());
        assertEquals(3L, unit.revision());
        assertTrue(unit.text().contains("质量"));
        assertTrue(unit.text().contains("选项"));
        assertNotNull(unit.contentHash());
        assertEquals(List.of("mechanics.newton"), unit.knowledgePointCodes());
    }

    @Test
    @DisplayName("LaTeX 公式保持完整，不被从中间切断")
    void latexPreserved() {
        String longExplanation = "推导：由牛顿第二定律$F=ma$可得$a=\\frac{F}{m}=\\frac{10\\,\\mathrm{N}}{2\\,\\mathrm{kg}}=5\\,\\mathrm{m/s^2}$。"
                + "故正确选项为B。注意单位统一使用$\\mathrm{m/s^2}$。";
        var units = builder.build(question("题干", "{}", longExplanation));
        assertTrue(units.stream().anyMatch(u -> u.part() == Part.EXPLANATION));
        String joined = units.stream().filter(u -> u.part() == Part.EXPLANATION).map(SearchUnit::text)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(joined.contains("$F=ma$"));
        assertTrue(joined.contains("\\frac{F}{m}"));
        assertTrue(joined.contains("$\\mathrm{m/s^2}$"));
    }

    @Test
    @DisplayName("长解析按段落拆分，每段不超过 400 字符，且不静默丢内容")
    void longExplanationSplit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append("第").append(i).append("步分析：物体在水平方向受到恒力作用，"
                    + "根据牛顿第二定律列出方程并代入数据计算加速度大小。"
                    + "此处补充说明单位换算与有效数字处理方式，确保答案可核对。");
        }
        var units = builder.build(question("题干", "{}", sb.toString()));
        List<SearchUnit> explanationUnits = units.stream()
                .filter(u -> u.part() == Part.EXPLANATION).toList();
        assertTrue(explanationUnits.size() > 1, "长解析应拆分为多段");
        for (SearchUnit unit : explanationUnits) {
            assertTrue(unit.text().length() <= QuestionSourceBuilder.MAX_SEGMENT_CHARS,
                    "每段不得超过上限，实际=" + unit.text().length());
        }
        // 不丢内容：拼接后仍包含首尾关键句
        String joined = String.join("", explanationUnits.stream().map(SearchUnit::text).toList());
        assertTrue(joined.contains("第0步"));
        assertTrue(joined.contains("第11步"));
    }

    @Test
    @DisplayName("超长单行公式段继续硬切分而非静默截断")
    void overlongSingleSegmentHardSplit() {
        String huge = "A".repeat(QuestionSourceBuilder.MAX_SEGMENT_CHARS * 3 + 50);
        var units = builder.build(question("题干", "{}", huge));
        List<SearchUnit> explanationUnits = units.stream()
                .filter(u -> u.part() == Part.EXPLANATION).toList();
        assertTrue(explanationUnits.size() >= 3);
        int total = explanationUnits.stream().mapToInt(u -> u.text().length()).sum();
        assertTrue(total >= QuestionSourceBuilder.MAX_SEGMENT_CHARS * 3,
                "不得静默截断，实际总长=" + total);
    }

    @Test
    @DisplayName("纯图片题（无文本）明确报错，不静默跳过")
    void pureImageQuestionRejected() {
        Question q = question(null, null, "解析");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> builder.build(q));
        assertEquals("QUESTION_TEXT_MISSING", error.getMessage());
    }

    @Test
    @DisplayName("ordinal 在同 part 内稳定递增，来源 ID 可拼出")
    void ordinalsStable() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append("段落").append(i).append("。内容足够长以便拆分，这里继续补充一些说明文字，"
                    + "保证每段超过一百字从而能够按段落边界切开，方便验证序号稳定性。");
        }
        var units = builder.build(question("短题干", "{}", sb.toString()));
        List<SearchUnit> explanation = units.stream().filter(u -> u.part() == Part.EXPLANATION).toList();
        for (int i = 0; i < explanation.size(); i++) {
            assertEquals(i, explanation.get(i).ordinal());
        }
        // 来源 ID 形如 Q:{questionId}:{revision}:{part}:{ordinal}
        SearchUnit first = explanation.get(0);
        String sourceId = "Q:" + first.questionId() + ":" + first.revision() + ":"
                + first.part() + ":" + first.ordinal();
        assertTrue(sourceId.matches("Q:101:3:EXPLANATION:0"));
    }
}
