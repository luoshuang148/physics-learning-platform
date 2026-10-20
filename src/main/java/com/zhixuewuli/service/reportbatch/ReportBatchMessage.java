package com.zhixuewuli.service.reportbatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 队列消息体：只携带任务标识与协议版本。
 *
 * <p>不包含 JWT、密钥、学生信息或完整作答内容；消费端只信任这些标识，
 * 班级、名单与权限一律以数据库中的任务记录为准。</p>
 */
public record ReportBatchMessage(int v, Long batchId, Long itemId, String messageId) {

    public static final int PROTOCOL_VERSION = 1;

    public static ReportBatchMessage of(Long batchId, Long itemId, String messageId) {
        return new ReportBatchMessage(PROTOCOL_VERSION, batchId, itemId, messageId);
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception error) {
            throw new IllegalStateException("报告消息序列化失败", error);
        }
    }

    public static ReportBatchMessage parse(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) throw new InvalidReportBatchMessageException("消息体为空");
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception error) {
            throw new InvalidReportBatchMessageException("消息体不是合法 JSON");
        }
        int version = node.path("v").asInt(-1);
        if (version != PROTOCOL_VERSION) {
            throw new InvalidReportBatchMessageException("不支持的协议版本: " + version);
        }
        Long batchId = positive(node.path("batchId"));
        Long itemId = positive(node.path("itemId"));
        String messageId = node.path("messageId").asText("");
        if (messageId.isBlank() || messageId.length() > 36) {
            throw new InvalidReportBatchMessageException("消息标识不合法");
        }
        return new ReportBatchMessage(version, batchId, itemId, messageId);
    }

    private static Long positive(JsonNode node) {
        long value = node.asLong(-1);
        if (value <= 0) throw new InvalidReportBatchMessageException("任务标识不合法");
        return value;
    }
}
