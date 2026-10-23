package com.zhixuewuli.dto.agent;

import java.util.List;

public record ValidatedPlanPreview(int days, Long sourceEventId, String sourceAnswerRevision,
                                   String executionMode, List<LearningPlanItemPreview> items,
                                   List<String> evidenceSummary, boolean partial) {
    public ValidatedPlanPreview {
        items = items == null ? List.of() : List.copyOf(items);
        evidenceSummary = evidenceSummary == null ? List.of() : List.copyOf(evidenceSummary);
    }
}
