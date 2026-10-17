package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生报告响应DTO
 * 
 * 用于返回学生最新的学习报告信息，包括报告内容和学生基本信息。
 * 主要用于教师查看特定学生的最新学习情况。
 * 
 * <p>
 * 包含信息：
 * </p>
 * <ul>
 * <li>学生真实姓名</li>
 * <li>最新报告内容</li>
 * <li>报告创建时间</li>
 * <li>会话ID（如果需要）</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "学生报告响应")
@Data
public class StudentReportResponse {

    /**
     * 学生真实姓名
     * 
     * 学生的真实姓名，用于标识和展示。
     */
    @Schema(description = "学生真实姓名", example = "张三")
    private String studentRealName;

    /**
     * 最新报告内容
     * 
     * AI生成的完整学习报告文本内容。
     */
    @Schema(description = "最新报告内容", example = "本次学习表现总体评估...")
    private String latestReportContent;

    /**
     * 报告创建时间
     * 
     * 最新报告的生成时间。
     */
    @Schema(description = "报告创建时间", example = "2026-01-15T14:30:00")
    private LocalDateTime reportCreateTime;
}