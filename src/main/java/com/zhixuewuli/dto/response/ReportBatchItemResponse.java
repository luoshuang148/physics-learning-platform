package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 批量报告子任务响应。 */
@Data
@Schema(description = "批量报告子任务")
public class ReportBatchItemResponse {

    @Schema(description = "子任务 ID")
    private Long id;

    @Schema(description = "学生用户 ID")
    private Long userId;

    @Schema(description = "学生姓名（创建批次时的快照）")
    private String studentName;

    @Schema(description = "状态：PENDING/PROCESSING/SUCCEEDED/FAILED/SKIPPED")
    private String status;

    @Schema(description = "已执行的模型生成次数（含首次）")
    private int attempts;

    @Schema(description = "自动重试上限")
    private int maxAttempts;

    @Schema(description = "手动重试次数")
    private int manualRetryCount;

    @Schema(description = "错误分类：TIMEOUT/RATE_LIMIT/UNAVAILABLE/AUTH/INPUT_INVALID/DATA_MISSING/AI_DISABLED/EXHAUSTED/UNKNOWN")
    private String errorCode;

    @Schema(description = "长度受限的安全错误信息")
    private String errorMessage;

    @Schema(description = "跳过原因")
    private String skipReason;

    @Schema(description = "报告 ID（成功后可读取报告）")
    private Long reportId;

    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;
}
