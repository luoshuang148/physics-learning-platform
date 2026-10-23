package com.zhixuewuli.dto.agent;

import java.time.Duration;

public record ToolModelConfig(String baseUrl, String apiKey, String modelName, Duration timeout) {
    public ToolModelConfig {
        if (baseUrl == null || baseUrl.isBlank() || modelName == null || modelName.isBlank())
            throw new IllegalArgumentException("工具模型端点和模型名称不能为空");
        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("工具模型超时须为正数");
        apiKey = apiKey == null ? "" : apiKey;
    }
}
