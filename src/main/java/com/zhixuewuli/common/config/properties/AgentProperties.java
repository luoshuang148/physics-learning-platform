package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.agent")
public class AgentProperties {
    private boolean toolCallingEnabled;
    private String baseUrl = "";
    private String model = "";
    private String apiKey = "";
    private long timeoutSeconds = 30;
}
