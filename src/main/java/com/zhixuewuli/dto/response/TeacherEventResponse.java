package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教师事件响应DTO
 * 
 * 用于返回教师事件的完整信息，包括事件ID、教师信息和事件详情。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "教师事件响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TeacherEventResponse {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Builder.Default
    private boolean timestampUtc=true;
    public String getTimeBasis() { return timestampUtc ? "UTC" : "LEGACY_LOCAL"; }
    @com.fasterxml.jackson.annotation.JsonGetter("eventTimestamp")
    public String learningTimestampForJson() {
        return com.zhixuewuli.common.util.LearningTimeWire.format(eventTimestamp,timestampUtc);
    }

    private java.util.List<Long> questionIds;

    @Schema(description = "事件ID", example = "1")
    private Long id;

    @Schema(description = "教师ID", example = "2")
    private Long teacherId;

    @Schema(description = "教师姓名", example = "张老师")
    private String teacherName;

    @Schema(description = "事件标题", example = "新学期开始")
    private String eventTitle;

    @Schema(description = "事件描述", example = "2026年春季学期正式开始")
    private String eventDescription;

    @Schema(description = "事件时间戳", example = "2026-03-01T08:00:00")
    private LocalDateTime eventTimestamp;

    @Schema(description = "是否为全局事件", example = "true")
    private Boolean isGlobal;

    @Schema(description = "创建时间", example = "2026-03-01T08:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-03-01T08:00:00")
    private LocalDateTime updatedAt;
}