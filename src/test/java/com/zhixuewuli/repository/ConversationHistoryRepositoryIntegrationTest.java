package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.entity.ConversationHistory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话历史仓库集成测试
 * 验证 H2 数据库连接和基本 CRUD 操作
 */
@Tag("integration-repository")
class ConversationHistoryRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ConversationHistoryRepository conversationHistoryRepository;

    @Test
    @Rollback
    void testSaveAndFindConversationHistory() {
        // Given
        ConversationHistory history = new ConversationHistory();
        history.setUserId(1L);
        history.setSessionId("session_" + System.currentTimeMillis());
        history.setQuestion("什么是牛顿第一定律？");
        history.setAnswer("牛顿第一定律，又称惯性定律，表明物体在不受外力作用时保持静止或匀速直线运动状态。");
        history.setSequenceNumber(1);
        history.setIsActive(true);
        history.setTokenCount(150);
        history.setConversationType(ConversationType.REAL_TIME_QA);

        // When
        ConversationHistory savedHistory = conversationHistoryRepository.save(history);
        Optional<ConversationHistory> foundHistory = conversationHistoryRepository.findById(savedHistory.getId());

        // Then
        assertThat(foundHistory).isPresent();
        assertThat(foundHistory.get().getUserId()).isEqualTo(1L);
        assertThat(foundHistory.get().getSessionId()).startsWith("session_");
        assertThat(foundHistory.get().getQuestion()).contains("牛顿第一定律");
        assertThat(foundHistory.get().getAnswer()).contains("惯性定律");
        assertThat(foundHistory.get().getSequenceNumber()).isEqualTo(1);
        assertThat(foundHistory.get().getTokenCount()).isEqualTo(150);
        assertThat(foundHistory.get().getConversationType()).isEqualTo(ConversationType.REAL_TIME_QA);
    }

    @Test
    @Rollback
    void testFindBySessionIdAndIsActiveOrderByCreatedAtAsc() {
        // Given - 清理数据以避免干扰
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "test_session_" + System.currentTimeMillis();

        ConversationHistory history1 = createConversation(1L, sessionId, "问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ConversationHistory history2 = createConversation(1L, sessionId, "问题 2", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ConversationHistory history3 = createConversation(1L, sessionId, "问题 3", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null);

        // When
        List<ConversationHistory> histories = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderByCreatedAtAsc(
                        sessionId, true);

        // Then - 验证按创建时间升序排列
        assertThat(histories).hasSize(3);
        assertThat(histories.get(0).getQuestion()).contains("问题 1");
        assertThat(histories.get(1).getQuestion()).contains("问题 2");
        assertThat(histories.get(2).getQuestion()).contains("问题 3");
    }

    @Test
    @Rollback
    void testFindBySessionIdAndIsActiveOrderBySequenceNumberAsc() {
        // Given
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "seq_order_session_" + System.currentTimeMillis();

        // 故意乱序创建
        ConversationHistory history3 = createConversation(1L, sessionId, "问题 3", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory history1 = createConversation(1L, sessionId, "问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory history2 = createConversation(1L, sessionId, "问题 2", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null);

        // When
        List<ConversationHistory> histories = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderBySequenceNumberAsc(
                        sessionId, true);

        // Then - 验证按序列号升序排列（而非创建时间）
        assertThat(histories).hasSize(3);
        assertThat(histories.get(0).getSequenceNumber()).isEqualTo(1);
        assertThat(histories.get(1).getSequenceNumber()).isEqualTo(2);
        assertThat(histories.get(2).getSequenceNumber()).isEqualTo(3);
    }

    @Test
    @Rollback
    void testFindBySessionIdAndConversationTypeAndQuestionIdAndIsActive() {
        // Given - 错题分析场景，相同题目 ID 共享会话
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "wrong_question_session_" + System.currentTimeMillis();
        Long questionId = 100L;

        ConversationHistory history1 = createConversation(1L, sessionId, "这道题为什么错了？", "分析：你的解题思路...", 1,
                ConversationType.WRONG_QUESTION_ANALYSIS, questionId);
        ConversationHistory history2 = createConversation(1L, sessionId, "那正确的做法是什么？", "正确解法：...", 2,
                ConversationType.WRONG_QUESTION_ANALYSIS, questionId);

        // 不同题目的会话
        ConversationHistory otherQuestion = createConversation(1L, sessionId, "另一道题的问题", "回答", 3,
                ConversationType.WRONG_QUESTION_ANALYSIS, 200L);

        // When
        List<ConversationHistory> histories = conversationHistoryRepository
                .findBySessionIdAndConversationTypeAndQuestionIdAndIsActiveOrderBySequenceNumberAsc(
                        sessionId, ConversationType.WRONG_QUESTION_ANALYSIS, questionId, true);

        // Then - 只返回相同题目 ID 的会话
        assertThat(histories).hasSize(2);
        assertThat(histories).extracting(ConversationHistory::getQuestionId)
                .containsOnly(questionId);
        assertThat(histories).extracting(ConversationHistory::getSequenceNumber)
                .containsExactly(1, 2);
    }

    @Test
    @Rollback
    void testFindBySessionIdAndConversationTypeAndIsActive() {
        // Given - 实时问答场景
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "realtime_session_" + System.currentTimeMillis();

        ConversationHistory realtime1 = createConversation(1L, sessionId, "什么是力？", "力是物体间的相互作用...", 1,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory realtime2 = createConversation(1L, sessionId, "力的单位是什么？", "牛顿...", 2,
                ConversationType.REAL_TIME_QA, null);

        // 不同类型的会话
        ConversationHistory wrongQuestion = createConversation(1L, sessionId, "题目分析", "分析...", 3,
                ConversationType.WRONG_QUESTION_ANALYSIS, 100L);

        // When
        List<ConversationHistory> realtimeHistories = conversationHistoryRepository
                .findBySessionIdAndConversationTypeAndIsActiveOrderBySequenceNumberAsc(
                        sessionId, ConversationType.REAL_TIME_QA, true);

        // Then - 只返回实时问答类型的会话
        assertThat(realtimeHistories).hasSize(2);
        assertThat(realtimeHistories).extracting(ConversationHistory::getConversationType)
                .containsOnly(ConversationType.REAL_TIME_QA);
    }

    @Test
    @Rollback
    void testFindRecentByUserIdAndSessionId() {
        // Given
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "recent_session_" + System.currentTimeMillis();
        Long userId = 1L;

        // 创建 5 条记录
        for (int i = 1; i <= 5; i++) {
            createConversation(userId, sessionId, "问题" + i, "回答" + i, i,
                    ConversationType.REAL_TIME_QA, null);
        }

        // When - 查询所有记录（该方法返回所有记录，limit 参数在@Query 中未实际使用）
        List<ConversationHistory> allRecords = conversationHistoryRepository.findRecentByUserIdAndSessionId(
                userId, sessionId, 10);

        // Then - 验证返回所有记录（按 sequenceNumber 倒序）
        assertThat(allRecords).hasSize(5);
        assertThat(allRecords.get(0).getSequenceNumber()).isEqualTo(5);
        assertThat(allRecords.get(1).getSequenceNumber()).isEqualTo(4);
        assertThat(allRecords.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(allRecords.get(3).getSequenceNumber()).isEqualTo(2);
        assertThat(allRecords.get(4).getSequenceNumber()).isEqualTo(1);

        // Note: 当前@Query实现未使用 limit 参数进行限制，返回所有匹配记录
        // 如需实现 limit 功能，需要修改@Query 使用原生 SQL 或分页参数
    }

    @Test
    @Rollback
    void testSumTokenCountBySessionId() {
        // Given
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "token_sum_session_" + System.currentTimeMillis();

        createConversation(1L, sessionId, "问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null, 100);
        createConversation(1L, sessionId, "问题 2", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null, 200);
        createConversation(1L, sessionId, "问题 3", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null, 150);

        // When
        Integer totalTokens = conversationHistoryRepository.sumTokenCountBySessionId(sessionId);

        // Then
        assertThat(totalTokens).isEqualTo(450);
    }

    @Test
    @Rollback
    void testDeleteBySessionId() {
        // Given
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "delete_session_" + System.currentTimeMillis();

        createConversation(1L, sessionId, "问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null);
        createConversation(1L, sessionId, "问题 2", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null);
        createConversation(1L, sessionId, "问题 3", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null);

        // When
        conversationHistoryRepository.deleteBySessionId(sessionId);

        // 验证该会话的所有记录都被删除
        List<ConversationHistory> remaining = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderByCreatedAtAsc(
                        sessionId, true);

        // Then
        assertThat(remaining).isEmpty();
    }

    @Test
    @Rollback
    void testCountBySessionIdAndUserId() {
        // Given
        cleanTable("CONVERSATION_HISTORY");

        String sessionId1 = "count_session_1_" + System.currentTimeMillis();
        String sessionId2 = "count_session_2_" + System.currentTimeMillis();
        Long userId1 = 1L;
        Long userId2 = 2L;

        // 用户 1 在会话 1 中有 3 条记录
        createConversation(userId1, sessionId1, "问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null);
        createConversation(userId1, sessionId1, "问题 2", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null);
        createConversation(userId1, sessionId1, "问题 3", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null);

        // 用户 1 在会话 2 中有 2 条记录
        createConversation(userId1, sessionId2, "问题 A", "回答 A", 1,
                ConversationType.REAL_TIME_QA, null);
        createConversation(userId1, sessionId2, "问题 B", "回答 B", 2,
                ConversationType.REAL_TIME_QA, null);

        // 用户 2 在会话 1 中有 1 条记录
        createConversation(userId2, sessionId1, "问题 X", "回答 X", 1,
                ConversationType.REAL_TIME_QA, null);

        // When & Then
        assertThat(conversationHistoryRepository.countBySessionIdAndUserId(sessionId1, userId1)).isEqualTo(3);
        assertThat(conversationHistoryRepository.countBySessionIdAndUserId(sessionId2, userId1)).isEqualTo(2);
        assertThat(conversationHistoryRepository.countBySessionIdAndUserId(sessionId1, userId2)).isEqualTo(1);
        assertThat(conversationHistoryRepository.countBySessionIdAndUserId(sessionId2, userId2)).isEqualTo(0);
    }

    @Test
    @Rollback
    void testUpdateConversationHistory() {
        // Given
        ConversationHistory history = new ConversationHistory();
        history.setUserId(1L);
        history.setSessionId("update_session_" + System.currentTimeMillis());
        history.setQuestion("原始问题");
        history.setAnswer("原始回答");
        history.setSequenceNumber(1);
        history.setIsActive(true);
        history.setTokenCount(100);
        history.setConversationType(ConversationType.REAL_TIME_QA);
        ConversationHistory savedHistory = conversationHistoryRepository.save(history);

        // When
        savedHistory.setQuestion("更新后的问题");
        savedHistory.setAnswer("更新后的回答");
        savedHistory.setTokenCount(200);
        ConversationHistory updatedHistory = conversationHistoryRepository.save(savedHistory);

        // Then
        assertThat(updatedHistory.getQuestion()).isEqualTo("更新后的问题");
        assertThat(updatedHistory.getAnswer()).isEqualTo("更新后的回答");
        assertThat(updatedHistory.getTokenCount()).isEqualTo(200);
    }

    @Test
    @Rollback
    void testDeactivateConversationHistory() {
        // Given
        String sessionId = "deactivate_session_" + System.currentTimeMillis();
        ConversationHistory history = createConversation(1L, sessionId, "问题", "回答", 1,
                ConversationType.REAL_TIME_QA, null);

        // When - 设置非活跃状态
        history.setIsActive(false);
        conversationHistoryRepository.save(history);

        // 验证查询活跃记录时不包含
        List<ConversationHistory> activeHistories = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderByCreatedAtAsc(
                        sessionId, true);

        // Then
        assertThat(activeHistories).isEmpty();

        // 验证查询非活跃记录
        List<ConversationHistory> inactiveHistories = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderByCreatedAtAsc(
                        sessionId, false);
        assertThat(inactiveHistories).hasSize(1);
        assertThat(inactiveHistories.get(0).getIsActive()).isFalse();
    }

    @Test
    @Rollback
    void testMultipleUsersInSameSession() {
        // Given - 模拟多个用户在同一会话中的对话
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "multi_user_session_" + System.currentTimeMillis();

        ConversationHistory user1History1 = createConversation(1L, sessionId, "用户 1 问题 1", "回答 1", 1,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory user2History1 = createConversation(2L, sessionId, "用户 2 问题 1", "回答 2", 2,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory user1History2 = createConversation(1L, sessionId, "用户 1 问题 2", "回答 3", 3,
                ConversationType.REAL_TIME_QA, null);
        ConversationHistory user2History2 = createConversation(2L, sessionId, "用户 2 问题 2", "回答 4", 4,
                ConversationType.REAL_TIME_QA, null);

        // When - 查询所有用户的对话
        List<ConversationHistory> allHistories = conversationHistoryRepository
                .findBySessionIdAndIsActiveOrderBySequenceNumberAsc(
                        sessionId, true);

        // When - 按用户 ID 过滤查询
        List<ConversationHistory> user1Histories = conversationHistoryRepository.findRecentByUserIdAndSessionId(
                1L, sessionId, 10);
        List<ConversationHistory> user2Histories = conversationHistoryRepository.findRecentByUserIdAndSessionId(
                2L, sessionId, 10);

        // Then
        assertThat(allHistories).hasSize(4);
        assertThat(user1Histories).hasSize(2);
        assertThat(user1Histories).extracting(ConversationHistory::getUserId).containsOnly(1L);
        assertThat(user2Histories).hasSize(2);
        assertThat(user2Histories).extracting(ConversationHistory::getUserId).containsOnly(2L);
    }

    @Test
    @Rollback
    void testWrongQuestionAnalysisWithSameQuestionId() {
        // Given - 相同题目 ID 的错题分析应共享会话历史
        cleanTable("CONVERSATION_HISTORY");

        String sessionId = "same_question_session_" + System.currentTimeMillis();
        Long questionId = 999L;

        // 第一次分析
        createConversation(1L, sessionId, "这道题我为什么错了？", "分析：概念理解有误...", 1,
                ConversationType.WRONG_QUESTION_ANALYSIS, questionId, 180);

        // 基于同一题目的追问
        createConversation(1L, sessionId, "能再详细解释一下吗？", "详细解释：...", 2,
                ConversationType.WRONG_QUESTION_ANALYSIS, questionId, 220);

        // 不同题目的分析
        createConversation(1L, sessionId, "另一道题的分析", "分析：...", 3,
                ConversationType.WRONG_QUESTION_ANALYSIS, 888L, 150);

        // When - 查询特定题目的所有分析
        List<ConversationHistory> questionHistories = conversationHistoryRepository
                .findBySessionIdAndConversationTypeAndQuestionIdAndIsActiveOrderBySequenceNumberAsc(
                        sessionId, ConversationType.WRONG_QUESTION_ANALYSIS, questionId, true);

        // Then - 只返回相同题目 ID 的记录
        assertThat(questionHistories).hasSize(2);
        assertThat(questionHistories).extracting(ConversationHistory::getQuestionId)
                .containsOnly(questionId);
        assertThat(questionHistories.get(0).getTokenCount() + questionHistories.get(1).getTokenCount())
                .isEqualTo(400); // 180 + 220
    }

    /**
     * 辅助方法：创建会话历史
     */
    private ConversationHistory createConversation(Long userId, String sessionId, String question,
            String answer, Integer sequenceNumber, ConversationType type, Long questionId) {
        return createConversation(userId, sessionId, question, answer, sequenceNumber, type, questionId, 100);
    }

    /**
     * 辅助方法：创建会话历史（可指定 token 数）
     */
    private ConversationHistory createConversation(Long userId, String sessionId, String question,
            String answer, Integer sequenceNumber, ConversationType type, Long questionId, int tokenCount) {
        ConversationHistory history = new ConversationHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId);
        history.setQuestion(question);
        history.setAnswer(answer);
        history.setSequenceNumber(sequenceNumber);
        history.setIsActive(true);
        history.setTokenCount(tokenCount);
        history.setConversationType(type);
        history.setQuestionId(questionId);
        return conversationHistoryRepository.save(history);
    }
}
