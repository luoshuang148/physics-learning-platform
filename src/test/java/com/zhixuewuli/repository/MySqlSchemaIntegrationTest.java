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
    @Autowired com.zhixuewuli.service.user.UserService userService;

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentTeacherDeactivationRetainsAnAdministratorOnMySql() throws Exception {
        var a=user("mysql_guard_a",User.UserType.TEACHER);
        var b=user("mysql_guard_b",User.UserType.TEACHER);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(Long id:java.util.List.of(a.getId(),b.getId())) futures.add(pool.submit(()->{
                gate.await();var update=new UserAdminUpdateRequest();update.setUserId(id);update.setActive(false);
                try {userService.updateUserByAdmin(update,id);return true;}
                catch(com.zhixuewuli.common.exception.BusinessException expected){assertThat(expected.getMessage()).contains("最后一位");return false;}
            }));
            gate.countDown();int succeeded=0;for(var future:futures)if(future.get(15,java.util.concurrent.TimeUnit.SECONDS))succeeded++;
            assertThat(succeeded).isEqualTo(1);
            assertThat(users.countByUserTypeAndActiveTrue(User.UserType.TEACHER)).isEqualTo(1);
        } finally {users.deleteAllById(java.util.List.of(a.getId(),b.getId()));}
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.zhixuewuli.service.teacherEvent.TaskRosterService roster;
    @Autowired com.zhixuewuli.service.user.ClassroomGuard classroomGuard;

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void enrollmentSeesRecipientCommittedAfterItsRepeatableReadSnapshot() throws Exception {
        var teacher=user("mysql_snapshot_teacher",User.UserType.TEACHER);
        var student=user("mysql_snapshot_student",User.UserType.STUDENT);
        var created=new java.util.concurrent.atomic.AtomicReference<Long>();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            tx.executeWithoutResult(status->{
                // Permission checking has already established this request's consistent-read snapshot.
                var enrolled=users.findById(student.getId()).orElseThrow();
                assertThat(events.count()).isZero();
                try {
                    created.set(pool.submit(()->{
                        var request=new TeacherEventCreateRequest();request.setEventTitle("snapshot publish");
                        return teaching.publishEvent(request,teacher.getId()).getId();
                    }).get(15,java.util.concurrent.TimeUnit.SECONDS));
                } catch(Exception failure) {throw new RuntimeException(failure);}
                classroomGuard.lock();
                roster.enroll(enrolled);
                // Old snapshot reads miss the publication's recipient and attempt a duplicate INSERT.
                users.flush();
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_recipients WHERE event_id=? AND user_id=?",
                    Integer.class,created.get(),student.getId())).isEqualTo(1);
        } finally {
            if(created.get()!=null) {
                jdbc.update("DELETE FROM task_recipients WHERE event_id=?",created.get());
                jdbc.update("DELETE FROM teacher_events WHERE id=?",created.get());
            }
            users.deleteAllById(java.util.List.of(student.getId(),teacher.getId()));
        }
    }

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
        var page = service.getTeacherAnswerPage(1, 10, true, "mysql_student");
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().getFirst().getImageBase64()).isNull();
        assertThat(answers.findAll().getFirst().getEventId()).isEqualTo(eventId);
        var input = reportInputs.student(student.getId(), "mysql-session", eventId);
        assertThat(input).hasSize(1);
        assertThat(input.getFirst().getContentSnapshot()).isEqualTo("MySQL projection fixture");
        assertThat(input.getFirst().getQuestionSnapshot()).isNull();
        assertThat(input.getFirst().isTimestampUtc()).isTrue();
        jdbc.update("UPDATE questions SET explanation='edited after assignment' WHERE id=?", question.getId());
        assertThat(reportInputs.student(student.getId(), "mysql-session", eventId).getFirst().getAnalysisSnapshot()).isNull();
        assertThat(service.getTeacherAnswerPage(1, 10, true, "mysql_student").getRecords().getFirst().getQuestionAnalysis()).isNull();
        teaching.deleteCurrentEvent();
        assertThat(answers.count()).isEqualTo(1);
        assertThat(events.findById(eventId).orElseThrow().getIsGlobal()).isFalse();
    }

    @Test
    void mysqlAllowsParallelTasksAndStoresLifecycleColumns() {
        var a = user("mysql_teacher_a", User.UserType.TEACHER);
        var b = user("mysql_teacher_b", User.UserType.TEACHER);
        event(a);
        event(b);
        assertThat(events.countByStatus(TeacherEvent.Status.PUBLISHED)).isEqualTo(2);
        Integer lifecycleColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='teacher_events' AND COLUMN_NAME IN ('status','start_at','due_at')",
                Integer.class);
        Integer legacyColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='teacher_events' AND COLUMN_NAME IN ('is_global','active_slot')",
                Integer.class);
        assertThat(lifecycleColumns).isEqualTo(3);
        assertThat(legacyColumns).isZero();
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
