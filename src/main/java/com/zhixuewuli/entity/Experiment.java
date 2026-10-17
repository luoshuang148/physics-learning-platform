package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 实验配置实体类
 * 
 * <p>
 * 对应数据库表：experiments，用于存储和管理物理实验的配置信息。
 * 每个实验包含有序的题目序列，支持按步骤引导学生完成实验探究。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>组织和管理物理实验教学流程</li>
 * <li>定义实验相关的题目序列和顺序</li>
 * <li>支持实验报告的自动生成</li>
 * <li>提供实验学习数据的统计分析</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>实验名称全局唯一，避免重复配置</li>
 * <li>题目 ID 列表有序存储，确保实验步骤顺序</li>
 * <li>排序序号全局唯一，支持自定义展示顺序</li>
 * <li>JSON 格式存储题目 ID 列表，灵活扩展</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Getter
@Setter
@Entity
@Table(name = "experiments", indexes = {
        @Index(name = "uk_name", columnList = "name", unique = true),
        @Index(name = "uk_order_number", columnList = "order_number", unique = true)
})
@Schema(description = "实验配置实体")
public class Experiment extends BaseTimeEntity {
    
    /**
     * 实验 ID - 主键，自增长
     * 
     * <p>
     * 数据库自动生成的唯一标识符，用于标识每个实验配置。
     * </p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "实验 ID", example = "1")
    private Long id;
    
    /**
     * 实验名称 - 全局唯一标识
     * 
     * <p>
     * 实验的唯一名称标识，用于区分不同的实验配置。
     * 示例："牛顿第一定律探究实验"、"自由落体运动分析"
     * </p>
     * 
     * <p>
     * 约束条件：
     * </p>
     * <ul>
     * <li>非空校验：必须填写实验名称</li>
     * <li>长度限制：最多 255 个字符</li>
     * <li>全局唯一：不允许重名实验</li>
     * </ul>
     */
    @NotBlank(message = "实验名称不能为空")
    @Size(max = 255, message = "实验名称长度不能超过 255 个字符")
    @Column(unique = true, nullable = false, length = 255)
    @Schema(description = "实验名称", example = "牛顿第一定律探究实验")
    private String name;
    
    /**
     * 有序题目 ID 列表 - 实验步骤序列
     * 
     * <p>
     * 存储实验相关的题目 ID 序列，按照实验步骤的顺序排列。
     * </p>
     * 
     * <p>
     * 数据格式：JSON 数组字符串
     * </p>
     * <p>
     * 示例：[1, 5, 8, 12] 表示该实验包含 4 道题目，题号依次为 1、5、8、12
     * </p>
     * 
     * <p>
     * 业务用途：
     * </p>
     * <ul>
     * <li>定义实验的学习路径和步骤顺序</li>
     * <li>学生按顺序完成实验题目</li>
     * <li>教师查看实验完成进度</li>
     * </ul>
     */
    @Column(columnDefinition = "LONGTEXT", name = "question_ids")
    @Schema(description = "题目 ID 列表 (JSON 数组)", example = "[1,2,3]")
    private String questionIds;
    
    /**
     * 实验描述 - 详细说明
     * 
     * <p>
     * 对实验内容、目标、注意事项等的详细描述。
     * 支持长文本，使用 TEXT 类型存储。
     * </p>
     * 
     * <p>
     * 示例："本实验通过模拟小车在不同阻力下的运动，探究牛顿第一定律..."
     * </p>
     */
    @Column(columnDefinition = "TEXT")
    @Schema(description = "实验描述", example = "通过模拟实验探究力与运动的关系")
    private String description;
    
    /**
     * 排序序号 - 全局唯一正整数
     * 
     * <p>
     * 用于控制实验在前端展示的先后顺序。
     * 数值越小，展示越靠前。
     * </p>
     * 
     * <p>
     * 约束条件：
     * </p>
     * <ul>
     * <li>非空校验：必须填写排序序号</li>
     * <li>最小值：必须为正整数（≥1）</li>
     * <li>全局唯一：不同实验的排序序号不能重复</li>
     * <li>默认值：1（新建实验默认值为 1）</li>
     * </ul>
     */
    @NotNull(message = "排序序号不能为空")
    @Min(value = 1, message = "排序序号必须为正整数")
    @Column(nullable = false, unique = true, name = "order_number")
    @Schema(description = "排序序号", example = "1")
    private Integer orderNumber = 1;
}
