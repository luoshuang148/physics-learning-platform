package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;

public record KnowledgeIndexVersion(String id) {
    public static KnowledgeIndexVersion forRevision(long revisionId, KnowledgeProperties properties) {
        String material = String.join("|", Long.toString(revisionId), properties.getEmbeddingModel(),
                Integer.toString(properties.getEmbeddingDimensions()), properties.getExtractorVersion(),
                properties.getChunkerVersion());
        return new KnowledgeIndexVersion("idx_" + DigestUtils.sha256Hex(material.getBytes(StandardCharsets.UTF_8)).substring(0, 24));
    }
}
