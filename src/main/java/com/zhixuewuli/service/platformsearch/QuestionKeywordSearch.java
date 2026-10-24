package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.entity.QuestionSearchUnit;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 中文关键词检索适配器。
 * 优先 MySQL FULLTEXT（ngram）；不可用时走可测试的 LIKE 适配器，
 * 并如实标记 actualMode=lexical_only，禁止悄悄变成全表 LIKE 后声称全文检索。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionKeywordSearch {

    private final QuestionSearchUnitRepository searchUnits;

    public record KeywordHit(QuestionSearchUnit unit, int rank, String mode) {
    }

    /**
     * 在授权候选集合内关键词检索。
     */
    public List<KeywordHit> search(String query, List<EligibleQuestion> allowed, int limit,
                                   boolean preferFullText) {
        if (query == null || query.isBlank() || allowed == null || allowed.isEmpty() || limit < 1) {
            return List.of();
        }
        List<Long> candidateIds = allowed.stream().map(EligibleQuestion::questionId).distinct().toList();
        String cleaned = query.strip();

        if (preferFullText) {
            try {
                List<QuestionSearchUnit> rows = searchUnits.searchByFullText(cleaned, candidateIds, limit);
                if (!rows.isEmpty()) {
                    return toHits(rows, "fulltext");
                }
                // FULLTEXT 可执行但 0 命中 → 继续走 LIKE 适配器补召回
            } catch (Exception error) {
                log.warn("FULLTEXT 检索不可用，降级 LIKE 适配器: {}", error.getMessage());
            }
        }
        // 显式 LIKE 适配器（actualMode 记录 lexical_only）
        List<QuestionSearchUnit> rows = new ArrayList<>();
        List<String> tokens = tokenize(cleaned);
        if (tokens.isEmpty()) return List.of();
        for (String token : tokens) {
            rows.addAll(searchUnits.searchByTokenLike(token, candidateIds, limit));
            if (rows.size() >= limit * 2) break;
        }
        // 要求至少命中两个不同 token，或命中最长 token（降低「物理」等通用词误召回）
        String longest = tokens.stream().max(Comparator.comparingInt(String::length)).orElse("");
        List<QuestionSearchUnit> filtered = rows.stream()
                .distinct()
                .filter(row -> {
                    String text = row.getUnitText() == null ? "" : row.getUnitText();
                    long matched = tokens.stream().filter(text::contains).count();
                    return matched >= 2 || (longest.length() >= 3 && text.contains(longest));
                })
                .limit(limit)
                .toList();
        return toHits(filtered, "lexical_only");
    }

    /**
     * 简易中文分词：CJK 走 2/3 字 n-gram，字母数字按连续串。
     * 单字 CJK 不检索（避免全表噪声）；无真实分词器时这是可测试的降级适配器。
     */
    static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        StringBuilder cjk = new StringBuilder();

        Runnable flushAscii = () -> {
            if (ascii.length() > 0) {
                tokens.add(ascii.toString());
                ascii.setLength(0);
            }
        };
        Runnable flushCjk = () -> {
            if (cjk.length() > 0) {
                String run = cjk.toString();
                if (run.length() == 1) {
                    // 单字跳过
                } else if (run.length() == 2) {
                    tokens.add(run);
                } else {
                    for (int i = 0; i + 1 < run.length(); i++) {
                        tokens.add(run.substring(i, i + 2));
                        if (i + 3 <= run.length() && i + 2 < run.length()) {
                            tokens.add(run.substring(i, i + 3));
                        }
                    }
                }
                cjk.setLength(0);
            }
        };

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            boolean isCjkChar = c >= 0x4E00 && c <= 0x9FFF;
            if (isCjkChar) {
                flushAscii.run();
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk.run();
                ascii.append(c);
            } else {
                flushAscii.run();
                flushCjk.run();
            }
        }
        flushAscii.run();
        flushCjk.run();

        return tokens.stream()
                .filter(t -> !containsParticle(t))
                .distinct()
                .limit(12)
                .toList();
    }

    /**
     * 虚词开头/结尾的 n-gram（如「的基本」「是的」）容易误召回，丢弃。
     * 中间含虚词的 3-gram 保留（如「功和能」）。
     */
    private static boolean containsParticle(String token) {
        if (token.isEmpty()) return true;
        return isParticle(token.charAt(0)) || isParticle(token.charAt(token.length() - 1));
    }

    private static boolean isParticle(char c) {
        return c == '的' || c == '是' || c == '了' || c == '和' || c == '与'
                || c == '在' || c == '有' || c == '不' || c == '为' || c == '要';
    }

    private List<KeywordHit> toHits(List<QuestionSearchUnit> rows, String mode) {
        List<KeywordHit> hits = new ArrayList<>();
        int rank = 0;
        for (QuestionSearchUnit row : rows) {
            hits.add(new KeywordHit(row, ++rank, mode));
        }
        return hits;
    }
}
