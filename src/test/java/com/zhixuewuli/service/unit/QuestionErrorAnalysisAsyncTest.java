package com.zhixuewuli.service.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.QuestionErrorConverter;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.ai.unit.AIAsyncTestBase;
import com.zhixuewuli.service.ai.report.ReportInputRepository;
import com.zhixuewuli.service.questionErrorAnalysis.impl.QuestionErrorAnalysisServiceImpl;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("错题分析异步流程测试")
@Tag("ai-async")
class QuestionErrorAnalysisAsyncTest extends AIAsyncTestBase {

        @InjectMocks
        private QuestionErrorAnalysisServiceImpl service;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        // 服务特定的 Mock对象
        @Mock
        private AnswerRecordRepository answerRecordRepository;

        @Mock
        private QuestionRepository questionRepository;

        @Mock
        private ReportInputRepository reportInputs;

        @Mock
        private UserPermissionUtil userPermissionUtil;

        // @Mock annotation removed - using parent class's mock
        // SessionManager sessionManager is inherited from AIServiceSyncTestBase

        // @Mock annotation removed - using parent class's mock
        // AliyunAIClient aliyunAIClient is inherited from AIServiceSyncTestBase

        @Mock
        private OptionMappingUtil optionMappingUtil;

        @Mock
        private QuestionErrorConverter errorConverter;

        // 测试数据
        private AIAskRequest teacherQuestionAnalysisRequest;
        private SseEmitter mockEmitter;
        private SessionContextInternalDTO mockSessionContext;
        private Long teacherId;

        @BeforeEach
        void setUp() {
                teacherId = TEST_USER_ID + 100; // 教师 ID

                teacherQuestionAnalysisRequest = createWrongQuestionAnalysisRequest("这道题学生错在哪里？", TEST_QUESTION_ID);

                mockEmitter = mock(SseEmitter.class);

                // 创建模拟会话上下文
                mockSessionContext = createSessionContext(TEST_SESSION_ID, teacherId, "教师题目分析上下文");
        }

