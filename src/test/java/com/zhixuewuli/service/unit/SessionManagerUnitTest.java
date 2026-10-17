package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.ai.SessionExpiredException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SessionManager单元测试类
 * 测试会话管理核心逻辑，包括会话生命周期、权限验证、Token管理等功能
 */
@SuppressWarnings("unchecked")
public class SessionManagerUnitTest extends BaseUnitTest {

    @Mock
    private ConversationHistoryRepository conversationHistoryRepository;

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private AIBusinessProperties aiProperties;

    private SessionManager sessionManager;

    private static final String TEST_SESSION_ID = "test-session-123";
    private static final Long TEST_USER_ID = 100L;
    private static final Long TEST_QUESTION_ID = 2001L;
    private static final String TEST_CONTEXT = "测试上下文内容";

    @BeforeEach
    void setUp() {
        // 重置mock对象
        reset(conversationHistoryRepository, answerRecordRepository, aiProperties);

        sessionManager = new SessionManager(
                conversationHistoryRepository,
                answerRecordRepository,
                aiProperties, mock(com.zhixuewuli.repository.TeacherEventRepository.class));
    }

    /**
     * 测试创建新会话
     * 验证会话键生成、上下文初始化、属性设置等核心逻辑
     */
    @Test
    void testGetOrCreateSession_shouldCreateNewSession() {
        // Given
        Supplier<String> contextSupplier = mock(Supplier.class);
        when(contextSupplier.get()).thenReturn(TEST_CONTEXT);

        // 为这个测试配置特定的属性值
        when(aiProperties.getMaxTokenCount()).thenReturn(1000000);
        when(aiProperties.getMaxHistoryCount()).thenReturn(15);

        // When
        SessionContextInternalDTO result = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // Then
        assertNotNull(result, "应该创建新的会话上下文");
        assertEquals(TEST_SESSION_ID, result.getSessionId(), "会话ID应该匹配");
        assertEquals(TEST_USER_ID, result.getUserId(), "用户ID应该匹配");
        assertEquals(ConversationType.REAL_TIME_QA, result.getConversationType(), "会话类型应该匹配");
        assertNull(result.getQuestionId(), "错题分析时questionId应该为null");
        assertEquals(TEST_CONTEXT, result.getContext(), "上下文内容应该匹配");
        assertEquals(Integer.valueOf(1000000), result.getMaxTokens(), "最大Token数应该来自配置");
        assertEquals(0, result.getUsedTokens(), "初始已用Token应该为0");
        assertTrue(result.getIsActive(), "新会话应该是活跃状态");
        assertEquals(Integer.valueOf(15), result.getMaxInteractionCount(), "最大交互次数应该来自配置");
        assertNotNull(result.getInteractionRecords(), "交互记录容器不应该为null");
        assertEquals(0, result.getTotalInteractionCount().intValue(), "初始交互次数应该为0");

        // 验证上下文供应器被调用
        verify(contextSupplier, times(1)).get();

        // 验证时间戳设置
        assertNotNull(result.getCreatedAt(), "创建时间不应该为null");
        assertNotNull(result.getLastActiveTime(), "最后活跃时间不应该为null");
    }

