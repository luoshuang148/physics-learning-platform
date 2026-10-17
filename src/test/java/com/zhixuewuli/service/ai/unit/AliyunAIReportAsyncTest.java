package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.ai.impl.AliyunAIReportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@DisplayName("阿里云AI报告异步流程测试")
class AliyunAIReportAsyncTest extends AIAsyncTestBase {

    @InjectMocks
    private AliyunAIReportServiceImpl service;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 服务特定的Mock对象
    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private StudentReportRepository studentReportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPermissionUtil userPermissionUtil;

    @Mock
    private OptionMappingUtil optionMappingUtil;

    @Mock
    private TransactionTemplate transactionTemplate;

    // 测试数据
    private SseEmitter mockEmitter;
    private User mockUser;
    private List<AnswerRecord> mockAnswerRecords;
    private List<Question> mockQuestions;

    @BeforeEach
    void setUp() {
        mockEmitter = mock(SseEmitter.class);

        // 创建模拟用户
        mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(TEST_USER_ID);
        when(mockUser.getUserType()).thenReturn(User.UserType.STUDENT);

        // 创建模拟答题记录
        AnswerRecord record1 = mock(AnswerRecord.class);
        when(record1.getQuestionId()).thenReturn(TEST_QUESTION_ID);
        when(record1.getUserAnswer()).thenReturn("A");
        when(record1.getCorrect()).thenReturn(false);
        when(record1.getAnsweredAt()).thenReturn(LocalDateTime.now());

        AnswerRecord record2 = mock(AnswerRecord.class);
        when(record2.getQuestionId()).thenReturn(TEST_QUESTION_ID + 1);
        when(record2.getUserAnswer()).thenReturn("B");
        when(record2.getCorrect()).thenReturn(true);
        when(record2.getAnsweredAt()).thenReturn(LocalDateTime.now());

        mockAnswerRecords = Arrays.asList(record1, record2);

        // 创建模拟题目列表
        Question question1 = mock(Question.class);
        when(question1.getId()).thenReturn(TEST_QUESTION_ID);
        when(question1.getContent()).thenReturn("题目1内容");
        when(question1.getOptions()).thenReturn("{\"A\":\"选项A\",\"B\":\"选项B\"}");
        when(question1.getCorrectOption()).thenReturn("A");

        Question question2 = mock(Question.class);
        when(question2.getId()).thenReturn(TEST_QUESTION_ID + 1);
        when(question2.getContent()).thenReturn("题目2内容");
        when(question2.getOptions()).thenReturn("{\"A\":\"选项A\",\"B\":\"选项B\"}");
        when(question2.getCorrectOption()).thenReturn("B");

        mockQuestions = Arrays.asList(question1, question2);

        // 配置 TransactionTemplate 以执行传入的操作
        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(
                org.mockito.ArgumentMatchers.any(java.util.function.Consumer.class));
    }

