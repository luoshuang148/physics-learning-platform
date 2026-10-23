package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Part;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchUnit;
import com.zhixuewuli.entity.Question;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将题目构建为可检索单元。
 *
 * <p>规则（设计 §5.1）：
 * 每道题分成 QUESTION（题干与选项）和 EXPLANATION（解析）两类；
 * 短题整体保留，长解析按段落拆分，目标每段不超过 {@value #MAX_SEGMENT_CHARS} 个字符
 * （以汉字/Unicode 字符计，不得超过 embedding 有效 token 上限——由依赖模型元数据/实测边界确认后写入配置与测试）。
 * 公式、选项和推导段落尽量保持完整；超过上限则继续分段。禁止静默截断。</p>
 */
@Component
public class QuestionSourceBuilder {

    /** 每段目标字符上限（汉字/字符，非 token）。超限必须继续分段或报错。 */
    public static final int MAX_SEGMENT_CHARS = 400;

    /** 单次不得丢弃的最小分段长度（低于此值视为分段策略失败）。 */
    private static final int MIN_SEGMENT_CHARS = 1;

    /** 段落/换行分隔。 */
    private static final Pattern PARAGRAPH = Pattern.compile("\\n+|(?<=。)|(?<=；)|(?<=;)|(?<=\\n)");

    /**
     * 构建检索单元；返回列表按 (part, ordinal) 稳定排序。
     * 空解析返回不含 EXPLANATION 的列表；纯图片题（无文本内容）抛出明确错误而非静默跳过。
     */
    public List<SearchUnit> build(Question question) {
        if (question == null || question.getId() == null) {
            throw new IllegalArgumentException("QUESTION_REQUIRED");
        }
        String stem = safe(question.getContent());
        String options = safe(question.getOptions());
        if (stem.isBlank() && options.isBlank()) {
            // 纯图片题：必须补录文字，禁止静默生成空单元
            throw new IllegalStateException("QUESTION_TEXT_MISSING");
        }
        List<String> codes = question.getKnowledgePointCodes() == null
                ? List.of() : question.getKnowledgePointCodes();
        Integer difficulty = question.getDifficulty();
        long revision = question.getAiContentRevision();

        List<SearchUnit> units = new ArrayList<>();

        String questionText = stem.isBlank() ? options
                : (options.isBlank() ? stem : stem + "\n选项：\n" + options);
        units.addAll(toUnits(question.getId(), revision, Part.QUESTION, questionText, codes, difficulty));

        String explanation = safe(question.getExplanation());
        if (!explanation.isBlank()) {
            units.addAll(toUnits(question.getId(), revision, Part.EXPLANATION, explanation, codes, difficulty));
        }
        return List.copyOf(units);
    }

    private List<SearchUnit> toUnits(Long questionId, long revision, Part part, String text,
                                     List<String> codes, Integer difficulty) {
        List<SearchUnit> result = new ArrayList<>();
        List<String> segments = segment(text);
        int ordinal = 0;
        for (String segment : segments) {
            if (segment.isBlank()) continue;
            if (segment.length() > MAX_SEGMENT_CHARS) {
                // 分段后仍超限：继续切分（不静默截断）
                for (String piece : hardSplit(segment)) {
                    result.add(unit(questionId, revision, part, ordinal++, piece, codes, difficulty));
                }
            } else {
                result.add(unit(questionId, revision, part, ordinal++, segment, codes, difficulty));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("SEGMENTATION_FAILED");
        }
        return result;
    }

    /** 段落优先分段，目标不超过 MAX_SEGMENT_CHARS；公式块（$...$）尽量保持完整。 */
    private List<String> segment(String text) {
        List<String> out = new ArrayList<>();
        String[] paragraphs = PARAGRAPH.split(text, -1);
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isBlank()) continue;
            String piece = paragraph.strip();
            if (piece.isEmpty()) continue;
            // 已超限的单段先落盘，交给 hardSplit
            if (piece.length() > MAX_SEGMENT_CHARS) {
                if (buffer.length() > 0) {
                    out.add(buffer.toString().strip());
                    buffer.setLength(0);
                }
                out.add(piece);
                continue;
            }
            int projected = buffer.length() + (buffer.length() > 0 ? 1 : 0) + piece.length();
            if (projected > MAX_SEGMENT_CHARS && buffer.length() > 0) {
                out.add(buffer.toString().strip());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) buffer.append('\n');
            buffer.append(piece);
        }
        if (buffer.length() > 0) out.add(buffer.toString().strip());
        return out;
    }

    /** 硬切分：在 MAX_SEGMENT_CHARS 边界切，公式区间尽量不切开。 */
    private List<String> hardSplit(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_SEGMENT_CHARS, text.length());
            if (end < text.length()) {
                // 尽量在句读处断开
                int preferred = Math.max(text.lastIndexOf('。', end - 1), text.lastIndexOf('\n', end - 1));
                if (preferred >= start + MIN_SEGMENT_CHARS) end = preferred + 1;
            }
            if (end <= start) end = Math.min(start + MAX_SEGMENT_CHARS, text.length());
            out.add(text.substring(start, end).strip());
            start = end;
        }
        return out.stream().filter(s -> !s.isBlank()).toList();
    }

    private SearchUnit unit(Long questionId, long revision, Part part, int ordinal, String text,
                            List<String> codes, Integer difficulty) {
        return new SearchUnit(questionId, revision, part, ordinal, text,
                DigestUtils.sha256Hex(text.getBytes(StandardCharsets.UTF_8)), codes, difficulty);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
