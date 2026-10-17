package com.zhixuewuli.common.client.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixuewuli.common.client.base.AbstractStreamingClient;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.exception.ai.AIResponseParseException;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import com.zhixuewuli.common.exception.ai.AITimeoutException;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 智谱AI客户端
 * 
 * 纯粹的智谱AI流式调用工具，专注于API调用功能。
 * 接收完整的提示词和模型参数，不处理业务逻辑。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>流式API调用</li>
 * <li>统一的回调处理</li>
 * <li>基础参数配置</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AbstractStreamingClient 基础客户端
 * @see AIStreamCallback 流式回调接口
 */
@Component
@Slf4j
public class ZhipuAIClient extends AbstractStreamingClient {

    @Autowired
    private AIBusinessProperties aiBusinessProperties;

    @Override
    protected boolean isEnabled() { return aiBusinessProperties.isEnabled(); }

    @Override
    protected String getApiKey() {
        return aiBusinessProperties.getZhipuApiKey();
    }

    @Override
    protected String getBaseUrl() {
        return aiBusinessProperties.getZhipuBaseUrl();
    }

    /**
     * 获取默认模型
     */
    public String getDefaultModel() {
        return aiBusinessProperties.getZhipuDefaultModel();
    }

    /**
     * 流式聊天接口
     * 
     * 纯粹的API调用工具，接收完整的提示词和模型参数。
     * 
     * @param fullPrompt 完整的提示词（包含可能的上下文）
     * @param model      AI模型名称
     * @param callback   流式回调处理器
     */
    public void streamChat(String fullPrompt, String model, AIStreamCallback callback) {
        validateConfiguration();
        logStreamStart(model, fullPrompt.length());

        try {
            String requestBody = buildChatRequest(fullPrompt, model);
            executeStreamingRequest(requestBody, callback, model);
        } catch (SocketTimeoutException e) {
            logStreamError(model, e);
            callback.onError(new AITimeoutException("AI响应超时: " + e.getMessage()));
        } catch (IOException e) {
            logStreamError(model, e);
            callback.onError(new AIServiceUnavailableException("AI服务不可用: " + e.getMessage(), e));
        } catch (Exception e) {
            logStreamError(model, e);
            callback.onError(new AIServiceUnavailableException("AI客户端异常: " + e.getMessage(), e));
        }
    }

    /**
     * 流式聊天接口 - 支持多模态消息（文本+图片混合）
     * 
     * @param messages 多模态消息列表，支持混合文本和图片
     * @param model    AI模型名称
     * @param callback 流式回调处理器
     */
    public void streamChatWithMessages(List<Map<String, Object>> messages, String model, AIStreamCallback callback) {
        validateConfiguration();
        logStreamStart(model, messages.toString().length());

        try {
            String requestBody = buildMultimodalChatRequest(messages, model);
            executeStreamingRequest(requestBody, callback, model);
        } catch (SocketTimeoutException e) {
            logStreamError(model, e);
            callback.onError(new AITimeoutException("AI响应超时: " + e.getMessage()));
        } catch (IOException e) {
            logStreamError(model, e);
            callback.onError(new AIServiceUnavailableException("AI服务不可用: " + e.getMessage(), e));
        } catch (Exception e) {
            logStreamError(model, e);
            callback.onError(new AIServiceUnavailableException("AI客户端异常: " + e.getMessage(), e));
        }
    }

    /**
     * 构建聊天请求
     * 符合智谱 AI 2026年规范：
     * - thinking 参数放入 extra_body 中
     * - 添加 stream_options 以获取用量
     */
    private String buildChatRequest(String fullPrompt, String model) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("stream", true);

        // 标准参数放在根层级
        request.put("temperature",
                aiBusinessProperties.getZhipuTemperature() != null
                        ? aiBusinessProperties.getZhipuTemperature()
                        : aiBusinessProperties.getTemperature());
        request.put("max_tokens",
                aiBusinessProperties.getZhipuMaxTokens() != null
                        ? aiBusinessProperties.getZhipuMaxTokens()
                        : aiBusinessProperties.getMaxTokens());

        // 构建 messages (使用 List)
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", fullPrompt);
        messages.add(userMessage);
        request.put("messages", messages);

