package com.zhixuewuli.service.platformsearch;

import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Evidence;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Part;
import com.zhixuewuli.entity.QuestionSearchUnit;
import com.zhixuewuli.repository.QuestionSearchUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 来源查看：重新鉴权后返回原题/解析片段。
 * 来源 ID 即使猜中也要重新鉴权；已失去权限的内容不得送入新模型请求。
 */
@Service
@RequiredArgsConstructor
public class PlatformSourceService {

    private final QuestionSearchUnitRepository searchUnits;
    private final QuestionRetrievalAccessPolicy accessPolicy;

    /**
     * 按来源 ID 读取证据；无权限或版本不匹配时抛出业务异常（不泄露内容）。
     */
    public Evidence requireReadable(Long viewerId, String sourceId) {
        var parsed = PlatformQuestionRetriever.parseSourceId(sourceId)
                .orElseThrow(() -> new com.zhixuewuli.common.exception.BusinessException("来源不存在或已失效"));
        if (!accessPolicy.isEligible(viewerId, parsed.questionId(), parsed.revision())) {
            // 不区分「不存在」与「无权限」，避免探测
            throw new com.zhixuewuli.common.exception.BusinessException("来源不存在或已失效");
        }
        QuestionSearchUnit unit = searchUnits
                .findByQuestionIdAndRevisionOrderByPartAscOrdinalNoAsc(parsed.questionId(), parsed.revision())
                .stream()
                .filter(u -> u.getPart().equals(parsed.part().name()) && u.getOrdinalNo() == parsed.ordinal())
                .findFirst()
                .orElseThrow(() -> new com.zhixuewuli.common.exception.BusinessException("来源不存在或已失效"));
        return new Evidence(sourceId, unit.getQuestionId(), unit.getRevision(),
                unit.getPart(), unit.getOrdinalNo(), unit.getUnitText());
    }

    /** 供检索结果回源补全：批量读取且再鉴权。 */
    public java.util.List<Evidence> readableAll(Long viewerId, java.util.List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return java.util.List.of();
        java.util.List<Evidence> out = new java.util.ArrayList<>();
        for (String sourceId : sourceIds) {
            try {
                out.add(requireReadable(viewerId, sourceId));
            } catch (RuntimeException ignored) {
                // 失去权限的来源直接丢弃，不进模型上下文
            }
        }
        return out;
    }
}
