package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.StudentReportResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.StudentReport;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.ai.impl.AliyunAIReportServiceImpl;
import com.zhixuewuli.common.util.UserPermissionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 阿里云AI报告服务同步逻辑测试
 * 
 * 专注于测试AliyunAIReportService的同步业务逻辑，
 * 确保AI客户端在这些测试中绝不被调用。
 * 
 * <p>
 * 测试范围：
 * </p>
 * <ul>
 * <li>SSE发射器配置相关方法</li>
 * <li>学生报告查询相关方法</li>
 * <li>权限验证逻辑</li>
 * <li>数据查询和转换</li>
 * <li>私有方法的复杂逻辑</li>
 * <li>异常处理场景</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@DisplayName("阿里云AI报告服务同步逻辑测试")
class AliyunAIReportSyncTest extends AIServiceSyncTestBase {

    // ========== 服务依赖Mock ==========

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
    private UserPermissionUtil userPermissionUtil;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    // ========== 被测试服务实例 ==========

    @InjectMocks
    private AliyunAIReportServiceImpl service;

    // ========== 测试数据 ==========

    private User mockStudent;
    private User mockTeacher;
    private List<AnswerRecord> mockAnswerRecords;
    private Question mockQuestion;

    @BeforeEach
    void setUpAliyunAIReportSyncTest() {
        // 准备测试数据
        mockStudent = com.zhixuewuli.common.factory.TestDataFactory.createUser();
        mockStudent.setId(TEST_USER_ID);
        mockStudent.setUserType(com.zhixuewuli.entity.User.UserType.STUDENT);
        mockStudent.setRealName("测试学生");

        mockTeacher = com.zhixuewuli.common.factory.TestDataFactory.createUser();
        mockTeacher.setId(TEST_USER_ID + 1);
        mockTeacher.setUserType(com.zhixuewuli.entity.User.UserType.TEACHER);
        mockTeacher.setRealName("测试教师");

        mockAnswerRecords = createTestAnswerRecords();
        mockQuestion = com.zhixuewuli.common.factory.TestDataFactory.createQuestion();
        mockQuestion.setId(TEST_QUESTION_ID);

        // 注意：不在这里配置AI业务属性的默认值，而是在具体测试中按需配置
        // 这样可以避免 UnnecessaryStubbingException
    }

    // ========== createConfiguredEmitter 测试 ==========

