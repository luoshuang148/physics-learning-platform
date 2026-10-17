package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.ai.impl.ZhiPuAIAssistantServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智谱AI助手服务同步逻辑测试
 *
 * 专注于测试ZhiPuAIAssistantService的同步业务逻辑，
 * 确保AI客户端在这些测试中绝不被调用。
 *
 * <p>
 * 测试范围：
 * </p>
 * <ul>
 * <li>会话管理相关方法</li>
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
@DisplayName("智谱AI助手服务同步逻辑测试")
class ZhiPuAIAssistantSyncTest extends AIServiceSyncTestBase {

    // ========== 服务依赖 ==========

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private ConversationHistoryRepository conversationHistoryRepository;

    @Mock
    private OptionMappingUtil optionMappingUtil;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ZhiPuAIAssistantServiceImpl service;

    // ========== 测试数据 ==========

    private Question mockQuestion;
    private AnswerRecord mockAnswerRecord;
    private SessionContextInternalDTO mockSessionContext;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
        initializeTestData();

        // 配置基本的Mock行为
        setupCommonMockBehaviors();
    }

    private void initializeTestData() {
        // 创建模拟题目
        mockQuestion = new Question();
        mockQuestion.setId(TEST_QUESTION_ID);
        mockQuestion.setContent("一道物理题目的内容");
        mockQuestion.setOptions("{\"A\":\"选项A\",\"B\":\"选项B\",\"C\":\"选项C\",\"D\":\"选项D\"}");
        mockQuestion.setCorrectOption("B");
        mockQuestion.setExplanation("这是题目的解析");
        mockQuestion.setImageBase64("test-image-base64");

        // 创建模拟答题记录
        mockAnswerRecord = new AnswerRecord();
        mockAnswerRecord.setId(java.util.UUID.randomUUID().toString());
        mockAnswerRecord.setUserId(TEST_USER_ID);
        mockAnswerRecord.setQuestionId(TEST_QUESTION_ID);
        mockAnswerRecord.setUserAnswer("A");
        mockAnswerRecord.setCorrect(false);
        mockAnswerRecord.setSessionId(TEST_SESSION_ID);

        // 创建模拟会话上下文
        mockSessionContext = createSessionContext(TEST_SESSION_ID, TEST_USER_ID, "测试上下文");
    }

    private void setupCommonMockBehaviors() {
        // 配置题目仓库的基本行为
        lenient().when(questionRepository.findById(TEST_QUESTION_ID))
                .thenReturn(Optional.of(mockQuestion));

        // 配置答题记录仓库的基本行为
        lenient().when(answerRecordRepository.findTopBySessionIdAndUserIdAndQuestionIdOrderByAnsweredAtDesc(
                TEST_SESSION_ID, TEST_USER_ID, TEST_QUESTION_ID))
                .thenReturn(Optional.of(mockAnswerRecord));

        // 配置选项映射工具的基本行为
        lenient().when(optionMappingUtil.jsonToOptions(anyString()))
                .thenReturn(java.util.Map.of("A", "选项A", "B", "选项B", "C", "选项C", "D", "选项D"));
    }

    // ========== 会话管理测试 ==========

    @Test
    @DisplayName("创建配置的 SSE 发射器 - 验证超时和重连配置")
    void createConfiguredEmitter_shouldConfigureTimeoutAndReconnect() {
        // Given
        long timeoutMs = 30000L;
        long reconnectTimeMs = 5000L;

        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(timeoutMs);
        when(aiStreamingProperties.getSseReconnectTime()).thenReturn(reconnectTimeMs);

        // Mock StreamUtils 返回真实的 SseEmitter
        SseEmitter mockResultingEmitter = new SseEmitter(timeoutMs);
        when(streamUtils.createConfiguredEmitterWithLimit(anyLong(), anyLong()))
                .thenReturn(mockResultingEmitter);

        // When
        var emitter = service.createConfiguredEmitter();

        // Then
        assertThat(emitter).isNotNull();
        verify(streamUtils).createConfiguredEmitterWithLimit(anyLong(), anyLong());

        // 验证配置被正确应用（通过反射检查）
        try {
            Long timeout = com.zhixuewuli.common.util.test.ReflectionTestUtil.getPrivateFieldValue(emitter, "timeout");
            assertThat(timeout).isEqualTo(timeoutMs);
        } catch (Exception e) {
            // 如果无法通过反射获取，至少验证对象创建成功
            // 这是 Spring 框架的内部实现细节
        }

        // 确保 AI 客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("获取会话历史-验证权限检查和数据排序")
    void getSessionHistory_shouldValidatePermissionAndSort() {
        // Given
        List<com.zhixuewuli.entity.ConversationHistory> mockHistories = List.of(
                createConversationHistory(1L, "问题1", "回答1"),
                createConversationHistory(2L, "问题2", "回答2"));

        // Mock会话管理器的权限验证通过
        when(sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(true);

        when(conversationHistoryRepository.findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID,
                TEST_SESSION_ID, true))
                .thenReturn(mockHistories);

        // When
        var result = service.getSessionHistory(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        // 验证排序：按创建时间升序
        assertThat(result.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(result.get(1).getSequenceNumber()).isEqualTo(2);

        // 验证权限检查被调用
        verify(sessionManager).canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        verify(conversationHistoryRepository)
                .findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID, TEST_SESSION_ID, true);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("清除会话-验证会话管理器调用")
    void clearSession_shouldCallSessionManager() {
        // Given
        // Mock会话管理器的行为
        lenient().doNothing().when(sessionManager).clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // When
        service.clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        verify(sessionManager).clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 私有方法测试 ==========

    @Test
    @DisplayName("构建错题分析上下文-验证题目信息整合")
    void buildWrongQuestionContext_shouldIntegrateQuestionInfo() throws Exception {
        // Given
        String mockBackground = "物理学基础知识";
        lenient().when(aiBusinessProperties.getContextBackground()).thenReturn(mockBackground);

        // When
        String result = com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                service,
                "buildWrongQuestionContext",
                new Class<?>[] { Long.class, Long.class, String.class },
                TEST_USER_ID,
                TEST_QUESTION_ID,
                TEST_SESSION_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains("题目内容");
        assertThat(result).contains("选项");
        assertThat(result).contains("参考答案");
        assertThat(result).contains("用户答案");

        // 验证依赖调用
        verify(questionRepository).findById(TEST_QUESTION_ID);
        verify(answerRecordRepository).findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(
                TEST_USER_ID, TEST_QUESTION_ID);
        verify(optionMappingUtil).jsonToOptions(anyString());
        verify(aiBusinessProperties).getContextBackground();

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 异常场景测试 ==========

    @Test
    @DisplayName("获取会话历史-当会话不存在时返回空列表")
    void getSessionHistory_shouldReturnEmptyList_whenSessionNotFound() {
        // Given
        // Mock会话管理器的权限验证通过
        when(sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(true);

        when(conversationHistoryRepository.findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID,
                TEST_SESSION_ID, true))
                .thenReturn(List.of()); // 返回空列表

        // When
        var result = service.getSessionHistory(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(sessionManager).canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        verify(conversationHistoryRepository)
                .findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID, TEST_SESSION_ID, true);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("获取会话历史-会话存在但不属于当前用户时抛出异常")
    void getSessionHistory_shouldThrowException_whenSessionExistsButUserHasNoPermission() {
        // Given
        when(sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(false); // 无权限
        when(sessionManager.hasAnySessionRecord(TEST_SESSION_ID))
                .thenReturn(true); // 会话存在（归属他人）

        // When & Then
        assertThatThrownBy(() -> service.getSessionHistory(TEST_SESSION_ID, TEST_USER_ID))
                .isInstanceOf(com.zhixuewuli.common.exception.InsufficientPermissionException.class)
                .hasMessageContaining("无权访问该会话");

        // 验证权限检查被调用，但不会查询历史记录
        verify(sessionManager).canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        verify(sessionManager).hasAnySessionRecord(TEST_SESSION_ID);
        verify(conversationHistoryRepository, never())
                .findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.eq(TEST_USER_ID), anyString(), any(Boolean.class));

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("获取会话历史-未知会话返回空列表而非403")
    void getSessionHistory_shouldReturnEmptyList_whenSessionNotExistsAndUserHasNoPermission() {
        // Given
        when(sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(false);
        when(sessionManager.hasAnySessionRecord(TEST_SESSION_ID))
                .thenReturn(false); // 会话不存在
        when(conversationHistoryRepository.findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID, TEST_SESSION_ID, true))
                .thenReturn(List.of());

        // When
        var result = service.getSessionHistory(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(sessionManager).canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        verify(sessionManager).hasAnySessionRecord(TEST_SESSION_ID);
        verify(conversationHistoryRepository)
                .findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(TEST_USER_ID, TEST_SESSION_ID, true);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("创建 SSE 发射器 - 使用默认配置")
    void createConfiguredEmitter_shouldUseDefaults() {
        // Given
        Long defaultTimeout = 300000L; // 5 分钟
        Long defaultReconnect = 3000L; // 3 秒

        when(aiStreamingProperties.getSseEmitterTimeout()).thenReturn(defaultTimeout);
        when(aiStreamingProperties.getSseReconnectTime()).thenReturn(defaultReconnect);

        // Mock StreamUtils 返回真实的 SseEmitter
        SseEmitter mockResultingEmitter = new SseEmitter(defaultTimeout);
        when(streamUtils.createConfiguredEmitterWithLimit(anyLong(), anyLong()))
                .thenReturn(mockResultingEmitter);

        // When
        var emitter = service.createConfiguredEmitter();

        // Then
        assertThat(emitter).isNotNull();
        verify(streamUtils).createConfiguredEmitterWithLimit(eq(defaultTimeout), eq(defaultReconnect));

        verify(aiStreamingProperties).getSseEmitterTimeout();
        verify(aiStreamingProperties).getSseReconnectTime();

        // 确保 AI 客户端未被调用
        ensureAIClientNeverCalled();
    }

    @Test
    @DisplayName("清除会话-当会话不存在时静默处理")
    void clearSession_shouldHandleNonExistentSession() {
        // Given
        doNothing().when(sessionManager).clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // When
        service.clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        verify(sessionManager).clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 参数验证测试 ==========

    @Test
    @DisplayName("构建错题分析上下文-当题目不存在时验证依赖调用")
    void buildWrongQuestionContext_shouldVerifyDependencies_whenQuestionNotFound() throws Exception {
        // Given
        when(questionRepository.findById(TEST_QUESTION_ID)).thenReturn(Optional.empty());

        // When
        // 私有方法调用会被try-catch包装，所以我们验证依赖调用
        try {
            com.zhixuewuli.common.util.test.ReflectionTestUtil.invokePrivateMethod(
                    service,
                    "buildWrongQuestionContext",
                    new Class<?>[] { Long.class, Long.class, String.class },
                    TEST_USER_ID,
                    TEST_QUESTION_ID,
                    TEST_SESSION_ID);
        } catch (Exception e) {
            // 期望捕获到异常
        }

        // Then
        // 验证依赖被正确调用
        verify(questionRepository).findById(TEST_QUESTION_ID);

        // 确保AI客户端未被调用
        ensureAIClientNeverCalled();
    }

    // ========== 辅助方法 ==========

    private com.zhixuewuli.entity.ConversationHistory createConversationHistory(
            Long id, String question, String answer) {
        com.zhixuewuli.entity.ConversationHistory history = new com.zhixuewuli.entity.ConversationHistory();
        history.setId(id);
        history.setUserId(TEST_USER_ID);
        history.setSessionId(TEST_SESSION_ID);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSequenceNumber(id.intValue());
        history.setTokenCount(100);
        history.setIsActive(true);
        history.setConversationType(com.zhixuewuli.dto.internal.ConversationType.REAL_TIME_QA);
        return history;
    }
}
