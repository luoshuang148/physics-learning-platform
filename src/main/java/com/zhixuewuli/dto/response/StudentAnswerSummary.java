package com.zhixuewuli.dto.response;

/** Global student history counters; never derived from one page. */
public record StudentAnswerSummary(long totalAttempts, long correctAttempts, long wrongQuestions) {}
