package com.zhixuewuli.dto.agent;

public record LearningToolContext(Long viewerId, Long eventId) {
    public LearningToolContext {
        if (viewerId == null || eventId == null) throw new IllegalArgumentException("学习工具上下文缺少学生或任务身份");
    }
}
