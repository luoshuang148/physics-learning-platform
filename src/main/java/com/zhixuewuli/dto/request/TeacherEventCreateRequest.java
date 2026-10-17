package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师事件创建请求DTO
 * 
 * 用于接收教师发布新事件的请求数据。
 * 包含事件标题、描述和时间戳等必要信息。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "教师事件创建请求")
@Data
public class TeacherEventCreateRequest {

    @NotBlank(message = "事件标题不能为空")
    @Size(max = 20, message = "事件标题长度不能超过20字符")
    @Schema(description = "事件标题", example = "新学期开始", requiredMode = Schema.RequiredMode.REQUIRED)
    private String eventTitle;

    @Size(max = 100, message = "事件描述长度不能超过100字符")
    @Schema(description = "事件描述", example = "2026年春季学期正式开始")
    private String eventDescription;

    @NotNull(message = "事件时间戳不能为空")
    @Schema(description = "事件时间戳", example = "2026-03-01T08:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime eventTimestamp;
}