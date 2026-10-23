package com.zhixuewuli.service.knowledge;

import java.util.List;
import java.util.function.BooleanSupplier;

public interface EmbeddingGateway {
    EmbeddingBatch embed(List<String> texts, boolean query, BooleanSupplier cancelled);
}
