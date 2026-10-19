package com.zhixuewuli.service.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.service.reportbatch.InvalidReportBatchMessageException;
import com.zhixuewuli.service.reportbatch.ReportBatchMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** 消息协议：只接受任务标识 + 协议版本，任何非法载荷直接被拒绝。 */
@Tag("unit")
class ReportBatchMessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsProtocolFields() {
        ReportBatchMessage message = ReportBatchMessage.of(11L, 22L, "msg-1");
        ReportBatchMessage parsed = ReportBatchMessage.parse(message.toJson(mapper), mapper);
        assertThat(parsed.v()).isEqualTo(1);
        assertThat(parsed.batchId()).isEqualTo(11L);
        assertThat(parsed.itemId()).isEqualTo(22L);
        assertThat(parsed.messageId()).isEqualTo("msg-1");
    }

    @Test
    void rejectsBlankAndMalformedPayloads() {
        assertThatThrownBy(() -> ReportBatchMessage.parse("", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
        assertThatThrownBy(() -> ReportBatchMessage.parse("not-json", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
    }

    @Test
    void rejectsUnknownProtocolVersion() {
        String payload = "{\"v\":99,\"batchId\":1,\"itemId\":2,\"messageId\":\"m\"}";
        assertThatThrownBy(() -> ReportBatchMessage.parse(payload, mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class)
                .hasMessageContaining("协议版本");
    }

    @Test
    void rejectsMissingOrNegativeIdentifiers() {
        assertThatThrownBy(() -> ReportBatchMessage.parse(
                "{\"v\":1,\"batchId\":1,\"itemId\":-2,\"messageId\":\"m\"}", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
        assertThatThrownBy(() -> ReportBatchMessage.parse(
                "{\"v\":1,\"batchId\":0,\"itemId\":2,\"messageId\":\"m\"}", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
        assertThatThrownBy(() -> ReportBatchMessage.parse(
                "{\"v\":1,\"batchId\":1,\"itemId\":2,\"messageId\":\"\"}", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
        assertThatThrownBy(() -> ReportBatchMessage.parse(
                "{\"v\":1,\"batchId\":1,\"itemId\":2,\"messageId\":\"" + "x".repeat(37) + "\"}", mapper))
                .isInstanceOf(InvalidReportBatchMessageException.class);
    }

    @Test
    void jsonPayloadContainsNoPersonalData() {
        String json = ReportBatchMessage.of(1L, 2L, "message-1").toJson(mapper);
        assertThat(json).doesNotContain("student", "answer", "jwt", "token", "className");
    }
}
