package com.zhixuewuli.dto.agent;

import java.util.List;

public record LearningPlanItemPreview(int day, String knowledgePointCode, Long questionId,
                                      List<String> reasonSourceIds, Long id) {
    public LearningPlanItemPreview(int day, String knowledgePointCode, Long questionId, List<String> reasonSourceIds) {
        this(day, knowledgePointCode, questionId, reasonSourceIds, null);
    }
    public LearningPlanItemPreview { reasonSourceIds = reasonSourceIds == null ? List.of() : List.copyOf(reasonSourceIds); }
}
