package com.zhixuewuli.dto.agent;

public record LearningAssistantStep(int step, String toolName, String status, String summary, long durationMs) {
    public LearningAssistantStep(int step, String toolName, String status, String summary) { this(step, toolName, status, summary, 0); }
}
