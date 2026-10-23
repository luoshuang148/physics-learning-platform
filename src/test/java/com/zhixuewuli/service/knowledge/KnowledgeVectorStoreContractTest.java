package com.zhixuewuli.service.knowledge;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class KnowledgeVectorStoreContractTest {
    private WireMockServer server;
    private QdrantKnowledgeVectorStore store;
    private final String index = "idx_0123456789abcdef01234567";

    @BeforeEach
    void setUp() {
        server = new WireMockServer(0);
        server.start();
        var properties = new KnowledgeProperties();
        properties.setQdrantUrl("http://localhost:" + server.port());
        properties.setQdrantApiKey("contract-key");
        store = new QdrantKnowledgeVectorStore(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    void collectionUpsertCountAndSearchUseVersionAndAuthorizationPayload() {
        server.stubFor(get(urlEqualTo("/collections/knowledge_" + index))
                .withHeader("api-key", equalTo("contract-key")).willReturn(aResponse().withStatus(404)));
        server.stubFor(put(urlEqualTo("/collections/knowledge_" + index))
                .withRequestBody(matchingJsonPath("$.vectors.size", equalTo("512")))
                .willReturn(okJson("{\"result\":true}")));
        server.stubFor(put(urlEqualTo("/collections/knowledge_" + index + "/points?wait=true"))
                .withRequestBody(matchingJsonPath("$.points[0].payload.classId", equalTo("20")))
                .withRequestBody(matchingJsonPath("$.points[0].payload.revisionId", equalTo("2")))
                .willReturn(okJson("{\"result\":{\"status\":\"completed\"}}")));
        server.stubFor(post(urlEqualTo("/collections/knowledge_" + index + "/points/count"))
                .willReturn(okJson("{\"result\":{\"count\":1}}")));
        server.stubFor(post(urlEqualTo("/collections/knowledge_" + index + "/points/query"))
                .withRequestBody(matchingJsonPath("$.filter.must[0].key", equalTo("classId")))
                .withRequestBody(matchingJsonPath("$.filter.must[1].key", equalTo("revisionId")))
                .willReturn(okJson("{\"result\":{\"points\":[{" +
                        "\"id\":\"d9428888-122b-11e1-b85c-61cd3cbb3210\"," +
                        "\"score\":0.91,\"payload\":{\"chunkId\":7,\"revisionId\":2,\"classId\":20,\"ordinal\":0}}]}}")));

        store.ensureCollection(index, 512);
        store.upsert(index, List.of(new KnowledgeVectorPoint(
                UUID.fromString("d9428888-122b-11e1-b85c-61cd3cbb3210"), new float[]{1, 0}, 7, 2, 20, 0)));

        assertEquals(1L, store.count(index));
        var hits = store.search(index, 20, 2, new float[]{1, 0}, 50);
        assertEquals(1, hits.size());
        assertEquals(7L, hits.getFirst().chunkId());
        assertEquals(0.91, hits.getFirst().score());
        store.deleteRevision(index);
        server.verify(1, putRequestedFor(urlEqualTo("/collections/knowledge_" + index + "/points?wait=true")));
        server.verify(1, postRequestedFor(urlEqualTo("/collections/knowledge_" + index + "/points/query")));
    }

    @Test
    void transientQdrantFailuresAreRetriedAtMostThreeTimes() {
        server.stubFor(put(urlEqualTo("/collections/knowledge_" + index))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        server.stubFor(put(urlEqualTo("/collections/knowledge_" + index))
                .inScenario("retry").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("third"));
        server.stubFor(put(urlEqualTo("/collections/knowledge_" + index))
                .inScenario("retry").whenScenarioStateIs("third")
                .willReturn(okJson("{\"result\":true}")));
        server.stubFor(get(urlEqualTo("/collections/knowledge_" + index))
                .willReturn(aResponse().withStatus(404)));

        assertDoesNotThrow(() -> store.ensureCollection(index, 512));
        server.verify(3, putRequestedFor(urlEqualTo("/collections/knowledge_" + index)));
    }

    @Test
    void collectionNameRejectsUntrustedIndexVersion() {
        assertThrows(IllegalArgumentException.class, () -> store.count("../../other"));
    }
}
