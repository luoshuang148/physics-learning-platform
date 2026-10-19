package com.zhixuewuli.dto.response;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhixuewuli.entity.TeacherEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "教学任务响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeacherEventResponse {
    @JsonIgnore
    @Builder.Default
    private boolean timestampUtc = true;

    public String getTimeBasis() { return timestampUtc ? "UTC" : "LEGACY_LOCAL"; }

    @JsonGetter("eventTimestamp")
    public String learningTimestampForJson() {
        return com.zhixuewuli.common.util.LearningTimeWire.format(eventTimestamp, timestampUtc);
    }

    @JsonGetter("startAt")
    public String startAtForJson() {
        return startAt == null ? null : com.zhixuewuli.common.util.LearningTimeWire.format(startAt, timestampUtc);
    }

    @JsonGetter("dueAt")
    public String dueAtForJson() {
        return dueAt == null ? null : com.zhixuewuli.common.util.LearningTimeWire.format(dueAt, timestampUtc);
    }

    private List<Long> questionIds;
    private Long id;
    private Long teacherId;
    private String teacherName;
    private Long classId;
    private String eventTitle;
    private String eventDescription;
    private LocalDateTime eventTimestamp;
    private TeacherEvent.Status status;
    private LocalDateTime startAt;
    private LocalDateTime dueAt;
    private String availability;
    private Boolean canAnswer;
    private Integer questionCount;
    private Boolean isGlobal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