    @Test
    @DisplayName("学生报告-验证数据查询和提示词构建")
    void streamStudentReport_shouldQueryDataAndBuildPrompt() {
        // Given
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID))
                .thenReturn(mockAnswerRecords);
        when(questionRepository.findByIdIn(anyList())).thenReturn(mockQuestions);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("学生报告内容", true);
            callback.onComplete(50, 100, 150);
            return null;
        }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);
        });

        // 验证数据查询被调用
        verify(answerRecordRepository).findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID);
        verify(questionRepository).findByIdIn(anyList());
        verify(userRepository, times(2)).findById(TEST_USER_ID);

        // 验证AI客户端被调用
        verify(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("学生报告-验证无答题记录处理")
    void streamStudentReport_shouldHandleNoAnswerRecords() {
        // Given
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID))
                .thenReturn(Collections.emptyList());

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);
        });

        // 验证抛出正确异常
        verify(answerRecordRepository).findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID);
        verify(aliyunAIClient, never()).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("教师分析报告 - 验证权限检查")
    void validateTeacherAnalysisRequest_shouldCheckPermissions() {
        // Given: 配置权限检查通过（不抛出异常）
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEST_USER_ID);

        // When: 调用验证方法
        service.validateTeacherAnalysisRequest(TEST_USER_ID);

        // Then: 验证权限检查被调用
        verify(userPermissionUtil).checkTeacherPermission(TEST_USER_ID);
    }

    @Test
    @DisplayName("教师分析报告 - 验证权限不足时抛出异常")
    void validateTeacherAnalysisRequest_shouldThrowExceptionWhenNoPermission() {
        // Given: 配置权限检查抛出异常
        doThrow(new com.zhixuewuli.common.exception.InsufficientPermissionException("权限不足"))
                .when(userPermissionUtil).checkTeacherPermission(TEST_USER_ID);

        // When & Then: 验证抛出正确异常
        org.junit.jupiter.api.Assertions.assertThrows(
                com.zhixuewuli.common.exception.InsufficientPermissionException.class,
                () -> service.validateTeacherAnalysisRequest(TEST_USER_ID));
    }

    @Test
    @DisplayName("教师分析报告-验证数据聚合逻辑")
    void streamTeacherAnalysis_shouldAggregateData() {
        // Given
        List<Long> mockUserIds = Arrays.asList(1001L, 1002L, 1003L);
        when(answerRecordRepository.findDistinctUserIds()).thenReturn(mockUserIds);

        // 模拟每个用户的会话ID查询
        when(answerRecordRepository.findDistinctSessionIdsByUserId(1001L))
                .thenReturn(Arrays.asList("session-1"));
        when(answerRecordRepository.findDistinctSessionIdsByUserId(1002L))
                .thenReturn(Arrays.asList("session-2"));
        when(answerRecordRepository.findDistinctSessionIdsByUserId(1003L))
                .thenReturn(Arrays.asList("session-3"));

        // 模拟会话记录查询
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(eq(1001L), anyString()))
                .thenReturn(mockAnswerRecords.subList(0, 1));
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(eq(1002L), anyString()))
                .thenReturn(mockAnswerRecords.subList(1, 2));
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(eq(1003L), anyString()))
                .thenReturn(new ArrayList<>());

        when(questionRepository.findByIdIn(anyList())).thenReturn(mockQuestions);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("教师分析报告内容", true);
            callback.onComplete(80, 120, 200);
            return null;
        }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamTeacherAnalysis(mockEmitter, TEST_USER_ID);
        });

        // 验证数据聚合逻辑
        verify(answerRecordRepository).findDistinctUserIds();
        verify(answerRecordRepository, times(3)).findDistinctSessionIdsByUserId(anyLong());
        verify(answerRecordRepository, atLeastOnce()).findByUserIdAndSessionIdOrderByAnsweredAtDesc(anyLong(),
                anyString());
        verify(questionRepository).findByIdIn(anyList());

        // 验证AI调用
        verify(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("异步任务提交验证")
    void streamStudentReport_shouldSubmitAsyncTask() {
        // Given
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID))
                .thenReturn(mockAnswerRecords);

        // When
        service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);

        // Then: 验证异步任务被提交
        verifyAsyncTaskSubmitted();
    }

    @Test
    @DisplayName("学生报告-连接结束后应停止后续推送")
    void streamStudentReport_shouldSkipSseAfterEmitterCompleted() {
        // Given
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID))
                .thenReturn(mockAnswerRecords);
        when(questionRepository.findByIdIn(anyList())).thenReturn(mockQuestions);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));

        final Runnable[] completionHook = new Runnable[1];
        doAnswer(invocation -> {
            completionHook[0] = invocation.getArgument(0);
            return null;
        }).when(mockEmitter).onCompletion(any(Runnable.class));

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            if (completionHook[0] != null) {
                completionHook[0].run();
            }
            callback.onContent("取消后的内容不应发送", false);
            callback.onComplete(20, 30, 50);
            return null;
        }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

        // When
        captureAndExecuteAsyncTask(() -> {
            service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);
        });

        // Then
        try {
            verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            // Mock 不会真实抛出异常
        }
        verify(studentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("学生报告-取消前有部分内容时应持久化部分报告")
    void streamStudentReport_shouldPersistPartialReportWhenCancelledAfterContent() {
        // Given
        when(answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(TEST_USER_ID, TEST_SESSION_ID))
                .thenReturn(mockAnswerRecords);
        when(questionRepository.findByIdIn(anyList())).thenReturn(mockQuestions);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));
        when(studentReportRepository.findByUserIdAndSessionId(TEST_USER_ID, TEST_SESSION_ID)).thenReturn(Optional.empty());

        final Runnable[] completionHook = new Runnable[1];
        doAnswer(invocation -> {
            completionHook[0] = invocation.getArgument(0);
            return null;
        }).when(mockEmitter).onCompletion(any(Runnable.class));

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("部分报告", false);
            if (completionHook[0] != null) {
                completionHook[0].run();
            }
            callback.onContent("取消后内容", true);
            callback.onComplete(20, 30, 50);
            return null;
        }).when(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));

        // When
        captureAndExecuteAsyncTask(() -> {
            service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);
        });

        // Then
        verify(studentReportRepository, times(1)).save(any());
        try {
            verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            // Mock 不会真实抛出异常
        }
    }
}
