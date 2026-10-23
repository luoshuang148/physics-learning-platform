package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.learning-assistant")
public class LearningAssistantProperties {
    public enum Mode { WORKFLOW, AGENT }
    private Mode mode = Mode.WORKFLOW;
    private int maxModelRounds = 6;
    private int maxToolCalls = 8;
    private long totalDeadlineSeconds = 60;
    private long modelTimeoutSeconds = 25;
    private long toolTimeoutSeconds = 5;
}
