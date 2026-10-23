package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.LearningToolContext;
import com.zhixuewuli.dto.agent.ToolCall;
import com.zhixuewuli.dto.agent.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class LearningWorkflowContractTest {
    private final LearningToolRegistry tools = mock(LearningToolRegistry.class);
    private final LearningWorkflowService workflow = new LearningWorkflowService(new ObjectMapper(), tools,
            new LearningPlanValidator(new ObjectMapper()));

    @Test void knowledgeOnlyRequestSkipsPersonalStatisticsAndWrongQuestions() {
        when(tools.execute(any(), any(), anyLong())).thenReturn(new ToolResult("search_knowledge", true, "{\"passages\":[]}"));
        var plan = workflow.preview(new LearningToolContext(4L, 9L), "解释牛顿第二定律", 3,
                System.currentTimeMillis() + 10_000, () -> false);
        assertEquals("WORKFLOW", plan.executionMode());
        assertTrue(plan.items().isEmpty());
        verify(tools, times(1)).execute(any(), argThat(call -> call != null && "search_knowledge".equals(call.name())), anyLong());
        verify(tools, never()).execute(any(), argThat(call -> call != null && "get_learning_summary".equals(call.name())), anyLong());
        verify(tools, never()).execute(any(), argThat(call -> call != null && "get_wrong_questions".equals(call.name())), anyLong());
        verify(tools, never()).execute(any(), argThat(call -> call != null && "recommend_practice".equals(call.name())), anyLong());
    }

    @Test void noWrongQuestionTagsMeansNoInventedPracticeRecommendation() {
        when(tools.execute(any(), argThat(call -> call != null && "get_learning_summary".equals(call.name())), anyLong()))
                .thenReturn(new ToolResult("get_learning_summary", true,
                        "{\"assignmentAttempts\":4,\"correctAttempts\":1,\"wrongQuestionCount\":2,\"sourceAnswerRevision\":\"r1\"}"));
        when(tools.execute(any(), argThat(call -> call != null && "get_wrong_questions".equals(call.name())), anyLong()))
                .thenReturn(new ToolResult("get_wrong_questions", true, "[]"));
        when(tools.execute(any(), argThat(call -> call != null && "search_knowledge".equals(call.name())), anyLong()))
                .thenReturn(new ToolResult("search_knowledge", true, "{\"passages\":[]}"));
        var plan = workflow.preview(new LearningToolContext(4L, 9L), "帮我制定复习计划", 2,
                System.currentTimeMillis() + 10_000, () -> false);
        assertTrue(plan.items().isEmpty());
        assertTrue(plan.evidenceSummary().contains("PERSONAL_SUMMARY"));
        verify(tools, never()).execute(any(), argThat(call -> call != null && "recommend_practice".equals(call.name())), anyLong());
    }

    @Test void cancellationAfterWrongQuestionReadStopsBeforeKnowledgeTool() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        when(tools.execute(any(), argThat(call -> call != null && "get_learning_summary".equals(call.name())), anyLong()))
                .thenReturn(new ToolResult("get_learning_summary", true,
                        "{\"assignmentAttempts\":4,\"correctAttempts\":1,\"wrongQuestionCount\":2,\"sourceAnswerRevision\":\"r1\"}"));
        when(tools.execute(any(), argThat(call -> call != null && "get_wrong_questions".equals(call.name())), anyLong()))
                .thenAnswer(invocation -> { cancelled.set(true); return new ToolResult("get_wrong_questions", true, "[]"); });
        assertThrows(java.util.concurrent.CancellationException.class, () -> workflow.preview(new LearningToolContext(4L, 9L),
                "帮我制定复习计划", 2, System.currentTimeMillis() + 10_000, cancelled::get));
        verify(tools, never()).execute(any(), argThat(call -> call != null && "search_knowledge".equals(call.name())), anyLong());
    }
}
