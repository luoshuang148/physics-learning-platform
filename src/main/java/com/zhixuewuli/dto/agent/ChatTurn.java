package com.zhixuewuli.dto.agent;

import java.util.List;

public record ChatTurn(Role role, String content, String toolCallId, String toolName, List<ToolCall> toolCalls) {
    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }
    public ChatTurn { toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls); }
}