    @Test
    @DisplayName("创建配置的 SSE 发射器 - 验证超时时间配置")
    void createConfiguredEmitter_shouldConfigureTimeout() {
        // Given
        Long mockTimeout = 30000L;
        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(mockTimeout);

        // Mock StreamUtils 返回真实的 SseEmitter
        SseEmitter mockResultingEmitter = new SseEmitter(mockTimeout);
        when(streamUtils.createConfiguredEmitterWithLimit(anyLong(), anyLong()))
                .thenReturn(mockResultingEmitter);

        // When
        SseEmitter result = service.createConfiguredEmitter();

        // Then
        assertThat(result).isNotNull();
        // 验证配置读取（优化后只调用一次）
        verify(aiStreamingProperties, times(1)).getSseEmitterTimeout();
        verify(streamUtils).createConfiguredEmitterWithLimit(anyLong(), anyLong());

        // 确保 AI 客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("创建 SSE 发射器 - 验证配置读取")
    void createConfiguredEmitter_shouldReadConfig() {
        // Given
        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(600000L);

        // Mock StreamUtils 返回真实的 SseEmitter
        SseEmitter mockResultingEmitter = new SseEmitter(600000L);
        when(streamUtils.createConfiguredEmitterWithLimit(anyLong(), anyLong()))
                .thenReturn(mockResultingEmitter);

        // When
        var emitter = service.createConfiguredEmitter();

        // Then
        assertThat(emitter).isNotNull();
        // 验证配置读取被调用（优化后只调用一次）
        verify(aiStreamingProperties, times(1)).getSseEmitterTimeout();
        verify(streamUtils).createConfiguredEmitterWithLimit(anyLong(), anyLong());
        // 不验证 getSseReconnectTime()，因为实现中没有调用

        // 确保 AI 客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== getStudentLatestReport 测试 ==========

    @Test
    @DisplayName("获取学生最新报告-验证权限检查和数据查询")
    void getStudentLatestReport_shouldValidatePermissionAndQueryData() {
        // Given
        Long studentId = TEST_USER_ID;
        Long teacherId = TEST_USER_ID + 1;

        // Mock权限验证通过
        doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);

        // Mock学生用户存在
        when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

        // Mock最新的学生报告
        StudentReport mockReport = createMockStudentReport(studentId);
        when(studentReportRepository.findLatestByUserId(studentId))
                .thenReturn(Optional.of(mockReport));

        // When
        StudentReportResponse result = service.getStudentLatestReport(teacherId, studentId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStudentRealName()).isEqualTo(mockStudent.getRealName());
        assertThat(result.getLatestReportContent()).isEqualTo(mockReport.getReportContent());
        assertThat(result.getReportCreateTime()).isEqualTo(mockReport.getUpdatedAt());

        // 验证依赖调用
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(userRepository).findById(studentId);
        verify(studentReportRepository).findLatestByUserId(studentId);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("获取学生最新报告-当教师权限不足时抛出异常")
    void getStudentLatestReport_shouldThrowException_whenInsufficientPermission() {
        // Given
        Long studentId = TEST_USER_ID;
        Long teacherId = TEST_USER_ID + 1;

        // Mock权限验证失败
        doThrow(new com.zhixuewuli.common.exception.InsufficientPermissionException("权限不足"))
                .when(userPermissionUtil).checkTeacherPermission(teacherId);

        // When & Then
        assertThatThrownBy(() -> service.getStudentLatestReport(teacherId, studentId))
                .isInstanceOf(com.zhixuewuli.common.exception.InsufficientPermissionException.class)
                .hasMessageContaining("权限不足");

        // 验证权限检查被调用
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        // 不应该查询其他依赖
        verify(userRepository, never()).findById(anyLong());
        verify(studentReportRepository, never()).findLatestByUserId(anyLong());

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 私有方法测试 ==========

    @Test
    @DisplayName("加载题目从答题记录-验证题目查询")
    void loadQuestionsFromRecords_shouldQueryQuestions() throws Exception {
        // Given
        List<Long> questionIds = List.of(TEST_QUESTION_ID, TEST_QUESTION_ID + 1);
        List<Question> mockQuestions = List.of(mockQuestion);

        when(questionRepository.findByIdIn(any())).thenReturn(mockQuestions);

        // When
        Object result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "loadQuestionsFromRecords",
                new Class<?>[] { List.class },
                mockAnswerRecords);

        // Then
        assertThat(result).isNotNull();
        // 验证依赖调用
        verify(questionRepository).findByIdIn(any());

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("加载题目从答题记录-当无题目时返回空映射")
    void loadQuestionsFromRecords_shouldReturnEmptyMap_whenNoQuestions() throws Exception {
        // Given
        List<AnswerRecord> emptyRecords = List.of();

        // When
        Object result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "loadQuestionsFromRecords",
                new Class<?>[] { List.class },
                emptyRecords);

        // Then
        assertThat(result).isNotNull();
        // 不应该调用题目查询
        verify(questionRepository, never()).findByIdIn(any());

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 异常场景测试 ==========

    @Test
    @DisplayName("获取学生最新报告-当学生不存在时抛出异常")
    void getStudentLatestReport_shouldThrowException_whenStudentNotFound() {
        // Given
        Long studentId = TEST_USER_ID;
        Long teacherId = TEST_USER_ID + 1;

        // Mock权限验证通过
        doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);

        // Mock学生用户不存在
        when(userRepository.findById(studentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getStudentLatestReport(teacherId, studentId))
                .isInstanceOf(com.zhixuewuli.common.exception.auth.UserNotFoundException.class)
                .hasMessageContaining("学生用户不存在");

        // 验证权限检查和用户查询被调用
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(userRepository).findById(studentId);
        verify(studentReportRepository, never()).findLatestByUserId(anyLong());

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("获取学生最新报告-当无报告记录时抛出异常")
    void getStudentLatestReport_shouldThrowException_whenNoReportFound() {
        // Given
        Long studentId = TEST_USER_ID;
        Long teacherId = TEST_USER_ID + 1;

        // Mock权限验证通过和用户存在
        doNothing().when(userPermissionUtil).checkTeacherPermission(teacherId);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

        // Mock无报告记录
        when(studentReportRepository.findLatestByUserId(studentId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.getStudentLatestReport(teacherId, studentId))
                .isInstanceOf(com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException.class)
                .hasMessageContaining("该学生暂无学习报告记录");

        // 验证所有依赖都被调用
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(userRepository).findById(studentId);
        verify(studentReportRepository).findLatestByUserId(studentId);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("创建 SSE 发射器 - 当配置为 null 时使用默认值")
    void createConfiguredEmitter_shouldUseDefaults_whenConfigNull() {
        // Given
        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(null);

        // Mock StreamUtils 返回真实的 SseEmitter（使用默认超时时间）
        SseEmitter mockResultingEmitter = new SseEmitter(30000L); // 默认 30 秒
        when(streamUtils.createConfiguredEmitterWithLimit(anyLong(), anyLong()))
                .thenReturn(mockResultingEmitter);

        // When
        var emitter = service.createConfiguredEmitter();

        // Then
        assertThat(emitter).isNotNull();
        // 验证即使配置为 null 也能正常创建（优化后只调用一次读取配置）
        verify(aiStreamingProperties, times(1)).getSseEmitterTimeout();
        verify(streamUtils).createConfiguredEmitterWithLimit(anyLong(), anyLong());

        // 确保 AI 客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 更多私有方法测试 ==========

    @Test
    @DisplayName("构建学生报告提示词-验证答题记录整合")
    void buildStudentReportPrompt_shouldIntegrateAnswerRecords() throws Exception {
        // Given
        java.util.Map<Long, Question> questionMap = java.util.Map.of(TEST_QUESTION_ID, mockQuestion);

        // When
        String result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "buildStudentReportPrompt",
                new Class<?>[] { List.class, java.util.Map.class, String.class },
                mockAnswerRecords,
                questionMap,
                mockStudent.getRealName());

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("学习报告");
        assertThat(result).doesNotContain(mockStudent.getRealName());
        assertThat(result).contains("题目");
        assertThat(result).contains("答题历史");

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("构建教师分析提示词-验证班级数据分析")
    void buildTeacherAnalysisPrompt_shouldAnalyzeClassData() throws Exception {
        // Given
        java.util.Map<Long, Question> questionMap = java.util.Map.of(TEST_QUESTION_ID, mockQuestion);
        java.util.Map<Long, String> studentNames = java.util.Map.of(TEST_USER_ID, "测试学生");

        // When
        String result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "buildTeacherAnalysisPrompt",
                new Class<?>[] { List.class, java.util.Map.class, java.util.Map.class },
                mockAnswerRecords,
                questionMap,
                studentNames);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("教学分析报告");
        assertThat(result).contains("整体教学统计");
        assertThat(result).contains("各题目详细答题情况");

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("保存学生报告-验证报告持久化")
    void saveStudentReport_shouldPersistReport() throws Exception {
        // Given
        Long userId = TEST_USER_ID;
        String sessionId = TEST_SESSION_ID;
        String reportContent = "测试报告内容";

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(studentReportRepository.save(any(StudentReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "saveStudentReport",
                new Class<?>[] { Long.class, String.class, Long.class, String.class },
                userId,
                sessionId,
                null,
                reportContent);

        // Then
        // 验证依赖调用
        verify(userRepository).findById(userId);
        verify(studentReportRepository).save(any(StudentReport.class));

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("构建学生报告提示词-当无答题记录时正确处理")
    void buildStudentReportPrompt_shouldHandleEmptyRecords() throws Exception {
        // Given
        List<AnswerRecord> emptyRecords = List.of();
        java.util.Map<Long, Question> emptyQuestionMap = java.util.Map.of();

        // When
        String result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "buildStudentReportPrompt",
                new Class<?>[] { List.class, java.util.Map.class, String.class },
                emptyRecords,
                emptyQuestionMap,
                "该学生");

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("学习报告");

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("保存学生报告-当学生不存在时处理异常")
    void saveStudentReport_shouldHandleUserNotFound() throws Exception {
        // Given
        Long userId = TEST_USER_ID;
        String sessionId = TEST_SESSION_ID;
        String reportContent = "测试报告内容";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
        com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service, "saveStudentReport", new Class<?>[] { Long.class, String.class, Long.class, String.class },
                userId, sessionId, null, reportContent));

        // Then
        verify(userRepository).findById(userId);
        // 不应该查询或保存报告
        verify(studentReportRepository, never()).findByUserIdAndSessionIdAndEventId(anyLong(), anyString(), org.mockito.ArgumentMatchers.nullable(Long.class));
        verify(studentReportRepository, never()).save(any(StudentReport.class));

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 辅助方法 ==========

    private List<AnswerRecord> createTestAnswerRecords() {
        AnswerRecord record1 = new AnswerRecord();
        record1.setId(java.util.UUID.randomUUID().toString());
        record1.setUserId(TEST_USER_ID);
        record1.setQuestionId(TEST_QUESTION_ID);
        record1.setSessionId(TEST_SESSION_ID);
        record1.setUserAnswer("A");
        record1.setCorrect(false);
        record1.setAnsweredAt(LocalDateTime.now().minusHours(1));

        AnswerRecord record2 = new AnswerRecord();
        record2.setId(java.util.UUID.randomUUID().toString());
        record2.setUserId(TEST_USER_ID);
        record2.setQuestionId(TEST_QUESTION_ID + 1);
        record2.setSessionId(TEST_SESSION_ID);
        record2.setUserAnswer("B");
        record2.setCorrect(true);
        record2.setAnsweredAt(LocalDateTime.now());

        return List.of(record1, record2);
    }

    private StudentReport createMockStudentReport(Long studentId) {
        StudentReport report = new StudentReport();
        report.setId(1001L);
        report.setUserId(studentId);
        report.setReportContent("这是一个测试报告内容，包含了学生的学习分析和建议。");
        report.setCreatedAt(LocalDateTime.now().minusDays(1));
        report.setUpdatedAt(LocalDateTime.now().minusDays(1));
        return report;
    }

    private void verifyConfigPropertiesRead() {
        // 验证配置属性被读取的方法
        // 注意：AliyunAIReportServiceImpl.createConfiguredEmitter() 只调用
        // getSseEmitterTimeout() 一次
        verify(aiStreamingProperties, atLeastOnce()).getSseEmitterTimeout();
        // 不验证 getSseReconnectTime()，因为实现中没有调用
    }
}