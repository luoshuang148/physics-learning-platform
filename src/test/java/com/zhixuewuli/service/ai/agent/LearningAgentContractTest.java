package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.AgentProperties;
import com.zhixuewuli.common.config.properties.LearningAssistantProperties;
import com.zhixuewuli.dto.agent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class LearningAgentContractTest {
    @Test void agentStopsAfterConfiguredModelRoundBudget() {
        ToolChatGateway gateway = mock(ToolChatGateway.class);
        LearningToolRegistry tools = mock(LearningToolRegistry.class);
        AgentProperties agent = new AgentProperties();
        agent.setToolCallingEnabled(true); agent.setBaseUrl("https://example.test/v1"); agent.setModel("test-model");
        LearningAssistantProperties limits = new LearningAssistantProperties();
        limits.setMaxModelRounds(1);
        LearningAgentService service = new LearningAgentService(gateway, tools,
                new LearningPlanValidator(new ObjectMapper()), agent, limits, new ObjectMapper(),
                new PlanCandidateRegistry());
        when(tools.execute(any(), argThat(toolCall -> toolCall != null && "get_learning_summary".equals(toolCall.name())), anyLong()))
                .thenReturn(new ToolResult("get_learning_summary", true,
                        "{\"assignmentAttempts\":3,\"correctAttempts\":1,\"wrongQuestionCount\":2,\"sourceAnswerRevision\":\"rev\"}"));
        when(tools.definitions()).thenReturn(List.of());
        when(gateway.complete(anyList(), anyList(), any(), any())).thenReturn(new ToolChatResponse(null,
                List.of(new ToolCall("call1", "search_knowledge", "{}")), "tool_calls", null, null,
                ToolChatResponse.UsageSource.UNKNOWN));
        AtomicInteger cancels = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> service.preview(new LearningToolContext(4L, 9L),
                "帮我制定复习计划", 2, () -> false, action -> cancels.incrementAndGet()));
        verify(gateway, times(1)).complete(anyList(), anyList(), any(), any());
        verify(tools).execute(any(), argThat(call -> call != null && "get_learning_summary".equals(call.name())), anyLong());
        verify(tools).execute(any(), argThat(call -> call != null && "search_knowledge".equals(call.name())), anyLong());
    }

    @Test void duplicateToolCallStopsWithoutReexecutingIt() {
        ToolChatGateway gateway = mock(ToolChatGateway.class);
        LearningToolRegistry tools = mock(LearningToolRegistry.class);
        AgentProperties agent = new AgentProperties();
        agent.setToolCallingEnabled(true); agent.setBaseUrl("https://example.test/v1"); agent.setModel("test-model");
        LearningAssistantProperties limits = new LearningAssistantProperties(); limits.setMaxModelRounds(3);
        LearningAgentService service = new LearningAgentService(gateway, tools,
                new LearningPlanValidator(new ObjectMapper()), agent, limits, new ObjectMapper(),
                new PlanCandidateRegistry());
        when(tools.execute(any(), argThat(toolCall -> toolCall != null && "get_learning_summary".equals(toolCall.name())), anyLong()))
                .thenReturn(new ToolResult("get_learning_summary", true,
                        "{\"assignmentAttempts\":3,\"correctAttempts\":1,\"wrongQuestionCount\":2,\"sourceAnswerRevision\":\"rev\"}"));
        when(tools.definitions()).thenReturn(List.of());
        ToolCall call = new ToolCall("call1", "search_knowledge", "{\"eventId\":9,\"query\":\"牛顿定律\"}");
        ToolChatResponse response = new ToolChatResponse(null, List.of(call), "tool_calls", null, null,
                ToolChatResponse.UsageSource.UNKNOWN);
        when(gateway.complete(anyList(), anyList(), any(), any())).thenReturn(response);
        when(tools.execute(any(), argThat(toolCall -> toolCall != null && "search_knowledge".equals(toolCall.name())), anyLong()))
                .thenReturn(new ToolResult("search_knowledge", true, "{\"passages\":[]}"));
        assertThrows(IllegalStateException.class, () -> service.preview(new LearningToolContext(4L, 9L),
                "帮我制定复习计划", 2, () -> false, ignored -> {}));
        verify(tools, times(1)).execute(any(), argThat(toolCall -> toolCall != null && "search_knowledge".equals(toolCall.name())), anyLong());
    }
}
