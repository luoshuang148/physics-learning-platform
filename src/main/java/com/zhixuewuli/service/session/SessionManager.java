package com.zhixuewuli.service.session;

import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.ai.SessionExpiredException;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 会话管理器
 * 统一管理AI会话的上下文、历史记录、token统计和生命周期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManager {

    private final ConversationHistoryRepository conversationHistoryRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final AIBusinessProperties aiProperties;

    // 会话存储：复合键 -> SessionContextInternalDTO
    private final Map<String, SessionContextInternalDTO> sessionStore = new ConcurrentHashMap<>();

    /**
     * 生成复合键
     * 格式：sessionId:conversationType[:questionId]（错题分析和教师题目分析时包含questionId）
     */
    private String generateSessionKey(String sessionId, ConversationType conversationType, Long questionId) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(sessionId).append(":").append(conversationType.name());
        if ((conversationType == ConversationType.WRONG_QUESTION_ANALYSIS
                || conversationType == ConversationType.TEACHER_QUESTION_ANALYSIS)
                && questionId != null) {
            keyBuilder.append(":").append(questionId);
        }
        return keyBuilder.toString();
    }

    /**
     * 获取或创建会话上下文
     *
     * @param sessionId              会话ID
     * @param userId                 用户ID
     * @param conversationType       会话类型
     * @param questionId             题目ID（可为null）
     * @param initialContextSupplier 创建新会话时提供初始上下文的函数（业务方实现）
     * @return 会话上下文（保证活跃）
     * @throws SessionExpiredException 如果会话已过期
     */
    public SessionContextInternalDTO getOrCreateSession(String sessionId, Long userId,
            ConversationType conversationType, Long questionId,
            Supplier<String> initialContextSupplier) {
        String sessionKey = userId + ":" + generateSessionKey(sessionId, conversationType, questionId);
        SessionContextInternalDTO existing = sessionStore.get(sessionKey);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                throw new SessionExpiredException(sessionId);
            }
            return existing;
        }
        // Admission is serialized only for cache misses; existing-session reads stay concurrent.
        synchronized (sessionStore) {
        int capacity = aiProperties.getMaxCachedSessions() > 0 ? aiProperties.getMaxCachedSessions() : 1000;
        if (!sessionStore.containsKey(sessionKey) && sessionStore.size() >= capacity) {
            cleanupExpiredSessions(aiProperties.getSessionExpiration());
            if (sessionStore.size() >= capacity) {
                throw new com.zhixuewuli.common.exception.BusinessException("会话缓存已满，请稍后重试");
            }
        }
        return sessionStore.computeIfAbsent(sessionKey, key -> {
            log.info("创建新会话上下文 - 会话ID: {}, 用户ID: {}, 类型: {}, 题目ID: {}",
                    sessionId, userId, conversationType, questionId);
            String initialContext = initialContextSupplier.get();
            SessionContextInternalDTO context = SessionContextInternalDTO.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .conversationType(conversationType)
                    .questionId(questionId)
                    .context(initialContext)
                    .maxTokens(aiProperties.getMaxTokenCount())
                    .usedTokens(new AtomicInteger(0))
                    .createdAt(LocalDateTime.now())
                    .lastActiveTime(LocalDateTime.now())
                    .isActive(true)
                    .maxInteractionCount(aiProperties.getMaxHistoryCount())
                    .interactionRecords(new ConcurrentSkipListMap<>())
                    .totalInteractionCount(0)
                    .build();
            var persisted = conversationHistoryRepository
                    .findByUserIdAndSessionIdAndConversationTypeAndQuestionIdAndIsActiveOrderBySequenceNumberAsc(
                            userId, sessionId, conversationType, questionId, true);
            long tokens = 0;
            int sequence = 0;
            for (ConversationHistory history : persisted) {
                context.addInteractionRecord(history);
                sequence = Math.max(sequence, history.getSequenceNumber() == null ? 0 : history.getSequenceNumber());
                tokens += history.getTokenCount() == null ? 0 : Math.max(0, history.getTokenCount());
            }
            context.setTotalInteractionCount(sequence);
            context.setUsedTokens((int) Math.min(Integer.MAX_VALUE, tokens));
            if (context.getMaxTokens() > 0 && tokens > context.getMaxTokens()) {
                throw new SessionExpiredException(sessionId);
            }
            return context;
        });
        }
    }

    /**
     * 添加一次交互记录（用户问题 + AI回答）
     * 会自动保存到数据库并更新内存中的历史记录
     */
    public void addInteraction(SessionContextInternalDTO context, String question, String answer, int tokenCount) {
        context.setLastActiveTime(LocalDateTime.now());
        int seq = context.generateNextSequence(); // 内部已加写锁

        ConversationHistory history = new ConversationHistory();
        history.setUserId(context.getUserId());
        history.setSessionId(context.getSessionId());
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSequenceNumber(seq);
        history.setTokenCount(tokenCount);
        history.setConversationType(context.getConversationType());
        history.setQuestionId(context.getQuestionId());
        history.setIsActive(context.getIsActive());

        // 先保存数据库
        try {
            conversationHistoryRepository.save(history);
            log.debug("对话历史保存成功 - 会话ID: {}, 序列号: {}", context.getSessionId(), seq);
        } catch (Exception e) {
            log.error("对话历史保存失败 - 会话ID: {}, 用户ID: {}, 序列号: {}, 问题: {}",
                    context.getSessionId(), context.getUserId(), seq, question, e);
            // 数据库保存失败不影响主流程，继续执行内存操作
        }

        // 再添加到内存（加写锁已在 addInteractionRecord 内）
        context.addInteractionRecord(history);
    }

    /**
     * 更新token用量并检查是否超限
     * 如果超限会自动将会话标记为不活跃
     *
     * @return 更新后的总用量
     */
    public int updateTokenUsage(SessionContextInternalDTO context, int delta) {
        int newUsed = context.addAndGetUsedTokens(delta);
        Integer maxTokens = context.getMaxTokens();
        if (maxTokens != null && maxTokens > 0 && newUsed > maxTokens) {
            context.setIsActive(false);
            log.warn("会话token达到上限 - 会话ID: {}, 已用: {}, 上限: {}",
                    context.getSessionId(), newUsed, maxTokens);
        }
        return newUsed;
    }

    /**
     * 检查用户是否有权访问会话
     * 通过 AnswerRecord 和 ConversationHistory 联合验证
     */
    public boolean canAccessSession(String sessionId, Long userId) {
        long answerCount = answerRecordRepository.countBySessionIdAndUserId(sessionId, userId);
        if (answerCount > 0) {
            return true;
        }
        long historyCount = conversationHistoryRepository.countBySessionIdAndUserId(sessionId, userId);
        return historyCount > 0;
    }

    /**
     * 判断会话是否存在任意持久化记录（不区分用户）
     */
    public boolean hasAnySessionRecord(String sessionId) {
        long answerCount = answerRecordRepository.countBySessionId(sessionId);
        if (answerCount > 0) {
            return true;
        }
        long historyCount = conversationHistoryRepository.countBySessionIdAndIsActive(sessionId, true);
        return historyCount > 0;
    }

    /**
     * 检查是否在测试环境中
     * 通过检查是否有特定系统属性或类路径来判断
     */
    private boolean isTestEnvironment() {
        return System.getProperty("spring.profiles.active") != null &&
                System.getProperty("spring.profiles.active").contains("test") ||
                Thread.currentThread().getContextClassLoader().getResource("application-test.yml") != null;
    }

    /**
     * 惰性清理过期会话
     * 1. 将过期会话标记为不活跃
     * 2. 移除已标记的不活跃会话
     *
     * @param expiration 过期时长（如 30分钟）
     */
    public void cleanupExpiredSessions(Duration expiration) {
        try {
            log.info("开始清理过期会话，当前会话总数: {}", sessionStore.size());
            LocalDateTime expiryThreshold = LocalDateTime.now().minus(expiration);
            int marked = 0;
            int removed = 0;

            Iterator<Map.Entry<String, SessionContextInternalDTO>> iterator = sessionStore.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, SessionContextInternalDTO> entry = iterator.next();
                SessionContextInternalDTO session = entry.getValue();

                session.getLock().writeLock().lock();
                try {
                    if (!Boolean.TRUE.equals(session.getIsActive())) {
                        iterator.remove();
                        removed++;
                    } else if (session.getLastActiveTime().isBefore(expiryThreshold)) {
                        session.setIsActive(false);
                        marked++;
                    }
                } finally {
                    session.getLock().writeLock().unlock();
                }
            }
            log.info("会话清理完成，标记了 {} 个过期会话，移除了 {} 个不活跃会话，剩余 {}",
                    marked, removed, sessionStore.size());
        } catch (Exception e) {
            log.error("清理过期会话时发生异常", e);
        }
    }

    /**
     * 清除指定会话（从内存和数据库）
     * 需先验证用户权限
     */
    @org.springframework.transaction.annotation.Transactional
    public void clearSession(String sessionId, Long userId) {
        if (!canAccessSession(sessionId, userId)) {
            throw new InsufficientPermissionException("无权清除该会话");
        }
        // 从内存中移除所有以该sessionId开头的复合键
        sessionStore.entrySet().removeIf(entry -> userId.equals(entry.getValue().getUserId()) && sessionId.equals(entry.getValue().getSessionId()));
        // 从数据库中删除
        conversationHistoryRepository.deleteBySessionIdAndUserId(sessionId, userId);
        log.info("会话清除成功 - 会话ID: {}, 用户ID: {}", sessionId, userId);
    }
}