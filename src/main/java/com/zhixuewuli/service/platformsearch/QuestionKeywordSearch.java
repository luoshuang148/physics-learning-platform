package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.entity.QuestionSearchUnit;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
                return toHits(rows, "fulltext");
            } catch (Exception error) {
                log.warn("FULLTEXT 检索不可用，降级 LIKE 适配器: {}", error.getMessage());
            }
        }
        // 显式 LIKE 适配器（actualMode 记录 lexical_only）
        List<QuestionSearchUnit> rows = new ArrayList<>();
        for (String token : tokenize(cleaned)) {
            rows.addAll(searchUnits.searchByTokenLike(token, candidateIds, limit));
            if (rows.size() >= limit * 2) break;
        }
        return toHits(rows.stream().distinct().limit(limit).toList(), "lexical_only");
    }

    /** 简易中文分词：连续 CJK/字母数字作为 token。 */
    static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isLetterOrDigit(c) || (c >= 0x4E00 && c <= 0x9FFF)) {
                buffer.append(c);
            } else if (buffer.length() > 0) {
                tokens.add(buffer.toString());
                buffer.setLength(0);
            }
        }
        if (buffer.length() > 0) tokens.add(buffer.toString());
        // 过短 token 不检索（避免全表噪声）
        return tokens.stream().filter(t -> t.length() >= 1).distinct().limit(8).toList();
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
