package com.zhixuewuli.repository;

import com.zhixuewuli.common.exception.teacherevent.EventAlreadyExistsException;
import com.zhixuewuli.dto.request.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.teacherEvent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/** Supported MySQL locks, with latches controlling the publication/submission overlap. */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
class MySqlAssignmentLockingTest extends com.zhixuewuli.common.redis.RedisTestSupport {
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired TeacherEventService teaching;
    @Autowired AnswerRecordService answers;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @SpyBean TaskRosterService roster;

    @Test
    void replacementAndSubmissionDoNotDeadlockOnTheStudentRow() throws Exception {
        var teacher=user("lock_teacher",User.UserType.TEACHER);
        var student=user("lock_student",User.UserType.STUDENT);
        var question=new Question();question.setContent("Lock fixture");question.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        question.setCorrectOption("A");question.setDifficulty(1);question=questions.saveAndFlush(question);
        var request=new TeacherEventCreateRequest();request.setEventTitle("Lock fixture");request.setQuestionIds(java.util.List.of(question.getId()));
        var original=teaching.publishEvent(request,teacher.getId()).getId();
        var capturing=new CountDownLatch(1);var allowCapture=new CountDownLatch(1);
        doAnswer(call->{capturing.countDown();assertThat(allowCapture.await(10,TimeUnit.SECONDS)).isTrue();return call.callRealMethod();})
                .when(org.springframework.test.util.AopTestUtils.<TaskRosterService>getUltimateTargetObject(roster)).capture(anyLong());
        Long questionId=question.getId();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var replacement=pool.submit(()->teaching.replaceEvent(original,request,teacher.getId()));
            assertThat(capturing.await(10,TimeUnit.SECONDS)).isTrue();
            var submission=pool.submit(()->{
                var answer=new AnswerRecordSubmitRequest();answer.setQuestionId(questionId);answer.setEventId(original);
                answer.setIntent("ASSIGNMENT");answer.setRequestId("overlapping-replacement");answer.setUserAnswer("A");
                return catchThrowable(()->answers.submitAnswerRecord(answer,student.getId(),"mysql-lock"));
            });
            try {
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                boolean waiting=false;
                while(System.nanoTime()<until) {
                    waiting=jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w " +
                            "JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID " +
                            "WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME IN ('users','teacher_events')",Integer.class)>0;
                    if(waiting)break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("submission reached the replacement locks").isTrue();
            } finally {allowCapture.countDown();}
            assertThat(replacement.get(15,TimeUnit.SECONDS).getId()).isNotEqualTo(original);
            assertThat(submission.get(15,TimeUnit.SECONDS)).isInstanceOf(EventAlreadyExistsException.class)
                    .hasMessageContaining("截止或归档");
        } finally {
            allowCapture.countDown();
            var ids=jdbc.queryForList("SELECT id FROM teacher_events WHERE teacher_id=?",Long.class,teacher.getId());
            for(Long id:ids) {
                jdbc.update("DELETE FROM task_recipients WHERE event_id=?",id);
                jdbc.update("DELETE FROM teacher_event_questions WHERE event_id=?",id);
                jdbc.update("DELETE FROM teacher_events WHERE id=?",id);
            }
            questions.deleteById(questionId);users.deleteAllById(java.util.List.of(student.getId(),teacher.getId()));
        }
    }
    private User user(String name,User.UserType role) {
        var user=new User();user.setUsername(name);user.setRealName(name);user.setUserType(role);
        user.setActive(true);user.setPasswordHash("fixture-hash");return users.saveAndFlush(user);
    }
}
