package com.zhixuewuli.service.ai.agent;

import com.zhixuewuli.dto.agent.ChatTurn;
import com.zhixuewuli.dto.agent.ToolCallCancellation;
import com.zhixuewuli.dto.agent.ToolChatResponse;
import com.zhixuewuli.dto.agent.ToolDefinition;
import com.zhixuewuli.dto.agent.ToolModelConfig;

import java.util.List;

public interface ToolChatGateway {
    ToolChatResponse complete(List<ChatTurn> turns, List<ToolDefinition> definitions,
                              ToolModelConfig modelConfig, ToolCallCancellation cancellation);
}
