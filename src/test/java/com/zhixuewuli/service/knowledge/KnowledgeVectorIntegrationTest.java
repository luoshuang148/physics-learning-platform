package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("knowledge-integration")
class KnowledgeVectorIntegrationTest {
    private static GenericContainer<?> qdrant;
    private static Path storage;
    private static final String INDEX = "idx_89abcdef0123456789abcdef";

    @BeforeAll
    static void startQdrant() throws Exception {
        storage = Files.createTempDirectory("qdrant-knowledge-it-");
        qdrant = new GenericContainer<>(DockerImageName.parse("qdrant/qdrant:v1.19.2"))
                .withFileSystemBind(storage.toString(), "/qdrant/storage")
                .withExposedPorts(6333);
        qdrant.start();
    }

    @AfterAll
    static void stopQdrant() {
        if (qdrant != null) qdrant.stop();
    }

    private static QdrantKnowledgeVectorStore store() {
        var properties = new KnowledgeProperties();
        properties.setQdrantUrl("http://" + qdrant.getHost() + ":" + qdrant.getMappedPort(6333));
        return new QdrantKnowledgeVectorStore(RestClient.builder(), properties);
    }

    @Test
    void persistsAuthorizedVersionedPointsAcrossRestart() {
        var store = store();
        store.ensureCollection(INDEX, 3);
        store.upsert(INDEX, List.of(new KnowledgeVectorPoint(
                UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210"), new float[]{1, 0, 0}, 71, 8, 20, 0)));
        assertEquals(1, store.count(INDEX));
        assertEquals(1, store.search(INDEX, 20, 8, new float[]{1, 0, 0}, 5).size());
        assertTrue(store.search(INDEX, 21, 8, new float[]{1, 0, 0}, 5).isEmpty());

        qdrant.stop();
        qdrant.start();

        var restarted = store();
        assertEquals(1, restarted.count(INDEX));
        assertEquals(1, restarted.search(INDEX, 20, 8, new float[]{1, 0, 0}, 5).size());
        restarted.deleteRevision(INDEX);
        assertThrows(KnowledgeVectorException.class, () -> restarted.count(INDEX));
    }
}
