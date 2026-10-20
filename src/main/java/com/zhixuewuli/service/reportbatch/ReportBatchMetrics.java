package com.zhixuewuli.service.reportbatch;

import com.zhixuewuli.entity.ReportBatchItem;
import com.zhixuewuli.entity.ReportOutboxMessage;
import com.zhixuewuli.repository.ReportBatchItemRepository;
import com.zhixuewuli.repository.ReportOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量报告的 Micrometer 观测。
 *
 * <p>指标标签只使用固定枚举值，不使用学生/任务 ID 等高基数标签；
 * 待投递量等仪表由调度器周期性刷新缓存值，避免每次抓取都查询数据库。</p>
 */
@Component
public class ReportBatchMetrics {

    private final Counter succeeded;
    private final Counter failed;
    private final Counter skipped;
    private final Counter retryScheduled;
    private final Counter recovered;
    private final Counter staleExecutions;
    private final Counter poisonMessages;
    private final Timer itemDuration;

    private final AtomicLong outboxPendingDue = new AtomicLong();
    private final AtomicLong itemsPending = new AtomicLong();
    private final AtomicLong itemsProcessing = new AtomicLong();

    private final ReportOutboxRepository outboxRepository;
    private final ReportBatchItemRepository itemRepository;
    private final MeterRegistry registry;

    public ReportBatchMetrics(MeterRegistry registry, ReportOutboxRepository outboxRepository,
                              ReportBatchItemRepository itemRepository) {
        this.registry = registry;
        this.outboxRepository = outboxRepository;
        this.itemRepository = itemRepository;
        this.succeeded = counter(registry, "report.batch.items.processed", "outcome", "succeeded");
        this.failed = counter(registry, "report.batch.items.processed", "outcome", "failed");
        this.skipped = counter(registry, "report.batch.items.processed", "outcome", "skipped");
        this.retryScheduled = counter(registry, "report.batch.items.processed", "outcome", "retry_scheduled");
        this.recovered = counter(registry, "report.batch.items.processed", "outcome", "recovered");
        this.staleExecutions = counter(registry, "report.batch.items.processed", "outcome", "stale_execution");
        this.poisonMessages = counter(registry, "report.batch.messages.rejected", "reason", "invalid_payload");
        this.itemDuration = Timer.builder("report.batch.item.duration").register(registry);
        Gauge.builder("report.batch.outbox.pending.due", outboxPendingDue, AtomicLong::get).register(registry);
        Gauge.builder("report.batch.items.pending", itemsPending, AtomicLong::get).register(registry);
        Gauge.builder("report.batch.items.processing", itemsProcessing, AtomicLong::get).register(registry);
    }

    private static Counter counter(MeterRegistry registry, String name, String tagKey, String tagValue) {
        return Counter.builder(name).tag(tagKey, tagValue).register(registry);
    }

    public void itemSucceeded(long millis) {
        succeeded.increment();
        itemDuration.record(Duration.ofMillis(millis));
    }

    public void itemFailed() { failed.increment(); }
    public void itemSkipped() { skipped.increment(); }
    public void retryScheduled() { retryScheduled.increment(); }
    public void itemRecovered() { recovered.increment(); }
    public void staleExecution() { staleExecutions.increment(); }
    public void invalidMessage() { poisonMessages.increment(); }

    /** 投递失败计数，reason 只使用固定枚举（confirm_nack/timeout/error/returned）。 */
    public void publishFailure(String reason) {
        Counter.builder("report.batch.outbox.publish.failures").tag("reason", reason)
                .register(registry).increment();
    }

    /** 由发布器/恢复调度器周期性调用，刷新仪表缓存。 */
    public void refreshGauges(LocalDateTime now) {
        outboxPendingDue.set(outboxRepository.countByStatusAndNextAttemptAtBefore(
                ReportOutboxMessage.Status.PENDING, now));
        itemsPending.set(itemRepository.countByStatus(ReportBatchItem.Status.PENDING));
        itemsProcessing.set(itemRepository.countByStatus(ReportBatchItem.Status.PROCESSING));
    }
}
