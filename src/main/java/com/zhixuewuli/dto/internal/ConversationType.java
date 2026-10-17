package com.zhixuewuli.dto.internal;

/**
 * 会话类型枚举
 * 
 * 用于区分不同类型的AI对话会话，实现不同功能的上下文隔离。
 * 
 * <p>
 * 核心设计理念：
 * </p>
 * <ul>
 * <li>实时问答和错题分析功能独立维护上下文</li>
 * <li>相同题目ID的错题分析共享会话历史</li>
 * <li>不同类型会话的token消耗独立统计</li>
 * <li>支持会话类型的扩展性</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>实时问答：通用物理学习问题咨询</li>
 * <li>错题分析：针对特定题目的深度分析</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public enum ConversationType {

    /**
     * 实时问答
     * 用于通用物理学习问题咨询
     */
    REAL_TIME_QA("实时问答"),

    /**
     * 错题分析
     * 用于特定题目的深度分析和讲解
     */
    WRONG_QUESTION_ANALYSIS("错题分析"),

    /**
     * 教师题目分析
     */
    TEACHER_QUESTION_ANALYSIS("教师题目分析");

    private final String description;

    ConversationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据字符串获取会话类型
     * 
     * @param type 类型字符串（不区分大小写）
     * @return 对应的ConversationType枚举，默认为REAL_TIME_QA
     */
    public static ConversationType fromString(String type) {
        if (type == null) {
            return REAL_TIME_QA;
        }

        try {
            return ConversationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return REAL_TIME_QA;
        }
    }
}