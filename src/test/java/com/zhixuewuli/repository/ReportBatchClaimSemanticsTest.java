package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ClassTeacher;
import com.zhixuewuli.entity.ReportBatch;
import com.zhixuewuli.entity.ReportBatchItem;
import com.zhixuewuli.entity.TeachingClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 认领/执行标识/租约恢复的条件更新语义：并发执行者中只有一个能推进状态。 */
@DisplayName("批量子任务认领与租约语义")
class ReportBatchClaimSemanticsTest extends ReportBatchTestSupport {

    private record Row(Long batchId, Long itemId) {}

    private Row newRow(int maxAttempts) {
        TeachingClass clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, ClassTeacher.Role.OWNER);
        com.zhixuewuli.entity.User student = user("rb_claim_student", com.zhixuewuli.entity.User.UserType.STUDENT);
        classAccess.ensureStudent(clazz.getId(), student.getId());
        TaskFixture task = publishTask(clazz.getId());
        events.closeTask(task.eventId());
        ReportBatch batch = new ReportBatch();
        batch.setClassId(clazz.getId());
        batch.setEventId(task.eventId());
        batch.setTeacherId(TEACHER_ID);
        batch.setIdempotencyKey("claim-" + UUID.randomUUID());
        batch.setRequestHash("claim-fixture-hash");
        batch.setStatus(ReportBatch.Status.PENDING);
        batch.setTotalCount(1);
        batches.saveAndFlush(batch);
        ReportBatchItem item = new ReportBatchItem();
        item.setBatchId(batch.getId());
        item.setUserId(student.getId());
        item.setStudentName(student.getRealName());
        item.setStatus(ReportBatchItem.Status.PENDING);
        item.setMaxAttempts(maxAttempts);
        items.saveAndFlush(item);
        return new Row(batch.getId(), item.getId());
    }

    private int claim(Row row, String executionId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return items.claim(row.itemId(), ReportBatchItem.Status.PENDING, ReportBatchItem.Status.PROCESSING,
                executionId, "test-node", now.plusMinutes(6), now);
    }

    private void expireLease(Row row) {
        ReportBatchItem item = freshItem(row.itemId());
        item.setLeaseExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        items.saveAndFlush(item);
    }

    @Test
    @DisplayName("同一子任务同时只能被一个执行者认领")
    void claimIsExclusiveWhileLeaseValid() {
        Row row = newRow(3);
        assertThat(claim(row, "exec-a")).isEqualTo(1);
        assertThat(claim(row, "exec-b")).as("有效租约内第二次认领必须失败").isZero();
        ReportBatchItem item = freshItem(row.itemId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.PROCESSING);
        assertThat(item.getExecutionId()).isEqualTo("exec-a");
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getLeaseExpiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("过期租约在重试次数未耗尽时回到待处理并可再次认领")
    void recoverExpiredLeaseRequeuesWhenAttemptsRemain() {
        Row row = newRow(3);
        claim(row, "exec-a");
        expireLease(row);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int recovered = items.recoverExpiredLease(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.PENDING, "LEASE_EXPIRED", "执行租约过期，已自动重新调度",
                now, 3);
        assertThat(recovered).isEqualTo(1);
        ReportBatchItem item = freshItem(row.itemId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        assertThat(item.getErrorCode()).isEqualTo("LEASE_EXPIRED");
        assertThat(claim(row, "exec-b")).isEqualTo(1);
        assertThat(freshItem(row.itemId()).getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("过期租约且重试耗尽直接判定失败")
    void exhaustedExpiredLeaseBecomesFailed() {
        Row row = newRow(1);
        claim(row, "exec-a");
        expireLease(row);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(items.recoverExpiredLease(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.PENDING, "LEASE_EXPIRED", "msg", now, 1)).isZero();
        assertThat(items.markExhaustedExpiredLease(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.FAILED, "EXHAUSTED", "执行租约过期且重试次数已耗尽", now, 1)).isEqualTo(1);
        ReportBatchItem item = freshItem(row.itemId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.FAILED);
        assertThat(item.getErrorCode()).isEqualTo("EXHAUSTED");
        assertThat(item.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("过期执行者不能覆盖已被取代的执行结果")
    void staleExecutionCannotOverwrite() {
        Row row = newRow(3);
        claim(row, "exec-a");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(items.markSucceeded(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.SUCCEEDED, "exec-b", 999L, "digest", "rev", now)).isZero();
        assertThat(items.markFailed(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.FAILED, "exec-b", "TIMEOUT", "msg", now)).isZero();
        assertThat(freshItem(row.itemId()).getStatus()).isEqualTo(ReportBatchItem.Status.PROCESSING);

        assertThat(items.markSucceeded(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.SUCCEEDED, "exec-a", 42L, "digest", "rev", now)).isEqualTo(1);
        ReportBatchItem item = freshItem(row.itemId());
        assertThat(item.getReportId()).isEqualTo(42L);
        assertThat(item.getLeaseExpiresAt()).isNull();
        assertThat(items.markSucceeded(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.SUCCEEDED, "exec-a", 43L, "digest", "rev", now))
                .as("已完成的行不能被重复写入").isZero();
    }

    @Test
    @DisplayName("释放重试与跳过同样要求执行标识匹配")
    void releaseAndSkipRequireOwningExecution() {
        Row row = newRow(3);
        claim(row, "exec-a");

        assertThat(items.releaseForRetry(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.PENDING, "exec-b", "TIMEOUT", "msg")).isZero();
        assertThat(items.releaseForRetry(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.PENDING, "exec-a", "TIMEOUT", "msg")).isEqualTo(1);
        ReportBatchItem item = freshItem(row.itemId());
        assertThat(item.getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        assertThat(item.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(item.getLeaseExpiresAt()).isNull();

        claim(row, "exec-c");
        assertThat(items.markSkipped(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.SKIPPED, "exec-a", "reason", LocalDateTime.now(ZoneOffset.UTC))).isZero();
        assertThat(items.markSkipped(row.itemId(), ReportBatchItem.Status.PROCESSING,
                ReportBatchItem.Status.SKIPPED, "exec-c", "该任务下无作答记录",
                LocalDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
        assertThat(freshItem(row.itemId()).getStatus()).isEqualTo(ReportBatchItem.Status.SKIPPED);
    }

    @Test
    @DisplayName("手动重试标记只接受失败状态并清空错误信息")
    void manualRetryOnlyAppliesToFailedItems() {
        Row row = newRow(3);
        assertThat(items.markManualRetry(row.itemId(), row.batchId(),
                ReportBatchItem.Status.FAILED, ReportBatchItem.Status.PENDING)).isZero();

        ReportBatchItem item = freshItem(row.itemId());
        item.setStatus(ReportBatchItem.Status.FAILED);
        item.setErrorCode("TIMEOUT");
        item.setErrorMessage("msg");
        item.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        items.saveAndFlush(item);

        assertThat(items.markManualRetry(row.itemId(), row.batchId(),
                ReportBatchItem.Status.FAILED, ReportBatchItem.Status.PENDING)).isEqualTo(1);
        ReportBatchItem retried = freshItem(row.itemId());
        assertThat(retried.getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        assertThat(retried.getManualRetryCount()).isEqualTo(1);
        assertThat(retried.getErrorCode()).isNull();
        assertThat(retried.getErrorMessage()).isNull();
        assertThat(retried.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("过期租约扫描只返回超过租约的 PROCESSING 行")
    void expiredLeaseQuerySelectsOnlyOverdueProcessingRows() {
        Row overdue = newRow(3);
        claim(overdue, "exec-a");
        expireLease(overdue);

        Row healthy = newRow(3);
        claim(healthy, "exec-b");

        var expired = items.findExpiredLeaseIds(ReportBatchItem.Status.PROCESSING,
                LocalDateTime.now(ZoneOffset.UTC), org.springframework.data.domain.PageRequest.of(0, 100));
        assertThat(expired).contains(overdue.itemId()).doesNotContain(healthy.itemId());
    }
}
