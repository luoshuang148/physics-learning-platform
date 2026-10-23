package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.request.PlanPracticeAnswerRequest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 计划条目补练：归属、幂等、revision 变化拒绝。
 */
@Tag("unit")
@DisplayName("PlanPracticeService 契约")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanPracticeServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock QuestionRepository questions;
    @Mock AnswerRecordRepository answers;
    @Mock AnswerAccessPolicy answerAccess;

    PlanPracticeService service;

    @BeforeEach
    void setUp() {
        service = new PlanPracticeService(jdbc, questions, answers, answerAccess, new ObjectMapper());
        when(answers.findByUserIdAndRequestId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(answerAccess.blocksPracticeBeforeAssignment(anyLong(), any(), anyLong())).thenReturn(false);
    }

    private Question question() {
        Question q = new Question();
        q.setId(10L);
        q.setContent("题干");
        q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        q.setCorrectOption("A");
        q.setAiPracticeEnabled(true);
        q.setAiContentRevision(3L);
        return q;
    }

    private PlanPracticeAnswerRequest request() {
        PlanPracticeAnswerRequest r = new PlanPracticeAnswerRequest();
        r.setUserAnswer("A");
        r.setRequestId("req-1");
        return r;
    }

    @Test
    @DisplayName("提交生成 practice 作答并关联 planItemId")
    void submitCreatesPracticeRecord() {
        when(jdbc.queryForMap(contains("learning_plan_items"), eq(5L), eq(1L), eq(100L))).thenReturn(Map.of(
                "id", 5L, "question_id", 10L, "question_revision", 3L,
                "status", "ACTIVE", "user_id", 100L));
        when(questions.findById(10L)).thenReturn(Optional.of(question()));
        when(jdbc.queryForMap(contains("learning_plan_attempt_links"), eq(5L), eq(100L))).thenReturn(Map.of(
                "attempts", 1L, "correctCount", 1L));
        when(answers.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.submit(100L, 1L, 5L, request());
        assertEquals(Boolean.TRUE, result.get("correct"));
        assertEquals("ON_TRACK", result.get("progressStatus"));
        verify(answers).save(argThat(r -> r.isPractice() && r.getEventId() == null
                && r.getQuestionId() == 10L));
        verify(jdbc).update(contains("learning_plan_attempt_links"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("同 requestId 重放返回原结果，不再写库")
    void replayReturnsOriginal() {
        AnswerRecord old = new AnswerRecord();
        old.setQuestionId(10L);
        old.setUserAnswer("A");
        old.setCorrect(true);
        when(answers.findByUserIdAndRequestId(100L, "req-1")).thenReturn(Optional.of(old));
        when(jdbc.queryForMap(contains("learning_plan_items"), eq(5L), eq(1L), eq(100L))).thenReturn(Map.of(
                "id", 5L, "question_id", 10L, "question_revision", 3L,
                "status", "ACTIVE", "user_id", 100L));
        when(jdbc.queryForMap(contains("learning_plan_attempt_links"), eq(5L), eq(100L))).thenReturn(Map.of(
                "attempts", 1L, "correctCount", 1L));

        service.submit(100L, 1L, 5L, request());
        verify(answers, never()).save(any());
    }

    @Test
    @DisplayName("题目 revision 变化后拒绝新提交")
    void revisionChangedRejects() {
        when(jdbc.queryForMap(contains("learning_plan_items"), eq(5L), eq(1L), eq(100L))).thenReturn(Map.of(
                "id", 5L, "question_id", 10L, "question_revision", 3L,
                "status", "ACTIVE", "user_id", 100L));
        Question updated = question();
        updated.setAiContentRevision(4L);
        when(questions.findById(10L)).thenReturn(Optional.of(updated));

        var error = assertThrows(com.zhixuewuli.common.exception.BusinessException.class,
                () -> service.submit(100L, 1L, 5L, request()));
        assertTrue(error.getMessage().contains("重新生成"));
        verify(answers, never()).save(any());
    }

    @Test
    @DisplayName("他人计划拒绝作答")
    void foreignPlanDenied() {
        when(jdbc.queryForMap(contains("learning_plan_items"), eq(5L), eq(1L), eq(200L)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        assertThrows(com.zhixuewuli.common.exception.BusinessException.class,
                () -> service.submit(200L, 1L, 5L, request()));
    }

    @Test
    @DisplayName("禁用题目拒绝新练习")
    void disabledQuestionRejects() {
        when(jdbc.queryForMap(contains("learning_plan_items"), eq(5L), eq(1L), eq(100L))).thenReturn(Map.of(
                "id", 5L, "question_id", 10L, "question_revision", 3L,
                "status", "ACTIVE", "user_id", 100L));
        Question disabled = question();
        disabled.setAiPracticeEnabled(false);
        when(questions.findById(10L)).thenReturn(Optional.of(disabled));
        assertThrows(com.zhixuewuli.common.exception.BusinessException.class,
                () -> service.submit(100L, 1L, 5L, request()));
    }
}
