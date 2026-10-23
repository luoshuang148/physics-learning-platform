package com.zhixuewuli.dto.agent;

public record PersonalLearningSummary(long assignmentAttempts, long correctAttempts, long wrongQuestionCount,
                                     String sourceAnswerRevision) {}
