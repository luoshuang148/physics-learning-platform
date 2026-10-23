package com.zhixuewuli.service.platformsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Part;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionVectorPoint;
import com.zhixuewuli.service.knowledge.KnowledgeVectorException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 题库向量索引。collection 名含模型/维度/分块配置版本，独立于文档集合。
 */
@Component
public class QdrantQuestionVectorIndex implements QuestionVectorIndex {

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient client;
    private final KnowledgeProperties properties;

    public QdrantQuestionVectorIndex(RestClient.Builder builder, KnowledgeProperties properties) {
        var clientBuilder = builder.clone().baseUrl(properties.getQdrantUrl());
        if (properties.getQdrantApiKey() != null && !properties.getQdrantApiKey().isBlank()) {
            clientBuilder.defaultHeader("api-key", properties.getQdrantApiKey());
        }
        this.client = clientBuilder.build();
        this.properties = properties;
    }

    @Override
    public void ensureCollection(String indexVersion, int dimensions) {
        var name = collectionName(indexVersion);
        try {
            client.get().uri("/collections/{name}", name).retrieve().body(JsonNode.class);
            return;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() != 404) {
                throw new KnowledgeVectorException("Qdrant collection lookup failed: "
                        + error.getStatusCode().value() + " " + error.getResponseBodyAsString(), error);
            }
        } catch (RestClientException error) {
            throw vectorFailure("Qdrant collection lookup failed", error);
        }
        execute(() -> {
            try {
                return client.put().uri("/collections/{name}", name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                        .retrieve().body(JsonNode.class);
            } catch (RestClientResponseException error) {
                // 并发创建时 409 = 已存在，视为成功
                if (error.getStatusCode().value() == 409) return null;
                throw error;
            }
        });
    }

    @Override
    public void upsert(String indexVersion, List<QuestionVectorPoint> points) {
        if (points == null || points.isEmpty()) return;
        var payload = points.stream().map(point -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", point.pointId());
            body.put("vector", point.vector());
            body.put("payload", Map.of(
                    "questionId", point.questionId(),
                    "revision", point.revision(),
                    "part", point.part().name(),
                    "ordinal", point.ordinal(),
                    "indexVersion", indexVersion));
            return body;
        }).toList();
        execute(() -> client.put().uri(uriBuilder -> uriBuilder.path("/collections/{name}/points")
                        .queryParam("wait", "true").build(collectionName(indexVersion)))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("points", payload))
                .retrieve().body(JsonNode.class));
    }

    @Override
    public long count(String indexVersion) {
        try {
            JsonNode result = execute(() -> client.post()
                    .uri("/collections/{name}/points/count", collectionName(indexVersion))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("exact", true))
                    .retrieve().body(JsonNode.class));
            return result == null ? -1 : result.path("result").path("count").asLong(-1);
        } catch (RuntimeException error) {
            if (error instanceof KnowledgeVectorException vectorError) throw vectorError;
            throw vectorFailure("Qdrant point count failed", error);
        }
    }

    @Override
    public List<QuestionVectorPoint> search(String indexVersion, List<EligibleQuestion> allowed,
                                            float[] vector, int limit) {
        if (vector == null || vector.length == 0 || limit < 1
                || allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        // 仅按 questionId 集合过滤（Qdrant match.any），revision 在结果里再校验
        List<Long> questionIds = allowed.stream().map(EligibleQuestion::questionId).distinct().toList();
        var filter = Map.of("must", List.of(
                Map.of("key", "questionId", "match", Map.of("any", questionIds))));
        JsonNode result = execute(() -> client.post()
                .uri(uriBuilder -> uriBuilder.path("/collections/{name}/points/search")
                        .queryParam("limit", limit * 2).build(collectionName(indexVersion)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vector", vector, "filter", filter, "limit", limit * 2,
                        "with_payload", true))
                .retrieve().body(JsonNode.class));
        var allowedSet = new java.util.HashSet<>(allowed);
        var hits = new ArrayList<QuestionVectorPoint>();
        if (result != null && result.has("result")) {
            for (JsonNode hit : result.get("result")) {
                if (hits.size() >= limit) break;
                JsonNode payload = hit.path("payload");
                long questionId = payload.path("questionId").asLong(-1);
                long revision = payload.path("revision").asLong(-1);
                String part = payload.path("part").asText("QUESTION");
                int ordinal = payload.path("ordinal").asInt(0);
                if (questionId <= 0) continue;
                if (!allowedSet.contains(new EligibleQuestion(questionId, revision))) continue;
                hits.add(new QuestionVectorPoint(hit.path("id").asText(), questionId, revision,
                        Part.valueOf(part), ordinal, null));
            }
        }
        return hits;
    }

    @Override
    public void deleteQuestion(String indexVersion, long questionId, long throughRevision) {
        var filter = Map.of("must", List.of(
                Map.of("key", "questionId", "match", Map.of("value", questionId)),
                Map.of("key", "revision", "range", Map.of("lte", throughRevision))));
        execute(() -> client.post()
                .uri("/collections/{name}/points/delete", collectionName(indexVersion))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filter", filter, "wait", true))
                .retrieve().body(JsonNode.class));
    }

    String collectionName(String indexVersion) {
        return "q_" + indexVersion;
    }

    private <T> T execute(java.util.function.Supplier<T> action) {
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RestClientException error) {
                last = error;
                if (error instanceof RestClientResponseException response
                        && response.getStatusCode().is4xxClientError()
                        && response.getStatusCode().value() != 429) {
                    throw new KnowledgeVectorException("Qdrant client error: "
                            + response.getStatusCode().value() + " " + response.getResponseBodyAsString(), error);
                }
            }
        }
        throw vectorFailure("Qdrant request failed after retries", last);
    }

    private KnowledgeVectorException vectorFailure(String message, Exception cause) {
        return new KnowledgeVectorException(message, cause);
    }
}
