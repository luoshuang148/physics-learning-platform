package com.zhixuewuli.dto.agent;

import java.util.List;

public record ToolChatResponse(String content, List<ToolCall> toolCalls, String finishReason,
                               Integer inputTokens, Integer outputTokens, UsageSource usageSource) {
    public enum UsageSource { REPORTED, UNKNOWN }
    public ToolChatResponse { toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls); }
}