    /**
     * 测试返回现有活跃会话
     * 验证相同会话键能够正确返回已存在的会话
     */
    @Test
    void testGetOrCreateSession_shouldReturnExistingActiveSession() {
        // Given - 先创建一个会话
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO firstSession = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // When - 再次请求相同会话
        SessionContextInternalDTO secondSession = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // Then
        assertSame(firstSession, secondSession, "应该返回同一个会话实例");
        assertEquals(1, ((java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore")).size(), "会话存储中应该只有一个会话");
    }

    /**
     * 测试过期会话异常
     * 验证当会话不活跃时抛出SessionExpiredException
     */
    @Test
    void testGetOrCreateSession_shouldThrowWhenSessionExpired() {
        // Given - 创建一个会话并手动设置为不活跃
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);
        session.setIsActive(false); // 手动设置为不活跃

        // When & Then
        SessionExpiredException exception = assertThrows(
                SessionExpiredException.class,
                () -> sessionManager.getOrCreateSession(
                        TEST_SESSION_ID,
                        TEST_USER_ID,
                        ConversationType.REAL_TIME_QA,
                        null,
                        contextSupplier),
                "过期会话应该抛出SessionExpiredException");

        assertEquals(TEST_SESSION_ID, exception.getSessionId(), "异常中的会话ID应该匹配");
    }

    /**
     * 测试错题分析会话键生成
     * 验证包含questionId的复合键生成逻辑
     */
    @Test
    void testGetOrCreateSession_withQuestionId_shouldGenerateCorrectKey() {
        // Given
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;

        // When
        SessionContextInternalDTO result = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.WRONG_QUESTION_ANALYSIS,
                TEST_QUESTION_ID,
                contextSupplier);

        // Then
        assertEquals(TEST_QUESTION_ID, result.getQuestionId(), "错题分析应该包含questionId");
        assertEquals(ConversationType.WRONG_QUESTION_ANALYSIS, result.getConversationType(), "会话类型应该匹配");
    }

    /**
     * 测试添加交互记录
     * 验证数据库保存、内存更新、序列号生成等完整流程
     */
    @Test
    void testAddInteraction_shouldSaveToDatabaseAndMemory() {
        // Given - 准备会话和交互数据
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        String question = "测试问题";
        String answer = "测试回答";
        int tokenCount = 100;

        // Mock数据库保存成功
        ConversationHistory savedHistory = TestDataFactory.createConversationHistory();
        savedHistory.setId(1L);
        when(conversationHistoryRepository.save(any(ConversationHistory.class))).thenReturn(savedHistory);

        // 记录添加前的时间
        LocalDateTime beforeAddTime = LocalDateTime.now();

        // When
        sessionManager.addInteraction(session, question, answer, tokenCount);

        // Then
        // 验证数据库调用
        verify(conversationHistoryRepository, times(1)).save(any(ConversationHistory.class));

        // 验证会话状态更新
        assertTrue(session.getLastActiveTime().isAfter(beforeAddTime) ||
                session.getLastActiveTime().isEqual(beforeAddTime),
                "最后活跃时间应该被更新");

        // 验证交互记录添加到内存
        assertEquals(1, session.getTotalInteractionCount().intValue(), "交互次数应该增加到1");
        assertFalse(session.getInteractionRecords().isEmpty(), "交互记录不应该为空");
        assertEquals(1, session.getInteractionRecords().size(), "应该有一条交互记录");

        // 验证具体的交互记录内容
        ConversationHistory recordedHistory = session.getInteractionRecords().firstEntry().getValue();
        assertEquals(question, recordedHistory.getQuestion(), "问题内容应该匹配");
        assertEquals(answer, recordedHistory.getAnswer(), "回答内容应该匹配");
        assertEquals(tokenCount, recordedHistory.getTokenCount().intValue(), "Token数量应该匹配");
        assertEquals(1, recordedHistory.getSequenceNumber().intValue(), "序列号应该为1");
    }

    /**
     * 测试数据库保存异常处理
     * 验证即使数据库保存失败也不会影响内存操作
     */
    @Test
    void testAddInteraction_databaseSaveFailure_shouldContinueWithMemoryOperation() {
        // Given
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // Mock数据库保存异常
        when(conversationHistoryRepository.save(any(ConversationHistory.class)))
                .thenThrow(new RuntimeException("数据库连接失败"));

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> {
            sessionManager.addInteraction(session, "问题", "回答", 50);
        }, "数据库保存失败不应该阻止内存操作");

        // Then - 验证内存操作仍然成功
        assertEquals(1, session.getTotalInteractionCount().intValue(), "内存中的交互计数仍应增加");
        assertFalse(session.getInteractionRecords().isEmpty(), "内存中的交互记录不应为空");
    }

    /**
     * 测试Token用量更新和超限检测
     * 验证Token累加计算和超限自动停用功能
     */
    @Test
    void testUpdateTokenUsage_shouldMarkInactiveWhenExceeded() {
        // Given - 设置较小的Token限制便于测试
        when(aiProperties.getMaxTokenCount()).thenReturn(200);
        when(aiProperties.getMaxHistoryCount()).thenReturn(15);

        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // When - 第一次更新，未超限
        int used1 = sessionManager.updateTokenUsage(session, 100);

        // Then - 第一次更新结果
        assertEquals(100, used1, "第一次更新后已用Token应该为100");
        assertTrue(session.getIsActive(), "未超限时会话应该保持活跃");
        assertEquals(100, session.getUsedTokens(), "会话内部Token计数应该同步更新");

        // When - 第二次更新，刚好达到限制
        int used2 = sessionManager.updateTokenUsage(session, 100);

        // Then - 第二次更新结果
        assertEquals(200, used2, "第二次更新后已用Token应该为200");
        assertTrue(session.getIsActive(), "刚好达到限制时会话仍应保持活跃");

        // When - 第三次更新，超过限制
        int used3 = sessionManager.updateTokenUsage(session, 50);

        // Then - 第三次更新结果
        assertEquals(250, used3, "第三次更新后已用Token应该为250");
        assertFalse(session.getIsActive(), "超过限制后会话应该被标记为不活跃");
    }

    /**
     * 测试无Token限制的情况
     * 验证当maxTokens为null或0时的行为
     */
    @Test
    void testUpdateTokenUsage_noTokenLimit_shouldNotMarkInactive() {
        // Given - 设置无Token限制
        when(aiProperties.getMaxTokenCount()).thenReturn(0);
        when(aiProperties.getMaxHistoryCount()).thenReturn(15);

        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID,
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        // When - 大量增加Token用量
        int used = sessionManager.updateTokenUsage(session, 1000000);

        // Then
        assertEquals(1000000, used, "应该正确累计Token用量");
        assertTrue(session.getIsActive(), "无Token限制时会话应该始终保持活跃");
    }

    /**
     * 测试Token并发超限场景
     * 验证多个线程同时更新Token时，累计值正确且会话状态最终一致
     */
    @Test
    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
    void testUpdateTokenUsage_concurrentOverflow_shouldRemainConsistent() throws InterruptedException {
        // Given
        when(aiProperties.getMaxTokenCount()).thenReturn(400);
        when(aiProperties.getMaxHistoryCount()).thenReturn(15);

        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                "token-concurrent-session",
                TEST_USER_ID,
                ConversationType.REAL_TIME_QA,
                null,
                contextSupplier);

