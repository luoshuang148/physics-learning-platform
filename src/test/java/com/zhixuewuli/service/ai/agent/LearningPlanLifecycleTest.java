package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.PersonalLearningSummary;
import com.zhixuewuli.service.knowledge.PracticeRecommendationService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class LearningPlanLifecycleTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LearningAssistantDataRepository data = mock(LearningAssistantDataRepository.class);
    private final TeacherEventService tasks = mock(TeacherEventService.class);
    private final LearningPlanService plans = new LearningPlanService(jdbc, new ObjectMapper(), data,
            mock(PracticeRecommendationService.class), tasks);

    @Test void duplicateSaveRequestReturnsTheExistingPlan() {
        when(jdbc.queryForObject(contains("request_id=?"), eq(Long.class), eq(3L), eq("11111111-1111-4111-8111-111111111111")))
                .thenReturn(21L);
        when(jdbc.queryForMap(contains("FROM learning_plans WHERE id=?"), eq(21L), eq(3L))).thenReturn(Map.of(
                "id", 21L, "event_id", 8L, "status", "DRAFT", "version", 1L, "days", 2,
                "source_answer_revision", "r1", "activated_at", "", "source_run_id", "22222222-2222-4222-8222-222222222222"));
        when(jdbc.query(contains("FROM learning_plan_items"), any(RowMapper.class), eq(21L))).thenReturn(List.of());
        when(jdbc.queryForObject("SELECT plan_json FROM learning_plan_previews WHERE run_id=? AND user_id=?", String.class,
                "22222222-2222-4222-8222-222222222222", 3L)).thenReturn("{\"executionMode\":\"WORKFLOW\",\"evidenceSummary\":[]}");
        var result = plans.saveDraft(3L, "22222222-2222-4222-8222-222222222222", "11111111-1111-4111-8111-111111111111");
        assertEquals(21L, result.id());
        assertEquals("DRAFT", result.status());
        verify(jdbc, never()).update(contains("INSERT INTO learning_plans"), any(Object[].class));
    }

    @Test void changedSourceRevisionRequiresNewPreview() {
        when(jdbc.queryForObject(contains("request_id=?"), eq(Long.class), eq(3L), eq("11111111-1111-4111-8111-111111111111")))
                .thenThrow(new EmptyResultDataAccessException(1));
        when(jdbc.queryForMap(contains("FROM learning_plan_previews"), eq("22222222-2222-4222-8222-222222222222"), eq(3L)))
                .thenReturn(Map.of("event_id", 8L, "source_answer_revision", "old", "plan_json", "{}", "status", "SUCCEEDED"));
        when(data.summary(3L, 8L)).thenReturn(new PersonalLearningSummary(2, 1, 1, "new"));
        assertThrows(IllegalStateException.class, () -> plans.saveDraft(3L,
                "22222222-2222-4222-8222-222222222222", "11111111-1111-4111-8111-111111111111"));
        verifyNoInteractions(tasks);
        verify(jdbc, never()).update(contains("INSERT INTO learning_plans"), any(Object[].class));
    }

    @Test void anotherStudentCannotReadPlan() {
        when(jdbc.queryForMap(contains("FROM learning_plans WHERE id=?"), eq(21L), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThrows(EmptyResultDataAccessException.class, () -> plans.get(99L, 21L));
    }

    @Test void progressCountsOnlyPostActivationPracticeWithoutWritingOriginalScores() {
        when(jdbc.queryForMap(contains("FROM learning_plans WHERE id=?"), eq(21L), eq(3L))).thenReturn(Map.of(
                "id", 21L, "event_id", 8L, "status", "ACTIVE", "version", 2L, "days", 2,
                "source_answer_revision", "r1", "activated_at", java.sql.Timestamp.valueOf("2026-10-23 10:00:00"), "source_run_id", "22222222-2222-4222-8222-222222222222"));
        when(jdbc.query(contains("FROM learning_plan_items"), any(RowMapper.class), eq(21L)))
                .thenReturn(List.of(new com.zhixuewuli.dto.agent.LearningPlanItemPreview(1, "mechanics.newton_2", 31L, List.of("S1"))));
        when(jdbc.queryForObject("SELECT plan_json FROM learning_plan_previews WHERE run_id=? AND user_id=?", String.class,
                "22222222-2222-4222-8222-222222222222", 3L)).thenReturn("{\"executionMode\":\"AGENT\",\"evidenceSummary\":[\"S1\"]}");
        when(jdbc.queryForMap(contains("SELECT COUNT(*) AS attempts"), any(Object[].class)))
                .thenReturn(Map.of("attempts", 2L, "correct_count", 1L));
        var view = plans.get(3L, 21L);
        assertEquals(2, view.practiceAttempts()); assertEquals(1, view.practiceCorrect());
        assertEquals("IN_PROGRESS", view.progressStatus());
        verify(jdbc).queryForMap(argThat(sql -> sql.contains("practice=1") && sql.contains("answered_at>=?")), any(Object[].class));
        verify(jdbc, never()).update(contains("UPDATE answer_records"), any(Object[].class));
    }

    @Test void repeatedActivationReturnsTheAlreadyActivePlan() {
        when(jdbc.queryForMap(contains("FROM learning_plans WHERE id=?"), eq(21L), eq(3L))).thenReturn(Map.of(
                "id", 21L, "event_id", 8L, "status", "ACTIVE", "version", 2L, "days", 2,
                "source_answer_revision", "r1", "activated_at", java.sql.Timestamp.valueOf("2026-10-23 10:00:00"), "source_run_id", "22222222-2222-4222-8222-222222222222"));
        when(jdbc.query(contains("FROM learning_plan_items"), any(RowMapper.class), eq(21L))).thenReturn(List.of());
        when(jdbc.queryForObject("SELECT plan_json FROM learning_plan_previews WHERE run_id=? AND user_id=?", String.class,
                "22222222-2222-4222-8222-222222222222", 3L)).thenReturn("{\"executionMode\":\"WORKFLOW\",\"evidenceSummary\":[]}");
        var result = plans.activate(3L, 21L, 1);
        assertEquals("ACTIVE", result.status()); assertEquals(2L, result.version());
        verifyNoInteractions(data, tasks);
        verify(jdbc, never()).update(contains("UPDATE learning_plans"), any(Object[].class));
    }
}