        // 根据模型类型动态设置思考参数
        Boolean enableReasoning = aiBusinessProperties.getZhipuEnableReasoning();
        Map<String, Object> extraBody = new HashMap<>();

        if (model != null) {
            if (model.contains("glm-4.5")) {
                // glm-4.5v 系列使用 enable_thinking 布尔值
                extraBody.put("enable_thinking", enableReasoning);
            } else if (model.contains("glm-4.1v-thinking")) {
                // glm-4.1v-thinking 系列默认内置思考，无需额外参数
                log.debug("模型 {} 默认开启思考模式，无需额外参数", model);
            } else {
                // 其他模型使用 thinking 对象
                Map<String, String> thinking = new HashMap<>();
                thinking.put("type", enableReasoning ? "enabled" : "disabled");
                extraBody.put("thinking", thinking);
            }
        } else {
            // model 为空，默认使用 thinking 参数
            Map<String, String> thinking = new HashMap<>();
            thinking.put("type", enableReasoning ? "enabled" : "disabled");
            extraBody.put("thinking", thinking);
        }

        // 只有当 extraBody 非空时才放入请求
        if (!extraBody.isEmpty()) {
            request.put("extra_body", extraBody);
        }

        // 添加 stream_options 以获取 token 用量
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        request.put("stream_options", streamOptions);

