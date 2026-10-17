package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实验响应 DTO
 * 
 * <p>
 * 用于向前端返回实验的完整信息。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "实验响应数据")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentResponse {
    
    /**
     * 实验ID
     */
    @Schema(description = "实验ID", example = "1")
    private Long id;
    
    /**
     * 实验名称
     */
    @Schema(description = "实验名称", example = "牛顿第一定律实验")
    private String name;
    
    /**
     * 题目ID列表（List<Long>类型，便于前端使用）
     */
    @Schema(description = "题目ID列表")
    private List<Long> questionIds;
    
    /**
     * 原始的 JSON 字符串（备用）
     */
    @Schema(description = "题目ID列表的 JSON 字符串", example = "[1,2,3,4,5]")
    private String questionIdsJson;
    
    /**
     * 实验描述
     */
    @Schema(description = "实验描述", example = "探究牛顿第一定律的实验配置")
    private String description;
    
    /**
     * 排序序号
     */
    @Schema(description = "排序序号", example = "1")
    private Integer orderNumber;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-01-15T10:30:00")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2026-01-15T15:45:00")
    private LocalDateTime updatedAt;
}
