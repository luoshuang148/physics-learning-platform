package com.zhixuewuli.service.knowledge;
public enum KnowledgeMode {
    OFF(null), VECTOR(RetrievalMode.VECTOR), AUTO(RetrievalMode.HYBRID);
    private final RetrievalMode retrievalMode;
    KnowledgeMode(RetrievalMode retrievalMode){this.retrievalMode=retrievalMode;}
    public RetrievalMode retrievalMode(){return retrievalMode;}
}
