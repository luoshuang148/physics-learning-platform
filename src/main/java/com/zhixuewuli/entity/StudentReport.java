package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生报告实体类
 * 
 * 存储AI生成的学生个人学习报告，包含学生基本信息和完整的报告内容。
 * 通过继承BaseTimeEntity自动管理创建和更新时间。
 */
@Entity
@Table(name = "student_reports", indexes = {
        @Index(name = "idx_student_report_session_id", columnList = "session_id"),
        @Index(name = "idx_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_user_session", columnList = "user_id, session_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class StudentReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "报告 ID", example = "1")
    private Long id;
    
    /**
     * 学生用户 ID - 外键引用
     * 
     * <p>
     * 指向 users 表的外键，标识该报告所属的学生用户。
     * 非空约束，确保每份报告都有明确的所有者。
     * </p>
     */
    @Column(name = "user_id", nullable = false)
    @Schema(description = "学生用户 ID", example = "1001")
    private Long userId;
    
    /**
     * 关联的学生用户对象 - 多对一懒加载
     * 
     * <p>
     * 用于 ORM 关联查询，可以直接获取学生的完整信息。
     * insertable 和 updatable 设为 false，避免与 userId 字段冲突。
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User student;
        
    /**
     * 学生真实姓名 - 冗余存储
     * 
     * <p>
     * 从 User 表同步的学生姓名，避免频繁关联查询。
     * 便于快速展示和统计，提高报告列表的查询性能。
     * </p>
     * 
     * <p>
     * 业务价值：在报告列表展示时无需 JOIN users 表，减少数据库压力。
     * </p>
     */
    @Column(name = "real_name", nullable = false, length = 100)
    @Schema(description = "学生姓名", example = "张三")
    private String realName;
    
    /**
     * 会话 ID - 学习场景标识
     * 
     * <p>
     * 标识该报告对应的具体学习会话或教学活动。
     * 同一学生在不同会话中会生成不同的学习报告。
     * </p>
     * 
     * <p>
     * 示例值："session_20261115_001", "midterm_exam_2026"
     * </p>
     * 
     * <p>
     * 业务用途：
     * </p>
     * <ul>
     * <li>区分不同时间节点的学习报告</li>
     * <li>支持按会话维度统计分析</li>
     * <li>便于追踪学生学习进度变化</li>
     * </ul>
     */
    @Column(name = "session_id", nullable = false, length = 100)
    @Schema(description = "会话 ID", example = "session_20261115_001")
    private String sessionId;
    
    /**
     * 报告内容 - AI 生成的完整分析文本
     * 
     * <p>
     * AI 系统根据学生答题情况生成的详细学习分析报告。
     * 包含知识点掌握度、错题分析、学习建议等内容。
     * </p>
     * 
     * <p>
     * 内容结构示例：
     * </p>
     * <pre>
     * 【学习概况】
     * 本次学习共完成 15 道题目，正确率 80%...
     * 
     * 【知识点分析】
     * 1. 牛顿第一定律：掌握良好 (正确率 90%)
     * 2. 力的合成：需要加强 (正确率 60%)...
     * 
     * 【学习建议】
     * 建议重点复习力的分解相关概念...
     * </pre>
     * 
     * <p>
     * 存储类型：TEXT，支持长文本内容。
     * </p>
     */
    @Column(name = "report_content", nullable = false, columnDefinition = "TEXT")
    @Schema(description = "报告内容 (AI 生成)", example = "【学习概况】本次学习共完成 15 道题目...")
    private String reportContent;
}