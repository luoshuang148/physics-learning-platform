package com.zhixuewuli.dto.agent;

import java.util.List;
import java.util.Map;

/**
 * 校验后的计划预览。保存的预览携带版本化 candidateRegistry 快照，
 * 不让调用方重新传入未授权题号。
 */
public record ValidatedPlanPreview(int days, Long sourceEventId, String sourceAnswerRevision,
                                   String executionMode, List<LearningPlanItemPreview> items,
                                   List<String> evidenceSummary, boolean partial,
                                   Map<String, Object> candidateRegistry) {
    public ValidatedPlanPreview {
        items = items == null ? List.of() : List.copyOf(items);
        evidenceSummary = evidenceSummary == null ? List.of() : List.copyOf(evidenceSummary);
        candidateRegistry = candidateRegistry == null ? Map.of() : Map.copyOf(candidateRegistry);
    }
}
