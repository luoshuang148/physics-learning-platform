package com.zhixuewuli.service.knowledge;

import java.util.List;

public record EmbeddingBatch(String model, int dimensions, List<float[]> vectors) {
    public EmbeddingBatch {
        vectors = List.copyOf(vectors);
    }
}
