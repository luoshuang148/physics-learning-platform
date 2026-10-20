package com.zhixuewuli.service.reportbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import com.zhixuewuli.entity.ReportOutboxMessage;
import com.zhixuewuli.repository.ReportOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 发件箱写入：与业务状态变更处于同一个数据库事务。
 *
 * <p>绝不允许“先提交业务、再单独发送消息并只记日志”的路径。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportBatchOutboxService {

    private final ReportOutboxRepository outbox;
    private final ReportBatchProperties properties;
    private final ObjectMapper objectMapper;

    /** 入队一条子任务消息，返回消息标识；调用方必须处于已开启的事务中。 */
    public String enqueueItem(Long batchId, Long itemId, LocalDateTime nextAttemptAt) {
        String messageId = UUID.randomUUID().toString();
        String payload = ReportBatchMessage.of(batchId, itemId, messageId).toJson(objectMapper);
        save(messageId, properties.getMainExchange(), properties.getMainRoutingKey(), payload, nextAttemptAt, itemId);
        return messageId;
    }

    /**
     * 终态失败检查记录投递到死信交换机：包含任务标识、错误分类、执行次数与最后失败时间，
     * 供运维在 DLQ 中排查；不携带学生信息与模型响应全文。
     */
    public String enqueueDeadLetterRecord(Long batchId, Long itemId, ReportBatchFailure failure,
                                          int attempts, LocalDateTime lastFailedAt) {
        String messageId = UUID.randomUUID().toString();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("v", ReportBatchMessage.PROTOCOL_VERSION);
        record.put("kind", "REPORT_ITEM_DEAD");
        record.put("batchId", batchId);
        record.put("itemId", itemId);
        record.put("messageId", messageId);
        record.put("errorCode", failure.code());
        record.put("errorClass", failure.retryable() ? "RETRYABLE_EXHAUSTED" : "PERMANENT");
        record.put("attempts", attempts);
        record.put("lastFailedAt", lastFailedAt.toString());
        record.put("errorMessage", failure.message());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(record);
        } catch (Exception error) {
            throw new IllegalStateException("死信检查记录序列化失败", error);
        }
        save(messageId, properties.getDeadLetterExchange(), properties.getDeadLetterRoutingKey(), payload,
                lastFailedAt, null);
        return messageId;
    }

    private void save(String messageId, String exchange, String routingKey, String payload,
                      LocalDateTime nextAttemptAt, Long workItemId) {
        ReportOutboxMessage message = new ReportOutboxMessage();
        message.setMessageId(messageId);
        message.setWorkItemId(workItemId);
        message.setExchangeName(exchange);
        message.setRoutingKey(routingKey);
        message.setPayload(payload);
        message.setStatus(ReportOutboxMessage.Status.PENDING);
        message.setNextAttemptAt(nextAttemptAt);
        outbox.save(message);
    }
}
