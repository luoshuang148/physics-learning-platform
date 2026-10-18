package com.zhixuewuli.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Lightweight history row: no image bytes, options or full explanation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerRecordSummaryResponse {
    private String recordId;
    private Long eventId;
    private boolean practice;
    private Long attemptOrder;
    private Long userId;
    private Long questionId;
    private String sessionId;
    private String userAnswer;
    private Boolean correct;
    private String questionPreview;

    @JsonIgnore
    private LocalDateTime answeredAt;
    @JsonIgnore
    private boolean timestampUtc;

    @JsonGetter("answeredAt")
    public String answeredAtForJson() {
        return com.zhixuewuli.common.util.LearningTimeWire.format(answeredAt, timestampUtc);
    }
}
