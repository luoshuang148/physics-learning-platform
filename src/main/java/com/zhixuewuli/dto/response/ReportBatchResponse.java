package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 批量报告批次响应。 */
@Data
@Schema(description = "批量报告批次")
public class ReportBatchResponse {

    @Schema(description = "批次 ID")
    private Long id;

    @Schema(description = "班级 ID")
    private Long classId;

    @Schema(description = "班级名称")
    private String className;

    @Schema(description = "教学任务 ID")
    private Long eventId;

    @Schema(description = "教学任务标题")
    private String eventTitle;

    @Schema(description = "批次状态：PENDING/PROCESSING/SUCCEEDED/PARTIAL_FAILED/FAILED")
    private String status;

    @Schema(description = "创建时的名单数量（含跳过项）")
    private int totalCount;

    @Schema(description = "成功数量")
    private int succeededCount;

    @Schema(description = "失败数量（含重试耗尽）")
    private int failedCount;

    @Schema(description = "跳过数量（名单内无作答记录，未调用模型）")
    private int skippedCount;

    @Schema(description = "待处理数量")
    private int pendingCount;

    @Schema(description = "处理中数量")
    private int processingCount;

    @Schema(description = "已完成数量（成功+失败+跳过）")
    private int finishedCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;
}
