package com.zhixuewuli.dto.request;

import com.zhixuewuli.entity.TeacherEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "教学任务创建/编辑请求")
@Data
public class TeacherEventCreateRequest {
    @Size(min = 0, max = 200)
    private List<@NotNull Long> questionIds;

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 20, message = "任务标题长度不能超过20字符")
    private String eventTitle;

    @Size(max = 100, message = "任务描述长度不能超过100字符")
    private String eventDescription;

    @NotNull(message = "任务时间不能为空")
    private LocalDateTime eventTimestamp;

    @Schema(description = "创建状态；默认 PUBLISHED，兼容旧调用")
    private TeacherEvent.Status status;

    @Schema(description = "允许开始作答的时间；为空表示立即开始")
    private LocalDateTime startAt;

    @Schema(description = "截止时间；为空表示不设截止")
    private LocalDateTime dueAt;

    @Schema(description = "所属班级；旧调用为空时使用教师默认班级")
    private Long classId;
}
