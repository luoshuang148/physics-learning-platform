package com.zhixuewuli.repository;

import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.ReportBatchResponse;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.service.reportbatch.ReportBatchService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 批量报告测试基座：H2 内存库 + 事务回滚 + 固定用户夹具（1001 学生 / 2001 教师 / 2002 教师）。
 *
 * <p>不依赖 Redis/RabbitMQ 容器；消费者与调度器在本环境关闭，只验证数据库语义。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@Transactional
@Tag("integration-repository")
@Sql("/sql/scenario-teacher-event-test-data.sql")
abstract class ReportBatchTestSupport {

    protected static final long TEACHER_ID = 2001L;
    protected static final long OTHER_TEACHER_ID = 2002L;
    protected static final long STUDENT_ID = 1001L;

    @Autowired protected ReportBatchService batchService;
    @Autowired protected TeacherEventService events;
    @Autowired protected AnswerRecordService answers;
    @Autowired protected ClassAccessService classAccess;
    @Autowired protected UserRepository users;
    @Autowired protected TeachingClassRepository classes;
    @Autowired protected QuestionRepository questionBank;
    @Autowired protected ReportBatchRepository batches;
    @Autowired protected ReportBatchItemRepository items;
    @Autowired protected ReportOutboxRepository outbox;
    @Autowired protected ReportBatchProperties properties;
    @Autowired protected JdbcTemplate jdbc;

    protected record TaskFixture(Long eventId, Long questionId) {}

    protected User user(String prefix, User.UserType type) {
        String username = prefix.replace('-', '_') + "_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setRealName(username);
        user.setUserType(type);
        user.setActive(true);
        user.setPasswordHash("test-hash");
        return users.saveAndFlush(user);
    }

    protected TeachingClass newClass() {
        TeachingClass clazz = new TeachingClass();
        clazz.setName("RB班级-" + UUID.randomUUID().toString().substring(0, 8));
        clazz.setGrade("初一");
        clazz.setStatus(TeachingClass.Status.ACTIVE);
        return classes.saveAndFlush(clazz);
    }

    protected TaskFixture publishTask(Long classId) {
        Question question = new Question();
        question.setContent("批量报告夹具题目-" + UUID.randomUUID());
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A");
        question.setDifficulty(1);
        question = questionBank.saveAndFlush(question);

        TeacherEventCreateRequest request = new TeacherEventCreateRequest();
        request.setEventTitle("批量报告任务-" + UUID.randomUUID().toString().substring(0, 8));
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setStatus(TeacherEvent.Status.PUBLISHED);
        request.setQuestionIds(List.of(question.getId()));
        request.setClassId(classId);
        Long eventId = events.publishEvent(request, TEACHER_ID).getId();
        return new TaskFixture(eventId, question.getId());
    }

    protected void submitAnswer(TaskFixture task, Long studentId) {
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setEventId(task.eventId());
        request.setQuestionId(task.questionId());
        request.setIntent("ASSIGNMENT");
        request.setRequestId("batch-" + UUID.randomUUID());
        request.setUserAnswer("A");
        answers.submitAnswerRecord(request, studentId, "batch-session");
    }

    protected ReportBatchResponse createBatch(Long eventId, String key) {
        ReportBatchCreateRequest request = new ReportBatchCreateRequest();
        request.setEventId(eventId);
        request.setIdempotencyKey(key);
        return batchService.createBatch(TEACHER_ID, request);
    }

    protected ReportBatchItem itemOf(Long batchId, Long userId) {
        return items.findByBatchIdOrderByIdAsc(batchId, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent().stream()
                .filter(item -> item.getUserId().equals(userId))
                .findFirst().orElseThrow();
    }

    @Autowired protected jakarta.persistence.EntityManager jpaEntityManager;

    protected ReportBatchItem freshItem(Long itemId) {
        jpaEntityManager.clear();
        return items.findById(itemId).orElseThrow();
    }

    protected ReportBatch freshBatch(Long batchId) {
        jpaEntityManager.clear();
        return batches.findById(batchId).orElseThrow();
    }

    protected void forceItemStatus(ReportBatchItem item, ReportBatchItem.Status status) {
        item.setStatus(status);
        if (status == ReportBatchItem.Status.FAILED) {
            item.setErrorCode("TIMEOUT");
            item.setErrorMessage("fixture failure");
        }
        if (status == ReportBatchItem.Status.SUCCEEDED || status == ReportBatchItem.Status.SKIPPED) {
            item.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        items.saveAndFlush(item);
    }
}
