package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("knowledge-integration")
class LocalBgeEmbeddingSmokeTest {
    @Test
    void localChineseModelReturnsFinite512DimensionalVectorsWithoutNetwork() {
        var properties = new KnowledgeProperties();
        var gateway = new LocalBgeEmbeddingGateway(properties);

        var result = gateway.embed(List.of("牛顿第二定律：合外力等于质量乘以加速度。"), false, () -> false);

        assertEquals("bge-small-zh-v1.5-quantized", result.model());
        assertEquals(512, result.dimensions());
        assertEquals(1, result.vectors().size());
        assertEquals(512, result.vectors().getFirst().length);
        assertTrue(java.util.Arrays.stream(toDoubleArray(result.vectors().getFirst())).allMatch(Double::isFinite));
    }

    @Test
    void cancellationIsCheckedBeforeLoadingOrRunningTheLocalModel() {
        var gateway = new LocalBgeEmbeddingGateway(new KnowledgeProperties());
        assertThrows(KnowledgeEmbeddingException.class,
                () -> gateway.embed(List.of("test"), false, () -> true));
    }

    private double[] toDoubleArray(float[] values) {
        var result = new double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }
}
