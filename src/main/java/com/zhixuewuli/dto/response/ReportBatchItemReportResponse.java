package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 批量报告内容响应（只能在班级权限校验后读取）。 */
@Data
@Schema(description = "批量报告内容")
public class ReportBatchItemReportResponse {

    @Schema(description = "批次 ID")
    private Long batchId;

    @Schema(description = "子任务 ID")
    private Long itemId;

    @Schema(description = "教学任务 ID")
    private Long eventId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "报告内容")
    private String content;

    @Schema(description = "报告数据依据（JSON）")
    private String evidenceJson;

    @Schema(description = "生成时间")
    private LocalDateTime generatedAt;
}
