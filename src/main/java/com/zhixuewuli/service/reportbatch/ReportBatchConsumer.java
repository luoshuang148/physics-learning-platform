package com.zhixuewuli.service.reportbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.entity.ReportBatch;
import com.zhixuewuli.entity.ReportBatchItem;
import com.zhixuewuli.repository.ReportBatchItemRepository;
import com.zhixuewuli.repository.ReportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 批量子任务消费者（至少一次投递 + 手动 ACK）。
 *
 * <p>执行步骤：校验消息与任务标识 → 短事务原子认领（条件更新 + 执行标识）→
 * 事务外调用模型 → 短事务保存报告并提交状态 → 提交后 ACK。
 * 重复投递由认领条件与执行标识兜底；禁止在事务内等待模型、禁止处理前 ACK。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportBatchConsumer {

    private final ObjectMapper objectMapper;
    private final ReportBatchRepository reportBatches;
    private final ReportBatchItemRepository items;
    private final BatchReportGenerator generator;
    private final ReportBatchOutboxService outbox;
    private final ReportBatchProperties properties;
    private final ReportBatchService batchService;
    private final ReportBatchMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @RabbitListener(queues = "${app.report-batch.main-queue:report.batch.item.queue}")
    public void onItemMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        ReportBatchMessage parsed;
        try {
            parsed = ReportBatchMessage.parse(body, objectMapper);
        } catch (InvalidReportBatchMessageException invalid) {
            metrics.invalidMessage();
            log.warn("报告消息不合法，拒绝进入死信队列 - reason: {}", invalid.getMessage());
            channel.basicReject(deliveryTag, false);
            return;
        }
        try {
            process(parsed);
            channel.basicAck(deliveryTag, false);
        } catch (Exception unexpected) {
            // 未预期异常（数据库不可用等）：消息进死信队列；子任务保持 PROCESSING，
            // 由租约恢复调度器保证不会永久卡住、也不会丢失。
            log.error("批量子任务处理出现未预期异常，消息进入死信队列，任务由租约恢复兜底 - batchId: {}, itemId: {}",
                    parsed.batchId(), parsed.itemId(), unexpected);
            channel.basicReject(deliveryTag, false);
        }
    }

    private void process(ReportBatchMessage parsed) {
        ReportBatchItem snapshot = items.findById(parsed.itemId()).orElse(null);
        if (snapshot == null || !snapshot.getBatchId().equals(parsed.batchId())) {
            log.warn("消息引用的子任务不存在，忽略 - itemId: {}", parsed.itemId());
            return;
        }
        if (snapshot.getStatus() == ReportBatchItem.Status.SUCCEEDED
                || snapshot.getStatus() == ReportBatchItem.Status.SKIPPED) {
            log.debug("子任务已完成，重复消息直接忽略 - itemId: {}", parsed.itemId());
            return;
        }
        ReportBatch batch = reportBatches.findById(snapshot.getBatchId()).orElse(null);
        if (batch == null) {
            log.warn("子任务所属批次不存在，忽略 - batchId: {}", parsed.batchId());
            return;
        }

        String executionId = UUID.randomUUID().toString();
        Claim claim = transactionTemplate.execute(status -> {
            LocalDateTime claimNow = LocalDateTime.now(ZoneOffset.UTC);
            int updated = items.claim(parsed.itemId(), ReportBatchItem.Status.PENDING,
                    ReportBatchItem.Status.PROCESSING, executionId, ReportBatchNode.ID,
                    claimNow.plus(properties.getItemLease()), claimNow);
            if (updated == 0) return null;
            reportBatches.markProcessing(batch.getId(), ReportBatch.Status.PENDING,
                    ReportBatch.Status.PROCESSING, claimNow);
            ReportBatchItem fresh = items.findById(parsed.itemId()).orElseThrow();
            return new Claim(executionId, fresh.getAttempts(), fresh.getMaxAttempts());
        });
        if (claim == null) {
            log.info("重复投递或已被其他执行者认领，忽略 - batchId: {}, itemId: {}", parsed.batchId(), parsed.itemId());
            return;
        }

        long startedAt = System.currentTimeMillis();
        try {
            BatchReportGenerator.Generated generated = generator.generate(snapshot.getUserId(), batch.getEventId());
            transactionTemplate.executeWithoutResult(status -> {
                Long reportId = generator.persist(snapshot.getUserId(), batch.getEventId(), batch.getId(), generated);
                int updated = items.markSucceeded(parsed.itemId(), ReportBatchItem.Status.PROCESSING,
                        ReportBatchItem.Status.SUCCEEDED, executionId, reportId,
                        generated.digest(), generated.revision(), LocalDateTime.now(ZoneOffset.UTC));
                if (updated == 0) throw new StaleReportBatchExecutionException("执行标识已被取代，迟到结果被丢弃");
                batchService.finalizeIfComplete(batch.getId(), LocalDateTime.now(ZoneOffset.UTC));
            });
            metrics.itemSucceeded(System.currentTimeMillis() - startedAt);
            log.info("批量子任务生成成功 - batchId: {}, itemId: {}, reportId: {}, 用时: {}ms",
                    batch.getId(), parsed.itemId(), snapshot.getUserId(), System.currentTimeMillis() - startedAt);
        } catch (StaleReportBatchExecutionException stale) {
            metrics.staleExecution();
            log.warn("批量子任务结果被更新的执行者取代，已整体回滚丢弃 - itemId: {}", parsed.itemId());
        } catch (Exception error) {
            handleFailure(batch, snapshot, claim, error);
        }
    }

    private void handleFailure(ReportBatch batch, ReportBatchItem snapshot, Claim claim, Exception error) {
        LocalDateTime failedAt = LocalDateTime.now(ZoneOffset.UTC);
        ReportBatchFailure failure = ReportBatchFailure.classify(error);
        if (isNoRecordFailure(error)) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    int updated = items.markSkipped(snapshot.getId(), ReportBatchItem.Status.PROCESSING,
                            ReportBatchItem.Status.SKIPPED, claim.executionId(), "该任务下无作答记录", failedAt);
                    if (updated == 0) throw new StaleReportBatchExecutionException("执行标识已被取代");
                    batchService.finalizeIfComplete(batch.getId(), failedAt);
                });
                metrics.itemSkipped();
                log.info("批量子任务因无作答记录跳过 - batchId: {}, itemId: {}", batch.getId(), snapshot.getId());
            } catch (StaleReportBatchExecutionException stale) {
                metrics.staleExecution();
            }
            return;
        }

        try {
            if (failure.retryable() && claim.attempts() < claim.maxAttempts()) {
                Duration delay = ReportBatchFailure.backoff(claim.attempts(),
                        properties.getRetryBaseDelay(), properties.getRetryMaxDelay());
                transactionTemplate.executeWithoutResult(status -> {
                    int released = items.releaseForRetry(snapshot.getId(), ReportBatchItem.Status.PROCESSING,
                            ReportBatchItem.Status.PENDING, claim.executionId(), failure.code(), failure.message());
                    if (released == 0) throw new StaleReportBatchExecutionException("执行标识已被取代");
                    outbox.enqueueItem(batch.getId(), snapshot.getId(),
                            LocalDateTime.now(ZoneOffset.UTC).plus(delay));
                });
                metrics.retryScheduled();
                log.warn("批量子任务临时失败，已调度自动重试 - batchId: {}, itemId: {}, code: {}, attempts: {}/{},"
                                + " 下次延迟(秒): {}", batch.getId(), snapshot.getId(), failure.code(),
                        claim.attempts(), claim.maxAttempts(), delay.toSeconds());
            } else {
                transactionTemplate.executeWithoutResult(status -> {
                    int failed = items.markFailed(snapshot.getId(), ReportBatchItem.Status.PROCESSING,
                            ReportBatchItem.Status.FAILED, claim.executionId(), failure.code(),
                            failure.message(), failedAt);
                    if (failed == 0) throw new StaleReportBatchExecutionException("执行标识已被取代");
                    outbox.enqueueDeadLetterRecord(batch.getId(), snapshot.getId(), failure,
                            claim.attempts(), failedAt);
                    batchService.finalizeIfComplete(batch.getId(), failedAt);
                });
                metrics.itemFailed();
                log.warn("批量子任务终态失败 - batchId: {}, itemId: {}, code: {}, retryable: {}, attempts: {}/{}",
                        batch.getId(), snapshot.getId(), failure.code(), failure.retryable(),
                        claim.attempts(), claim.maxAttempts());
            }
        } catch (StaleReportBatchExecutionException stale) {
            metrics.staleExecution();
            log.warn("批量子任务失败结果被更新的执行者取代，已整体回滚丢弃 - itemId: {}", snapshot.getId());
        }
    }

    private boolean isNoRecordFailure(Exception error) {
        return error instanceof BusinessException
                && error.getMessage() != null && error.getMessage().contains("无作答记录");
    }

    private record Claim(String executionId, int attempts, int maxAttempts) {}
}
