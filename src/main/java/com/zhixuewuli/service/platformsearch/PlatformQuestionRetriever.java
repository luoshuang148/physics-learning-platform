package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.common.config.properties.PlatformSearchProperties;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.*;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionSearchUnit;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import com.zhixuewuli.service.knowledge.EmbeddingGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * 平台题库授权混合检索：同一候选集合同时约束关键词与向量，RRF 融合，
 * 去重并限制最多 5 个来源单元；题目级返回最多 5 道题，同题多段不得占满题目列表。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformQuestionRetriever {

    private final QuestionRetrievalAccessPolicy accessPolicy;
    private final QuestionKeywordSearch keywordSearch;
    private final QuestionVectorIndex vectorIndex;
    private final EmbeddingGateway embeddings;
    private final QuestionRepository questions;
    private final QuestionSearchUnitRepository searchUnits;
    private final PlatformSearchProperties properties;
    private final com.zhixuewuli.common.config.properties.KnowledgeProperties knowledgeProperties;

    public SearchResult retrieve(Long viewerId, SearchRequest request, BooleanSupplier cancelled) {
        long start = System.currentTimeMillis();
        if (request == null || request.query() == null || request.query().isBlank()) {
            return new SearchResult(RetrievalStatus.INSUFFICIENT, "none", List.of(), List.of(), 0);
        }
        List<EligibleQuestion> allowed = accessPolicy.eligible(viewerId, request);
        if (allowed.isEmpty()) {
            return new SearchResult(RetrievalStatus.INSUFFICIENT, "empty_candidates",
                    List.of(), List.of(), System.currentTimeMillis() - start);
        }

        int perSource = Math.max(5, properties.getPerSourceLimit());
        List<QuestionSearchUnit> keywordRows = new ArrayList<>();
        String actualMode = "hybrid";

        // 关键词路
        List<QuestionKeywordSearch.KeywordHit> keywordHits =
                keywordSearch.search(request.query(), allowed, perSource, properties.isPreferFullText());
        String kwMode = keywordHits.isEmpty() ? "fulltext" : keywordHits.get(0).mode();
        for (var hit : keywordHits) keywordRows.add(hit.unit());

        // 向量路
        List<QuestionSearchUnit> vectorRows = new ArrayList<>();
        boolean vectorOk = true;
        try {
            var batch = embeddings.embed(List.of(request.query()), true,
                    () -> cancelled != null && cancelled.getAsBoolean());
            if (batch.vectors().isEmpty()) throw new IllegalStateException("NO_QUERY_VECTOR");
            String indexVersion = pickIndexVersion(allowed);
            if (indexVersion != null) {
                // 关键词无命中时提高向量门槛，避免把无关近邻当结果
                double minScore = keywordRows.isEmpty()
                        ? QdrantQuestionVectorIndex.MIN_VECTOR_SCORE_WITHOUT_KEYWORD
                        : QdrantQuestionVectorIndex.MIN_VECTOR_SCORE;
                List<QuestionVectorPoint> vectorHits = vectorIndex.search(indexVersion, allowed,
                        batch.vectors().get(0), perSource, minScore);
                for (QuestionVectorPoint point : vectorHits) {
                    var unit = searchUnits.findByQuestionIdAndRevisionOrderByPartAscOrdinalNoAsc(
                                    point.questionId(), point.revision()).stream()
                            .filter(u -> u.getPart().equals(point.part().name())
                                    && u.getOrdinalNo() == point.ordinal())
                            .findFirst().orElse(null);
                    if (unit != null) vectorRows.add(unit);
                }
            }
        } catch (Exception error) {
            log.warn("向量检索失败: {}", error.getMessage());
            vectorOk = false;
        }

        if (!vectorOk && !properties.isAllowLexicalFallback()) {
            return new SearchResult(RetrievalStatus.UNAVAILABLE, "vector_unavailable",
                    List.of(), List.of(), System.currentTimeMillis() - start);
        }
        if (!vectorOk) {
            actualMode = "lexical_only";
        } else if ("lexical_only".equals(kwMode)) {
            actualMode = "lexical_only";
        }

        // RRF 融合
        List<Evidence> sources = rrfFuse(keywordRows, vectorRows, properties.getRrfK(), properties.getMaxSources());
        List<QuestionHit> questionHits = toQuestionHits(sources, allowed, request.limit());
        // 回源补全 content / difficulty / knowledgePointCodes（再鉴权由调用方保证）
        questionHits = enrich(questionHits, allowed);

        RetrievalStatus status = questionHits.isEmpty()
                ? RetrievalStatus.INSUFFICIENT : RetrievalStatus.MATCHED;
        return new SearchResult(status, actualMode, questionHits, sources,
                System.currentTimeMillis() - start);
    }

    private List<QuestionHit> enrich(List<QuestionHit> hits, List<EligibleQuestion> allowed) {
        Map<Long, EligibleQuestion> allowedMap = new HashMap<>();
        for (EligibleQuestion e : allowed) allowedMap.put(e.questionId(), e);
        return hits.stream().map(hit -> {
            Question q = questions.findById(hit.questionId()).orElse(null);
            if (q == null || !allowedMap.containsKey(hit.questionId())) return hit;
            return new QuestionHit(hit.questionId(), hit.revision(), q.getContent(),
                    q.getDifficulty(), q.getKnowledgePointCodes(), hit.sourceIds());
        }).toList();
    }

    /**
     * RRF(k=60)：score = Σ 1/(k+rank)。按 sourceId 对齐两路。
     */
    static List<Evidence> rrfFuse(List<QuestionSearchUnit> keyword, List<QuestionSearchUnit> vector,
                                  int k, int maxSources) {
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, QuestionSearchUnit> byId = new LinkedHashMap<>();

        int rank = 0;
        for (QuestionSearchUnit unit : keyword) {
            String id = sourceId(unit);
            byId.putIfAbsent(id, unit);
            scores.merge(id, 1.0 / (k + ++rank), Double::sum);
        }
        rank = 0;
        for (QuestionSearchUnit unit : vector) {
            String id = sourceId(unit);
            byId.putIfAbsent(id, unit);
            scores.merge(id, 1.0 / (k + ++rank), Double::sum);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(0, maxSources))
                .map(e -> {
                    QuestionSearchUnit unit = byId.get(e.getKey());
                    return new Evidence(e.getKey(), unit.getQuestionId(), unit.getRevision(),
                            unit.getPart(), unit.getOrdinalNo(), unit.getUnitText());
                })
                .toList();
    }

    /** 题目级去重：同题多段只计一道题。 */
    static List<QuestionHit> toQuestionHits(List<Evidence> sources, List<EligibleQuestion> allowed, int limit) {
        Map<Long, List<String>> byQuestion = new LinkedHashMap<>();
        Map<Long, Long> revisions = new HashMap<>();
        Set<EligibleQuestion> allowedSet = new HashSet<>(allowed);
        for (Evidence source : sources) {
            if (!allowedSet.contains(new EligibleQuestion(source.questionId(), source.revision()))) {
                continue;
            }
            byQuestion.computeIfAbsent(source.questionId(), k -> new ArrayList<>()).add(source.sourceId());
            revisions.put(source.questionId(), source.revision());
        }
        // 详情稍后回源；此处先占位 content
        return byQuestion.entrySet().stream()
                .limit(Math.max(0, limit))
                .map(e -> new QuestionHit(e.getKey(), revisions.getOrDefault(e.getKey(), 0L),
                        "", null, List.of(), e.getValue()))
                .toList();
    }

    public static String sourceId(QuestionSearchUnit unit) {
        return "Q:" + unit.getQuestionId() + ":" + unit.getRevision() + ":"
                + unit.getPart() + ":" + unit.getOrdinalNo();
    }

    public static String sourceId(long questionId, long revision, Part part, int ordinal) {
        return "Q:" + questionId + ":" + revision + ":" + part.name() + ":" + ordinal;
    }

    /** 解析来源 ID 形如 Q:{questionId}:{revision}:{part}:{ordinal} */
    public static Optional<ParsedSource> parseSourceId(String sourceId) {
        if (sourceId == null || !sourceId.startsWith("Q:")) return Optional.empty();
        String[] parts = sourceId.split(":");
        if (parts.length != 5) return Optional.empty();
        try {
            long questionId = Long.parseLong(parts[1]);
            long revision = Long.parseLong(parts[2]);
            Part part = Part.valueOf(parts[3]);
            int ordinal = Integer.parseInt(parts[4]);
            return Optional.of(new ParsedSource(questionId, revision, part, ordinal));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }

    public record ParsedSource(long questionId, long revision, Part part, int ordinal) {
    }

    private String pickIndexVersion(List<EligibleQuestion> allowed) {
        for (EligibleQuestion e : allowed) {
            var q = questions.findById(e.questionId()).orElse(null);
            if (q == null || !q.isAiPracticeEnabled()) continue;
            return QuestionIndexWorker.staticIndexVersion(e.questionId(), e.revision(), knowledgeProperties);
        }
        return null;
    }
}
