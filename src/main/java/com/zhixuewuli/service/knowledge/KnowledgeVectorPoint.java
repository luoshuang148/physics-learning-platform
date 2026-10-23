package com.zhixuewuli.service.knowledge;

import java.util.UUID;

public record KnowledgeVectorPoint(UUID pointId, float[] vector, long chunkId,
                                   long revisionId, long classId, int ordinal) {
}
