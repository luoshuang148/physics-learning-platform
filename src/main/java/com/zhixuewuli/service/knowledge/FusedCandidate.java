package com.zhixuewuli.service.knowledge;
public record FusedCandidate(KnowledgeSearchHit hit, Integer keywordRank, Integer vectorRank, double rrfScore) {
    public Long chunkId() { return hit.chunkId(); }
}
