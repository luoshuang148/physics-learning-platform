package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 批量生成学生报告请求：只提交任务与幂等键，名单和班级由服务端校验。 */
@Data
@Schema(description = "批量生成学生报告请求")
public class ReportBatchCreateRequest {

    @NotNull(message = "教学任务 ID 不能为空")
    @Schema(description = "教学任务 ID", example = "1")
    private Long eventId;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 100, message = "幂等键长度不能超过100字符")
    @Schema(description = "幂等键：同教师同键同参数返回原批次，同键不同参数会被拒绝", example = "evt-1-batch-20261019")
    private String idempotencyKey;
}
