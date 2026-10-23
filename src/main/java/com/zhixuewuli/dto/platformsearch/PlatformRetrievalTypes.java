package com.zhixuewuli.dto.platformsearch;

import java.util.List;

/**
 * 平台题库检索共用契约（Task 2/3/4/5/6 共用，勿在各任务私自复制分叉）。
 *
 * <p>命名与语义对齐设计文档 §5 与实施计划「文件布局与共用契约」。</p>
 */
public final class PlatformRetrievalTypes {

    private PlatformRetrievalTypes() {
    }

    /** 检索模式：服务端策略，公共请求不允许调用方改变权限。 */
    public enum RetrievalMode {
        /** 仅关键词（MySQL 文本检索 / 可测试中文分词适配器） */
        KEYWORD,
        /** 仅向量 */
        VECTOR,
        /** 混合 RRF */
        HYBRID
    }

    /** 检索结果状态。 */
    public enum RetrievalStatus {
        /** 命中且数量足够 */
        MATCHED,
        /** 检索执行成功但授权候选/相关结果不足 */
        INSUFFICIENT,
        /** 检索服务故障（向量不可用且未允许降级等） */
        UNAVAILABLE
    }

    /**
     * 检索请求。mode 是服务端策略字段，不接受前端覆盖权限。
     *
     * @param query                  查询文本 1～500 字（接口层校验）
     * @param eventId                可选作业上下文（用于待考题保护等）
     * @param knowledgePointCodes    知识点过滤（最多 15 个）
     * @param difficulty             难度 1～5
     * @param excludeAttempted       是否排除本人已作答题
     * @param limit                  返回题目数 1～5
     * @param mode                   检索模式
     */
    public record SearchRequest(
            String query,
            Long eventId,
            List<String> knowledgePointCodes,
            Integer difficulty,
            boolean excludeAttempted,
            int limit,
            RetrievalMode mode
    ) {
    }

    /**
     * 命中的题目（题目级去重后的展示单元）。
     */
    public record QuestionHit(
            Long questionId,
            long revision,
            String content,
            Integer difficulty,
            List<String> knowledgePointCodes,
            List<String> sourceIds
    ) {
    }

    /**
     * 来源单元（检索单元原文，回源鉴权后才可进入模型上下文）。
     * 来源 ID 形如 {@code Q:{questionId}:{revision}:{part}:{ordinal}}。
     */
    public record Evidence(
            String sourceId,
            Long questionId,
            long revision,
            String part,
            int ordinal,
            String text
    ) {
    }

    /**
     * 检索结果。
     *
     * @param status      检索状态
     * @param actualMode  实际执行模式（降级时如 lexical_only 由调用方放入此字符串）
     * @param questions   题目级结果（最多 5 道）
     * @param sources     来源单元（最多 5 个）
     * @param durationMs  检索耗时
     */
    public record SearchResult(
            RetrievalStatus status,
            String actualMode,
            List<QuestionHit> questions,
            List<Evidence> sources,
            long durationMs
    ) {
    }

    /** 授权后的候选题（仅 ID+revision，不含内容）。 */
    public record EligibleQuestion(Long questionId, long revision) {
    }

    /** 题目索引状态（教师侧展示）。 */
    public record QuestionIndexStatus(
            Long questionId,
            long revision,
            String status,
            String failureCode
    ) {
    }

    /** 检索单元分片类型。 */
    public enum Part {
        /** 题干与选项 */
        QUESTION,
        /** 解析 */
        EXPLANATION
    }

    /**
     * 构建后的检索单元。
     */
    public record SearchUnit(
            Long questionId,
            long revision,
            Part part,
            int ordinal,
            String text,
            String contentHash,
            List<String> knowledgePointCodes,
            Integer difficulty
    ) {
    }

    /** 向量点（不存学生数据）。pointId 确定性生成，重试幂等。 */
    public record QuestionVectorPoint(
            String pointId,
            Long questionId,
            long revision,
            Part part,
            int ordinal,
            float[] vector
    ) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuestionVectorPoint other)) return false;
            return ordinal == other.ordinal
                    && revision == other.revision
                    && java.util.Objects.equals(questionId, other.questionId)
                    && part == other.part
                    && java.util.Objects.equals(pointId, other.pointId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pointId, questionId, revision, part, ordinal);
        }
    }
}
