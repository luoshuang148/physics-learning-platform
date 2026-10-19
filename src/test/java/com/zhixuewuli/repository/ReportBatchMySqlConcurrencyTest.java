package com.zhixuewuli.repository;

import com.zhixuewuli.common.redis.RedisTestSupport;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.service.reportbatch.ReportBatchService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** MySQL 真实并发语义：幂等唯一约束与子任务认领只能有一个赢家。 */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-test")
@Transactional
class ReportBatchMySqlConcurrencyTest extends RedisTestSupport {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TeachingClassRepository classes;
    @Autowired private QuestionRepository questions;
    @Autowired private TeacherEventService events;
    @Autowired private AnswerRecordService answers;
    @Autowired private ClassAccessService classAccess;
    @Autowired private ReportBatchService batchService;
    @Autowired private ReportBatchRepository batches;
    @Autowired private ReportBatchItemRepository items;
    @Autowired private PlatformTransactionManager transactionManager;

    private record Fixture(long teacherId, long studentId, long secondStudentId,
                           long classId, long eventId, long questionId) {}

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("同一教师用相同幂等键并发创建，唯一约束保证只产生一个批次")
    void concurrentCreateWithSameKeyProducesSingleBatch() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<Long> batchId = new AtomicReference<>();
        try {
            ReportBatchCreateRequest request = new ReportBatchCreateRequest();
            request.setEventId(fixture.eventId());
            request.setIdempotencyKey("mysql-idem-" + UUID.randomUUID());

            CountDownLatch gate = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                List<java.util.concurrent.Future<Long>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    futures.add(pool.submit(() -> {
                        gate.await();
                        return batchService.createBatch(fixture.teacherId(), request).getId();
                    }));
                }
                gate.countDown();
                Set<Long> ids = new HashSet<>();
                for (var future : futures) ids.add(future.get(20, TimeUnit.SECONDS));
                batchId.set(ids.iterator().next());
                assertThat(ids).as("并发创建必须收敛到同一批次").hasSize(1);
            }

            Long id = batchId.get();
            assertThat(batches.findById(id)).isPresent();
            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM report_batches WHERE teacher_id=? AND idempotency_key=?",
                    Integer.class, fixture.teacherId(), request.getIdempotencyKey());
            assertThat(rows).isEqualTo(1);
            Integer itemRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM report_batch_items WHERE batch_id=?", Integer.class, id);
            assertThat(itemRows).isEqualTo(2);
            assertThat(items.findByBatchIdOrderByIdAsc(id, org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent()).extracting(ReportBatchItem::getStatus)
                    .containsExactlyInAnyOrder(ReportBatchItem.Status.PENDING, ReportBatchItem.Status.SKIPPED);
        } finally {
            cleanup(fixture, batchId.get());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("两个并发执行者认领同一子任务，只有一人成功且执行次数为 1")
    void concurrentClaimHasSingleWinner() throws Exception {
        Fixture fixture = fixture();
        AtomicReference<Long> batchId = new AtomicReference<>();
        try {
            ReportBatchCreateRequest request = new ReportBatchCreateRequest();
            request.setEventId(fixture.eventId());
            request.setIdempotencyKey("mysql-claim-" + UUID.randomUUID());
            long id = batchService.createBatch(fixture.teacherId(), request).getId();
            batchId.set(id);
            ReportBatchItem item = items.findByBatchIdOrderByIdAsc(id,
                    org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
            assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            CountDownLatch gate = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                List<java.util.concurrent.Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    String executor = "mysql-exec-" + i;
                    futures.add(pool.submit(() -> {
                        gate.await();
                        return template.execute(status -> {
                            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                            return items.claim(item.getId(), ReportBatchItem.Status.PENDING,
                                    ReportBatchItem.Status.PROCESSING, executor, "mysql-node",
                                    now.plusMinutes(6), now);
                        });
                    }));
                }
                gate.countDown();
                int winners = 0;
                for (var future : futures) winners += future.get(20, TimeUnit.SECONDS);
                assertThat(winners).as("条件更新只允许一个赢家").isEqualTo(1);
            }

            ReportBatchItem claimed = items.findById(item.getId()).orElseThrow();
            assertThat(claimed.getStatus()).isEqualTo(ReportBatchItem.Status.PROCESSING);
            assertThat(claimed.getAttempts()).isEqualTo(1);
        } finally {
            cleanup(fixture, batchId.get());
        }
    }

    @Autowired private ReportOutboxRepository deliveries;
    @Autowired private com.zhixuewuli.service.reportbatch.ReportBatchOutboxService writer;
    @Autowired private com.zhixuewuli.service.reportbatch.ReportBatchMetrics metrics;
    @Autowired private com.zhixuewuli.common.config.properties.ReportBatchProperties properties;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoRecoveryNodesCreateOnlyOneReplacementDelivery() throws Exception {
        Fixture fixture = fixture();
        Long batchId = null;
        try {
            var request = new ReportBatchCreateRequest(); request.setEventId(fixture.eventId());
            request.setIdempotencyKey(UUID.randomUUID().toString());
            batchId = batchService.createBatch(fixture.teacherId(), request).getId();
            var pending = items.findByBatchIdOrderByIdAsc(batchId, org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent().stream().filter(i -> i.getStatus() == ReportBatchItem.Status.PENDING).findFirst().orElseThrow();
            jdbc.update("UPDATE report_outbox SET status='PUBLISHED', published_at=? WHERE work_item_id=?",
                    LocalDateTime.now(ZoneOffset.UTC).minusHours(1), pending.getId());
            var tx = new TransactionTemplate(transactionManager);
            var node1 = new com.zhixuewuli.service.reportbatch.ReportBatchRecoveryScheduler(items, writer, batchService, properties, metrics, tx, deliveries);
            var node2 = new com.zhixuewuli.service.reportbatch.ReportBatchRecoveryScheduler(items, writer, batchService, properties, metrics, tx, deliveries);
            properties.setEnabled(true);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var gate = new CountDownLatch(1);
                var a = pool.submit(() -> { gate.await(); node1.recoverExpiredLeases(); return true; });
                var b = pool.submit(() -> { gate.await(); node2.recoverExpiredLeases(); return true; });
                gate.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
            } finally { properties.setEnabled(false); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_outbox WHERE work_item_id=?", Integer.class, pending.getId())).isEqualTo(2);
            assertThat(items.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        } finally { cleanup(fixture, batchId); }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void stalePublisherSnapshotCannotFailANewManualRetry() throws Exception {
        Fixture fixture = fixture(); Long batchId = null;
        try {
            var request = new ReportBatchCreateRequest(); request.setEventId(fixture.eventId());
            request.setIdempotencyKey(UUID.randomUUID().toString());
            batchId = batchService.createBatch(fixture.teacherId(), request).getId();
            final Long targetBatch = batchId;
            var item = items.findByBatchIdOrderByIdAsc(batchId, org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent().stream().filter(i -> i.getStatus() == ReportBatchItem.Status.PENDING).findFirst().orElseThrow();
            var delivery = deliveries.findFirstByWorkItemIdOrderByIdDesc(item.getId()).orElseThrow();
            jdbc.update("UPDATE report_batch_items SET status='FAILED' WHERE id=?", item.getId());
            jdbc.update("UPDATE report_outbox SET status='FAILED' WHERE id=?", delivery.getId());
            var tx = new TransactionTemplate(transactionManager);
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            try (var worker = Executors.newSingleThreadExecutor()) {
                tx.executeWithoutResult(status -> {
                    var stale = deliveries.findById(delivery.getId()).orElseThrow(); // open an old RR snapshot
                    try { worker.submit(() -> batchService.retryItem(fixture.teacherId(), targetBatch, item.getId()))
                            .get(15, TimeUnit.SECONDS); }
                    catch (Exception error) { throw new RuntimeException(error); }
                    batchService.failUndeliverableItem(stale);
                });
            }
            assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        } finally { cleanup(fixture, batchId); }
    }

    private Fixture fixture() {
        User teacher = user("rb_mysql_teacher", User.UserType.TEACHER);
        User student = user("rb_mysql_student", User.UserType.STUDENT);
        User second = user("rb_mysql_student2", User.UserType.STUDENT);
        TeachingClass clazz = new TeachingClass();
        clazz.setName("RB-MySQL-" + UUID.randomUUID().toString().substring(0, 8));
        clazz.setGrade("初一");
        clazz.setStatus(TeachingClass.Status.ACTIVE);
        classes.saveAndFlush(clazz);
        classAccess.addTeacher(clazz.getId(), teacher.getId(), ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), student.getId());
        classAccess.ensureStudent(clazz.getId(), second.getId());

        Question question = new Question();
        question.setContent("MySQL 批量夹具-" + UUID.randomUUID());
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A");
        question.setDifficulty(1);
        question = questions.saveAndFlush(question);

        TeacherEventCreateRequest request = new TeacherEventCreateRequest();
        request.setEventTitle("MYSQL-" + UUID.randomUUID().toString().substring(0, 8));
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setStatus(TeacherEvent.Status.PUBLISHED);
        request.setQuestionIds(List.of(question.getId()));
        request.setClassId(clazz.getId());
        long eventId = events.publishEvent(request, teacher.getId()).getId();

        AnswerRecordSubmitRequest answer = new AnswerRecordSubmitRequest();
        answer.setEventId(eventId);
        answer.setQuestionId(question.getId());
        answer.setIntent("ASSIGNMENT");
        answer.setRequestId("mysql-" + UUID.randomUUID());
        answer.setUserAnswer("A");
        answers.submitAnswerRecord(answer, student.getId(), "mysql-batch-session");
        events.closeTask(eventId);

        return new Fixture(teacher.getId(), student.getId(), second.getId(),
                clazz.getId(), eventId, question.getId());
    }

    private User user(String prefix, User.UserType type) {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setRealName(username);
        user.setUserType(type);
        user.setActive(true);
        user.setPasswordHash("test-hash");
        return users.saveAndFlush(user);
    }

    private void cleanup(Fixture fixture, Long batchId) {
        try {
            if (batchId != null) {
                jdbc.update("DELETE FROM report_outbox WHERE payload LIKE ?",
                        "%\"batchId\":" + batchId + "%");
                jdbc.update("DELETE FROM student_reports WHERE session_id=?", "batch:" + batchId);
                jdbc.update("DELETE FROM report_batch_items WHERE batch_id=?", batchId);
                jdbc.update("DELETE FROM report_batches WHERE id=?", batchId);
            }
            jdbc.update("DELETE FROM answer_records WHERE event_id=?", fixture.eventId());
            jdbc.update("DELETE FROM task_recipients WHERE event_id=?", fixture.eventId());
            jdbc.update("DELETE FROM teacher_events WHERE id=?", fixture.eventId());
            jdbc.update("DELETE FROM class_teachers WHERE class_id=?", fixture.classId());
            jdbc.update("DELETE FROM class_students WHERE class_id=?", fixture.classId());
            jdbc.update("DELETE FROM questions WHERE id=?", fixture.questionId());
            jdbc.update("DELETE FROM classes WHERE id=?", fixture.classId());
            users.deleteAllById(List.of(fixture.teacherId(), fixture.studentId(), fixture.secondStudentId()));
        } catch (RuntimeException ignored) {
            // Best-effort cleanup; failures surface in the following test setup.
        }
    }
}
