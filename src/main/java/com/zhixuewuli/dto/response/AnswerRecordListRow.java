package com.zhixuewuli.dto.response;

import java.time.LocalDateTime;

/** List projection intentionally excludes question image bodies. */
public record AnswerRecordListRow(String recordId, Long userId, Long questionId, String sessionId,
        String userAnswer, Boolean correct, LocalDateTime answeredAt, String errorReason, String studentName,
        String questionContent, String options, String correctAnswer, String analysis, Integer difficulty, boolean timestampUtc) { }
