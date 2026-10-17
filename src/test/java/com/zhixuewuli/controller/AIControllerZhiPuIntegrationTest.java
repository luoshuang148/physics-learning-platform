package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.common.test.SseTestUtils;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.entity.ConversationHistory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import java.util.concurrent.Executor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 控制器智谱 AI 模块集成测试
 * 
 * <p>
 * 测试智谱 AI 相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>实时问答流式生成 - 验证SSE流式响应和 AI 集成（不需要questionId）</li>
 * <li>错题分析流式生成 - 验证SSE 流式响应和 AI 集成（需要questionId 且题目必须存在）</li>
 * <li>获取会话历史记录 - 验证会话管理功能</li>
 * <li>清除会话 - 验证会话生命周期管理</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-question-error-analysis-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景和权限控制场景</li>
 * <li>验证HTTP状态码、响应结构和业务逻辑</li>
 * <li>使用 Mock 对象模拟外部 AI 服务调用</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("AI 控制器智谱 AI 模块集成测试")
@Tag("integration-controller")
@Sql(scripts = {
                "/sql/scenario-question-error-analysis-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class AIControllerZhiPuIntegrationTest extends BaseControllerIntegrationTest {

        @LocalServerPort
        private int localServerPort;

        @TestConfiguration
        static class TestConfig {
                @Bean
                @Primary
                public TestExecutor aiTaskExecutor() {
                        return new TestExecutor();
                }
        }

        static class TestExecutor implements Executor {
                volatile boolean asynchronous;
                @Override public void execute(Runnable task) {
                        if (asynchronous) new Thread(task, "test-ai-worker").start();
                        else task.run();
                }
        }

        @Autowired private TestExecutor testExecutor;

        @org.junit.jupiter.api.AfterEach
        void resetExecutor() { testExecutor.asynchronous = false; }

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private AIStreamingProperties aiStreamingProperties;

        @Autowired
        private ConversationHistoryRepository conversationHistoryRepository;

        // ==================== AI 服务 Mock ====================

        /**
         * Mock 智谱 AI客户端 - 核心外部依赖
         */
        @MockBean
        private ZhipuAIClient zhipuAIClient;

        // 注意：AliyunAIReportService 真实注入，但不会被调用（测试范围分离）

        // ==================== 测试数据 ====================

        private Long studentId;
        private String studentUsername;
        private Long teacherId;
        private String teacherUsername;
        private Long questionIdWithErrors;
        private Long questionIdWithoutErrors;
        private String validSessionId;
        private String studentSessionId;

        @BeforeEach
        void setUp() {
                // 初始化测试用户信息（从 SQL 脚本）
                studentId = 5002L; // student_error_01
                studentUsername = "student_error_01";
                teacherId = 5001L; // teacher_error_analysis
                teacherUsername = "teacher_error_analysis";

                // 初始化测试题目ID
                questionIdWithErrors = 6001L; // 有错误记录的题目
                questionIdWithoutErrors = 6003L; // 无错误记录的题目

                // 初始化会话 ID(符合规范：14 位时间戳 + 下划线 +16 位 UUID 十六进制)
                validSessionId = "20260315000001_a1b2c3d4e5f67890";
                // 为学生创建专属会话 ID(用于会话管理测试)
                studentSessionId = "20260315000002_b2c3d4e5f6a78901";
        }

        // ============================================================
        // POST /ai/student/realtimeQA 测试 - 实时问答流式生成
        // ============================================================

        @DisplayName("实时问答流式生成成功 - 有题目ID")
        @Test
        void testStreamRealtimeQA_WithQuestionId_Success() throws Exception {
                // Given: 配置 AI客户端 Mock
                CountDownLatch latch = new CountDownLatch(1);
                setupZhipuAIClientMockForQA(latch);

                // 准备请求数据
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("请问牛顿第一定律是什么？");
                request.setQuestionId(questionIdWithErrors);

                // When: 发送实时问答请求
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andDo(print())
                                .andReturn();

                // Then: 等待异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("应该在超时前完成").isTrue();

                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(1000);

                // 验证异步响应
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                // 验证SSE 格式
                SseTestUtils.assertValidSseFormat(finalResult);

                // 验证 Mock 被调用
                verify(zhipuAIClient, org.mockito.Mockito.times(1)).streamChatWithMessages(any(), anyString(),
                                any(AIStreamCallback.class));
        }

        @DisplayName("实时问答流式生成成功 - 无题目ID 场景")
        @Test
        void testStreamRealtimeQA_WithoutQuestionId_Success() throws Exception {
                // Given: 配置 AI客户端 Mock
                CountDownLatch latch = new CountDownLatch(1);
                setupZhipuAIClientMockForQA(latch);

                // 准备请求数据 - 不设置 questionId
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("请问什么是能量守恒定律？");

                // When: 发送实时问答请求
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andDo(print())
                                .andReturn();

                // Then: 等待异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("应该在超时前完成").isTrue();

                // 验证异步响应
                mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

                // 验证 Mock 被调用
                verify(zhipuAIClient, org.mockito.Mockito.times(1)).streamChatWithMessages(any(), anyString(),
                                any(AIStreamCallback.class));
        }

        @DisplayName("实时问答流式生成失败 - 问题内容为空")
        @Test
        void testStreamRealtimeQA_EmptyQuestion() throws Exception {
                // Given: 准备空问题请求
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("");

                // When: 发送请求
                var result = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数校验失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("实时问答流式生成失败 - 未认证")
        @Test
        void testStreamRealtimeQA_Unauthorized() throws Exception {
                // Given: 准备请求
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试问题");

                // When: 无认证访问
                var result = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // POST /ai/student/wrong-question-analysis 测试 - 错题分析流式生成
        // ============================================================

        @DisplayName("错题分析流式生成成功 - 有错误记录的题目")
        @Test
        void testStreamWrongQuestionAnalysis_Success() throws Exception {
                // Given: 配置 AI客户端 Mock
                CountDownLatch latch = new CountDownLatch(1);
                setupZhipuAIClientMockForWrongQuestionAnalysis(latch);

                // 准备请求数据
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("这道题我为什么错了？");
                request.setQuestionId(questionIdWithErrors); // 6001，有错误记录

                // When: 发送错题分析请求
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/wrong-question-analysis")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andDo(print())
                                .andReturn();

                // Then: 等待异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("应该在超时前完成").isTrue();

                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(1000);

                // 验证异步响应
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                // 验证SSE 格式
                SseTestUtils.assertValidSseFormat(finalResult);

                // 验证 Mock 被调用
                verify(zhipuAIClient, org.mockito.Mockito.times(1)).streamChatWithMessages(any(), anyString(),
                                any(AIStreamCallback.class));
        }

        @DisplayName("错题分析流式生成失败 - 缺少题目ID")
        @Test
        void testStreamWrongQuestionAnalysis_MissingQuestionId() throws Exception {
                // Given: 准备请求 - 不设置 questionId
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("这道题为什么错了？");

                // When: 发送请求
                var result = mockMvc.perform(post("/ai/student/wrong-question-analysis")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数校验失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("错题分析流式生成失败 - 题目不存在")
        @Test
        void testStreamWrongQuestionAnalysis_QuestionNotFound() throws Exception {
                // Given: 配置 AI客户端 Mock（实际上不会调用，因为题目校验会先失败）
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("这道题为什么错了？");
                request.setQuestionId(99999L); // 不存在的题目ID

                // When: 发送请求
                var result = mockMvc.perform(post("/ai/student/wrong-question-analysis")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证题目不存在错误（在 SSE 连接建立前就失败）
                result.andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());

                // 验证AI客户端未被调用（校验失败直接返回）
                verify(zhipuAIClient, times(0)).streamChatWithMessages(any(), anyString(),
                                any(AIStreamCallback.class));
        }

        @DisplayName("错题分析流式生成失败 - 未认证")
        @Test
        void testStreamWrongQuestionAnalysis_Unauthorized() throws Exception {
                // Given: 准备请求
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("错题分析");
                request.setQuestionId(questionIdWithErrors);

                // When: 无认证访问
                var result = mockMvc.perform(post("/ai/student/wrong-question-analysis")
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // GET /ai/session/history 测试 - 获取会话历史记录
        // ============================================================

        @DisplayName("获取会话历史记录 - 成功")
        @Test
        void testGetSessionHistory_Success() throws Exception {
                // Given: 数据库中有该学生的会话历史记录
                // 注意：智谱 AI 的会话管理是学生功能，不是教师功能
                // SQL 中没有预置学生的会话记录，需要先创建一条
                        
                // 验证数据库中确实存在该会话记录
                long historyCount = conversationHistoryRepository.countBySessionIdAndUserId(studentSessionId, studentId);
                System.out.println("=== 会话 " + studentSessionId + " 在学生 " + studentId + " 名下的历史记录数量：" + historyCount);
                assertTrue(historyCount > 0, "数据库中应该存在该会话的历史记录");
        
                // When: 发送 GET 请求到/ai/session/history
                var result = mockMvc.perform(get("/ai/session/history")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", studentSessionId))
                                .andDo(print());
        
                // Then: 验证结果 - 由于没有预置会话记录，应该返回空数组但状态码为 200
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data").isArray());
                                // 注：不验证数组长度，因为可能为空（取决于是否有历史会话）
        }

        @DisplayName("获取会话历史记录 - 无权访问他人会话")
        @Test
        void testGetSessionHistory_Forbidden_AccessOtherUserSession() throws Exception {
                // Given: 学生 A 尝试访问学生 B 的会话
                // 注意：这里测试的是学生之间的会话隔离，不是教师和学生之间
                Long otherStudentId = 5003L; // student_error_02
                String otherStudentSession = "20260315000003_c3d4e5f6a7b89012";
                
                // When: 学生 A 发送请求
                var result = mockMvc.perform(get("/ai/session/history")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", otherStudentSession))
                                .andDo(print());

                // Then: 验证权限不足 - 无权访问他人的会话
                result.andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("获取会话历史记录失败 - 未认证")
        @Test
        void testGetSessionHistory_Unauthorized() throws Exception {
                // When: 无认证访问
                var result = mockMvc.perform(get("/ai/session/history")
                                .header("X-Session-Id", validSessionId))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // DELETE /ai/session 测试 - 清除会话
        // ============================================================

        @DisplayName("清除会话成功")
        @Test
        void testClearSession_Success() throws Exception {
                // Given: 学生用户有自己的会话记录
                // 注意：智谱 AI 的会话管理是学生功能
                
                // 验证数据库中确实存在该会话记录
                long historyCount = conversationHistoryRepository.countBySessionIdAndUserId(studentSessionId, studentId);
                System.out.println("=== 清除会话前，会话 " + studentSessionId + " 在学生 " + studentId + " 名下的历史记录数量：" + historyCount);
                assertTrue(historyCount > 0, "数据库中应该存在该会话的历史记录");
                
                // When: 发送 DELETE 请求到/ai/session
                var result = mockMvc.perform(delete("/ai/session")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", studentSessionId))
                                .andDo(print());

                // Then: 验证结果 - 即使没有会话记录，清除操作也应该成功（幂等性）
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
                // 注：由于事务回滚，无法验证数据库中会话是否被删除
        }

        @DisplayName("清除会话失败 - 无权清除他人会话")
        @Test
        void testClearSession_Forbidden_ClearOtherUserSession() throws Exception {
                // Given: 学生 A 尝试清除学生 B 的会话
                Long otherStudentId = 5003L; // student_error_02
                String otherStudentSession = "20260315000003_c3d4e5f6a7b89012";
                
                // When: 学生 A 发送清除请求
                var result = mockMvc.perform(delete("/ai/session")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", otherStudentSession))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("清除会话失败 - 未认证")
        @Test
        void testClearSession_Unauthorized() throws Exception {
                // When: 无认证访问
                var result = mockMvc.perform(delete("/ai/session")
                                .header("X-Session-Id", validSessionId))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // SSE流式响应专项测试
        // ============================================================

        @DisplayName("实时问答 - SSE 事件格式验证")
        @Test
        void testStreamRealtimeQA_SseEventFormat() throws Exception {
                // Given
                CountDownLatch latch = new CountDownLatch(1);
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                callback.onContent("第一部分回答...", false);
                                callback.onContent("最后一部分回答...", true);
                                callback.onComplete(50, 100, 150);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                // When
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试问题");

                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                // Then: 等待完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).as("应该在超时前完成").isTrue();

                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(1000);

                // 验证异步响应
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                // 验证SSE 格式
                SseTestUtils.assertValidSseFormat(finalResult);

                // 验证事件数量
                String responseContent = finalResult.getResponse().getContentAsString();
                List<String> events = SseTestUtils.extractSseEvents(responseContent);
                assertThat(events).isNotEmpty();
        }

        @DisplayName("错题分析 - 多分片响应")
        @Test
        void testStreamWrongQuestionAnalysis_MultiChunkResponse() throws Exception {
                // Given
                CountDownLatch latch = new CountDownLatch(1);
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                callback.onContent("错误原因分析...", false);
                                callback.onContent("思路引导...", false);
                                callback.onContent("重难点突破...", false);
                                callback.onContent("学习建议...", true);
                                callback.onComplete(80, 120, 200);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                // When
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("这道题为什么错了？");
                request.setQuestionId(questionIdWithErrors);

                MvcResult mvcResult = mockMvc.perform(post("/ai/student/wrong-question-analysis")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                // Then: 等待完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).as("应该在超时前完成").isTrue();

                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(1000);

                // 验证异步响应
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                // 验证有多个分片
                String responseContent = finalResult.getResponse().getContentAsString();
                List<String> events = SseTestUtils.extractSseEvents(responseContent);
                assertThat(events).hasSizeGreaterThan(1);
        }

        @DisplayName("实时问答 - 思维链内容测试（如果启用）")
        @Test
        void testStreamRealtimeQA_WithReasoning() throws Exception {
                // Given: 配置 AI 客户端 Mock 支持思维链
                CountDownLatch latch = new CountDownLatch(1);
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                // 模拟思维链内容
                                callback.onReasoningContent("首先分析题目考查的知识点...");
                                callback.onContent("牛顿第一定律是...", false);
                                callback.onReasoningContent("然后组织答案结构...");
                                callback.onContent("具体内容如下...", true);
                                callback.onComplete(60, 120, 180);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        
                // When
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("请详细解释牛顿第一定律");
        
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();
        
                // Then: 等待完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).as("应该在超时前完成").isTrue();
        
                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(1000);
        
                // 验证异步响应
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();
        
                // 验证 SSE 格式
                SseTestUtils.assertValidSseFormat(finalResult);
        
                // 验证 Mock 被调用
                verify(zhipuAIClient, org.mockito.Mockito.times(1)).streamChatWithMessages(any(), anyString(),
                                any(AIStreamCallback.class));
        }

        @DisplayName("实时问答 - AI异常时应返回SSE error事件")
        @Test
        void testStreamRealtimeQA_WhenAiThrows_ShouldReturnErrorEvent() throws Exception {
                // Given
                CountDownLatch latch = new CountDownLatch(1);
                doAnswer(invocation -> {
                        try {
                                throw new RuntimeException("mock-zhipu-failure");
                        } finally {
                                latch.countDown();
                        }
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试异常处理");

                // When
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                // Then
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).as("应在超时前完成").isTrue();

                Thread.sleep(600);

                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                String responseContent = finalResult.getResponse().getContentAsString();
                assertContainsEventName(responseContent, "error");
        }

        @DisplayName("实时问答 - 仅onComplete收尾时应返回complete事件")
        @Test
        void testStreamRealtimeQA_UsageOnlyTail_ShouldReturnCompleteEvent() throws Exception {
                // Given
                CountDownLatch latch = new CountDownLatch(1);
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                // 模拟供应商最后一帧只有usage统计
                                callback.onComplete(11, 22, 33);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试仅complete收尾");

                // When
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", validSessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                // Then
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).as("应在超时前完成").isTrue();

                Thread.sleep(600);

                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();

                String responseContent = finalResult.getResponse().getContentAsString();
                assertContainsEventName(responseContent, "complete");
                assertThat(responseContent).doesNotContain("event:error");
        }

        @DisplayName("实时问答 - HTTP 客户端中途断连应触发服务端取消感知")
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Sql(scripts = { "/sql/cleanup-all.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void testStreamRealtimeQA_HttpClientDisconnect_ShouldBeCancelledServerSide() throws Exception {
                testExecutor.asynchronous = true;
                // Given
                CountDownLatch firstChunkSent = new CountDownLatch(1);
                CountDownLatch cancelObserved = new CountDownLatch(1);
                AtomicBoolean completionCalled = new AtomicBoolean(false);

                long tempUserId = 990000L + (System.nanoTime() % 100000L);
                String tempUsername = "http_disconnect_student_" + tempUserId;

                jdbcTemplate.update(
                                "INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at) "
                                                + "VALUES (?, ?, ?, ?, 'STUDENT', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                tempUserId,
                                tempUsername,
                                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                                "HTTP断连测试学生",
                                "test-class");

                doAnswer(invocation -> {
                        AIStreamCallback callback = invocation.getArgument(2);
                        Thread streamThread = new Thread(() -> {
                                try {
                                        for (int i = 0; i < 40; i++) {
                                                if (callback.isCancelled()) {
                                                        cancelObserved.countDown();
                                                        return;
                                                }

                                                callback.onContent("chunk-" + i, false);
                                                if (i == 0) {
                                                        firstChunkSent.countDown();
                                                }
                                                Thread.sleep(80);
                                        }

                                        if (callback.isCancelled()) {
                                                cancelObserved.countDown();
                                                return;
                                        }

                                        completionCalled.set(true);
                                        callback.onComplete(10, 20, 30);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                }
                        }, "http-disconnect-stream-mock");

                        streamThread.run();
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试HTTP客户端主动断开");
                String requestBody = objectMapper.writeValueAsString(request);

                String endpoint = "http://127.0.0.1:" + localServerPort + "/api/v1/ai/student/realtimeQA";
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", createAuthHeader(tempUserId, tempUsername));
                connection.setRequestProperty("X-Session-Id", validSessionId);
                connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                connection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);

                connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));
                int statusCode = connection.getResponseCode();
                assertThat(statusCode).isEqualTo(200);

                byte[] buffer = new byte[256];
                int readBytes;
                try (var inputStream = connection.getInputStream()) {
                        readBytes = inputStream.read(buffer);
                        assertThat(firstChunkSent.await(3, TimeUnit.SECONDS)).isTrue();
                }

                assertThat(readBytes).isGreaterThan(0);
                boolean firstChunkReady = firstChunkSent.await(3, TimeUnit.SECONDS);
                assertThat(firstChunkReady).as("应在断连前已发送至少一个内容分片").isTrue();

                connection.disconnect();

                boolean cancelled = cancelObserved.await(8, TimeUnit.SECONDS);
                assertTrue(cancelled, "客户端断开后服务端应感知取消");
                assertThat(completionCalled.get()).isFalse();

                jdbcTemplate.update("DELETE FROM users WHERE id = ?", tempUserId);
        }

        @DisplayName("实时问答 - 服务端异常主动关闭时客户端最后事件应为error")
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Sql(scripts = { "/sql/cleanup-all.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void testStreamRealtimeQA_ServerErrorClose_ShouldEndWithErrorEvent() throws Exception {
                // Given
                CountDownLatch errorTriggered = new CountDownLatch(1);

                long tempUserId = 991000L + (System.nanoTime() % 100000L);
                String tempUsername = "http_server_error_student_" + tempUserId;

                jdbcTemplate.update(
                                "INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at) "
                                                + "VALUES (?, ?, ?, ?, 'STUDENT', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                tempUserId,
                                tempUsername,
                                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                                "HTTP服务端关闭测试学生",
                                "test-class");

                doAnswer(invocation -> {
                        AIStreamCallback callback = invocation.getArgument(2);
                        Thread streamThread = new Thread(() -> {
                                callback.onContent("partial-before-error", false);
                                callback.onError(new RuntimeException("forced-server-close"));
                                errorTriggered.countDown();
                        }, "http-server-error-stream-mock");

                        streamThread.run();
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                AIAskRequest request = new AIAskRequest();
                request.setQuestion("测试服务端异常关闭");
                String requestBody = objectMapper.writeValueAsString(request);

                String endpoint = "http://127.0.0.1:" + localServerPort + "/api/v1/ai/student/realtimeQA";
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Authorization", createAuthHeader(tempUserId, tempUsername));
                connection.setRequestProperty("X-Session-Id", validSessionId);
                connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                connection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);

                connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));
                int statusCode = connection.getResponseCode();
                assertThat(statusCode).isEqualTo(200);

                StringBuilder sseRaw = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                                sseRaw.append(line).append('\n');
                        }
                }

                boolean errorCalled = errorTriggered.await(3, TimeUnit.SECONDS);
                assertThat(errorCalled).as("应触发服务端异常回调").isTrue();

                String sseResponse = sseRaw.toString();
                assertContainsEventName(sseResponse, "error");
                assertThat(getLastEventName(sseResponse)).isEqualTo("error");
                assertThat(sseResponse).doesNotContainPattern("(?m)^event:\\s*complete\\s*$");

                connection.disconnect();
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", tempUserId);
        }

        @DisplayName("实时问答 - 服务端超时主动关闭时客户端不应收到命名终止事件")
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Sql(scripts = { "/sql/cleanup-all.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void testStreamRealtimeQA_ServerTimeoutClose_ShouldEndWithoutNamedTerminalEvent() throws Exception {
                testExecutor.asynchronous = true;
                // Given
                CountDownLatch timeoutObserved = new CountDownLatch(1);
                AtomicBoolean completionCalled = new AtomicBoolean(false);

                long tempUserId = 992000L + (System.nanoTime() % 100000L);
                String tempUsername = "http_server_timeout_student_" + tempUserId;

                jdbcTemplate.update(
                                "INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at) "
                                                + "VALUES (?, ?, ?, ?, 'STUDENT', ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                tempUserId,
                                tempUsername,
                                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                                "HTTP超时测试学生",
                                "test-class");

                Long originTimeout = aiStreamingProperties.getSseEmitterTimeout();
                Long originReconnect = aiStreamingProperties.getSseReconnectTime();
                aiStreamingProperties.setSseEmitterTimeout(300L);
                aiStreamingProperties.setSseReconnectTime(100L);

                HttpURLConnection connection = null;
                try {
                        doAnswer(invocation -> {
                                AIStreamCallback callback = invocation.getArgument(2);
                                Thread streamThread = new Thread(() -> {
                                        try {
                                                for (int i = 0; i < 80; i++) {
                                                        if (callback.isCancelled()) {
                                                                timeoutObserved.countDown();
                                                                return;
                                                        }
                                                        Thread.sleep(50);
                                                }
                                                completionCalled.set(true);
                                                callback.onComplete(1, 1, 2);
                                        } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                        }
                                }, "http-server-timeout-stream-mock");

                                streamThread.run();
                                return null;
                        }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

                        AIAskRequest request = new AIAskRequest();
                        request.setQuestion("测试服务端超时主动关闭");
                        String requestBody = objectMapper.writeValueAsString(request);

                        String endpoint = "http://127.0.0.1:" + localServerPort + "/api/v1/ai/student/realtimeQA";
                        connection = (HttpURLConnection) new URL(endpoint).openConnection();
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);
                        connection.setReadTimeout(8000);
                        connection.setRequestProperty("Authorization", createAuthHeader(tempUserId, tempUsername));
                        connection.setRequestProperty("X-Session-Id", validSessionId);
                        connection.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                        connection.setRequestProperty("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);

                        connection.getOutputStream().write(requestBody.getBytes(StandardCharsets.UTF_8));
                        int statusCode = connection.getResponseCode();
                        assertThat(statusCode).isEqualTo(200);

                        StringBuilder sseRaw = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                        sseRaw.append(line).append('\n');
                                }
                        }

                        String sseResponse = sseRaw.toString();
                        assertThat(sseResponse).contains("data:连接成功");
                        assertThat(sseResponse).doesNotContainPattern("(?m)^event:\\s*error\\s*$");
                        assertThat(sseResponse).doesNotContainPattern("(?m)^event:\\s*complete\\s*$");
                        assertThat(getLastEventName(sseResponse)).isEmpty();

                        boolean timeoutCancelled = timeoutObserved.await(5, TimeUnit.SECONDS);
                        assertThat(timeoutCancelled).as("超时关闭后应触发取消感知").isTrue();
                        assertThat(completionCalled.get()).isFalse();
                } finally {
                        if (connection != null) {
                                connection.disconnect();
                        }
                        aiStreamingProperties.setSseEmitterTimeout(originTimeout);
                        aiStreamingProperties.setSseReconnectTime(originReconnect);
                        jdbcTemplate.update("DELETE FROM users WHERE id = ?", tempUserId);
                }
        }
        
        // ============================================================
        // 边界场景并发测试（新增）
        // ============================================================
        
        @DisplayName("实时问答流式生成 - 基础并发压力测试")
        @Test
        void testStreamRealtimeQA_BasicConcurrency() throws Exception {
                // 测试目标：验证多个并发请求能同时建立 SSE 连接
                // 关键改进：相同用户 + 不同 Session ID 实现数据隔离
                int concurrentUsers = 3;
                CountDownLatch latch = new CountDownLatch(concurrentUsers);
        
                // Given: 配置 AI 客户端 Mock（线程安全）
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                callback.onContent("并发响应内容", false);
                                callback.onContent("完成", true);
                                callback.onComplete(30, 60, 90);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        
                // When: 并发发送 3 个请求（相同用户，不同 Session ID）
                java.util.List<MvcResult> results = new java.util.ArrayList<>();
                for (int i = 0; i < concurrentUsers; i++) {
                        AIAskRequest request = new AIAskRequest();
                        request.setQuestion("并发测试问题" + i);
        
                        MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                        .header("Authorization", createAuthHeader(studentId, studentUsername))
                                        .header("X-Session-Id", "2026031500000" + i + "_a1b2c3d4e5f6789" + i)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(request().asyncStarted())
                                        .andReturn();
                        results.add(mvcResult);
                }
        
                // Then: 等待所有异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("所有请求应该在超时前完成").isTrue();
        
                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(800);
        
                // 验证所有请求都成功建立 SSE 连接
                for (MvcResult mvcResult : results) {
                        mockMvc.perform(asyncDispatch(mvcResult))
                                        .andExpect(status().isOk())
                                        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
                }
        
                // 验证 Mock 被调用至少 3 次（宽松策略）
                verify(zhipuAIClient, atLeast(concurrentUsers))
                                .streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        }
        
        @DisplayName("实时问答流式生成 - 延迟响应测试")
        @Test
        void testStreamRealtimeQA_DelayedResponse() throws Exception {
                // 测试目标：验证 AI 服务响应慢时 SSE 连接的稳定性
                // 注意：这是"延迟响应测试"，非真实 SSE 超时测试
                CountDownLatch latch = new CountDownLatch(1);
        
                // Given: 配置 AI 客户端 Mock（模拟 200ms 延迟）
                doAnswer(invocation -> {
                        new Thread(() -> {
                                try {
                                        AIStreamCallback callback = invocation.getArgument(2);
                                        // 模拟延迟响应
                                        Thread.sleep(200);
                                        callback.onContent("延迟响应内容", false);
                                        Thread.sleep(100);
                                        callback.onContent("最后一部分内容", true);
                                        callback.onComplete(40, 80, 120);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                } finally {
                                        latch.countDown();
                                }
                        }).run();
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        
                // When: 发送实时问答请求
                AIAskRequest request = new AIAskRequest();
                request.setQuestion("延迟响应测试问题");
        
                MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(studentId, studentUsername))
                                .header("X-Session-Id", "20260315000099_delayed_response")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(request().asyncStarted())
                                .andReturn();
        
                // Then: 等待异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("应该在超时前完成").isTrue();
        
                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(800);
        
                // 验证异步响应（SSE 连接稳定）
                MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcResult))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                                .andReturn();
                
                // 验证多分片响应完整性（至少 2 个内容分片 + 1 个统计信息）
                String responseContent = finalResult.getResponse().getContentAsString();
                java.util.List<String> events = SseTestUtils.extractSseEvents(responseContent);
                assertThat(events).hasSizeGreaterThan(1); // 至少有两个事件（内容分片）
        }
        
        @DisplayName("实时问答流式生成 - 混合场景并发测试（异常隔离验证）")
        @Test
        void testStreamRealtimeQA_MixedScenariosConcurrency() throws Exception {
                // 测试目标：验证异常请求不会影响其他正常请求
                // 关键改进：使用 QuestionNotFoundException 更符合业务场景
                int totalRequests = 5;
                int errorRequestIndex = 5; // 第 5 个请求抛出异常
                CountDownLatch latch = new CountDownLatch(totalRequests);
        
                // Given: 配置 AI 客户端 Mock（第 5 个请求抛出异常）
                java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
                doAnswer(invocation -> {
                        int currentCall = callCount.incrementAndGet();
                        try {
                                if (currentCall == errorRequestIndex) {
                                        // 第 5 个请求：模拟题目不存在异常
                                        throw new QuestionNotFoundException(999L);
                                }
                                AIStreamCallback callback = invocation.getArgument(2);
                                callback.onContent("正常响应" + currentCall, false);
                                callback.onContent("完成" + currentCall, true);
                                callback.onComplete(30, 60, 90);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        
                // When: 并发发送 5 个请求（前 4 个成功，第 5 个异常）
                java.util.List<MvcResult> results = new java.util.ArrayList<>();
                for (int i = 0; i < totalRequests; i++) {
                        AIAskRequest request = new AIAskRequest();
                        request.setQuestion("混合场景测试问题" + i);
        
                        MvcResult mvcResult = mockMvc.perform(post("/ai/student/realtimeQA")
                                        .header("Authorization", createAuthHeader(studentId, studentUsername))
                                        .header("X-Session-Id", "2026031500000" + i + "_mixed_scenario")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(request().asyncStarted())
                                        .andReturn();
                        results.add(mvcResult);
                }
        
                // Then: 等待所有异步处理完成
                boolean completed = latch.await(10, TimeUnit.SECONDS);
                assertThat(completed).withFailMessage("所有请求应该在超时前完成").isTrue();
        
                // 关键：额外等待确保所有事件处理完成
                Thread.sleep(800);
        
                // 验证所有请求都有响应（包括异常请求）
                int successCount = 0;
                int errorCount = 0;
                for (MvcResult mvcResult : results) {
                        try {
                                mockMvc.perform(asyncDispatch(mvcResult))
                                                .andExpect(status().isOk()); // SSE 连接本身成功
                                successCount++;
                        } catch (AssertionError e) {
                                // 可能有请求失败（如异常场景）
                                errorCount++;
                        }
                }
        
                // 验证：至少 4 个成功（允许 1 个异常）
                assertThat(successCount).isGreaterThanOrEqualTo(4);
                System.out.println("=== 混合场景测试结果：成功=" + successCount + ", 异常=" + errorCount);
        
                // 验证 Mock 被调用（宽松策略）
                verify(zhipuAIClient, atLeast(4))
                                .streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        }

        @DisplayName("同一会话已有请求时拒绝重复生成且保留首个请求")
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @Sql(scripts = { "/sql/cleanup-all.sql" }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
        void testStreamRealtimeQA_SameSessionConcurrentFollowUp_ShouldKeepConsistentHistory() throws Exception {
                testExecutor.asynchronous = true;
                long uid = 998001L;
                String username = "single_flight_student";
                jdbcTemplate.update("INSERT INTO users(id,username,password_hash,real_name,user_type,active) VALUES(?,?,? ,?,'STUDENT',TRUE)",
                        uid, username, "test-hash", username);
                CountDownLatch started = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                doAnswer(invocation -> {
                        AIStreamCallback callback = invocation.getArgument(2);
                        started.countDown();
                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("first request not released");
                        callback.onContent("single response", true);
                        callback.onComplete(20, 40, 60);
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
                String session = "20260418093000_aabbccddeeff0011";
                try {
                        MvcResult first = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(uid, username)).header("X-Session-Id", session)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"first\"}"))
                                .andExpect(request().asyncStarted()).andReturn();
                        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                        MvcResult duplicate = mockMvc.perform(post("/ai/student/realtimeQA")
                                .header("Authorization", createAuthHeader(uid, username)).header("X-Session-Id", session)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"duplicate\"}"))
                                .andExpect(request().asyncStarted()).andReturn();
                        duplicate.getAsyncResult(5000);
                        String error = mockMvc.perform(asyncDispatch(duplicate)).andReturn().getResponse().getContentAsString();
                        assertContainsEventName(error, "error");
                        release.countDown();
                        first.getAsyncResult(5000);
                        String response = mockMvc.perform(asyncDispatch(first)).andReturn().getResponse().getContentAsString();
                        assertContainsEventName(response, "complete");
                        verify(zhipuAIClient, org.mockito.Mockito.times(1)).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
                        var history = conversationHistoryRepository.findBySessionIdAndConversationTypeAndIsActiveOrderBySequenceNumberAsc(
                                session, ConversationType.REAL_TIME_QA, true);
                        assertThat(history).hasSize(1);
                } finally {
                        release.countDown();
                        jdbcTemplate.update("DELETE FROM conversation_history WHERE user_id=?", uid);
                        jdbcTemplate.update("DELETE FROM users WHERE id=?", uid);
                }
        }

        // ============================================================
        // 辅助方法
        // ============================================================

        /**
         * 设置实时问答的 AI客户端 Mock 配置
         */
        private void setupZhipuAIClientMockForQA(CountDownLatch latch) {
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                // 模拟实时问答 AI 响应
                                callback.onContent("牛顿第一定律指出：一切物体在没有受到外力作用的时候，总保持静止状态或匀速直线运动状态。", false);
                                callback.onContent("这一定律揭示了力和运动的关系，表明力不是维持物体运动的原因，而是改变物体运动状态的原因。", true);
                                callback.onComplete(50, 100, 150);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        }

        /**
         * 设置错题分析的 AI客户端 Mock 配置
         */
        private void setupZhipuAIClientMockForWrongQuestionAnalysis(CountDownLatch latch) {
                doAnswer(invocation -> {
                        try {
                                AIStreamCallback callback = invocation.getArgument(2);
                                // 模拟错题分析 AI 响应
                                callback.onContent("【错误原因分析】您的答案是 A，认为不受力物体一定静止。这个错误源于对牛顿第一定律的误解。", false);
                                callback.onContent("【思路引导】从题目中可以发现，关键在于理解'不受力'时物体的状态。", false);
                                callback.onContent("【重难点突破】惯性是物体保持原有运动状态的性质，不受力的物体会保持静止或匀速直线运动。", false);
                                callback.onContent("【学习建议】建议多做相关练习，深入理解牛顿第一定律的本质含义。", true);
                                callback.onComplete(80, 160, 240);
                        } finally {
                                latch.countDown();
                        }
                        return null;
                }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));
        }

        /**
         * 兼容 SseEmitter 可能输出的 event:xx / event: xx 两种格式
         */
        private void assertContainsEventName(String sseResponse, String eventName) {
                assertThat(sseResponse)
                                .containsPattern("(?m)^event:\\s*" + eventName + "\\s*$");
        }

        /**
         * 提取 SSE 响应中最后一个 event 名称（若不存在则返回空字符串）。
         */
        private String getLastEventName(String sseResponse) {
                String lastEvent = "";
                if (sseResponse == null || sseResponse.isBlank()) {
                        return lastEvent;
                }

                String[] lines = sseResponse.split("\\R");
                for (String line : lines) {
                        if (line != null && line.startsWith("event:")) {
                                lastEvent = line.substring("event:".length()).trim();
                        }
                }
                return lastEvent;
        }
}
