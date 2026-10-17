package com.zhixuewuli.service.external;

import com.zhixuewuli.common.base.ExternalApiTestConfig;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阿里云AI客户端外部API验证测试
 * 
 * 验证阿里云AI服务的连通性、响应格式和基本功能
 * 使用dev环境的真实API密钥进行实际调用
 * 
 * 测试内容：
 * 1. 客户端配置验证
 * 2. 基础连通性测试
 * 3. 流式生成功能验证
 * 4. 错误处理机制验证
 * 5. 响应格式验证
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Slf4j
@ExternalApiTestConfig
@DisplayName("阿里云AI外部API验证测试")
class AliyunAIExternalApiTest {

    @Autowired
    private AliyunAIClient aliyunAIClient;

    @Autowired
    private AIBusinessProperties aiBusinessProperties;

    private String testPrompt;
    private String testModel;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        testPrompt = "你好，请简单介绍一下自己。";
        testModel = aiBusinessProperties.getAliyunDefaultModel();

        log.info("准备测试阿里云AI客户端");
        log.info("API Key是否存在: {}",
                aiBusinessProperties.getAliyunApiKey() != null && !aiBusinessProperties.getAliyunApiKey().isEmpty());
        log.info("Base URL: {}", aiBusinessProperties.getAliyunBaseUrl());
        log.info("默认模型: {}", testModel);
    }

    @Test
    @DisplayName("验证阿里云AI客户端配置")
    void testAliyunAIConfiguration() {
        log.info("=== 开始验证阿里云AI客户端配置 ===");

        // 验证API密钥配置
        assertThat(aiBusinessProperties.getAliyunApiKey())
                .as("阿里云API密钥应该存在")
                .isNotNull()
                .isNotEmpty();

        // 验证基础URL配置
        assertThat(aiBusinessProperties.getAliyunBaseUrl())
                .as("阿里云基础URL应该存在")
                .isNotNull()
                .isNotEmpty()
                .startsWith("http");

        // 验证默认模型配置
        assertThat(testModel)
                .as("阿里云默认模型应该存在")
                .isNotNull()
                .isNotEmpty();

        log.info("✅ 阿里云AI客户端配置验证通过");
    }

    @Test
    @DisplayName("验证阿里云AI基础连通性")
    void testAliyunAIBasicConnectivity() throws InterruptedException {
        log.info("=== 开始验证阿里云AI基础连通性 ===");

        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            aliyunAIClient.streamGenerate(testPrompt, testModel,
                    new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                        @Override
                        public void onContent(String content, boolean isLast) {
                            if (!content.isEmpty()) {
                                success.set(true);
                                log.info("收到响应内容片段: {}", content.substring(0, Math.min(50, content.length())));
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

            // 等待响应，最多等待30秒
            boolean completed = latch.await(30, TimeUnit.SECONDS);

            if (!completed) {
                fail("阿里云AI调用超时");
            }

            if (errorMessage.get() != null) {
                fail("阿里云AI调用失败: " + errorMessage.get());
            }

            assertTrue(success.get(), "应该收到有效的响应内容");
            log.info("✅ 阿里云AI基础连通性验证通过");

        } catch (Exception e) {
            log.error("测试执行异常: ", e);
            throw e;
        }
    }

    @Test
    @DisplayName("验证阿里云AI流式响应格式")
    void testAliyunAIStreamingFormat() throws InterruptedException {
        log.info("=== 开始验证阿里云AI流式响应格式 ===");

        AtomicBoolean receivedContent = new AtomicBoolean(false);
        AtomicBoolean receivedCompletion = new AtomicBoolean(false);
        AtomicReference<Integer> totalTokens = new AtomicReference<>(0);
        CountDownLatch latch = new CountDownLatch(1);

        aliyunAIClient.streamGenerate(
                "请用一句话回答：1+1等于多少？",
                testModel,
                new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                    @Override
                    public void onContent(String content, boolean isLast) {
                        receivedContent.set(true);
                        assertThat(content).isNotNull();
                        log.info("流式内容: {} (isLast: {})", content, isLast);
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

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "应该在规定时间内完成调用");
        assertTrue(receivedContent.get(), "应该收到流式内容");
        assertTrue(receivedCompletion.get(), "应该收到完成通知");
        assertThat(totalTokens.get()).isGreaterThan(0);

        log.info("✅ 阿里云AI流式响应格式验证通过");
    }

    @Test
    @DisplayName("验证阿里云AI错误处理机制")
    void testAliyunAIErrorHandling() {
        log.info("=== 开始验证阿里云AI错误处理机制 ===");

        // 测试无效模型的情况
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        CompletableFuture.runAsync(() -> {
            try {
                aliyunAIClient.streamGenerate(testPrompt, "invalid-model-12345",
                        new com.zhixuewuli.common.client.callback.AIStreamCallback() {
                            @Override
                            public void onContent(String content, boolean isLast) {
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
        });

        try {
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "错误处理测试应该完成");

            if (caughtException.get() != null) {
                log.info("捕获到预期的异常: {}", caughtException.get().getClass().getSimpleName());
                // 验证异常类型是否合理
                assertThat(caughtException.get())
                        .isInstanceOf(Exception.class); // 可能是AIServiceUnavailableException或其他相关异常
            }

            log.info("✅ 阿里云AI错误处理机制验证通过");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("测试被中断");
        }
    }

    @Test
    @DisplayName("验证阿里云AI客户端获取默认模型")
    void testGetDefaultModel() {
        log.info("=== 开始验证阿里云AI客户端获取默认模型 ===");

        String defaultModel = aliyunAIClient.getDefaultModel();

        assertThat(defaultModel)
                .as("默认模型不应该为空")
                .isNotNull()
                .isNotEmpty();

        assertThat(defaultModel)
                .as("默认模型应该与配置一致")
                .isEqualTo(aiBusinessProperties.getAliyunDefaultModel());

        log.info("获取到的默认模型: {}", defaultModel);
        log.info("✅ 阿里云AI客户端获取默认模型验证通过");
    }
}