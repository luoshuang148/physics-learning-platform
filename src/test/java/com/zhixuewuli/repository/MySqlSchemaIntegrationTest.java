package com.zhixuewuli.repository;

import com.zhixuewuli.dto.request.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
@Transactional
class MySqlSchemaIntegrationTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired TeacherEventRepository events;
    @Autowired AnswerRecordRepository answers;
    @Autowired AnswerRecordService service;
    @Autowired com.zhixuewuli.service.ai.report.ReportInputRepository reportInputs;
    @Autowired TeacherEventService teaching;

    @Test
    void allLearningTablesRetainValidTaskForeignKeys() {
        Integer foreignKeys = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME='event_id' AND REFERENCED_TABLE_NAME='teacher_events' "
                + "AND TABLE_NAME IN ('answer_records','conversation_history','student_reports')", Integer.class);
        assertThat(foreignKeys).isEqualTo(3);
    }

    @Test
    void validatesRealSchemaProjectionAndPreservingArchives() {
        User teacher = user("mysql_teacher", User.UserType.TEACHER);
        User student = user("mysql_student", User.UserType.STUDENT);
        TeacherEventCreateRequest task = new TeacherEventCreateRequest(); task.setEventTitle("MySQL task");
        task.setEventTimestamp(LocalDateTime.now());

        Question question = new Question(); question.setContent("MySQL projection fixture"); question.setDifficulty(1);
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}"); question.setCorrectOption("A");
        question.setImageBase64("large-image-must-not-be-in-page"); question = questions.saveAndFlush(question);
        task.setQuestionIds(java.util.List.of(question.getId()));
        Long eventId = teaching.publishEvent(task, teacher.getId()).getId();
        var request = new AnswerRecordSubmitRequest(); request.setQuestionId(question.getId());
        request.setUserAnswer("B"); request.setIntent("ASSIGNMENT");request.setEventId(eventId);request.setRequestId("mysql-request");
        service.submitAnswerRecord(request, student.getId(), "mysql-session");
        var page = service.getTeacherAnswerPage(teacher.getId(), 1, 10, true, "mysql_student");
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().getFirst().getImageBase64()).isNull();
        assertThat(answers.findAll().getFirst().getEventId()).isEqualTo(eventId);
        var input = reportInputs.student(student.getId(), "mysql-session", eventId);
        assertThat(input).hasSize(1);
        assertThat(input.getFirst().getContentSnapshot()).isEqualTo("MySQL projection fixture");
        assertThat(input.getFirst().getQuestionSnapshot()).isNull();
        assertThat(input.getFirst().isTimestampUtc()).isTrue();
        teaching.deleteCurrentEvent(teacher.getId());
        assertThat(answers.count()).isEqualTo(1);
        assertThat(events.findById(eventId).orElseThrow().getIsGlobal()).isFalse();
    }

    @Test
    void mysqlEnforcesOneActiveTaskWithGeneratedUniqueSlot() {
        var a = user("mysql_teacher_a", User.UserType.TEACHER);
        var b = user("mysql_teacher_b", User.UserType.TEACHER);
        event(a);
        assertThatThrownBy(() -> event(b)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private void event(User user) {
        TeacherEvent event = new TeacherEvent(); event.setTeacherId(user.getId()); event.setTeacherName(user.getRealName());
        event.setEventTitle("unique task"); event.setEventTimestamp(LocalDateTime.now()); event.setIsGlobal(true);
        events.saveAndFlush(event);
    }
    private User user(String name, User.UserType role) {
        User user = new User(); user.setUsername(name); user.setRealName(name); user.setUserType(role);
        user.setActive(true); user.setPasswordHash("test-hash"); return users.saveAndFlush(user);
    }
}