        return objectMapper.writeValueAsString(request);
    }

    /**
     * 构建多模态聊天请求
     * 支持文本消息和图片消息的混合格式
     */
    private String buildMultimodalChatRequest(List<Map<String, Object>> messages, String model) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("stream", true);

        request.put("temperature",
                aiBusinessProperties.getZhipuTemperature() != null
                        ? aiBusinessProperties.getZhipuTemperature()
                        : aiBusinessProperties.getTemperature());
        request.put("max_tokens",
                aiBusinessProperties.getZhipuMaxTokens() != null
                        ? aiBusinessProperties.getZhipuMaxTokens()
                        : aiBusinessProperties.getMaxTokens());

        request.put("messages", messages);

        Boolean enableReasoning = aiBusinessProperties.getZhipuEnableReasoning();
        Map<String, Object> extraBody = new HashMap<>();

        if (model != null) {
            if (model.contains("glm-4.5")) {
                extraBody.put("enable_thinking", enableReasoning);
            } else if (model.contains("glm-4.1v-thinking")) {
                log.debug("模型 {} 默认开启思考模式", model);
            } else {
                Map<String, String> thinking = new HashMap<>();
                thinking.put("type", enableReasoning ? "enabled" : "disabled");
                extraBody.put("thinking", thinking);
            }
        } else {
            Map<String, String> thinking = new HashMap<>();
            thinking.put("type", enableReasoning ? "enabled" : "disabled");
            extraBody.put("thinking", thinking);
        }

        if (!extraBody.isEmpty()) {
            request.put("extra_body", extraBody);
        }

        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        request.put("stream_options", streamOptions);

        return objectMapper.writeValueAsString(request);
    }

    /**
     * 执行流式请求
     */
    private void executeStreamingRequest(String requestBody, AIStreamCallback callback, String model)
            throws IOException {
        long startTime = System.currentTimeMillis();

        Request request = new Request.Builder()
                .url(getBaseUrl() + "/chat/completions")
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();

        okhttp3.Call call = httpClient.newCall(request);
        callback.onCancel(call::cancel);
        if (callback.isCancelled()) { call.cancel(); return; }
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String errorMsg = "AI服务返回错误状态: " + response.code();
                if (response.code() == 401) {
                    errorMsg += " (认证失败，请检查API密钥配置)";
                } else if (response.code() >= 500) {
                    errorMsg += " (服务器内部错误)";
                } else if (response.code() == 429) {
                    errorMsg += " (请求频率过高，请稍后重试)";
                }
                throw new AIServiceUnavailableException(errorMsg);
            }

            ResponseBody body = response.body();
            if (body != null) {
                int bufferSize = aiStreamingProperties.getBufferSize() != null
                        ? aiStreamingProperties.getBufferSize()
                        : 8192;
                try (BufferedReader reader = new BufferedReader(body.charStream(), bufferSize)) {
                    processStreamingResponse(reader, callback);
                }
            } else {
                // 响应体为空，直接完成（无内容输出）
                callback.onComplete(0, 0, 0);
            }

            long duration = System.currentTimeMillis() - startTime;
            logStreamComplete(model, duration);
        } catch (IOException error) {
            if (callback.isCancelled()) callback.onComplete(0, 0, 0);
            else throw error;
        }
    }

    /**
     * 处理流式响应，从 BufferedReader 中逐行读取
     * 增强版：添加回调异常捕获机制，防止客户端挂起
     */
    private void processStreamingResponse(BufferedReader reader, AIStreamCallback callback) throws IOException {
        String line;
        JsonNode lastDataNode = null;
        boolean cancelled = false;

        while ((line = reader.readLine()) != null) {
            // 实时检查取消信号，提前中断上游读取
            if (callback.isCancelled() || Thread.currentThread().isInterrupted()) {
                log.info("检测到取消信号，终止流式响应读取流程");
                cancelled = true;
                break;
            }

            line = line.trim();

            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();

                if ("[DONE]".equals(data)) {
                    log.debug("接收到流式传输结束标记");
                    break;
                }

                try {
                    JsonNode jsonNode = objectMapper.readTree(data);
                    lastDataNode = jsonNode;

                    // 提取深度思考内容（如果存在）
                    String reasoning = extractReasoningFromStream(jsonNode);
                    if (reasoning != null && !reasoning.isEmpty()) {
                        try {
                            callback.onReasoningContent(reasoning);
                        } catch (Exception e) {
                            log.error("处理reasoning内容时发生异常", e);
                            callback.onError(e);
                            return;
                        }
                    }

                    // 提取普通内容
                    String content = extractContentFromStream(jsonNode);
                    if (content != null && !content.isEmpty()) {
                        boolean isFinished = checkIfFinished(jsonNode);
                        try {
                            callback.onContent(content, isFinished);
                        } catch (Exception e) {
                            log.error("处理content内容时发生异常", e);
                            callback.onError(e);
                            return;
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析流式数据失败: {}", data.length() > 100 ? data.substring(0, 100) + "..." : data, e);
                    callback.onError(new AIResponseParseException("AI响应数据解析失败: " + e.getMessage(), e));
                    return;
                }
            }
        }

        if (cancelled) {
            callback.onComplete(0, 0, 0);
            return;
        }

        extractTokenUsageAndComplete(lastDataNode, callback);
    }

    /**
     * 从流式响应中提取内容
     */
    private String extractContentFromStream(JsonNode jsonNode) {
        try {
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取内容时发生异常", e);
        }
        return null;
    }

    /**
     * 从流式响应中提取深度思考内容
     * 根据智谱API文档，深度思考内容在delta.reasoning_content字段中[citation:1]
     */
    private String extractReasoningFromStream(JsonNode jsonNode) {
        try {
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.get("delta");
                if (delta != null && delta.has("reasoning_content")) {
                    return delta.get("reasoning_content").asText();
                }
            }
        } catch (Exception e) {
            log.debug("提取reasoning内容时发生异常", e);
        }
        return null;
    }

    /**
     * 检查是否完成
     */
    private boolean checkIfFinished(JsonNode jsonNode) {
        try {
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode finishReasonNode = firstChoice.get("finish_reason");
                if (finishReasonNode != null) {
                    String finishReason = finishReasonNode.asText();
                    return "stop".equals(finishReason) || "length".equals(finishReason);
                }
            }
        } catch (Exception e) {
            log.debug("检查完成状态时发生异常", e);
        }
        return false;
    }

    /**
     * 从最后一个数据节点提取token用量并调用onComplete
     */
    private void extractTokenUsageAndComplete(JsonNode lastDataNode, AIStreamCallback callback) {
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;

        if (lastDataNode != null && lastDataNode.has("usage")) {
            JsonNode usage = lastDataNode.get("usage");
            promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            totalTokens = usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0;

            log.debug("提取到token用量 - prompt: {}, completion: {}, total: {}",
                    promptTokens, completionTokens, totalTokens);
        }

        callback.onComplete(promptTokens, completionTokens, totalTokens);
    }
}