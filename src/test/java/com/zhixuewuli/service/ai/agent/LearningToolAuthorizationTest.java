package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.*;
import com.zhixuewuli.dto.agent.LearningToolContext;
import com.zhixuewuli.dto.agent.PersonalLearningSummary;
import com.zhixuewuli.dto.agent.ToolCall;
import com.zhixuewuli.service.knowledge.KnowledgeRetriever;
import com.zhixuewuli.service.knowledge.PracticeRecommendationService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class LearningToolAuthorizationTest {
    private final LearningAssistantDataRepository data = mock(LearningAssistantDataRepository.class);
    private final TeacherEventService tasks = mock(TeacherEventService.class);
    private final LearningToolRegistry tools = new LearningToolRegistry(new ObjectMapper(), data,
            mock(KnowledgeRetriever.class), mock(PracticeRecommendationService.class), tasks);

    @Test void modelCannotOverrideAuthenticatedViewer() {
        var context = new LearningToolContext(44L, 900L);
        assertThrows(IllegalArgumentException.class, () -> tools.execute(context,
                new ToolCall("c1", "get_learning_summary", "{\"eventId\":900,\"userId\":1}"), Long.MAX_VALUE));
        verifyNoInteractions(tasks, data);
    }

    @Test void personalSummaryAlwaysUsesServerSuppliedViewerAndEvent() {
        var context = new LearningToolContext(44L, 900L);
        when(data.summary(44L, 900L)).thenReturn(new PersonalLearningSummary(4, 2, 1, "personal-rev"));
        var result = tools.execute(context, new ToolCall("c1", "get_learning_summary", "{\"eventId\":900}"), Long.MAX_VALUE);
        assertTrue(result.success());
        verify(data).summary(44L, 900L);
        verify(tasks).getEventQuestions(900L, 44L);
        verify(data, never()).summary(1L, 900L);
    }

    @Test void modelCannotSwitchToAnotherAssignment() {
        assertThrows(IllegalArgumentException.class, () -> tools.execute(new LearningToolContext(44L, 900L),
                new ToolCall("c1", "get_wrong_questions", "{\"eventId\":901}"), Long.MAX_VALUE));
        verifyNoInteractions(tasks, data);
    }
}
