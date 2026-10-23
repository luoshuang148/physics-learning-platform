package com.zhixuewuli.dto.agent;

import java.util.List;

public record LearningPlanItemPreview(int day, String knowledgePointCode, Long questionId,
                                      List<String> reasonSourceIds) {
    public LearningPlanItemPreview { reasonSourceIds = reasonSourceIds == null ? List.of() : List.copyOf(reasonSourceIds); }
}
