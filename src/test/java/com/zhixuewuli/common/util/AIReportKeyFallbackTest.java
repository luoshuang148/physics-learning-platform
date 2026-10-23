package com.zhixuewuli.common.util;

import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.controller.AICapabilityController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AIReportKeyFallbackTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankReportKeyUsesTutoringKeyAndEnablesReports(String reportKey) {
        var properties = new AIBusinessProperties();
        properties.setEnabled(true);
        properties.setAliyunApiKey(reportKey);
        properties.setZhipuApiKey("tutoring-fixture-key");

        assertThat(properties.getAliyunApiKey()).isEqualTo("tutoring-fixture-key");
        assertThat(new AICapabilityController(properties,new com.zhixuewuli.common.metrics.AIMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),new com.zhixuewuli.common.config.properties.KnowledgeProperties()).capabilities().getData())
                .containsEntry("reports", true).containsEntry("workflowReady", false).containsEntry("agentReady", false);
    }

    @Test
    void explicitReportKeyTakesPriority() {
        var properties = new AIBusinessProperties();
        properties.setAliyunApiKey("report-fixture-key");
        properties.setZhipuApiKey("tutoring-fixture-key");

        assertThat(properties.getAliyunApiKey()).isEqualTo("report-fixture-key");
    }

    @Test
    void missingBothKeysKeepsReportsUnavailable() {
        var properties = new AIBusinessProperties();
        properties.setEnabled(true);
        properties.setAliyunApiKey("");
        properties.setZhipuApiKey("");

        assertThat(new AICapabilityController(properties,new com.zhixuewuli.common.metrics.AIMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),new com.zhixuewuli.common.config.properties.KnowledgeProperties()).capabilities().getData())
                .containsEntry("reports", false);
    }
}
