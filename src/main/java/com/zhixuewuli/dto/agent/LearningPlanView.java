package com.zhixuewuli.dto.agent;

import java.util.List;

public record LearningPlanView(Long id, Long eventId, String status, long version, int days,
                               List<LearningPlanItemPreview> items, String sourceAnswerRevision,
                               int practiceAttempts, int practiceCorrect, String progressStatus) {
    public LearningPlanView { items = items == null ? List.of() : List.copyOf(items); }
}
