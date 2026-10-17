package com.zhixuewuli.repository;

import com.zhixuewuli.entity.ConversationHistory;
import com.zhixuewuli.dto.internal.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversationHistoryRepository extends JpaRepository<ConversationHistory, Long> {
        List<ConversationHistory> findByUserIdAndSessionIdAndIsActiveOrderByCreatedAtAsc(
                        Long userId, String sessionId, Boolean active);
        List<ConversationHistory> findByUserIdAndSessionIdAndConversationTypeAndQuestionIdAndIsActiveOrderBySequenceNumberAsc(
                        Long userId, String sessionId, ConversationType type, Long questionId, Boolean active);
        @org.springframework.transaction.annotation.Transactional
        void deleteBySessionIdAndUserId(String sessionId, Long userId);


        /**
         * 根据会话 ID 查找活跃的对话历史（按创建时间升序）
         * 
         * <p><strong>查询条件：</strong></p>
         * <ul>
         *   <li>sessionId = ?1 - 会话 ID 匹配</li>
         *   <li>isActive = true - 仅查询活跃记录</li>
         * </ul>
         * 
         * @param sessionId 会话 ID
         * @param isActive  是否活跃
         * @return 对话历史列表
         */
        List<ConversationHistory> findBySessionIdAndIsActiveOrderByCreatedAtAsc(
                        String sessionId, Boolean isActive);

        /**
         * 根据会话 ID 查找活跃的对话历史（按序列号升序）
         * 
         * <p><strong>查询条件：</strong></p>
         * <ul>
         *   <li>sessionId = ?1 - 会话 ID 匹配</li>
         *   <li>isActive = true - 仅查询活跃记录</li>
         * </ul>
         * 
         * @param sessionId 会话 ID
         * @param isActive  是否活跃
         * @return 对话历史列表
         */
        List<ConversationHistory> findBySessionIdAndIsActiveOrderBySequenceNumberAsc(
                        String sessionId, Boolean isActive);

        /**
         * 根据会话 ID、会话类型和题目 ID 查找活跃的对话历史
         * 
         * <p>用于错题分析场景，相同题目 ID 的对话历史可以共享。</p>
         * 
         * <p><strong>查询条件：</strong></p>
         * <ul>
         *   <li>sessionId = ?1 - 会话 ID 匹配</li>
         *   <li>conversationType = ?2 - 会话类型匹配</li>
         *   <li>questionId = ?3 - 题目 ID 匹配</li>
         *   <li>isActive = true - 仅查询活跃记录</li>
         * </ul>
         * 
         * @param sessionId      会话 ID
         * @param conversationType 会话类型
         * @param questionId     题目 ID
         * @param isActive       是否活跃
         * @return 对话历史列表
         */
        List<ConversationHistory> findBySessionIdAndConversationTypeAndQuestionIdAndIsActiveOrderBySequenceNumberAsc(
                        String sessionId, ConversationType conversationType, Long questionId, Boolean isActive);

        /**
         * 根据会话 ID 和会话类型查找活跃的对话历史
         * 
         * <p>用于实时问答场景。</p>
         * 
         * <p><strong>查询条件：</strong></p>
         * <ul>
         *   <li>sessionId = ?1 - 会话 ID 匹配</li>
         *   <li>conversationType = ?2 - 会话类型匹配</li>
         *   <li>isActive = true - 仅查询活跃记录</li>
         * </ul>
         * 
         * @param sessionId      会话 ID
         * @param conversationType 会话类型
         * @param isActive       是否活跃
         * @return 对话历史列表
         */
        List<ConversationHistory> findBySessionIdAndConversationTypeAndIsActiveOrderBySequenceNumberAsc(
                        String sessionId, ConversationType conversationType, Boolean isActive);

        /**
         * 根据用户 ID 和会话 ID 查找最近的 N 条记录
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ch FROM ConversationHistory ch 
         * WHERE ch.userId = ?1             -- 用户 ID 匹配
         *   AND ch.sessionId = ?2          -- 会话 ID 匹配
         *   AND ch.isActive = true         -- 仅查询活跃记录
         * ORDER BY ch.sequenceNumber DESC  -- 按序列号降序
         * }</pre>
         * 
         * @param userId    用户 ID
         * @param sessionId 会话 ID
         * @param limit     返回记录数量限制
         * @return 对话历史列表
         */
        @Query("SELECT ch FROM ConversationHistory ch WHERE ch.userId = ?1 AND ch.sessionId = ?2 " +
                        "AND ch.isActive = true ORDER BY ch.sequenceNumber DESC")
        List<ConversationHistory> findRecentByUserIdAndSessionId(Long userId, String sessionId, int limit);

        /**
         * 统计会话中的总 token 数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT SUM(ch.tokenCount) FROM ConversationHistory ch 
         * WHERE ch.sessionId = ?1          -- 会话 ID 匹配
         *   AND ch.isActive = true         -- 仅统计活跃记录
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @return 总 token 数
         */
        @Query("SELECT SUM(ch.tokenCount) FROM ConversationHistory ch WHERE ch.sessionId = ?1 AND ch.isActive = true")
        Integer sumTokenCountBySessionId(String sessionId);

        /**
         * 删除指定会话的历史记录
         * 
         * <p><strong>查询条件：</strong>sessionId = ?1</p>
         * 
         * @param sessionId 会话 ID
         */
        void deleteBySessionId(String sessionId);

        /**
         * 统计会话中的对话记录数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ch) FROM ConversationHistory ch 
         * WHERE ch.sessionId = ?1          -- 会话 ID 匹配
         *   AND ch.userId = ?2             -- 用户 ID 匹配
         *   AND ch.isActive = true         -- 仅统计活跃记录
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @param userId    用户 ID
         * @return 对话记录数
         */
        @Query("SELECT COUNT(ch) FROM ConversationHistory ch WHERE ch.sessionId = ?1 AND ch.userId = ?2 AND ch.isActive = true")
        long countBySessionIdAndUserId(String sessionId, Long userId);

        /**
         * 统计会话下所有活跃对话记录数（不区分用户）
         *
         * @param sessionId 会话 ID
         * @param isActive  是否活跃
         * @return 活跃记录数
         */
        long countBySessionIdAndIsActive(String sessionId, Boolean isActive);
}
