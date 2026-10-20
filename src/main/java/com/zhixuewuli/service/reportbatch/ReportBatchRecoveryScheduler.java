package com.zhixuewuli.service.reportbatch;

import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.entity.ReportBatchItem;
import com.zhixuewuli.repository.ReportBatchItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 过期执行恢复：PROCESSING 子任务的租约到期后重新投递。
 *
 * <p>避免“消费者崩溃/消息丢失导致永久处理中”；重试次数已耗尽的子任务
 * 收敛为失败并留下死信检查记录。恢复同样使用条件更新，重复扫描不会重复入队。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportBatchRecoveryScheduler {

    private final ReportBatchItemRepository items;
    private final ReportBatchOutboxService outbox;
    private final ReportBatchService batchService;
    private final ReportBatchProperties properties;
    private final ReportBatchMetrics metrics;
    private final TransactionTemplate transactionTemplate;
    private final com.zhixuewuli.repository.ReportOutboxRepository deliveries;

    @Scheduled(fixedDelayString = "#{@reportBatchProperties.recoverInterval.toMillis()}")
    public void recoverExpiredLeases() {
        if (!properties.isEnabled()) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Long> expired = items.findExpiredLeaseIds(ReportBatchItem.Status.PROCESSING, now,
                PageRequest.of(0, properties.getRecoverBatchSize()));
        for (Long id : expired) recoverOne(id, now);
        LocalDateTime cutoff = now.minus(properties.getPendingDeliveryTimeout());
        for (Long id : items.findUnclaimedDeliveryIds(ReportBatchItem.Status.PENDING,
                com.zhixuewuli.entity.ReportOutboxMessage.Status.PENDING,
                com.zhixuewuli.entity.ReportOutboxMessage.Status.FAILED,
                com.zhixuewuli.entity.ReportOutboxMessage.Status.PUBLISHED,
                cutoff, PageRequest.of(0, properties.getRecoverBatchSize()))) {
            transactionTemplate.executeWithoutResult(status -> {
                var item = items.lockById(id).orElse(null);
                if (item == null || item.getStatus() != ReportBatchItem.Status.PENDING) return;
                var latestRows = deliveries.lockLatestForWorkItem(id, PageRequest.of(0, 1));
                var latest = latestRows.isEmpty() ? null : latestRows.getFirst();
                if (latest != null) {
                    if (latest.getStatus() == com.zhixuewuli.entity.ReportOutboxMessage.Status.PENDING) return;
                    if (latest.getStatus() == com.zhixuewuli.entity.ReportOutboxMessage.Status.FAILED) {
                        batchService.failUndeliverableItem(latest);
                        return;
                    }
                    if (latest.getPublishedAt() == null || latest.getPublishedAt().isAfter(cutoff)) return;
                }
                outbox.enqueueItem(item.getBatchId(), id, now);
            });
        }
    }

    private void recoverOne(Long id, LocalDateTime now) {
        ReportBatchItem snapshot = items.findById(id).orElse(null);
        if (snapshot == null || snapshot.getStatus() != ReportBatchItem.Status.PROCESSING
                || snapshot.getLeaseExpiresAt() == null || !snapshot.getLeaseExpiresAt().isBefore(now)) {
            return;
        }
        boolean exhausted = snapshot.getAttempts() >= snapshot.getMaxAttempts();
        try {
            if (exhausted) {
                String message = "执行租约过期且重试次数已耗尽";
                transactionTemplate.executeWithoutResult(status -> {
                    int updated = items.markExhaustedExpiredLease(id, ReportBatchItem.Status.PROCESSING,
                            ReportBatchItem.Status.FAILED, "EXHAUSTED", message, now, snapshot.getMaxAttempts());
                    if (updated == 0) return;
                    outbox.enqueueDeadLetterRecord(snapshot.getBatchId(), id,
                            new ReportBatchFailure("EXHAUSTED", message, false), snapshot.getAttempts(), now);
                    batchService.finalizeIfComplete(snapshot.getBatchId(), now);
                });
                metrics.itemFailed();
                log.warn("过期执行次数耗尽，判定失败 - batchId: {}, itemId: {}, attempts: {}",
                        snapshot.getBatchId(), id, snapshot.getAttempts());
            } else {
                transactionTemplate.executeWithoutResult(status -> {
                    int updated = items.recoverExpiredLease(id, ReportBatchItem.Status.PROCESSING,
                            ReportBatchItem.Status.PENDING, "LEASE_EXPIRED", "执行租约过期，已自动重新调度",
                            now, snapshot.getMaxAttempts());
                    if (updated == 0) return;
                    outbox.enqueueItem(snapshot.getBatchId(), id, now);
                });
                metrics.itemRecovered();
                log.info("过期执行已重新调度 - batchId: {}, itemId: {}, attempts: {}",
                        snapshot.getBatchId(), id, snapshot.getAttempts());
            }
        } catch (StaleReportBatchExecutionException stale) {
            metrics.staleExecution();
        }
    }
}
