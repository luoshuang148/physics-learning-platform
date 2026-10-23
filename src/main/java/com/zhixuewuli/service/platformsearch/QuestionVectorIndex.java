package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.EligibleQuestion;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionVectorPoint;

import java.util.List;

/**
 * 题库向量索引（独立 collection，禁止伪造 classId 复用文档集合）。
 */
public interface QuestionVectorIndex {

    void ensureCollection(String indexVersion, int dimensions);

    void upsert(String indexVersion, List<QuestionVectorPoint> points);

    long count(String indexVersion);

    /**
     * 在授权候选集合内向量检索。
     * 必须同时过滤 allowed（questionId+revision），禁止以过滤后不足为理由扩大授权范围。
     */
    List<QuestionVectorPoint> search(String indexVersion, List<EligibleQuestion> allowed,
                                     float[] vector, int limit);

    /** 删除某题 <= throughRevision 的向量点。 */
    void deleteQuestion(String indexVersion, long questionId, long throughRevision);
}
