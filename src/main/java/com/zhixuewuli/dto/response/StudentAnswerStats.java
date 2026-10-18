package com.zhixuewuli.dto.response;

/** Attempt totals used by the teacher's student list; includes original and practice attempts. */
public record StudentAnswerStats(Long userId, Long totalCount, Long wrongCount) {}
