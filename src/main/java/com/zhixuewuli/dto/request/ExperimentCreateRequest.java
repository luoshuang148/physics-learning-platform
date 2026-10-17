package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 实验创建请求 DTO
 * 
 * <p>
 * 封装创建新实验时所需的各项参数信息。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "实验创建请求参数")
@Data
public class ExperimentCreateRequest {
    
    /**
     * 实验名称（全局唯一）
     */
    @NotBlank(message = "实验名称不能为空")
    @Schema(description = "实验名称（全局唯一）", example = "牛顿第一定律实验")
    private String name;
    
    /**
     * 题目ID列表的 JSON 字符串
     */
    @NotBlank(message = "题目ID列表不能为空")
    @Schema(description = "题目ID列表的 JSON 字符串", example = "[1,2,3,4,5]")
    private String questionIds;
    
    /**
     * 实验描述
     */
    @NotBlank(message = "实验描述不能为空")
    @Schema(description = "实验描述", example = "探究牛顿第一定律的实验配置")
    private String description;
    
    /**
     * 排序序号（非负整数，全局唯一）
     */
    @NotNull(message = "排序序号不能为空")
    @Min(value = 1, message = "排序序号必须为正整数")
    @Schema(description = "排序序号（正整数，全局唯一）", example = "1")
    private Integer orderNumber;
}
