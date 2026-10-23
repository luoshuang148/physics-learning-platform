package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.LearningToolContext;
import com.zhixuewuli.dto.agent.ToolCall;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.*;
import com.zhixuewuli.service.platformsearch.PlatformQuestionRetriever;
import com.zhixuewuli.service.platformsearch.PlatformSourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 新工具与候选注册表：并集去重、来源授权、伪造题号拒绝。
 */
@Tag("unit")
@DisplayName("平台工具与计划候选")
class PlanCandidateRegistryTest {

    @Test
    @DisplayName("多次检索候选取并集并按 questionId+revision 去重")
    void unionAndDedupe() {
        PlanCandidateRegistry registry = new PlanCandidateRegistry();
        registry.add(new SearchResult(RetrievalStatus.MATCHED, "hybrid",
                List.of(new QuestionHit(1L, 1L, "a", 1, List.of(), List.of("Q:1:1:QUESTION:0"))),
                List.of(new Evidence("Q:1:1:QUESTION:0", 1L, 1L, "QUESTION", 0, "t")), 1));
        registry.add(new SearchResult(RetrievalStatus.MATCHED, "hybrid",
                List.of(new QuestionHit(1L, 1L, "a", 1, List.of(), List.of()),
                        new QuestionHit(2L, 3L, "b", 2, List.of(), List.of("Q:2:3:EXPLANATION:0"))),
                List.of(new Evidence("Q:2:3:EXPLANATION:0", 2L, 3L, "EXPLANATION", 0, "t2")), 1));
        assertTrue(registry.contains(1L, 1L));
        assertTrue(registry.contains(2L, 3L));
        assertEquals(2, registry.candidateList().size());
        assertTrue(registry.containsSource("Q:1:1:QUESTION:0"));
        assertTrue(registry.containsSource("Q:2:3:EXPLANATION:0"));
    }

    @Test
    @DisplayName("search_platform_questions 写入 registry；不在 registry 的来源读取被拒绝")
    void toolsUpdateAndGuardRegistry() {
        var retriever = mock(PlatformQuestionRetriever.class);
        var sources = mock(PlatformSourceService.class);
        var registry = new PlanCandidateRegistry();
        var tools = new LearningToolRegistry(new ObjectMapper(),
                mock(LearningAssistantDataRepository.class),
                mock(com.zhixuewuli.service.knowledge.KnowledgeRetriever.class),
                mock(com.zhixuewuli.service.knowledge.PracticeRecommendationService.class),
                mock(com.zhixuewuli.service.teacherEvent.TeacherEventService.class),
                retriever, sources, registry);

        when(retriever.retrieve(anyLong(), any(), any())).thenReturn(new SearchResult(
                RetrievalStatus.MATCHED, "hybrid",
                List.of(new QuestionHit(1L, 1L, "题干", 2, List.of(), List.of("Q:1:1:QUESTION:0"))),
                List.of(new Evidence("Q:1:1:QUESTION:0", 1L, 1L, "QUESTION", 0, "原文")), 3L));

        var context = new LearningToolContext(44L, 900L);
        tools.execute(context, new ToolCall("c1", "search_platform_questions",
                "{\"query\":\"作用力与反作用力\"}"), Long.MAX_VALUE);
        assertTrue(registry.contains(1L, 1L));

        // get_question_evidence 只回传可读集合；伪造 sourceIds 不会被写入 registry
        when(sources.readableAll(eq(44L), anyList())).thenReturn(List.of(
                new Evidence("Q:1:1:QUESTION:0", 1L, 1L, "QUESTION", 0, "原文")));
        tools.execute(context, new ToolCall("c2", "get_question_evidence",
                "{\"sourceIds\":[\"Q:1:1:QUESTION:0\",\"Q:99:1:QUESTION:0\"]}"), Long.MAX_VALUE);
        assertTrue(registry.containsSource("Q:1:1:QUESTION:0"));
        assertFalse(registry.containsSource("Q:99:1:QUESTION:0"), "不可读来源不得进入 registry");
    }

    @Test
    @DisplayName("工具拒绝未注册名与越权字段")
    void rejectsUnknownToolAndFields() {
        var tools = new LearningToolRegistry(new ObjectMapper(),
                mock(LearningAssistantDataRepository.class),
                mock(com.zhixuewuli.service.knowledge.KnowledgeRetriever.class),
                mock(com.zhixuewuli.service.knowledge.PracticeRecommendationService.class),
                mock(com.zhixuewuli.service.teacherEvent.TeacherEventService.class),
                mock(PlatformQuestionRetriever.class),
                mock(PlatformSourceService.class),
                new PlanCandidateRegistry());
        var context = new LearningToolContext(44L, 900L);
        assertThrows(IllegalArgumentException.class, () -> tools.execute(context,
                new ToolCall("c1", "hack_tool", "{}"), Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> tools.execute(context,
                new ToolCall("c2", "search_platform_questions", "{\"query\":\"x\",\"userId\":1}"), Long.MAX_VALUE));
    }
}
