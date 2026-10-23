package com.zhixuewuli.service.knowledge;
public record KnowledgeSearchHit(Long chunkId, Long documentId, Long revisionId, Long classId, Long ordinal,
                                 String title, Integer page, String section, String text, int rank, double rawScore) {}
