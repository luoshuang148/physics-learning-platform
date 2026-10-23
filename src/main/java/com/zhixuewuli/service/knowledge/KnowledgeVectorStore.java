package com.zhixuewuli.service.knowledge;

import java.util.List;

public interface KnowledgeVectorStore {
    void ensureCollection(String indexVersion, int dimensions);
    void upsert(String indexVersion, List<KnowledgeVectorPoint> points);
    long count(String indexVersion);
    List<KnowledgeVectorHit> search(String indexVersion, long classId, long revisionId, float[] vector, int limit);
    void deleteRevision(String indexVersion);
}
