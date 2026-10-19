package com.zhixuewuli.service.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.common.util.QuestionErrorConverter;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.QuestionErrorSummaryResponse;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.questionErrorAnalysis.impl.QuestionErrorAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 错题分析服务同步测试层
 * 
 * 专注测试QuestionErrorAnalysisService的同步业务逻辑，包括：
 * - getAllQuestionErrorStatistics方法的权限验证、数据查询、异常处理
 * - streamTeacherQuestionAnalysis方法的参数接收和异步任务启动验证
 * 
 * <p>
 * 遵循三层测试策略的第一层：同步逻辑测试
 * </p>
 * <ul>
 * <li>使用@Tag("ai-sync")标签进行分类</li>
 * <li>Mock所有外部依赖，确保零外部调用</li>
 * <li>测试纯业务逻辑，不涉及异步执行细节</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@DisplayName("错题分析服务同步测试层")
@Tag("ai-sync")
class QuestionErrorAnalysisServiceSyncTest extends BaseUnitTest {

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private QuestionRepository questionRepository;


    @Mock
    private QuestionErrorConverter errorConverter;

    @Mock
    private com.zhixuewuli.service.session.SessionManager sessionManager;

    @Mock
    private com.zhixuewuli.common.client.vendor.AliyunAIClient aliyunAIClient;

    @Mock
    private com.zhixuewuli.common.config.properties.AIBusinessProperties aiBusinessProperties;

    @Mock
    private com.zhixuewuli.common.config.properties.AIStreamingProperties aiStreamingProperties;

    @Mock
    private java.util.concurrent.Executor aiTaskExecutor;

    @Mock
    private com.zhixuewuli.common.util.StreamUtils streamUtils;

    @InjectMocks
    private QuestionErrorAnalysisServiceImpl questionErrorAnalysisService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // 测试数据
    private Long teacherId;
    private Long questionId;
    private User teacher;
    private Question question;
    private List<Object[]> mockStatisticsData;
    private List<QuestionErrorSummaryResponse> mockSummaryResponses;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        teacherId = 1001L;
        questionId = 2001L;

        // 创建测试教师用户
        teacher = TestDataFactory.createUser();
        teacher.setId(teacherId);
        teacher.setUserType(User.UserType.TEACHER);
        teacher.setRealName("张老师");

        // 创建测试题目
        question = TestDataFactory.createQuestion();
        question.setId(questionId);

        // 模拟统计数据
        // 模拟统计数据（10列：questionId, content, options, correctOption, explanation, difficulty, imageBase64, errorCount, correctCount, errorRate）
        Object[] statRow = new Object[] { questionId, "一个物体从高度h自由落下，求落地时间", "{\"A\":\"√(2h/g)\",\"B\":\"2h/g\"}", "A",
                "根据自由落体公式", "中等", null, 2L, 3L, 40.0 };
        mockStatisticsData = new ArrayList<>();
        mockStatisticsData.add(statRow);

        // 模拟转换后的响应数据
        QuestionErrorSummaryResponse summaryResponse = new QuestionErrorSummaryResponse();
        summaryResponse.setQuestionId(questionId);
        summaryResponse.setContent("一个物体从高度h自由落下，求落地时间");
        summaryResponse.setErrorCount(2L);
        summaryResponse.setErrorRate(0.4);
        mockSummaryResponses = Arrays.asList(summaryResponse);
    }

    @Test
    @DisplayName("成功获取题目错误统计信息")
    void shouldReturnQuestionErrorStatisticsWhenValidTeacher() {
        // Given
        when(answerRecordRepository.findQuestionErrorStatisticsOptimized()).thenReturn(mockStatisticsData);
        when(errorConverter.convertQuestionErrorSummaries(mockStatisticsData)).thenReturn(mockSummaryResponses);

        // When
        List<QuestionErrorSummaryResponse> result = questionErrorAnalysisService
                .getAllQuestionErrorStatistics(teacherId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestionId()).isEqualTo(questionId);
        assertThat(result.get(0).getErrorCount()).isEqualTo(2L);
        assertThat(result.get(0).getErrorRate()).isEqualTo(0.4);

        verify(answerRecordRepository).findQuestionErrorStatisticsOptimized();
        verify(errorConverter).convertQuestionErrorSummaries(mockStatisticsData);
    }

    @Test
    @DisplayName("当没有错误记录时返回空列表")
    void shouldReturnEmptyListWhenNoErrorRecords() {
        // Given
        when(answerRecordRepository.findQuestionErrorStatisticsOptimized()).thenReturn(Collections.emptyList());
        when(errorConverter.convertQuestionErrorSummaries(Collections.emptyList())).thenReturn(Collections.emptyList());

        // When
        List<QuestionErrorSummaryResponse> result = questionErrorAnalysisService
                .getAllQuestionErrorStatistics(teacherId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(errorConverter).convertQuestionErrorSummaries(Collections.emptyList());
    }


    @Test
    @DisplayName("当数据库查询异常时抛出技术异常")
    void shouldThrowTechnicalExceptionWhenDatabaseQueryFails() {
        // Given
        when(answerRecordRepository.findQuestionErrorStatisticsOptimized())
                .thenThrow(new RuntimeException("数据库连接失败"));

        // When & Then
        assertThatThrownBy(() -> questionErrorAnalysisService.getAllQuestionErrorStatistics(teacherId))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("查询题目错误统计失败");

        verify(answerRecordRepository).findQuestionErrorStatisticsOptimized();
    }

    @Test
    @DisplayName("流式分析方法能够正常接收参数并启动异步执行")
    void shouldAcceptStreamAnalysisParameters() {
        // Given
        AIAskRequest request = new AIAskRequest();
        request.setQuestionId(questionId);
        request.setQuestion("请分析这道题");

        SseEmitter emitter = mock(SseEmitter.class);
        String sessionId = "test-session";

        // When & Then - 不验证内部逻辑，只验证方法能正常接受参数
        questionErrorAnalysisService.streamTeacherQuestionAnalysis(request, emitter, teacherId, sessionId);

        // 验证没有抛出异常即可
        org.mockito.Mockito.verify(aiTaskExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }
}