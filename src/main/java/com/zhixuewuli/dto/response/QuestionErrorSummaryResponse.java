package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 题目错误统计响应DTO
 * 
 * 遵循字段展平设计原则，与Question实体保持命名一致性。
 * 用于返回题目维度的错题统计信息，包括题目基本信息和错误统计数据。
 * 
 * <p>
 * 数据结构：
 * </p>
 * <ul>
 * <li>题目基本信息（ID、内容、选项等）</li>
 * <li>错误统计信息（错误人次、错误率）</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>教师端错题分析首页展示</li>
 * <li>题目错误排行榜显示</li>
 * <li>错题统计报表生成</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.entity.Question 题目实体类
 */
@Schema(description = "题目错误统计响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionErrorSummaryResponse {

    // 题目基本信息（与Question实体字段名保持一致）

    @Schema(description = "题目ID")
    private Long questionId;

    @Schema(description = "题目内容")
    private String content;

    @Schema(description = "题目选项键值对映射")
    private Map<String, String> options;

    @Schema(description = "正确答案")
    private String correctOption;

    @Schema(description = "题目解析")
    private String explanation;

    @Schema(description = "题目难度")
    private String difficulty;

    @Schema(description = "题目图片")
    private String imageBase64;

    // 错误统计信息

    @Schema(description = "错误人次")
    private Long errorCount;

    @Schema(description = "正确人次")
    private Long correctCount;

    @Schema(description = "错误率")
    private Double errorRate;
}