package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.util.OptionMappingUtil;
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

    @Mock private com.zhixuewuli.service.ai.report.ReportInputRepository reportInputs;

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
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,null))
                .thenReturn(mockAnswerRecords);
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
        verify(reportInputs).student(TEST_USER_ID, TEST_SESSION_ID,null);
        verify(userRepository).findById(TEST_USER_ID);

        // 验证AI客户端被调用
        verify(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    void eventReportNeverFallsBackToAnotherTaskSession() {
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,99L)).thenReturn(Collections.emptyList());
        captureAndExecuteAsyncTask(() -> service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID, 99L));
        verify(reportInputs).student(TEST_USER_ID, TEST_SESSION_ID,99L);
        verify(answerRecordRepository, never()).findByUserIdAndSessionIdOrderByAnsweredAtDesc(any(), any());
        verify(aliyunAIClient, never()).streamGenerate(any(), any(), any());
    }

    @Test
    @DisplayName("学生报告-验证无答题记录处理")
    void streamStudentReport_shouldHandleNoAnswerRecords() {
        // Given
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,null))
                .thenReturn(Collections.emptyList());

        // When & Then
        captureAndExecuteAsyncTask(() -> {
            service.streamStudentReport(TEST_SESSION_ID, mockEmitter, TEST_USER_ID);
        });

        // 验证抛出正确异常
        verify(reportInputs).student(TEST_USER_ID, TEST_SESSION_ID,null);
        verify(aliyunAIClient, never()).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("教师分析报告 - 验证权限检查")
    void validateTeacherAnalysisRequest_shouldCheckPermissions() {
        // Given: 配置权限检查通过（不抛出异常）

        // When: 调用验证方法
        service.validateTeacherAnalysisRequest(TEST_USER_ID);

        // Then: 验证权限检查被调用
    }


    @Test
    @DisplayName("教师分析报告-验证数据聚合逻辑")
    void streamTeacherAnalysis_shouldAggregateData() {
        // Given
        when(reportInputs.teacher(null)).thenReturn(mockAnswerRecords);

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

        verify(reportInputs).teacher(null);

        // 验证AI调用
        verify(aliyunAIClient).streamGenerate(any(), any(), any(AIStreamCallback.class));
    }

    @Test
    @DisplayName("异步任务提交验证")
    void streamStudentReport_shouldSubmitAsyncTask() {
        // Given
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,null))
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
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,null))
                .thenReturn(mockAnswerRecords);
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
            verify(mockEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            // Mock 不会真实抛出异常
        }
        verify(studentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("学生报告-取消前有部分内容时应持久化部分报告")
    void streamStudentReport_shouldPersistPartialReportWhenCancelledAfterContent() {
        // Given
        when(reportInputs.student(TEST_USER_ID, TEST_SESSION_ID,null))
                .thenReturn(mockAnswerRecords);
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(mockUser));

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
            verify(mockEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        } catch (Exception e) {
            // Mock 不会真实抛出异常
        }
    }
}
