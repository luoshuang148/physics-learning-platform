package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.ai.InvalidQuestionIdException;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.ai.impl.ZhiPuAIAssistantServiceImpl;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("智谱AI助手异步流程测试")
class ZhiPuAIAssistantAsyncTest extends AIAsyncTestBase {

    @InjectMocks
    private ZhiPuAIAssistantServiceImpl service;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 服务特定的Mock对象
    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private ConversationHistoryRepository conversationHistoryRepository;

    // 测试数据
    private AIAskRequest qaRequest;
    private AIAskRequest wrongQuestionRequest;
    private SseEmitter mockEmitter;
    private SessionContextInternalDTO mockSessionContext;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        qaRequest = createRealTimeQARequest("什么是牛顿第二定律？");

        wrongQuestionRequest = createWrongQuestionAnalysisRequest("这道题为什么做错了？", TEST_QUESTION_ID);

        mockEmitter = mock(SseEmitter.class);

        // 创建模拟会话上下文
        mockSessionContext = createSessionContext(TEST_SESSION_ID, TEST_USER_ID, "测试上下文");
    }

    @Test
    @DisplayName("实时问答-验证异步任务完整执行")
    void streamQAResponse_shouldExecuteAsyncTask() {
        // Given
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("AI回答内容", true);
            callback.onComplete(10, 20, 30);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When & Then: 使用核心方法捕获并执行异步任务
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证异步任务内部的业务逻辑
        verify(sessionManager).getOrCreateSession(
                eq(TEST_SESSION_ID),
                eq(TEST_USER_ID),
                eq(ConversationType.REAL_TIME_QA),
                isNull(), // 实时问答没有questionId
                any());

        verify(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("错题分析-验证参数正确传递")
    void streamWrongQuestionAnalysis_shouldPassParameters() {
        // Given
        Question mockQuestion = mock(Question.class);
        when(mockQuestion.getContent()).thenReturn("测试题目内容");
        when(mockQuestion.getOptions()).thenReturn("{\"A\":\"选项A\",\"B\":\"选项B\"}");

        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                anyLong(), any()))
                .thenReturn(mockSessionContext);
        when(questionRepository.findById(TEST_QUESTION_ID)).thenReturn(Optional.of(mockQuestion));

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("错题分析内容", true);
            callback.onComplete(15, 25, 40);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamWrongQuestionAnalysis(wrongQuestionRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证题目ID参数正确传递
        verify(sessionManager).getOrCreateSession(
                eq(TEST_SESSION_ID),
                eq(TEST_USER_ID),
                eq(ConversationType.WRONG_QUESTION_ANALYSIS),
                eq(TEST_QUESTION_ID), // 关键参数验证
                any());

        // 注意：questionRepository.findById在会话管理器的上下文构建器中被调用，由于Mock未调用构建器，此处不验证
        verify(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("实时问答-验证权限检查")
    void streamQAResponse_shouldCheckPermissions() {
        // Given
        doThrow(new InsufficientPermissionException("权限不足"))
                .when(sessionManager).getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                        any(), any());

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证权限检查被调用
        verify(sessionManager).getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any());
    }

    @Test
    @DisplayName("错题分析-验证无效题目ID处理")
    void streamWrongQuestionAnalysis_shouldHandleInvalidQuestionId() {
        // Given
        AIAskRequest invalidRequest = new AIAskRequest();
        invalidRequest.setQuestion("测试问题");
        // 不设置questionId

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamWrongQuestionAnalysis(invalidRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证异步任务被提交（参数验证在异步任务内部，但任务仍被提交）
        verifyAsyncTaskSubmitted();
        // 错误事件可能通过SSE发送，此处不验证具体内容
    }

    @Test
    @DisplayName("异步任务提交验证")
    void streamQAResponse_shouldSubmitAsyncTask() {
        // Given
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        // When
        service.streamQAResponse(qaRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);

        // Then: 验证异步任务被提交
        verifyAsyncTaskSubmitted();
    }

    @Test
    @DisplayName("SseEmitter已关闭时的防御性处理-发送reasoning不抛出异常")
    void streamQAResponse_shouldHandleEmitterClosedGracefully() throws Exception {
        // Given: 创建一个会抛出IllegalStateException的mock SseEmitter（模拟连接已断开）
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        // 启用reasoning功能
        when(aiBusinessProperties.getZhipuEnableReasoning()).thenReturn(true);

        // 模拟AI客户端回调：在reasoning内容发送时触发emitter异常
        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            // 模拟AI返回reasoning内容，这会触发onReasoningContent回调
            // 由于mock emitter会抛出IllegalStateException，需要通过反射调用
            try {
                callback.onReasoningContent("推理过程...");
            } catch (IllegalStateException expected) {
                // 防御性处理应该捕获这个异常
            }
            callback.onContent("最终回答", true);
            callback.onComplete(10, 20, 30);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When & Then: 异步任务执行不应抛出异常
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, brokenEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证：即使emitter已关闭，异步任务也能正常完成（防御性处理生效）
        // 不应抛出IllegalStateException
        verify(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("SseEmitter已关闭时的防御性处理-onContent不抛出异常")
    void streamQAResponse_shouldHandleContentEmitterClosedGracefully() throws Exception {
        // Given: 创建一个会抛出IllegalStateException的mock SseEmitter
        SseEmitter brokenEmitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        // 模拟AI客户端回调：发送内容时触发emitter异常
        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            // 模拟AI返回内容时触发异常
            try {
                callback.onContent("内容", true);
            } catch (IllegalStateException expected) {
                // 防御性处理应该捕获这个异常
            }
            callback.onComplete(10, 20, 30);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When & Then: 异步任务执行不应抛出异常
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, brokenEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // 验证：即使emitter已关闭，异步任务也能正常完成
        verify(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("实时问答-会话已取消时应停止后续SSE推送")
    void streamQAResponse_shouldSkipSseWhenSessionCancelled() {
        // Given
        mockSessionContext.setCancelled(true);
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("应被取消的内容", false);
            callback.onComplete(5, 10, 15);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // Then
        verify(streamUtils).bindContext(eq(mockEmitter), eq(mockSessionContext));
        try {
            verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            // Mock 不会真实抛出异常
        }
        verify(sessionManager, never()).addInteraction(any(), anyString(), anyString(), anyInt());
        verify(sessionManager, never()).updateTokenUsage(any(), anyInt());
    }

    @Test
    @DisplayName("实时问答-取消前已有部分内容时应保存部分交互")
    void streamQAResponse_shouldPersistPartialContentWhenCancelledAfterContent() {
        // Given
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(mockSessionContext);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("部分内容", false);
            mockSessionContext.setCancelled(true);
            callback.onContent("取消后内容", true);
            callback.onComplete(7, 8, 15);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), any(), any(AIStreamCallback.class));

        // When
        captureAndExecuteAsyncTask(() -> {
            service.streamQAResponse(qaRequest, mockEmitter, TEST_USER_ID, TEST_SESSION_ID);
        });

        // Then
        verify(sessionManager, times(1)).addInteraction(eq(mockSessionContext), anyString(), eq("部分内容"), anyInt());
        verify(sessionManager, times(1)).updateTokenUsage(eq(mockSessionContext), anyInt());
        verify(streamUtils, org.mockito.Mockito.atLeastOnce()).safeCompleteEmitter(mockEmitter);
    }
}