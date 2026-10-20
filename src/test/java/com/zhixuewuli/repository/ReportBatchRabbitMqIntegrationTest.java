package com.zhixuewuli.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import com.zhixuewuli.common.rabbitmq.RabbitMqTestSupport;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.ReportBatchItemReportResponse;
import com.zhixuewuli.dto.response.ReportBatchResponse;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.service.reportbatch.ReportBatchService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 真实 RabbitMQ 端到端语义：成功生成、重复投递幂等、临时失败自动重试、
 * 永久失败进入死信、毒消息拒绝、过期租约恢复。
 *
 * <p>模型调用被 mock，不产生真实费用；断言通过轮询数据库与死信队列完成。</p>
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@Tag("integration-repository")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportBatchRabbitMqIntegrationTest extends RabbitMqTestSupport {

    private static final long WAIT_MILLIS = 45_000;

    @MockBean private AliyunAIClient aiClient;

    @Autowired private ReportBatchService batchService;
    @Autowired private ReportBatchRepository batches;
    @org.springframework.boot.test.mock.mockito.SpyBean private ReportBatchItemRepository items;
    @Autowired private ReportOutboxRepository outbox;
    @Autowired private ReportBatchProperties properties;
    @Autowired private TeacherEventService events;
    @Autowired private AnswerRecordService answers;
    @Autowired private ClassAccessService classAccess;
    @Autowired private UserRepository users;
    @Autowired private TeachingClassRepository classes;
    @Autowired private QuestionRepository questionBank;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitListenerEndpointRegistry listenerRegistry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    private record Fixture(long teacherId, long studentId, String studentName, long eventId) {}

    // ========== 场景 ==========

    @Test
    @DisplayName("消费成功：生成报告、更新进度、发件箱最终全部发布")
    void consumesMessageAndGeneratesReport() {
        stubSuccess("学生学情报告正文");
        Fixture fixture = fixture();
        ReportBatchResponse batch = createBatch(fixture);

        awaitBatchStatus(batch.getId(), ReportBatch.Status.SUCCEEDED);
        ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.SUCCEEDED);
        assertThat(item.getReportId()).isNotNull();
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getFinishedAt()).isNotNull();
        // 批次 SUCCEEDED 可能先于发件箱 ack/marker 清空，需等待发件箱收敛
        awaitUntil(WAIT_MILLIS, () -> pendingOutboxFor(batch.getId()) == 0, "发件箱 pending 应清空");

        ReportBatchItemReportResponse report = batchService.getItemReport(
                fixture.teacherId(), batch.getId(), item.getId());
        assertThat(report.getContent()).contains("学生学情报告正文");
        assertThat(report.getStudentName()).isEqualTo(fixture.studentName());
        verify(aiClient, times(1)).generateBlocking(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("重复投递同一消息不会重复生成，也不会重复计数")
    void duplicateDeliveryIsIgnored() {
        stubSuccess("幂等报告正文");
        Fixture fixture = fixture();
        ReportBatchResponse batch = createBatch(fixture);
        awaitBatchStatus(batch.getId(), ReportBatch.Status.SUCCEEDED);
        ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());

        String payload = jdbc.queryForObject(
                "SELECT payload FROM report_outbox WHERE payload LIKE ? ORDER BY id DESC LIMIT 1",
                String.class, "%\"itemId\":" + item.getId() + "%");
        rabbitTemplate.convertAndSend(properties.getMainExchange(), properties.getMainRoutingKey(), payload);
        sleep(1500);

        ReportBatchItem after = items.findById(item.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReportBatchItem.Status.SUCCEEDED);
        assertThat(after.getAttempts()).isEqualTo(1);
        verify(aiClient, times(1)).generateBlocking(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("临时失败自动重试：第二次执行成功，执行次数为 2")
    void transientFailureIsRetriedAutomatically() {
        when(aiClient.generateBlocking(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new AIServiceUnavailableException("上游连接中断"))
                .thenReturn("自动重试后的报告");
        Fixture fixture = fixture();
        ReportBatchResponse batch = createBatch(fixture);

        awaitBatchStatus(batch.getId(), ReportBatch.Status.SUCCEEDED);
        ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());
        assertThat(item.getAttempts()).isEqualTo(2);
        assertThat(item.getErrorCode()).isNull();
        verify(aiClient, times(2)).generateBlocking(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("永久失败：子任务失败并投递死信检查记录，不自动重试")
    void permanentFailureGoesToDeadLetterQueue() {
        when(aiClient.generateBlocking(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new BusinessException("报告输入超过上限"));
        Fixture fixture = fixture();
        ReportBatchResponse batch = createBatch(fixture);

        awaitBatchStatus(batch.getId(), ReportBatch.Status.FAILED);
        ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.FAILED);
        assertThat(item.getErrorCode()).isEqualTo("INPUT_INVALID");
        assertThat(item.getReportId()).isNull();
        verify(aiClient, times(1)).generateBlocking(anyString(), anyString(), any(Duration.class));

        String deadBody = awaitDlq(body -> body.contains("\"itemId\": " + item.getId())
                || body.contains("\"itemId\":" + item.getId()), WAIT_MILLIS);
        JsonNode record = readJson(deadBody);
        assertThat(record.path("kind").asText()).isEqualTo("REPORT_ITEM_DEAD");
        assertThat(record.path("errorCode").asText()).isEqualTo("INPUT_INVALID");
        assertThat(record.path("errorClass").asText()).isEqualTo("PERMANENT");
        assertThat(record.path("batchId").asLong()).isEqualTo(batch.getId());
        assertThat(deadBody)
                .as("死信记录不得包含学生姓名或作答内容")
                .doesNotContain(fixture.studentName())
                .doesNotContain("报告输入超过上限的原文");
        assertThatThrownBy(() -> batchService.getItemReport(fixture.teacherId(), batch.getId(), item.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成完成");
    }

    @Test
    @DisplayName("非法消息被直接拒绝进入死信队列，不产生任何副作用")
    void malformedMessageIsRejectedIntoDeadLetterQueue() {
        String poison = "not-a-report-batch-message-" + UUID.randomUUID();
        rabbitTemplate.convertAndSend(properties.getMainExchange(), properties.getMainRoutingKey(), poison);

        String body = awaitDlq(poison::equals, WAIT_MILLIS);
        assertThat(body).isEqualTo(poison);
    }

    @Test
    @DisplayName("执行者失联后租约过期：恢复调度器重新投递并成功生成")
    void expiredLeaseIsRecoveredAndRegenerated() {
        stubSuccess("租约恢复后的报告");
        stopListeners();
        Fixture fixture;
        ReportBatchResponse batch;
        try {
            fixture = fixture();
            batch = createBatch(fixture);
            awaitUntil(WAIT_MILLIS, () -> pendingOutboxFor(batch.getId()) == 0,
                    "发件箱应先把消息投递到队列");
            ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int claimed = jdbc.update("""
                    UPDATE report_batch_items SET status='PROCESSING', execution_id=?, lease_owner=?,
                    lease_expires_at=?, attempts=attempts+1, started_at=?
                    WHERE id=? AND status='PENDING'
                    """, "dead-executor", "dead-node", now.plusMinutes(1), now, item.getId());
            assertThat(claimed).isEqualTo(1);
            jdbc.update("UPDATE report_batch_items SET lease_expires_at=? WHERE id=?",
                    now.minusMinutes(1), item.getId());
        } finally {
            startListeners();
        }

        awaitBatchStatus(batch.getId(), ReportBatch.Status.SUCCEEDED);
        ReportBatchItem recovered = itemOf(batch.getId(), fixture.studentId());
        assertThat(recovered.getAttempts()).as("首次认领 + 恢复后重试").isEqualTo(2);
        assertThat(recovered.getStatus()).isEqualTo(ReportBatchItem.Status.SUCCEEDED);
        verify(aiClient, times(1)).generateBlocking(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void unroutableMessageStaysRetryableInsteadOfPublished() {
        ReportOutboxMessage row = new ReportOutboxMessage();
        row.setMessageId(UUID.randomUUID().toString());
        row.setExchangeName(properties.getMainExchange());
        row.setRoutingKey("missing.route." + UUID.randomUUID());
        row.setPayload("{}");
        row.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC));
        Long id = outbox.saveAndFlush(row).getId();
        awaitUntil(WAIT_MILLIS, () -> outbox.findById(id)
                .map(message -> message.getAttempts() > 0 && message.getLeaseOwner() == null).orElse(false),
                "不可路由消息的投递应完成失败收尾");
        ReportOutboxMessage after = outbox.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReportOutboxMessage.Status.PENDING);
        assertThat(after.getLastError()).isNotBlank();
        // Keep this deliberately unroutable fixture out of later scheduled scans.
        outbox.deleteById(id);
    }

    @Autowired private com.zhixuewuli.service.reportbatch.ReportBatchRecoveryScheduler recovery;

    @Test
    void databaseFailureBeforeClaimRecoversWithoutManualDlqReplay() throws Exception {
        stubSuccess("数据库恢复后的报告");
        stopListeners();
        Fixture fixture = fixture();
        ReportBatchResponse batch = createBatch(fixture);
        ReportBatchItem item = itemOf(batch.getId(), fixture.studentId());
        var failed = new java.util.concurrent.CountDownLatch(1);
        var original = mockingDetails(items).getMockCreationSettings().getDefaultAnswer();
        var failOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call -> {
            if (!failOnce.getAndSet(false)) return original.answer(call);
            failed.countDown();
            throw new org.springframework.dao.DataAccessResourceFailureException("before-claim fixture outage");
        }).when(items).findById(item.getId());
        try {
            startListeners();
            assertThat(failed.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            awaitDlq(body -> body.contains("\"itemId\":" + item.getId()), WAIT_MILLIS);
            awaitUntil(WAIT_MILLIS, () -> pendingOutboxFor(batch.getId()) == 0, "原消息已发布");
            assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
            jdbc.update("UPDATE report_outbox SET published_at=? WHERE payload LIKE ?",
                    LocalDateTime.now(ZoneOffset.UTC).minusHours(1), "%\"itemId\":" + item.getId() + "%");
            recovery.recoverExpiredLeases();
            awaitBatchStatus(batch.getId(), ReportBatch.Status.SUCCEEDED);
            assertThat(itemOf(batch.getId(), fixture.studentId()).getAttempts()).isEqualTo(1);
        } finally { startListeners(); }
    }

    // ========== 夹具与工具 ==========

    private void stubSuccess(String content) {
        when(aiClient.generateBlocking(anyString(), anyString(), any(Duration.class))).thenReturn(content);
    }

    private Fixture fixture() {
        User teacher = user("rb_mq_teacher", User.UserType.TEACHER);
        User student = user("rb_mq_student", User.UserType.STUDENT);
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), teacher.getId(), ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), student.getId());

        Question question = new Question();
        question.setContent("批量报告 MQ 夹具-" + UUID.randomUUID());
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A");
        question.setDifficulty(1);
        question = questionBank.saveAndFlush(question);

        TeacherEventCreateRequest request = new TeacherEventCreateRequest();
        request.setEventTitle("MQ-" + UUID.randomUUID().toString().substring(0, 8));
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setStatus(TeacherEvent.Status.PUBLISHED);
        request.setQuestionIds(List.of(question.getId()));
        request.setClassId(clazz.getId());
        Long eventId = events.publishEvent(request, teacher.getId()).getId();

        AnswerRecordSubmitRequest answer = new AnswerRecordSubmitRequest();
        answer.setEventId(eventId);
        answer.setQuestionId(question.getId());
        answer.setIntent("ASSIGNMENT");
        answer.setRequestId("mq-" + UUID.randomUUID());
        answer.setUserAnswer("A");
        answers.submitAnswerRecord(answer, student.getId(), "mq-session");
        events.closeTask(eventId);
        return new Fixture(teacher.getId(), student.getId(), student.getRealName(), eventId);
    }

    private ReportBatchResponse createBatch(Fixture fixture) {
        ReportBatchCreateRequest request = new ReportBatchCreateRequest();
        request.setEventId(fixture.eventId());
        request.setIdempotencyKey("mq-" + UUID.randomUUID());
        return batchService.createBatch(fixture.teacherId(), request);
    }

    private ReportBatchItem itemOf(Long batchId, Long userId) {
        return items.findByBatchIdOrderByIdAsc(batchId, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent().stream()
                .filter(item -> item.getUserId().equals(userId))
                .findFirst().orElseThrow();
    }

    private long pendingOutboxFor(Long batchId) {
        String marker = "\"batchId\":" + batchId + ",";
        return outbox.findAll().stream()
                .filter(message -> message.getStatus() == ReportOutboxMessage.Status.PENDING)
                .filter(message -> message.getPayload().replace(" ", "").contains(marker))
                .count();
    }

    private void awaitBatchStatus(Long batchId, ReportBatch.Status expected) {
        awaitUntil(WAIT_MILLIS,
                () -> batches.findById(batchId).map(batch -> batch.getStatus() == expected).orElse(false),
                "批次应变为 " + expected);
    }

    private void awaitUntil(long timeoutMillis, BooleanSupplier condition, String description) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            sleep(200);
        }
        throw new AssertionError("等待超时: " + description);
    }

    private String awaitDlq(Predicate<String> matcher, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(properties.getDeadLetterQueue(), 500);
            if (message == null) continue;
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            if (matcher.test(body)) return body;
        }
        throw new AssertionError("死信队列中未出现预期消息");
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception error) {
            throw new AssertionError("死信消息不是合法 JSON: " + body, error);
        }
    }

    private void stopListeners() {
        listenerRegistry.getListenerContainers().forEach(MessageListenerContainer::stop);
        awaitUntil(10_000, () -> listenerRegistry.getListenerContainers().stream()
                .noneMatch(MessageListenerContainer::isRunning), "监听器应停止");
    }

    private void startListeners() {
        listenerRegistry.getListenerContainers().forEach(MessageListenerContainer::start);
        awaitUntil(10_000, () -> listenerRegistry.getListenerContainers().stream()
                .allMatch(MessageListenerContainer::isRunning), "监听器应启动");
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

    private TeachingClass newClass() {
        TeachingClass clazz = new TeachingClass();
        clazz.setName("RB-MQ-" + UUID.randomUUID().toString().substring(0, 8));
        clazz.setGrade("初一");
        clazz.setStatus(TeachingClass.Status.ACTIVE);
        return classes.saveAndFlush(clazz);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