        @Test
        @DisplayName("教师题目分析-验证首次交互特殊逻辑")
        void streamTeacherQuestionAnalysis_shouldHandleFirstInteraction() {
                System.out.println("=== Starting test streamTeacherQuestionAnalysis_shouldHandleFirstInteraction ===");
                System.out.println("DEBUG Test: sessionManager is " + (sessionManager == null ? "null" : "not null"));
                System.out.println("DEBUG Test: mockSessionContext is "
                                + (mockSessionContext == null ? "null" : "not null"));
                if (mockSessionContext != null) {
                        System.out.println("DEBUG Test: mockSessionContext.lock is "
                                        + (mockSessionContext.getLock() == null ? "null" : "not null"));
                }

                try (MockedStatic<com.zhixuewuli.common.util.HanLPWordCloudUtil> mocked = Mockito
                                .mockStatic(com.zhixuewuli.common.util.HanLPWordCloudUtil.class)) {
                        mocked.when(() -> com.zhixuewuli.common.util.HanLPWordCloudUtil.analyzeErrorReasons(anyList()))
                                        .thenReturn(new java.util.HashMap<>());

                        // Given
                        doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);

                        // 模拟 sessionManager 调用 - 使用简单的返回，确保不为null
                        System.out.println("DEBUG Test: Setting up sessionManager.getOrCreateSession mock");
                        when(sessionManager.getOrCreateSession(
                                        anyString(),
                                        anyLong(),
                                        any(ConversationType.class),
                                        anyLong(),
                                        any()))
                                        .thenAnswer(invocation -> {
                                                System.out.println(
                                                                "DEBUG Test: sessionManager.getOrCreateSession mock called!");
                                                System.out.println("DEBUG Test: Invocation args: " +
                                                                "sessionId=" + invocation.getArgument(0) +
                                                                ", userId=" + invocation.getArgument(1) +
                                                                ", conversationType=" + invocation.getArgument(2) +
                                                                ", questionId=" + invocation.getArgument(3));
                                                // 调用 supplier 获取有界的历史快照上下文
                                                java.util.function.Supplier<String> contextSupplier = invocation
                                                                .getArgument(4);
                                                String context = contextSupplier.get();
                                                System.out.println(
                                                                "DEBUG Test: Context supplier returned context length: "
                                                                                + context.length());
                                                // 返回一个包含构建上下文的会话上下文
                                                SessionContextInternalDTO session = createSessionContext(
                                                                invocation.getArgument(0),
                                                                invocation.getArgument(1),
                                                                context);
                                                System.out.println(
                                                                "DEBUG Test: Created session, lock is "
                                                                                + (session.getLock() == null ? "null"
                                                                                                : "not null"));
                                                System.out.println("DEBUG Test: Interaction records is "
                                                                + (session.getInteractionRecords() == null ? "null"
                                                                                : "not null"));
                                                return session;
                                        });

                        // 备选方案：如果上面的mock不工作，使用这个简单的返回
                        // 注意：这个配置会覆盖上面的配置，所以暂时注释掉
                        // when(sessionManager.getOrCreateSession(anyString(), anyLong(),
                        // any(ConversationType.class),
                        // anyLong(), any()))
                        // .thenReturn(mockSessionContext);

                        // 模拟首次交互：空会话历史（使用默认的空ConcurrentSkipListMap）

                        Question mockQuestion = mock(Question.class);
                        when(mockQuestion.getContent()).thenReturn("测试题目内容");
                        when(mockQuestion.getOptions()).thenReturn("{\"A\":\"选项 A\",\"B\":\"选项 B\"}");
                        when(questionRepository.findById(TEST_QUESTION_ID)).thenReturn(Optional.of(mockQuestion));
                        // 配置答题记录仓库
                        when(answerRecordRepository.findStudentErrorsByQuestionId(TEST_QUESTION_ID))
                                        .thenReturn(Collections.emptyList());
                        // 用于构建上下文的答题记录查询
                        when(answerRecordRepository.findByQuestionIdOrderByAnsweredAtDesc(TEST_QUESTION_ID))
                                        .thenReturn(Collections.emptyList());
                        // 配置答题记录数量查询（必须，否则在 sessionManager 调用前就抛异常）
                        when(answerRecordRepository.countByQuestionId(TEST_QUESTION_ID))
                                        .thenReturn(1L);
                        // 配置错误转换器
                        when(errorConverter.convertStudentErrorDetails(anyList())).thenReturn(Collections.emptyList());
                        // 配置选项映射工具
                        when(optionMappingUtil.jsonToOptions(anyString()))
                                        .thenReturn(java.util.Map.of("A", "选项 A", "B", "选项 B", "C", "选项 C", "D",
                                                        "选项 D"));
                        // 配置题目额外信息
                        when(mockQuestion.getCorrectOption()).thenReturn("A");
                        when(mockQuestion.getExplanation()).thenReturn("测试解析");

                        // 配置 ObjectMapper（避免 JSON 序列化失败）
                        try {
                                when(objectMapper.writeValueAsString(any())).thenReturn("{}");
                        } catch (Exception e) {
                                // 不应该发生
                        }

                        // 配置 AI 业务属性
                        when(aiBusinessProperties.getTeacherReportGenerationModel()).thenReturn("teacher-model");
                        when(aiBusinessProperties.getTeacherQuestionAnalysisDefaultPrompt()).thenReturn("默认提示词");
                        // 配置 AI 流式属性
                        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(30000L);
                        when(aiStreamingProperties.getSseReconnectTime()).thenReturn(5000L);

                        // 配置阿里云 AI 客户端 Mock（必须在 captureAndExecuteAsyncTask 之前）
                        System.out.println("DEBUG Test: Setting up aliyunAIClient.streamGenerate mock");
                        doAnswer(invocation -> {
                                System.out.println("DEBUG Test: aliyunAIClient.streamGenerate called");
                                System.out.println("DEBUG Test: Invocation arguments: "
                                                + invocation.getArguments().length);
                                AIStreamCallback callback = invocation.getArgument(2);
                                System.out.println(
                                                "DEBUG Test: Callback is " + (callback == null ? "null" : "not null"));
                                callback.onContent("AI 分析内容", true);
                                callback.onComplete(30, 60, 90);
                                return null;
                        }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));
                        System.out.println("DEBUG Test: aliyunAIClient.streamGenerate mock setup complete");

                        // When & Then
                        captureAndExecuteAsyncTask(() -> {
                                service.streamTeacherQuestionAnalysis(teacherQuestionAnalysisRequest, mockEmitter,
                                                teacherId,
                                                TEST_SESSION_ID);
                        });

                        // 验证异步任务已提交
                        verify(aiTaskExecutor).execute(any(Runnable.class));

                        // 验证权限检查被调用
                        verify(userPermissionUtil).checkTeacherPermission(teacherId);

                        // 验证会话管理被调用
                        verify(sessionManager).getOrCreateSession(
                                        anyString(),
                                        anyLong(),
                                        any(ConversationType.class),
                                        anyLong(),
                                        any());

                        // 首次上下文使用有界投影，避免加载含图片的完整题目实体。
                        verify(reportInputs).question(TEST_QUESTION_ID);
                        verify(questionRepository, never()).findById(TEST_QUESTION_ID);

