package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class TeacherEventArchiveRegressionTest extends BaseControllerIntegrationTest {
    @Autowired TeacherEventService events;
    @Autowired TeacherEventRepository eventRepository;
    @Autowired AnswerRecordRepository answers;
    @Autowired ConversationHistoryRepository histories;
    @Autowired StudentReportRepository reports;

    @Test
    void endingAnEventArchivesItWithoutDeletingLearningData() throws Exception {
        Question question = new Question(); question.setContent("Archive fixture"); question.setDifficulty(1);
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}"); question.setCorrectOption("A");
        jpaEntityManager.persist(question); jpaEntityManager.flush();
        AnswerRecord answer = new AnswerRecord(); answer.setUserId(1001L); answer.setQuestionId(question.getId());
        answer.setSessionId("archive"); answer.setUserAnswer("A"); answer.setCorrect(true);
        answers.saveAndFlush(answer);
        ConversationHistory history = new ConversationHistory(); history.setUserId(1001L);
        history.setSessionId("archive"); history.setConversationType(ConversationType.REAL_TIME_QA);
        history.setQuestion("question"); history.setAnswer("answer"); history.setSequenceNumber(1); history.setIsActive(true);
        histories.saveAndFlush(history);
        StudentReport report = new StudentReport(); report.setUserId(1001L); report.setRealName("学生用户");
        report.setSessionId("archive"); report.setReportContent("report"); reports.saveAndFlush(report);

        events.deleteCurrentEvent(2001L);
        assertThat(answers.count()).isEqualTo(1);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(reports.count()).isEqualTo(1);
        assertThat(eventRepository.findById(1L)).isPresent().get().extracting(TeacherEvent::getIsGlobal).isEqualTo(false);
        assertThat(events.getGlobalEvent()).isNull();
        mockMvc.perform(get("/teacher/events/history").header("Authorization", createAuthHeader(2001L, "teacher_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    @Autowired com.zhixuewuli.service.answerRecord.AnswerRecordService answerService;
    @Autowired com.zhixuewuli.service.session.SessionManager sessions;

    @Test
    void newLearningWritesCaptureTheEventAndStaleSubmissionsAreRejected() {
        Question question = new Question(); question.setContent("Event association fixture"); question.setDifficulty(1);
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}"); question.setCorrectOption("A");
        jpaEntityManager.persist(question); jpaEntityManager.flush();
        var request = new com.zhixuewuli.dto.request.AnswerRecordSubmitRequest();
        request.setQuestionId(question.getId()); request.setUserAnswer("A"); request.setEventId(1L);
        answerService.submitAnswerRecord(request, 1001L, "event-linked");
        assertThat(answers.findAll()).extracting(AnswerRecord::getEventId).containsExactly(1L);
        var context = sessions.getOrCreateSession("event-linked", 1001L, ConversationType.REAL_TIME_QA, null, () -> "physics");
        sessions.addInteraction(context, "question", "answer", 10);
        assertThat(histories.findAll()).extracting(ConversationHistory::getEventId).containsExactly(1L);
        events.deleteCurrentEvent(2001L);
        assertThatThrownBy(() -> answerService.submitAnswerRecord(request, 1001L, "event-linked"))
                .isInstanceOf(com.zhixuewuli.common.exception.BusinessException.class);
        assertThat(answers.count()).isEqualTo(1);
        var freePractice = sessions.getOrCreateSession("event-linked", 1001L, ConversationType.REAL_TIME_QA, null, () -> "physics");
        assertThat(freePractice.getEventId()).isNull();
        assertThat(freePractice.getInteractionRecords()).isEmpty();
    }

    @Test
    void databaseRejectsTwoActiveEventsEvenIfServiceChecksAreBypassed() {
        TeacherEvent competing = new TeacherEvent(); competing.setTeacherId(2002L);
        competing.setTeacherName("无事件教师"); competing.setEventTitle("competing");
        competing.setEventTimestamp(LocalDateTime.now()); competing.setIsGlobal(true);
        assertThatThrownBy(() -> eventRepository.saveAndFlush(competing))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
