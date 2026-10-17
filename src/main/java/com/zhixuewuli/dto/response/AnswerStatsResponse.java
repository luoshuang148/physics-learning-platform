package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 答题统计响应DTO
 * 
 * 用于返回答题统计数据，包括有答题记录的学生人数和系统中学生总人数。
 * 主要用于教师端展示答题覆盖率等统计数据。
 * 
 * <p>
 * 包含信息：
 * </p>
 * <ul>
 * <li>有答题记录的学生人数（去重）</li>
 * <li>系统中学生总人数</li>
 * <li>答题覆盖率百分比</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "答题统计响应")
@Data
public class AnswerStatsResponse {

    /**
     * 有答题记录的学生人数（去重）
     * 
     * 指系统中至少完成过一次答题的学生数量。
     */
    @Schema(description = "有答题记录的学生人数（去重）", example = "45")
    private Long answeredStudentCount;

    /**
     * 学生总人数
     * 
     * 指系统中所有学生的总数（包括已激活和未激活）。
     */
    @Schema(description = "学生总人数", example = "50")
    private Long totalStudentCount;

    /**
     * 答题覆盖率百分比
     * 
     * 有答题记录的学生人数占总学生人数的百分比，保留一位小数。
     */
    @Schema(description = "答题覆盖率百分比", example = "90.0")
    private Double coverageRate;
}
