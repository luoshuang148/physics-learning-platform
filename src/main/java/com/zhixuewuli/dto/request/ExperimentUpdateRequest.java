package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

/**
 * 实验更新请求 DTO
 * 
 * <p>
 * 封装更新实验时所需的各项参数信息，所有字段（除 id 外）均为可选，实现增量覆盖更新。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "实验更新请求参数")
@Data
public class ExperimentUpdateRequest {
    
    /**
     * 实验ID（唯一必填字段）
     */
    @NotNull(message = "实验ID 不能为空")
    @Positive(message = "实验ID 必须为正整数")
    @Schema(description = "实验ID（唯一必填字段）", example = "1")
    private Long id;
    
    /**
     * 实验名称（可选）
     */
    @Size(max = 255, message = "实验名称长度不能超过 255 个字符")
    @Schema(description = "实验名称（可选）", example = "牛顿第一定律实验")
    private String name;
    
    /**
     * 题目ID列表的 JSON 字符串（可选）
     */
    @Schema(description = "题目ID列表的 JSON 字符串（可选）", example = "[1,2,3,4,5]")
    private String questionIds;
    
    /**
     * 实验描述（可选）
     */
    @Schema(description = "实验描述（可选）", example = "探究牛顿第一定律的实验配置")
    private String description;
    
    /**
     * 排序序号（可选，非负整数，全局唯一）
     */
    @Min(value = 1, message = "排序序号必须为正整数")
    @Schema(description = "排序序号（可选，正整数，全局唯一）", example = "1")
    private Integer orderNumber;
}
