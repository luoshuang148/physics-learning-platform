package com.zhixuewuli.repository;

import com.zhixuewuli.entity.*;
import com.zhixuewuli.service.reportbatch.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = {"app.report-batch.publish-interval=1h", "app.report-batch.recover-interval=1h"})
class ReportBatchDeliveryRecoveryTest extends ReportBatchTestSupport {
    @Autowired ReportBatchPublisher publisher;
    @Autowired ReportBatchRecoveryScheduler recovery;
    @MockBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;

    private ReportBatchItem pendingItem() {
        var clazz = newClass();
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        var task = publishTask(clazz.getId());
        submitAnswer(task, STUDENT_ID); events.closeTask(task.eventId());
        return itemOf(createBatch(task.eventId(), UUID.randomUUID().toString()).getId(), STUDENT_ID);
    }
    private void runEnabled(Runnable action) {
        boolean previous = properties.isEnabled(); properties.setEnabled(true);
        try { action.run(); } finally { properties.setEnabled(previous); }
        jpaEntityManager.clear();
    }
    @Test void publishedButUnclaimedItemIsRedispatchedAfterItsDeliveryDeadline() {
        var item = pendingItem();
        var old = outbox.findAll().getFirst();
        old.setStatus(ReportOutboxMessage.Status.PUBLISHED);
        old.setPublishedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        outbox.saveAndFlush(old); jpaEntityManager.clear();
        runEnabled(recovery::recoverExpiredLeases);
        assertThat(outbox.count()).isEqualTo(2);
        assertThat(freshItem(item.getId()).getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        runEnabled(recovery::recoverExpiredLeases);
        assertThat(outbox.count()).as("重扫不能重复创建待投递消息").isEqualTo(2);
    }
    @Test void stalePublisherCannotFinalizeOrClearAReplacementClaim() {
        pendingItem();
        Long id = outbox.findAll().getFirst().getId();
        var now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(outbox.claim(id, ReportOutboxMessage.Status.PENDING, "old", now.minusMinutes(1), now, 20)).isEqualTo(1);
        assertThat(outbox.claim(id, ReportOutboxMessage.Status.PENDING, "replacement", now.plusMinutes(1), now, 20)).isEqualTo(1);
        assertThat(outbox.markPublished(id, ReportOutboxMessage.Status.PENDING, ReportOutboxMessage.Status.PUBLISHED, now, "old")).isZero();
        assertThat(outbox.markRetry(id, ReportOutboxMessage.Status.PENDING, now, "late failure", "old")).isZero();
        assertThat(outbox.markFailed(id, ReportOutboxMessage.Status.PENDING, ReportOutboxMessage.Status.FAILED, "late failure", "old")).isZero();
        jpaEntityManager.clear();
        assertThat(outbox.findById(id).orElseThrow().getLeaseOwner()).isEqualTo("replacement");
    }

    @Test void finalPublisherClaimCrashConvergesToRetryableBusinessFailure() {
        var item = pendingItem();
        var old = outbox.findAll().getFirst();
        old.setAttempts(properties.getOutboxMaxAttempts());
        old.setLeaseOwner("crashed-last-publisher");
        old.setLeaseExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        outbox.saveAndFlush(old); jpaEntityManager.clear();
        runEnabled(publisher::publishDueMessages);
        assertThat(outbox.findById(old.getId()).orElseThrow().getStatus()).isEqualTo(ReportOutboxMessage.Status.FAILED);
        assertThat(freshItem(item.getId()).getStatus()).isEqualTo(ReportBatchItem.Status.FAILED);
        assertThat(freshBatch(item.getBatchId()).getStatus()).isEqualTo(ReportBatch.Status.FAILED);
        batchService.retryItem(TEACHER_ID, item.getBatchId(), item.getId());
        assertThat(freshItem(item.getId()).getStatus()).isEqualTo(ReportBatchItem.Status.PENDING);
        assertThat(outbox.count()).isEqualTo(2);
    }
}
