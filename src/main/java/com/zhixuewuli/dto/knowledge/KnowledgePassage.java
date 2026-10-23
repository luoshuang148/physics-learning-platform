package com.zhixuewuli.dto.knowledge;
public record KnowledgePassage(String sourceId, Long chunkId, Long documentId, Long revisionId, String title,
                               Integer page, String section, String text, Integer keywordRank, Double keywordScore,
                               Integer vectorRank, Double vectorScore, Double rrfScore) {}
