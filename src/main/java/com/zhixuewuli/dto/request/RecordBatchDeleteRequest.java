package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量删除请求DTO
 * 
 * 用于批量删除指定时间范围内的答题记录。
 * 该DTO主要用于教师的批量数据清理操作。
 * 
 * <p>
 * 核心字段说明：
 * </p>
 * <ul>
 * <li><strong>startTime</strong>: 删除时间范围的起始时间（包含）</li>
 * <li><strong>endTime</strong>: 删除时间范围的结束时间（包含）</li>
 * </ul>
 * 
 * <p>
 * 业务约束：
 * </p>
 * <ul>
 * <li>开始时间必须早于结束时间</li>
 * <li>时间范围不应超过合理限制（由业务层验证）</li>
 * <li>只有教师角色才能执行此操作</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "批量删除请求参数")
@Data
public class RecordBatchDeleteRequest {

    /**
     * 开始时间
     * 
     * 删除时间范围的起始时间点（包含）。
     * 必须填写且不能为null。
     */
    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间", example = "2026-01-01T00:00:00")
    private LocalDateTime startTime;

    /**
     * 结束时间
     * 
     * 删除时间范围的结束时间点（包含）。
     * 必须填写且不能为null。
     * 必须晚于开始时间。
     */
    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间", example = "2026-01-31T23:59:59")
    private LocalDateTime endTime;
}