                        // 验证 AI 客户端被调用
                        verify(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

                        // 验证 SSE 事件被发送（2 次：ai-content、complete）
                        // 注：首次交互不再发送 student-list 和 word-cloud 初始化数据
                        try {
                                verify(mockEmitter, times(2))
                                                .send(any(
                                                                org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
                        } catch (IOException e) {
                                // Mock 方法不会真正抛出 IOException
                        }
                }

        }

        @Test
        @DisplayName("教师题目分析-验证权限检查")

        void streamTeacherQuestionAnalysis_shouldCheckPermissions() {
                // Given
                doThrow(new InsufficientPermissionException("权限不足"))
                                .when(userPermissionUtil).checkTeacherPermission(teacherId);

                // When & Then
                captureAndExecuteAsyncTask(() -> {
                        service.streamTeacherQuestionAnalysis(teacherQuestionAnalysisRequest, mockEmitter, teacherId,
                                        TEST_SESSION_ID);
                });

                // 验证权限检查被调用
                verify(userPermissionUtil).checkTeacherPermission(teacherId);
        }

        @Test
        @DisplayName("教师题目分析-验证无效题目 ID 处理")
        void streamTeacherQuestionAnalysis_shouldHandleInvalidQuestionId() {
                // Given
                AIAskRequest invalidRequest = new AIAskRequest();
                invalidRequest.setQuestion("测试问题");
                // 不设置 questionId

                // When & Then
                captureAndExecuteAsyncTask(() -> {
                        service.streamTeacherQuestionAnalysis(invalidRequest, mockEmitter, teacherId, TEST_SESSION_ID);
                });

                // 验证异步任务被提交（参数验证在异步任务内部，但任务仍被提交）
                verifyAsyncTaskSubmitted();
                // 错误事件可能通过SSE发送，此处不验证具体内容
        }

        @Test
        @DisplayName("异步任务提交验证")
        void streamTeacherQuestionAnalysis_shouldSubmitAsyncTask() {
                // Given
                doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);
                when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                                anyLong(), any()))
                                .thenReturn(mockSessionContext);

                // When
                service.streamTeacherQuestionAnalysis(teacherQuestionAnalysisRequest, mockEmitter, teacherId,
                                TEST_SESSION_ID);

                // Then: 验证异步任务被提交
                verifyAsyncTaskSubmitted();
        }

        @Test
        @DisplayName("教师题目分析-会话取消后应停止SSE推送")
        void streamTeacherQuestionAnalysis_shouldSkipSseWhenSessionCancelled() {
                // Given
                doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);
                when(answerRecordRepository.countByQuestionId(TEST_QUESTION_ID)).thenReturn(1L);
                when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                                anyLong(), any()))
                                .thenReturn(mockSessionContext);
                when(aiBusinessProperties.getTeacherReportGenerationModel()).thenReturn("teacher-model");
                when(aiBusinessProperties.getTeacherQuestionAnalysisDefaultPrompt()).thenReturn("默认提示词");

                doAnswer(invocation -> {
                        AIStreamCallback callback = invocation.getArgument(2);
                        mockSessionContext.setCancelled(true);
                        callback.onContent("取消后的内容", false);
                        callback.onComplete(10, 20, 30);
                        return null;
                }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

                // When
                captureAndExecuteAsyncTask(() -> {
                        service.streamTeacherQuestionAnalysis(teacherQuestionAnalysisRequest, mockEmitter, teacherId,
                                        TEST_SESSION_ID);
                });

                // Then
                verify(streamUtils).bindContext(eq(mockEmitter), eq(mockSessionContext));
                try {
                        verify(mockEmitter, never()).send(any(
                                        org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
                } catch (IOException e) {
                        // Mock 不会真实抛出 IO 异常
                }
                verify(sessionManager, never()).addInteraction(any(), anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("教师题目分析-取消前有部分内容时应保存交互")
        void streamTeacherQuestionAnalysis_shouldPersistPartialWhenCancelledAfterContent() {
                // Given
                doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);
                when(answerRecordRepository.countByQuestionId(TEST_QUESTION_ID)).thenReturn(1L);
                when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                                anyLong(), any()))
                                .thenReturn(mockSessionContext);
                when(aiBusinessProperties.getTeacherReportGenerationModel()).thenReturn("teacher-model");

                doAnswer(invocation -> {
                        AIStreamCallback callback = invocation.getArgument(2);
                        callback.onContent("部分分析内容", false);
                        mockSessionContext.setCancelled(true);
                        callback.onContent("取消后内容", true);
                        callback.onComplete(10, 20, 30);
                        return null;
                }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

                // When
                captureAndExecuteAsyncTask(() -> {
                        service.streamTeacherQuestionAnalysis(teacherQuestionAnalysisRequest, mockEmitter, teacherId,
                                        TEST_SESSION_ID);
                });

                // Then
                verify(sessionManager, times(1)).addInteraction(eq(mockSessionContext), nullable(String.class), eq("部分分析内容"), anyInt());
                verify(sessionManager, times(1)).updateTokenUsage(eq(mockSessionContext), anyInt());
        }
}
