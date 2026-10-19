package com.zhixuewuli.service.reportbatch;

import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.entity.ReportOutboxMessage;
import com.zhixuewuli.repository.ReportOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 发件箱发布器：周期性认领到期消息并投递。
 *
 * <p>认领使用带租约的条件更新，多实例并发扫描只有一台会取得投递权；
 * 投递在数据库事务之外进行，收到 broker confirm 后才标记 PUBLISHED。
 * 崩溃后租约到期由其他实例接管，接受重复投递窗口（消费端幂等）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportBatchPublisher {

    private final ReportOutboxRepository outbox;
    private final ReportBatchProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ReportBatchMetrics metrics;
    private final ReportBatchService batchService;

    @Scheduled(fixedDelayString = "#{@reportBatchProperties.publishInterval.toMillis()}")
    public void publishDueMessages() {
        if (!properties.isEnabled()) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        metrics.refreshGauges(now);
        for (Long id : outbox.findExhaustedIds(ReportOutboxMessage.Status.PENDING,
                properties.getOutboxMaxAttempts(), now, PageRequest.of(0, properties.getPublishBatchSize()))) {
            transactionTemplate.executeWithoutResult(status -> {
                if (outbox.failExhausted(id, ReportOutboxMessage.Status.PENDING, ReportOutboxMessage.Status.FAILED,
                        properties.getOutboxMaxAttempts(), LocalDateTime.now(ZoneOffset.UTC)) == 1) {
                    batchService.failUndeliverableItem(outbox.findById(id).orElseThrow());
                }
            });
        }
        List<Long> dueIds = outbox.findDueIds(ReportOutboxMessage.Status.PENDING,
                properties.getOutboxMaxAttempts(), now, PageRequest.of(0, properties.getPublishBatchSize()));
        for (Long id : dueIds) {
            publishOne(id, LocalDateTime.now(ZoneOffset.UTC));
        }
    }

    private void publishOne(Long id, LocalDateTime now) {
        String claimId = java.util.UUID.randomUUID().toString();
        LocalDateTime leaseUntil = now.plus(properties.getPublishLease());
        Integer claimed = transactionTemplate.execute(status -> outbox.claim(id, ReportOutboxMessage.Status.PENDING,
                claimId, leaseUntil, now, properties.getOutboxMaxAttempts()));
        if (claimed == null || claimed == 0) return;

        ReportOutboxMessage row = outbox.findById(id).orElse(null);
        if (row == null) return;

        try {
            CorrelationData correlation = new CorrelationData(row.getMessageId());
            Message message = MessageBuilder
                    .withBody(row.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setMessageId(row.getMessageId())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbitTemplate.send(row.getExchangeName(), row.getRoutingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(properties.getPublishConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("broker 未确认消息: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("消息不可路由: " + correlation.getReturned().getReplyText());
            }
            LocalDateTime publishedAt = LocalDateTime.now(ZoneOffset.UTC);
            transactionTemplate.executeWithoutResult(status -> outbox.markPublished(id,
                    ReportOutboxMessage.Status.PENDING, ReportOutboxMessage.Status.PUBLISHED, publishedAt, claimId));
        } catch (Exception error) {
            String reason = error instanceof java.util.concurrent.TimeoutException ? "timeout"
                    : error.getMessage() != null && error.getMessage().contains("未确认") ? "confirm_nack" : "error";
            metrics.publishFailure(reason);
            log.warn("报告消息投递失败，将按退避重试 - outboxId: {}, messageId: {}, reason: {}",
                    id, row.getMessageId(), ReportBatchFailure.summarize(error));
            scheduleOutboxRetry(id, row, error, claimId);
        }
    }

    private void scheduleOutboxRetry(Long id, ReportOutboxMessage row, Exception error, String claimId) {
        Duration delay = ReportBatchFailure.backoff(Math.max(row.getAttempts(), 1),
                properties.getRetryBaseDelay(), properties.getRetryMaxDelay());
        LocalDateTime nextAttempt = LocalDateTime.now(ZoneOffset.UTC).plus(delay);
        transactionTemplate.executeWithoutResult(status -> {
            if (row.getAttempts() >= properties.getOutboxMaxAttempts()) {
                if (outbox.markFailed(id, ReportOutboxMessage.Status.PENDING, ReportOutboxMessage.Status.FAILED,
                        ReportBatchFailure.summarize(error), claimId) == 1) batchService.failUndeliverableItem(row);
                log.error("报告消息投递次数耗尽，需要人工介入 - outboxId: {}, messageId: {}", id, row.getMessageId());
                return;
            }
            outbox.markRetry(id, ReportOutboxMessage.Status.PENDING, nextAttempt,
                    ReportBatchFailure.summarize(error), claimId);
        });
    }
}
