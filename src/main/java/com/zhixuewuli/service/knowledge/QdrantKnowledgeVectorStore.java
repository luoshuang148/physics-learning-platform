package com.zhixuewuli.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class QdrantKnowledgeVectorStore implements KnowledgeVectorStore {
    private static final int MAX_ATTEMPTS = 3;
    private final RestClient client;

    public QdrantKnowledgeVectorStore(RestClient.Builder builder, KnowledgeProperties properties) {
        var clientBuilder = builder.clone().baseUrl(properties.getQdrantUrl());
        if (properties.getQdrantApiKey() != null && !properties.getQdrantApiKey().isBlank()) {
            clientBuilder.defaultHeader("api-key", properties.getQdrantApiKey());
        }
        this.client = clientBuilder.build();
    }

    @Override
    public void ensureCollection(String indexVersion, int dimensions) {
        var name = collectionName(indexVersion);
        try {
            execute(() -> client.get().uri("/collections/{name}", name).retrieve().body(JsonNode.class));
            return;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() != 404) throw vectorFailure("collection lookup failed", error);
        }
        execute(() -> client.put().uri("/collections/{name}", name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                .retrieve().body(JsonNode.class));
    }

    @Override
    public void upsert(String indexVersion, List<KnowledgeVectorPoint> points) {
        if (points == null || points.isEmpty()) return;
        var payload = points.stream().map(point -> Map.of(
                "id", point.pointId().toString(),
                "vector", point.vector(),
                "payload", Map.of("chunkId", point.chunkId(), "revisionId", point.revisionId(),
                        "classId", point.classId(), "ordinal", point.ordinal(), "indexVersion", indexVersion))).toList();
        execute(() -> client.put().uri(uriBuilder -> uriBuilder.path("/collections/{name}/points")
                        .queryParam("wait", "true").build(collectionName(indexVersion)))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("points", payload))
                .retrieve().body(JsonNode.class));
    }

    @Override
    public long count(String indexVersion) {
        try {
            JsonNode result = execute(() -> client.post().uri("/collections/{name}/points/count", collectionName(indexVersion))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("exact", true))
                    .retrieve().body(JsonNode.class));
            return result == null ? -1 : result.path("result").path("count").asLong(-1);
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            if (error instanceof KnowledgeVectorException vectorError) throw vectorError;
            throw vectorFailure("Qdrant point count failed", error);
        }
    }

    @Override
    public List<KnowledgeVectorHit> search(String indexVersion, long classId, long revisionId,
                                           float[] vector, int limit) {
        if (vector == null || vector.length == 0 || limit < 1) return List.of();
        var filter = Map.of("must", List.of(
                Map.of("key", "classId", "match", Map.of("value", classId)),
                Map.of("key", "revisionId", "match", Map.of("value", revisionId))));
        JsonNode result;
        try {
            result = execute(() -> client.post().uri("/collections/{name}/points/query", collectionName(indexVersion))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", vector, "filter", filter, "limit", Math.min(limit, 10), "with_payload", true))
                    .retrieve().body(JsonNode.class));
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            if (error instanceof KnowledgeVectorException vectorError) throw vectorError;
            throw vectorFailure("Qdrant vector search failed", error);
        }
        var hits = new ArrayList<KnowledgeVectorHit>();
        if (result == null || !result.path("result").path("points").isArray()) return List.of();
        for (JsonNode point : result.path("result").path("points")) {
            JsonNode payload = point.path("payload");
            try {
                hits.add(new KnowledgeVectorHit(UUID.fromString(point.path("id").asText()),
                        payload.path("chunkId").asLong(), payload.path("revisionId").asLong(),
                        payload.path("classId").asLong(), payload.path("ordinal").asInt(),
                        point.path("score").asDouble()));
            } catch (IllegalArgumentException ignored) {
                // Invalid or incomplete payloads are never returned as readable evidence.
            }
        }
        return List.copyOf(hits);
    }

    @Override
    public void deleteRevision(String indexVersion) {
        try {
            execute(() -> client.delete().uri("/collections/{name}", collectionName(indexVersion))
                    .retrieve().body(JsonNode.class));
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() != 404) throw vectorFailure("revision index deletion failed", error);
        }
    }

    private <T> T execute(Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (RestClientResponseException error) {
                if (error.getStatusCode().value() < 500 && error.getStatusCode().value() != 429) throw error;
                last = error;
            } catch (RestClientException error) {
                last = error;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new KnowledgeVectorException("Qdrant request interrupted", interrupted);
                }
            }
        }
        throw vectorFailure("Qdrant request failed after three attempts", last);
    }

    private String collectionName(String indexVersion) {
        if (indexVersion == null || !indexVersion.matches("idx_[a-f0-9]{24}")) {
            throw new IllegalArgumentException("invalid knowledge index version");
        }
        return "knowledge_" + indexVersion;
    }

    private KnowledgeVectorException vectorFailure(String message, Throwable cause) {
        return new KnowledgeVectorException(message, cause);
    }
}
