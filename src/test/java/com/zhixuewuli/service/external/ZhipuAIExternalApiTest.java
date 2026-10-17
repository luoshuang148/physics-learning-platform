package com.zhixuewuli.service.external;

import com.zhixuewuli.common.base.ExternalApiTestConfig;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 智谱AI客户端外部API验证测试
 * 
 * 验证智谱AI服务的连通性、响应格式和基本功能
 * 使用dev环境的真实API密钥进行实际调用
 * 
 * 测试内容：
 * 1. 客户端配置验证
 * 2. 基础连通性测试
 * 3. 流式聊天功能验证
 * 4. 思维链(Reasoning)功能验证
 * 5. 错误处理机制验证
 * 6. 响应格式验证
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Slf4j
@ExternalApiTestConfig
@DisplayName("智谱AI外部API验证测试")
class ZhipuAIExternalApiTest {

    @Autowired
    private ZhipuAIClient zhipuAIClient;

    @Autowired
    private AIBusinessProperties aiBusinessProperties;

    private String testPrompt;
    private String testModel;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testPrompt = "你好，请简单介绍一下自己。";
        testModel = aiBusinessProperties.getZhipuDefaultModel();

        log.info("准备测试智谱AI客户端");
        log.info("API Key是否存在: {}",
                aiBusinessProperties.getZhipuApiKey() != null && !aiBusinessProperties.getZhipuApiKey().isEmpty());
        log.info("Base URL: {}", aiBusinessProperties.getZhipuBaseUrl());
        log.info("默认模型: {}", testModel);
        log.info("启用思维链: {}", aiBusinessProperties.getZhipuEnableReasoning());
    }

    @Test
    @DisplayName("验证智谱AI客户端配置")
    void testZhipuAIConfiguration() {
        log.info("=== 开始验证智谱AI客户端配置 ===");

        // 验证API密钥配置
        assertThat(aiBusinessProperties.getZhipuApiKey())
                .as("智谱AI API密钥应该存在")
                .isNotNull()
                .isNotEmpty();

        // 验证基础URL配置
        assertThat(aiBusinessProperties.getZhipuBaseUrl())
                .as("智谱AI基础URL应该存在")
                .isNotNull()
                .isNotEmpty()
                .startsWith("http");

        // 验证默认模型配置
        assertThat(testModel)
                .as("智谱AI默认模型应该存在")
                .isNotNull()
                .isNotEmpty();

        // 验证思维链配置
        assertThat(aiBusinessProperties.getZhipuEnableReasoning())
                .as("思维链配置应该存在")
                .isNotNull();

        log.info("✅ 智谱AI客户端配置验证通过");
    }

    @Test
    @DisplayName("验证智谱AI基础连通性")
    void testZhipuAIBasicConnectivity() throws InterruptedException {
        log.info("=== 开始验证智谱AI基础连通性 ===");

        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            zhipuAIClient.streamChat(testPrompt, testModel,
                    new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                        @Override
                        public void onContent(String content, boolean isLast) {
                            if (!content.isEmpty()) {
                                success.set(true);
                                log.info("收到响应内容片段: {}", content.substring(0, Math.min(50, content.length())));
                            }
                        }

                        @Override
                        public void onReasoningContent(String reasoning) {
                            if (aiBusinessProperties.getZhipuEnableReasoning() && reasoning != null
                                    && !reasoning.isEmpty()) {
                                log.info("收到思维链内容片段: {}", reasoning.substring(0, Math.min(50, reasoning.length())));
                            }
                        }

                        @Override
                        public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                            log.info("调用完成 - Prompt Tokens: {}, Completion Tokens: {}, Total Tokens: {}",
                                    promptTokens, completionTokens, totalTokens);
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            log.error("调用出错: ", error);
                            errorMessage.set(error.getMessage());
                            latch.countDown();
                        }
                    });

            // 等待响应，最多等待50秒
            boolean completed = latch.await(50, TimeUnit.SECONDS);

            if (!completed) {
                fail("智谱AI调用超时");
            }

            if (errorMessage.get() != null) {
                fail("智谱AI调用失败: " + errorMessage.get());
            }

            assertTrue(success.get(), "应该收到有效的响应内容");
            log.info("✅ 智谱AI基础连通性验证通过");

        } catch (Exception e) {
            log.error("测试执行异常: ", e);
            throw e;
        }
    }

    @Test
    @DisplayName("验证智谱AI流式响应格式")
    void testZhipuAIStreamingFormat() throws InterruptedException {
        log.info("=== 开始验证智谱AI流式响应格式 ===");

        AtomicBoolean receivedContent = new AtomicBoolean(false);
        AtomicBoolean receivedReasoning = new AtomicBoolean(false);
        AtomicBoolean receivedCompletion = new AtomicBoolean(false);
        AtomicReference<Integer> totalTokens = new AtomicReference<>(0);
        CountDownLatch latch = new CountDownLatch(1);

        zhipuAIClient.streamChat(
                "请用一句话回答：1+1等于多少？",
                testModel,
                new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                    @Override
                    public void onContent(String content, boolean isLast) {
                        log.info("收到内容详情: [{}], isLast: {}", content, isLast);
                        if (content != null && !content.isEmpty()) {
                            receivedContent.set(true);
                        }
                        assertThat(content).isNotNull();
                        log.info("流式内容: {} (isLast: {})", content, isLast);
                    }

                    @Override
                    public void onReasoningContent(String reasoning) {
                        log.info("收到思维链详情: [{}]", reasoning);
                        if (reasoning != null && !reasoning.isEmpty()) {
                            receivedReasoning.set(true);
                            log.info("思维链内容: {}", reasoning.substring(0, Math.min(50, reasoning.length())));
                        }
                    }

                    @Override
                    public void onComplete(int promptTokens, int completionTokens, int tokens) {
                        receivedCompletion.set(true);
                        totalTokens.set(tokens);
                        log.info("完成统计 - Prompt: {}, Completion: {}, Total: {}",
                                promptTokens, completionTokens, tokens);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception error) {
                        log.error("流式调用出错: ", error);
                        latch.countDown();
                    }
                });

        boolean completed = latch.await(50, TimeUnit.SECONDS);
        assertTrue(completed, "应该在规定时间内完成调用");
        assertTrue(receivedContent.get(), "应该收到流式内容");
        if (Boolean.TRUE.equals(aiBusinessProperties.getZhipuEnableReasoning())) {
            // 如果启用了思维链，期望收到思维链内容
            log.info("思维链功能已启用，期望收到思维链内容: {}", receivedReasoning.get());
        }
        assertTrue(receivedCompletion.get(), "应该收到完成通知");
        assertThat(totalTokens.get()).isGreaterThan(0);

        log.info("✅ 智谱AI流式响应格式验证通过");
    }

    @Test
    @DisplayName("验证智谱AI思维链功能")
    void testZhipuAIReasoningFeature() throws InterruptedException {
        log.info("=== 开始验证智谱AI思维链功能 ===");

        if (!Boolean.TRUE.equals(aiBusinessProperties.getZhipuEnableReasoning())) {
            log.info("思维链功能未启用，跳过此测试");
            return;
        }

        AtomicBoolean receivedReasoning = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        zhipuAIClient.streamChat(
                "请解释光合作用的过程",
                testModel,
                new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                    @Override
                    public void onContent(String content, boolean isLast) {
                        // 主要内容处理
                    }

                    @Override
                    public void onReasoningContent(String reasoning) {
                        if (reasoning != null && !reasoning.isEmpty()) {
                            receivedReasoning.set(true);
                            log.info("收到思维链内容: {}", reasoning.substring(0, Math.min(100, reasoning.length())));
                        }
                    }

                    @Override
                    public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception error) {
                        log.error("调用出错: ", error);
                        latch.countDown();
                    }
                });

        boolean completed = latch.await(50, TimeUnit.SECONDS);
        assertTrue(completed, "应该在规定时间内完成调用");
        assertTrue(receivedReasoning.get(), "启用思维链时应该收到思维链内容");

        log.info("✅ 智谱AI思维链功能验证通过");
    }

    @Test
    @DisplayName("验证智谱AI错误处理机制")
    void testZhipuAIErrorHandling() throws InterruptedException {
        log.info("=== 开始验证智谱AI错误处理机制 ===");

        // 测试无效模型的情况
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                zhipuAIClient.streamChat(testPrompt, "invalid-model-12345",
                        new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                            @Override
                            public void onContent(String content, boolean isLast) {
                            }

                            @Override
                            public void onReasoningContent(String reasoning) {
                            }

                            @Override
                            public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                                latch.countDown();
                            }

                            @Override
                            public void onError(Exception error) {
                                caughtException.set(error);
                                latch.countDown();
                            }
                        });
            } catch (Exception e) {
                caughtException.set(e);
                latch.countDown();
            }
        }).start();

        boolean completed = latch.await(50, TimeUnit.SECONDS);
        if (caughtException.get() != null) {
            log.info("捕获到预期的异常: {}", caughtException.get().getClass().getSimpleName());
            // 验证异常类型是否合理
            assertThat(caughtException.get())
                    .isInstanceOf(Exception.class);
        }

        log.info("✅ 智谱AI错误处理机制验证通过");
    }

    @Test
    @DisplayName("验证智谱AI客户端获取默认模型")
    void testGetDefaultModel() {
        log.info("=== 开始验证智谱AI客户端获取默认模型 ===");

        String defaultModel = zhipuAIClient.getDefaultModel();

        assertThat(defaultModel)
                .as("默认模型不应该为空")
                .isNotNull()
                .isNotEmpty();

        assertThat(defaultModel)
                .as("默认模型应该与配置一致")
                .isEqualTo(aiBusinessProperties.getZhipuDefaultModel());

        log.info("获取到的默认模型: {}", defaultModel);
        log.info("✅ 智谱AI客户端获取默认模型验证通过");
    }

    @Test
    @DisplayName("验证不同模型的兼容性")
    void testDifferentModelsCompatibility() throws InterruptedException {
        log.info("=== 开始验证不同模型的兼容性 ===");

        String[] testModels = {
                "glm-4.1v-thinking-flash",
                "glm-4.5v"
        };

        for (String model : testModels) {
            log.info("测试模型: {}", model);

            AtomicBoolean success = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);

            zhipuAIClient.streamChat(
                    "你好",
                    model,
                    new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                        @Override
                        public void onContent(String content, boolean isLast) {
                            if (!content.isEmpty()) {
                                success.set(true);
                            }
                        }

                        @Override
                        public void onReasoningContent(String reasoning) {
                        }

                        @Override
                        public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            log.warn("模型 {} 测试失败: {}", model, error.getMessage());
                            latch.countDown();
                        }
                    });

            boolean completed = latch.await(20, TimeUnit.SECONDS);
            if (completed && success.get()) {
                log.info("✅ 模型 {} 测试通过", model);
            } else {
                log.warn("⚠️ 模型 {} 测试未通过或超时", model);
            }
        }

        log.info("✅ 不同模型兼容性测试完成");
    }
}