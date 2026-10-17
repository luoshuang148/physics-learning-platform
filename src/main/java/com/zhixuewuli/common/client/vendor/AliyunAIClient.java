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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里AI客户端
 * 
 * 统一的阿里AI流式调用客户端，整合学习报告生成和教师分析功能。
 * 支持参数化配置和完整的流式输出处理。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>学生个人学习报告生成</li>
 * <li>教师综合分析报告生成</li>
 * <li>参数化内容生成</li>
 * <li>统一的流式输出接口</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 2.0.0
 * @since 2026
 * @see AbstractStreamingClient 基础客户端
 * @see AIStreamCallback 流式回调接口
 */
@Component
@Slf4j
public class AliyunAIClient extends AbstractStreamingClient {

    @Autowired
    private AIBusinessProperties aiBusinessProperties;

    @Override
    protected String getApiKey() {
        return aiBusinessProperties.getAliyunApiKey();
    }

    @Override
    protected String getBaseUrl() {
        return aiBusinessProperties.getAliyunBaseUrl();
    }

    /**
     * 获取默认模型
     */
    public String getDefaultModel() {
        return aiBusinessProperties.getAliyunDefaultModel();
    }

    /**
     * 流式内容生成接口
     * 
     * 纯粹的API调用工具，接收完整的提示词和模型参数。
     * 所有配置参数均从aiBusinessProperties读取。
     * 
     * @param prompt   完整的提示词
     * @param model    AI模型名称
     * @param callback 流式回调处理器
     */
    public void streamGenerate(String prompt, String model, AIStreamCallback callback) {
        validateConfiguration();
        logStreamStart(model, prompt.length());

        try {
            String requestBody = buildGenerateRequest(prompt, model);
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
     * 流式内容生成接口 - 支持多模态消息（文本+图片混合）
     * 
     * 根据阿里云百炼API规范，多模态消息的content字段应为数组格式：
     * - text类型：{"type": "text", "text": "内容"}
     * - image_url类型：{"type": "image_url", "image_url": {"url": "图片URL或Base64 Data URL"}}
     * 
     * @param messages 多模态消息列表，支持混合文本和图片
     * @param model   AI模型名称
     * @param callback 流式回调处理器
     */
    public void streamGenerateWithMessages(List<Map<String, Object>> messages, String model, AIStreamCallback callback) {
        validateConfiguration();
        logStreamStart(model, messages.toString().length());

        try {
            String requestBody = buildMultimodalGenerateRequest(messages, model);
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
     * 构建生成请求
     * 符合阿里云百炼OpenAI兼容模式规范：
     * - 使用标准的messages格式
     * - 阿里云特有参数（enable_thinking等）放入extra_body
     * - 添加 stream_options 以获取用量
     */
    private String buildGenerateRequest(String prompt, String model) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("stream", true);

        // 添加 stream_options 以在最后一个 chunk 中获取 usage
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        request.put("stream_options", streamOptions);

        // 构建 messages 数组
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        request.put("messages", messages);

        // ===== 阿里云特有参数放入 extra_body =====
        Map<String, Object> extraBody = new HashMap<>();

        // 温度参数
        extraBody.put("temperature",
                aiBusinessProperties.getAliyunTemperature() != null
                        ? aiBusinessProperties.getAliyunTemperature()
                        : aiBusinessProperties.getTemperature());

        // 最大输出token数
        extraBody.put("max_tokens",
                aiBusinessProperties.getAliyunMaxTokens() != null
                        ? aiBusinessProperties.getAliyunMaxTokens()
                        : aiBusinessProperties.getMaxTokens());

        // 只有当 extraBody 非空时才放入请求
        if (!extraBody.isEmpty()) {
            request.put("extra_body", extraBody);
        }

        return objectMapper.writeValueAsString(request);
    }

    /**
     * 构建多模态生成请求
     * 支持文本消息和图片消息的混合格式
     * 
     * 根据阿里云百炼API规范，多模态消息的content字段应为数组格式：
     * - text类型：{"type": "text", "text": "内容"}
     * - image_url类型：{"type": "image_url", "image_url": {"url": "图片URL或Base64 Data URL"}}
     * 
     * @param messages 多模态消息列表
     * @param model   AI模型名称
     * @return 序列化的JSON请求体
     */
    private String buildMultimodalGenerateRequest(List<Map<String, Object>> messages, String model) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("stream", true);

        // 添加 stream_options 以在最后一个 chunk 中获取 usage
        Map<String, Object> streamOptions = new HashMap<>();
        streamOptions.put("include_usage", true);
        request.put("stream_options", streamOptions);

        // 直接使用传入的多模态消息列表
        request.put("messages", messages);

        // ===== 阿里云特有参数放入 extra_body =====
        Map<String, Object> extraBody = new HashMap<>();

        // 温度参数
        extraBody.put("temperature",
                aiBusinessProperties.getAliyunTemperature() != null
                        ? aiBusinessProperties.getAliyunTemperature()
                        : aiBusinessProperties.getTemperature());

        // 最大输出token数
        extraBody.put("max_tokens",
                aiBusinessProperties.getAliyunMaxTokens() != null
                        ? aiBusinessProperties.getAliyunMaxTokens()
                        : aiBusinessProperties.getMaxTokens());

        // 只有当 extraBody 非空时才放入请求
        if (!extraBody.isEmpty()) {
            request.put("extra_body", extraBody);
        }

        return objectMapper.writeValueAsString(request);
    }

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
                // 响应体为空，直接完成
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
            if (callback.isCancelled() || Thread.currentThread().isInterrupted()) {
                log.info("检测到取消信号，终止阿里云流式响应读取流程");
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

                    // 提取普通内容 - 阿里云OpenAI兼容模式使用标准的OpenAI格式[citation:1][citation:3]
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

                    // 提取思考内容（如果未来qwen3.5-plus支持reasoning_content字段）
                    // 目前qwen系列将思考内容直接整合在content中，此功能预留
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

        // 流式传输完成后，提取 token 用量
        extractTokenUsageAndComplete(lastDataNode, callback);
    }

    /**
     * 从流式响应中提取内容
     * 修正：使用标准的OpenAI格式 choices[0].delta.content
     */
    private String extractContentFromStream(JsonNode jsonNode) {
        try {
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull()) {
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
     * 检查是否完成
     */
    private boolean checkIfFinished(JsonNode jsonNode) {
        try {
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode finishReason = firstChoice.get("finish_reason");
                if (finishReason != null && !finishReason.isNull()) {
                    return "stop".equals(finishReason.asText()) || "length".equals(finishReason.asText());
                }
            }
        } catch (Exception e) {
            log.debug("检查完成状态时发生异常", e);
        }
        return false;
    }

    /**
     * 从最后一个数据节点提取token用量并调用onComplete
     * 适配阿里云OpenAI兼容模式的usage字段
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

            // 如果 total_tokens 不存在，手动计算
            if (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0)) {
                totalTokens = promptTokens + completionTokens;
            }

            log.debug("提取到阿里云token用量 - prompt: {}, completion: {}, total: {}",
                    promptTokens, completionTokens, totalTokens);
        }

        callback.onComplete(promptTokens, completionTokens, totalTokens);
    }
}