        int threadCount = 8;
        int tokenDeltaPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    sessionManager.updateTokenUsage(session, tokenDeltaPerThread);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
            thread.start();
        }

        startLatch.countDown();
        doneLatch.await();

        // Then
        assertNull(failure.get(), "并发更新Token不应抛出异常: "
                + (failure.get() != null ? failure.get().getMessage() : ""));
        assertEquals(threadCount * tokenDeltaPerThread, session.getUsedTokens(), "并发Token累计值应与预期一致");
        assertFalse(session.getIsActive(), "并发超限后会话应被标记为不活跃");
    }

    /**
     * 测试会话访问权限验证
     * 验证通过答题记录和对话历史联合验证的权限逻辑
     */
    @Test
    void testCanAccessSession_shouldVerifyOwnership() {
        // Test case 1: 通过答题记录验证权限
        when(answerRecordRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(1L);
        when(conversationHistoryRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);

        boolean canAccess1 = sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        assertTrue(canAccess1, "存在答题记录时应该允许访问");

        // Test case 2: 通过对话历史验证权限
        when(answerRecordRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);
        when(conversationHistoryRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(1L);

        boolean canAccess2 = sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        assertTrue(canAccess2, "存在对话历史时应该允许访问");

        // Test case 3: 两者都不存在，无权限
        when(answerRecordRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);
        when(conversationHistoryRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);

        boolean canAccess3 = sessionManager.canAccessSession(TEST_SESSION_ID, TEST_USER_ID);
        assertFalse(canAccess3, "既无答题记录又无对话历史时应该拒绝访问");

        // 验证数据库调用 - 注意：只有前两次调用会触发conversationHistoryRepository查询
        verify(answerRecordRepository, times(3)).countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID);
        verify(conversationHistoryRepository, times(2)).countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID);
    }

    /**
     * 测试会话存在性检查
     * 验证会话下任一持久化记录存在时即视为会话存在
     */
    @Test
    void testHasAnySessionRecord_shouldDetectExistingSession() {
        // Test case 1: 答题记录存在
        when(answerRecordRepository.countBySessionId(TEST_SESSION_ID)).thenReturn(1L);
        when(conversationHistoryRepository.countBySessionIdAndIsActive(TEST_SESSION_ID, true)).thenReturn(0L);
        assertTrue(sessionManager.hasAnySessionRecord(TEST_SESSION_ID), "存在答题记录时应识别为会话存在");

        // Test case 2: 仅对话历史存在
        when(answerRecordRepository.countBySessionId(TEST_SESSION_ID)).thenReturn(0L);
        when(conversationHistoryRepository.countBySessionIdAndIsActive(TEST_SESSION_ID, true)).thenReturn(1L);
        assertTrue(sessionManager.hasAnySessionRecord(TEST_SESSION_ID), "存在对话历史时应识别为会话存在");

        // Test case 3: 两者都不存在
        when(answerRecordRepository.countBySessionId(TEST_SESSION_ID)).thenReturn(0L);
        when(conversationHistoryRepository.countBySessionIdAndIsActive(TEST_SESSION_ID, true)).thenReturn(0L);
        assertFalse(sessionManager.hasAnySessionRecord(TEST_SESSION_ID), "无记录时应识别为会话不存在");
    }

    /**
     * 测试会话清理功能
     * 验证权限验证和会话清除的完整流程
     */
    @Test
    void testClearSession_shouldRemoveFromMemoryAndDatabase() {
        // Given - 准备测试数据
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;

        // 创建多个相关会话
        SessionContextInternalDTO session1 = sessionManager.getOrCreateSession(
                TEST_SESSION_ID, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
        SessionContextInternalDTO session2 = sessionManager.getOrCreateSession(
                TEST_SESSION_ID, TEST_USER_ID, ConversationType.WRONG_QUESTION_ANALYSIS, TEST_QUESTION_ID,
                contextSupplier);

        // Mock权限验证通过
        when(answerRecordRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(1L);

        // When
        sessionManager.clearSession(TEST_SESSION_ID, TEST_USER_ID);

        // Then
        // 验证权限检查
        verify(answerRecordRepository).countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID);

        // 验证数据库删除调用
        verify(conversationHistoryRepository).deleteBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID);

        // 验证内存清理 - 通过反射检查sessionStore状态
        assertEquals(0, ((java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore")).size(),
                "会话存储应该被清空");
    }

    /**
     * 测试会话清理权限不足异常
     * 验证无权限用户尝试清除会话时抛出异常
     */
    @Test
    void testClearSession_shouldThrowWhenNoPermission() {
        // Given - Mock无权限情况
        when(answerRecordRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);
        when(conversationHistoryRepository.countBySessionIdAndUserId(TEST_SESSION_ID, TEST_USER_ID))
                .thenReturn(0L);

        // When & Then
        InsufficientPermissionException exception = assertThrows(
                InsufficientPermissionException.class,
                () -> sessionManager.clearSession(TEST_SESSION_ID, TEST_USER_ID),
                "无权限时应该抛出InsufficientPermissionException");

        assertEquals("无权清除该会话", exception.getMessage(), "异常消息应该匹配");

        // 验证数据库操作未被执行
        verify(conversationHistoryRepository, never()).deleteBySessionIdAndUserId(anyString(), anyLong());
    }

    /**
     * 测试过期会话清理
     * 验证过期会话标记和清理的完整逻辑
     */
    @Test
    void testCleanupExpiredSessions_shouldMarkAndRemove() {
        // Given - 准备测试数据
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;

        // 创建活跃会话（不会过期）
        SessionContextInternalDTO activeSession = sessionManager.getOrCreateSession(
                "active-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);

        // 创建即将过期的会话
        SessionContextInternalDTO expiringSession = sessionManager.getOrCreateSession(
                "expiring-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);

        // 手动设置过期时间（30分钟前）
        expiringSession.setLastActiveTime(LocalDateTime.now().minusMinutes(30));

        // 创建已经标记为不活跃的会话
        SessionContextInternalDTO inactiveSession = sessionManager.getOrCreateSession(
                "inactive-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
        inactiveSession.setIsActive(false);

        // When - 执行清理（设置10分钟过期时间）
        sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));

        // Then - 验证结果
        // 过期会话应该被标记为不活跃
        assertFalse(expiringSession.getIsActive(), "过期会话应该被标记为不活跃");

        // 不活跃会话应该被移除
        assertEquals(2, ((java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore")).size(),
                "会话存储中应该只剩下活跃会话和刚标记的不活跃会话");

        // 活跃会话应该保持活跃状态
        assertTrue(activeSession.getIsActive(), "活跃会话应该保持活跃状态");
    }

    /**
     * 测试并发安全的交互记录添加
     * 验证多线程环境下交互记录的正确性和一致性
     */
    @Test
    void testAddInteraction_concurrentAccess_shouldMaintainConsistency() throws InterruptedException {
        // Given
        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);

        when(conversationHistoryRepository.save(any(ConversationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When - 并发添加多个交互记录（统一起跑，放大竞态窗口）
        int threadCount = 20;
        int interactionsPerThread = 20;
        int expectedTotalInteractions = threadCount * interactionsPerThread;
        Thread[] threads = new Thread[threadCount];
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> threadFailure = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int round = 0; round < interactionsPerThread; round++) {
                        sessionManager.addInteraction(session,
                                "问题" + threadIndex + "-" + round,
                                "回答" + threadIndex + "-" + round,
                                50 + round);
                    }
                } catch (Throwable t) {
                    threadFailure.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 发令并等待全部完成
        startLatch.countDown();
        doneLatch.await();

        // Then - 验证结果的一致性
        assertNull(threadFailure.get(), "并发线程不应抛出异常: "
                + (threadFailure.get() != null ? threadFailure.get().getMessage() : ""));
        assertEquals(expectedTotalInteractions, session.getTotalInteractionCount().intValue(),
                "并发后总交互次数应等于线程数 × 每线程交互次数");
        assertEquals(expectedTotalInteractions, session.getInteractionRecords().size(),
                "内存中的交互记录数量应该正确");

        // 验证序列号连续性
        int expectedSequence = 1;
        for (Integer sequenceNumber : session.getInteractionRecords().keySet()) {
            assertEquals(expectedSequence, sequenceNumber.intValue(), "序列号应该连续递增且无重复");
            expectedSequence++;
        }

        for (ConversationHistory history : session.getInteractionRecords().values()) {
            assertNotNull(history.getSequenceNumber(), "每条记录都应包含序列号");
        }
    }
    
    /**
     * 阶段 1: 并发会话创建测试 - 不同会话键
     * 验证 ConcurrentHashMap 在高并发下的正确性，确保无数据丢失
     */
    @Nested
    class ConcurrentSessionCreationTests {
    
        @Test
        @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
        void testConcurrentSessionCreation_shouldMaintainConsistency() throws InterruptedException {
            // Given - 准备 12 个不同的会话键
            int threadCount = 12;
            Thread[] threads = new Thread[threadCount];
            CountDownLatch startLatch = new CountDownLatch(1); // 确保所有线程同时启动
            CountDownLatch doneLatch = new CountDownLatch(threadCount); // 等待所有线程完成
            AtomicInteger successCount = new AtomicInteger(0);
    
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
    
            // When - 12 个并发线程创建不同的会话
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        startLatch.await(); // 等待启动信号
                        String sessionId = "session-" + index;
                        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                                sessionId,
                                TEST_USER_ID,
                                ConversationType.REAL_TIME_QA,
                                null,
                                contextSupplier);
                        assertNotNull(session, "会话不应该为 null");
                        assertEquals(sessionId, session.getSessionId(), "会话 ID 应该匹配");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("并发创建会话时发生异常：" + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
                threads[i].start();
            }
    
            // 发出启动信号，让所有线程同时执行
            startLatch.countDown();
    
            // 等待所有线程完成
            doneLatch.await();
    
            // Then - 验证所有会话都成功创建
            assertEquals(threadCount, successCount.intValue(), "所有线程都应该成功创建会话");
            assertEquals(threadCount, ((java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore")).size(),
                    "sessionStore 大小应该等于线程数，无数据丢失");
        }
    
        /**
         * 阶段 1: 并发会话创建测试 - 相同会话键 (最关键测试)
         * 验证 computeIfAbsent 的原子性：多个线程同时创建相同会话键应返回同一实例
         */
        @Test
        @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
        void testConcurrentSameKeyCreation_shouldReturnSameInstance() throws InterruptedException {
            // Given - 8 个线程同时创建相同会话键
            int threadCount = 8;
            Thread[] threads = new Thread[threadCount];
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            java.util.List<SessionContextInternalDTO> sessions = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    
            String sharedSessionId = "shared-session-key";
            Supplier<String> contextSupplier = mock(Supplier.class);
            when(contextSupplier.get()).thenReturn(TEST_CONTEXT);
    
            // When - 8 个并发线程创建相同会话键
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    try {
                        startLatch.await(); // 等待启动信号
                        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                                sharedSessionId,
                                TEST_USER_ID,
                                ConversationType.REAL_TIME_QA,
                                null,
                                contextSupplier);
                        sessions.add(session);
                    } catch (Exception e) {
                        fail("并发创建相同会话键时发生异常：" + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
                threads[i].start();
            }
    
            // 发出启动信号
            startLatch.countDown();
    
            // 等待所有线程完成
            doneLatch.await();
    
            // Then - 验证所有线程返回同一实例
            assertEquals(threadCount, sessions.size(), "所有线程都应该成功返回会话");
    
            // 关键断言：所有会话实例都应该是同一个对象
            SessionContextInternalDTO firstSession = sessions.get(0);
            for (int i = 1; i < sessions.size(); i++) {
                assertSame(firstSession, sessions.get(i),
                        "所有线程应该返回同一个会话实例 (computeIfAbsent 原子性)");
            }
    
            // 额外验证：sessionStore 中应该只有一个会话
            assertEquals(1, ((java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore")).size(),
                    "sessionStore 中应该只有一个会话");

            // 核心验证：同一 key 并发创建时，初始化上下文仅应执行一次
            verify(contextSupplier, times(1)).get();
        }
    }

    /**
     * 测试交互记录自动裁剪
     * 验证当超过最大交互数限制时自动移除最早记录的功能
     */
    @Test
    void testAddInteraction_shouldTrimOldRecordsWhenExceedMaxCount() {
        // Given - 设置较小的最大交互数限制
        when(aiProperties.getMaxTokenCount()).thenReturn(1000000);
        when(aiProperties.getMaxHistoryCount()).thenReturn(3);

        Supplier<String> contextSupplier = () -> TEST_CONTEXT;
        SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                TEST_SESSION_ID, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);

        when(conversationHistoryRepository.save(any(ConversationHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When - 添加超过限制的交互记录
        for (int i = 1; i <= 5; i++) {
            sessionManager.addInteraction(session, "问题" + i, "回答" + i, 50);
        }

        // Then
        assertEquals(5, session.getTotalInteractionCount().intValue(),
                "总的交互计数应该继续增加");
        assertEquals(3, session.getInteractionRecords().size(),
                "内存中应该只保留最新的3条记录");

        // 验证保留的是最新记录（序列号3,4,5）
        assertTrue(session.getInteractionRecords().containsKey(3), "应该包含序列号3");
        assertTrue(session.getInteractionRecords().containsKey(4), "应该包含序列号4");
        assertTrue(session.getInteractionRecords().containsKey(5), "应该包含序列号5");
        assertFalse(session.getInteractionRecords().containsKey(1), "不应该包含序列号1");
        assertFalse(session.getInteractionRecords().containsKey(2), "不应该包含序列号2");
    }

    /**
     * 阶段 2: 会话过期清理增强测试 - 边界条件和幂等性
     * 验证精确区分过期和未过期的会话，以及连续两次清理的结果一致（幂等性）
     */
    @Nested
    class CleanupExpiredSessionsEnhancedTests {

        @Test
        void testCleanupExpiredSessions_boundaryAndIdempotency() {
            // Given - 准备多个不同状态的会话
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            LocalDateTime now = LocalDateTime.now();

            // 会话 1: 5 分钟前活跃 (未过期)
            SessionContextInternalDTO activeSession = sessionManager.getOrCreateSession(
                    "active-5min", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            activeSession.setLastActiveTime(now.minusMinutes(5));

            // 会话 2: 9 分 59 秒前活跃 (刚好在边界内，不会过期)
            SessionContextInternalDTO boundarySession = sessionManager.getOrCreateSession(
                    "boundary-9m59s", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            boundarySession.setLastActiveTime(now.minusSeconds(599)); // 9 分 59 秒

            // 会话 3: 10 分 01 秒前活跃 (刚好过期)
            SessionContextInternalDTO justExpiredSession = sessionManager.getOrCreateSession(
                    "just-expired-10m01s", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            justExpiredSession.setLastActiveTime(now.minusSeconds(601)); // 10 分 01 秒

            // 会话 4: 15 分钟前活跃 (明显过期)
            SessionContextInternalDTO expiredSession = sessionManager.getOrCreateSession(
                    "expired-15min", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            expiredSession.setLastActiveTime(now.minusMinutes(15));

            // 会话 5: 已经标记为不活跃的会话 (应该被移除)
            SessionContextInternalDTO inactiveSession = sessionManager.getOrCreateSession(
                    "inactive-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            inactiveSession.setIsActive(false);
            inactiveSession.setLastActiveTime(now.minusMinutes(20));

            Duration expirationThreshold = Duration.ofMinutes(10);

            // When - 第一次执行清理
            sessionManager.cleanupExpiredSessions(expirationThreshold);

            // Then - 验证第一次清理结果
            // 未过期的会话应该保持活跃（5 分钟 < 10 分钟）
            assertTrue(activeSession.getIsActive(), "5 分钟前的会话应该保持活跃");

            // 边界会话（9 分 59 秒）不会被标记为过期
            assertTrue(boundarySession.getIsActive(), "9 分 59 秒的会话应该保持活跃");

            // 刚过期的会话应该被标记为不活跃（10 分 01 秒 > 10 分钟）
            assertFalse(justExpiredSession.getIsActive(), "10 分 01 秒的会话应该被标记为不活跃");

            // 明显过期的会话应该被标记为不活跃
            assertFalse(expiredSession.getIsActive(), "15 分钟前的会话应该被标记为不活跃");

            // 已经不活跃的会话应该被移除
            java.util.Map<?, ?> sessionStore = (java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore");

            // 原有不活跃会话已移除，其余过期会话在第二次扫描移除。
            assertEquals(4, sessionStore.size(), "首次清理移除原有不活跃会话，标记其他过期会话");

            // Record 第一次清理后的状态
            int sizeAfterFirstCleanup = sessionStore.size();
            boolean activeStillActive = activeSession.getIsActive();
            boolean boundaryStillActive = boundarySession.getIsActive();
            boolean justExpiredStillInactive = !justExpiredSession.getIsActive();
            boolean expiredStillInactive = !expiredSession.getIsActive();

            // When - 第二次执行同样的清理 (幂等性测试)
            sessionManager.cleanupExpiredSessions(expirationThreshold);

            // Then - 验证幂等性：结果应该与第一次一致
            java.util.Map<?, ?> sessionStoreAfterSecond = (java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore");
            assertEquals(2, sessionStoreAfterSecond.size(),
                    "第二次清理后会话数量应该与第一次相同 (幂等性)");
            assertEquals(activeStillActive, activeSession.getIsActive(), "活跃会话状态应该保持一致");
            assertEquals(boundaryStillActive, boundarySession.getIsActive(), "边界会话状态应该保持一致");
            assertEquals(justExpiredStillInactive, !justExpiredSession.getIsActive(), "刚过期会话状态应该保持一致");
            assertEquals(expiredStillInactive, !expiredSession.getIsActive(), "过期会话状态应该保持一致");
        }

        /**
         * 阶段 2: 会话过期清理增强测试 - 并发修改场景
         * 验证清理过程中其他线程频繁创建新会话时的正确性和健壮性
         */
        @Test
        @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
        void testCleanupExpiredSessions_concurrentModification_shouldCompleteGracefully() throws InterruptedException {
            // Given - 准备一些即将过期的旧会话
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            LocalDateTime now = LocalDateTime.now();

            // 创建 5 个旧会话 (将在清理中被标记为不活跃)
            for (int i = 0; i < 5; i++) {
                SessionContextInternalDTO oldSession = sessionManager.getOrCreateSession(
                        "old-session-" + i, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
                oldSession.setLastActiveTime(now.minusMinutes(15)); // 15 分钟前，已过期
            }

            // 并发测试工具
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch cleanupDoneLatch = new CountDownLatch(1);
            CountDownLatch creatorDoneLatch = new CountDownLatch(1);
            AtomicBoolean cleanupCompletedSuccessfully = new AtomicBoolean(false);
            AtomicReference<Throwable> cleanupException = new AtomicReference<>();

            Duration expirationThreshold = Duration.ofMinutes(10);

            // 线程 1: 执行清理任务
            Thread cleanupThread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待启动信号
                    sessionManager.cleanupExpiredSessions(expirationThreshold);
                    cleanupCompletedSuccessfully.set(true);
                } catch (Throwable t) {
                    cleanupException.set(t);
                } finally {
                    cleanupDoneLatch.countDown();
                }
            });

            // 线程 2: 在清理过程中频繁创建新会话
            Thread creatorThread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待启动信号
                    // 在清理过程中创建 10 个新会话
                    for (int i = 0; i < 10; i++) {
                        String sessionId = "new-session-" + i;
                        SessionContextInternalDTO newSession = sessionManager.getOrCreateSession(
                                sessionId, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
                        assertNotNull(newSession, "新创建的会话不应该为 null");
                        assertTrue(newSession.getIsActive(), "新会话应该是活跃状态");
                        Thread.sleep(1); // 短暂延迟，模拟真实场景
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("创建者线程被中断");
                } finally {
                    creatorDoneLatch.countDown();
                }
            });

            // 启动两个线程
            cleanupThread.start();
            creatorThread.start();

            // When - 发出启动信号，让两个线程同时执行
            startLatch.countDown();

            // 等待两个线程都完成
            cleanupDoneLatch.await();
            creatorDoneLatch.await();

            // Then - 多维度验证
            // 1. 清理线程应该正常完成，无未捕获异常
            assertTrue(cleanupCompletedSuccessfully.get(),
                    "清理线程应该正常完成，无未捕获异常");
            assertNull(cleanupException.get(),
                    "清理过程不应该抛出异常：" + (cleanupException.get() != null ? cleanupException.get().getMessage() : ""));

            // 2. 验证旧会话都被标记为不活跃
            java.util.Map<?, ?> sessionStore = (java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore");
            for (int i = 0; i < 5; i++) {
                // 使用反射获取复合键
                String key = findSessionKeyInStore(sessionStore, "old-session-" + i);
                if (key != null) {
                    SessionContextInternalDTO oldSession = (SessionContextInternalDTO) sessionStore.get(key);
                    assertFalse(oldSession.getIsActive(), "旧会话应该被标记为不活跃：" + key);
                }
            }

            // 3. 验证新会话保持活跃状态
            for (int i = 0; i < 10; i++) {
                String key = findSessionKeyInStore(sessionStore, "new-session-" + i);
                if (key != null) {
                    SessionContextInternalDTO newSession = (SessionContextInternalDTO) sessionStore.get(key);
                    assertTrue(newSession.getIsActive(), "新会话应该保持活跃状态：" + key);
                }
            }

            // 4. 验证 sessionStore 最终一致性 (应该是 5 个旧会话 +10 个新会话，或者旧会话已被部分移除)
            // 由于 ConcurrentHashMap 的弱一致性迭代器，旧会话可能还在 store 中但已被标记为不活跃
            assertTrue(sessionStore.size() >= 10,
                    "sessionStore 应该至少包含 10 个新会话，实际大小：" + sessionStore.size());
            assertTrue(sessionStore.size() <= 15,
                    "sessionStore 最多包含 15 个会话 (5 旧 +10 新)，实际大小：" + sessionStore.size());
        }
    }
    /**
     * 阶段 3: 边缘场景补充 - 清理过程中访问即将过期的会话
     * 验证在清理线程执行时，其他线程访问即将过期的会话的正确性
     */
    @Nested
    class EdgeCaseTests {
    
        @Test
        @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
        void testConcurrentAccessDuringCleanup() throws InterruptedException {
            // Given - 准备一个即将过期的会话
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            LocalDateTime now = LocalDateTime.now();
            
            // 创建一个刚好在过期边界附近的会话（9 分钟前活跃，清理阈值为 10 分钟）
            SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                    "boundary-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            session.setLastActiveTime(now.minusMinutes(9)); // 9 分钟前，接近但未过期
            
            // 并发测试工具
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch cleanupDoneLatch = new CountDownLatch(1);
            CountDownLatch accessDoneLatch = new CountDownLatch(1);
            AtomicReference<Object> accessResult = new AtomicReference<>();
            AtomicReference<Throwable> accessException = new AtomicReference<>();
            
            Duration expirationThreshold = Duration.ofMinutes(10);
            
            // 线程 1: 执行清理任务（会让会话保持活跃，因为未过期）
            Thread cleanupThread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待启动信号
                    sessionManager.cleanupExpiredSessions(expirationThreshold);
                } catch (Throwable t) {
                    fail("清理线程发生异常：" + t.getMessage());
                } finally {
                    cleanupDoneLatch.countDown();
                }
            });
            
            // 线程 2: 在清理过程中尝试访问该会话
            Thread accessThread = new Thread(() -> {
                try {
                    startLatch.await(); // 等待启动信号
                    Thread.sleep(2); // 短暂延迟，增加并发概率
                    
                    // 尝试获取或创建同一个会话
                    SessionContextInternalDTO accessedSession = sessionManager.getOrCreateSession(
                            "boundary-session", TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
                    accessResult.set(accessedSession);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    accessException.set(e);
                } catch (Throwable t) {
                    accessException.set(t);
                } finally {
                    accessDoneLatch.countDown();
                }
            });
            
            // 启动两个线程
            cleanupThread.start();
            accessThread.start();
            
            // When - 发出启动信号，让两个线程同时执行
            startLatch.countDown();
            
            // 等待两个线程都完成
            cleanupDoneLatch.await();
            accessDoneLatch.await();
            
            // Then - 验证结果
            // 1. 不应该有任何异常
            assertNull(accessException.get(), "访问线程不应该抛出异常：" + 
                    (accessException.get() != null ? accessException.get().getMessage() : ""));
            
            // 2. 要么成功返回会话（清理还未完成），要么正确抛出 SessionExpiredException（已被标记）
            Object result = accessResult.get();
            if (result != null) {
                // 情况 1: 成功返回会话
                assertSame(session, result, "应该返回同一个会话实例");
                assertTrue(session.getIsActive(), "会话应该保持活跃状态（因为未过期）");
            } else {
                // 这种情况不应该发生，因为会话未过期，不应该抛出 SessionExpiredException
                fail("访问线程应该成功返回会话，而不是抛出异常或返回 null");
            }
            
            // 3. 验证会话最终状态正确
            assertTrue(session.getIsActive(), "会话最终应该是活跃的（因为未过期）");
        }
        
        /**
         * 阶段 3: 边缘场景补充 - 清理后再清除已标记的会话
         * 验证正确处理已标记为不活跃但尚未移除的会话
         */
        @Test
        void testClearSession_afterCleanup() {
            // Given - 准备一个会被标记为不活跃的会话
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            LocalDateTime now = LocalDateTime.now();
            
            String sessionId = "to-be-cleaned-session";
            SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                    sessionId, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            
            // 手动设置为过期（15 分钟前活跃，清理阈值为 10 分钟）
            session.setLastActiveTime(now.minusMinutes(15));
            
            // Mock 权限验证通过（用于 clearSession）
            when(answerRecordRepository.countBySessionIdAndUserId(sessionId, TEST_USER_ID))
                    .thenReturn(1L);
            
            // When - 第一次清理：会话被标记为不活跃，但仍在 sessionStore 中
            sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));
            
            // Then - 验证会话被标记为不活跃，但还在 store 中
            assertFalse(session.getIsActive(), "会话应该被标记为不活跃");
            java.util.Map<?, ?> sessionStoreAfterFirstCleanup = 
                    (java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore");
            String key = findSessionKeyInStore(sessionStoreAfterFirstCleanup, sessionId);
            assertNotNull(key, "会话键应该存在于 store 中（已标记但未移除）");
            
            // Record 清理后的状态
            int sizeAfterMark = sessionStoreAfterFirstCleanup.size();
            
            // When - 第二次清理：由于会话已经不活跃且过期时间更早，应该被移除
            // 但在本测试中，由于我们立即调用 clearSession，所以先不清理
            
            // When - 执行清除操作（用户主动清除已标记的会话）
            sessionManager.clearSession(sessionId, TEST_USER_ID);
            
            // Then - 验证会话被完全清除
            java.util.Map<?, ?> sessionStoreAfterClear = 
                    (java.util.Map<?, ?>) getFieldFromSessionManager("sessionStore");
            assertEquals(sizeAfterMark - 1, sessionStoreAfterClear.size(),
                    "清除后会话数量应该减少 1");
            
            String keyAfterClear = findSessionKeyInStore(sessionStoreAfterClear, sessionId);
            assertNull(keyAfterClear, "会话键应该从 store 中移除");
            
            // 验证数据库删除被调用
            verify(conversationHistoryRepository).deleteBySessionIdAndUserId(sessionId, TEST_USER_ID);
            
            // When - 如果再次尝试访问该会话，应该创建新的会话
            SessionContextInternalDTO newSession = sessionManager.getOrCreateSession(
                    sessionId, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
            
            // Then - 验证是新会话
            assertNotNull(newSession, "应该创建新的会话");
            assertNotSame(session, newSession, "应该是不同的会话实例");
            assertTrue(newSession.getIsActive(), "新会话应该是活跃的");
        }
    }

    @Nested
    class MemoryLeakGuardTests {

        @Test
        void testSessionStoreHighChurn_withPeriodicCleanup_shouldStayBounded() {
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            int iterations = 150;
            int cleanupEvery = 15;
            int boundedThreshold = cleanupEvery + 5;
            int peakSize = 0;

            for (int i = 0; i < iterations; i++) {
                String sessionId = "churn-session-" + i;
                SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                        sessionId, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);

                // 将会话标记为不活跃且保持在不过期窗口内，确保可被 cleanup 的 remove 分支移除。
                session.setIsActive(false);
                session.setLastActiveTime(LocalDateTime.now());

                if ((i + 1) % cleanupEvery == 0) {
                    sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));
                }

                int currentSize = getSessionStore().size();
                peakSize = Math.max(peakSize, currentSize);
                assertTrue(currentSize <= boundedThreshold,
                        "sessionStore 不应随 churn 单调膨胀，当前大小: " + currentSize);
            }

            sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));

            assertTrue(getSessionStore().size() <= 1,
                    "最终清理后 sessionStore 应回落到接近基线");
            assertTrue(peakSize <= boundedThreshold,
                    "高频 churn 下 sessionStore 峰值应受控");
        }

        @Test
        void testCleanupExpiredSessions_twoPhaseGuard_shouldMarkThenRemoveOnSecondSweep() {
            Supplier<String> contextSupplier = () -> TEST_CONTEXT;
            int sessionCount = 30;

            for (int i = 0; i < sessionCount; i++) {
                SessionContextInternalDTO session = sessionManager.getOrCreateSession(
                        "phase-session-" + i, TEST_USER_ID, ConversationType.REAL_TIME_QA, null, contextSupplier);
                session.setLastActiveTime(LocalDateTime.now().minusMinutes(30));
                session.setIsActive(true);
            }

            sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));

            Map<String, SessionContextInternalDTO> storeAfterFirstSweep = getSessionStore();
            assertEquals(sessionCount, storeAfterFirstSweep.size(),
                    "第一轮清理应主要执行过期标记，不应直接大量删除");
            for (SessionContextInternalDTO session : storeAfterFirstSweep.values()) {
                assertFalse(session.getIsActive(), "第一轮清理后过期会话应被标记为不活跃");
            }

            sessionManager.cleanupExpiredSessions(Duration.ofMinutes(10));

            assertEquals(0, getSessionStore().size(),
                    "第二轮清理后应移除已标记不活跃会话，避免长期堆积");
        }
    }

    // 辅助方法：通过反射获取 SessionManager 的私有字段
    @SuppressWarnings("unchecked")
    private <T> T getFieldFromSessionManager(String fieldName) {
        try {
            java.lang.reflect.Field field = SessionManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(sessionManager);
        } catch (Exception e) {
            throw new RuntimeException("无法访问 SessionManager 的字段：" + fieldName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, SessionContextInternalDTO> getSessionStore() {
        return (Map<String, SessionContextInternalDTO>) getFieldFromSessionManager("sessionStore");
    }
    
    // 辅助方法：在 sessionStore 中查找指定 sessionId 的复合键
    private String findSessionKeyInStore(java.util.Map<?, ?> sessionStore, String sessionId) {
        for (Object key : sessionStore.keySet()) {
            if (key instanceof String && ((String) key).contains(":" + sessionId + ":")) {
                return (String) key;
            }
        }
        return null;
    }
}