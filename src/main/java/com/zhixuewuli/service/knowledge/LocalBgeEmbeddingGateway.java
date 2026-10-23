package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Component
public class LocalBgeEmbeddingGateway implements EmbeddingGateway {
    private static final String MODEL_ID = "bge-small-zh-v1.5-quantized";
    private static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";
    private final KnowledgeProperties properties;
    private volatile BgeSmallZhV15QuantizedEmbeddingModel model;

    public LocalBgeEmbeddingGateway(KnowledgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmbeddingBatch embed(List<String> texts, boolean query, BooleanSupplier cancelled) {
        if (!MODEL_ID.equals(properties.getEmbeddingModel())) {
            throw new KnowledgeEmbeddingException("UNSUPPORTED_EMBEDDING_MODEL");
        }
        if (texts == null || texts.isEmpty()) return new EmbeddingBatch(MODEL_ID, properties.getEmbeddingDimensions(), List.of());
        var vectors = new ArrayList<float[]>(texts.size());
        for (String text : texts) {
            if (cancelled != null && cancelled.getAsBoolean()) throw new KnowledgeEmbeddingException("embedding cancelled");
            var input = query ? QUERY_PREFIX + text : text;
            vectors.add(model().embed(input).content().vector());
        }
        return new EmbeddingBatch(MODEL_ID, vectors.getFirst().length, vectors);
    }

    private BgeSmallZhV15QuantizedEmbeddingModel model() {
        var current = model;
        if (current != null) return current;
        synchronized (this) {
            if (model == null) model = new BgeSmallZhV15QuantizedEmbeddingModel();
            return model;
        }
    }
}
