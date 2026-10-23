package com.zhixuewuli.common.config.properties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data @Component @ConfigurationProperties(prefix="app.knowledge")
public class KnowledgeProperties {
    private boolean enabled=false;
    private String storagePath="data/knowledge";
    private long maxFileBytes=10L*1024*1024;
    private int maxDocumentsPerClass=50;
    private int maxPdfPages=100;
    private int maxExtractedCharacters=200000;
    private String qdrantUrl="http://localhost:6333";
    private String qdrantApiKey="";
    private String embeddingModel="bge-small-zh-v1.5-quantized";
    private int embeddingDimensions=512;
    private String extractorVersion="pdfbox-3-v1";
    private String chunkerVersion="char-900-overlap-100-v1";
    private int indexWorkers=2;
}
