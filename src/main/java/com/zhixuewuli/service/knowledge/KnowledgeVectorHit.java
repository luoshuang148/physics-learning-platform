package com.zhixuewuli.service.knowledge;

import java.util.UUID;

public record KnowledgeVectorHit(UUID pointId, long chunkId, long revisionId,
                                 long classId, int ordinal, double score) {
}
