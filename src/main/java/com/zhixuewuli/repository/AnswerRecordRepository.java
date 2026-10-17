package com.zhixuewuli.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.zhixuewuli.entity.AnswerRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerRecordRepository extends JpaRepository<AnswerRecord, String> {
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByEventIdOrderByAnsweredAtDesc(Long eventId);

        @Override
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findAll();

        @Query(value = "SELECT new com.zhixuewuli.dto.response.AnswerRecordListRow(" +
                "a.id,a.userId,a.questionId,a.sessionId,a.userAnswer,a.correct,a.answeredAt,a.errorReason," +
                "u.realName,q.content,q.options,q.correctOption,q.explanation,q.difficulty) " +
                "FROM AnswerRecord a JOIN a.question q JOIN a.user u " +
                "WHERE (:wrongOnly = false OR a.correct = false) " +
                "AND LOWER(u.realName) LIKE LOWER(:name) ESCAPE '!' " +
                "ORDER BY a.answeredAt DESC, a.id DESC",
                countQuery = "SELECT COUNT(a) FROM AnswerRecord a JOIN a.user u " +
                "WHERE (:wrongOnly = false OR a.correct = false) " +
                "AND LOWER(u.realName) LIKE LOWER(:name) ESCAPE '!'")
        org.springframework.data.domain.Page<com.zhixuewuli.dto.response.AnswerRecordListRow> findTeacherPage(
                @org.springframework.data.repository.query.Param("wrongOnly") boolean wrongOnly,
                @org.springframework.data.repository.query.Param("name") String name, Pageable pageable);


        /**
         * 根据会话ID和题目ID查找记录（用于幂等性检查）
         */
        AnswerRecord findBySessionIdAndQuestionId(String sessionId, Long questionId);

        /**
         * 查询用户的错题记录
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.userId = ?1           -- 用户 ID 匹配
         *   AND ar.correct = false       -- 仅查询错误记录
         * ORDER BY ar.answeredAt DESC    -- 按答题时间降序
         * }</pre>
         * 
         * @param userId 用户 ID
         * @return 错题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = false ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findWrongAnswersByUserId(Long userId);

        /**
         * 统计时间段内的答题总数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.answeredAt BETWEEN ?1 AND ?2  -- 时间范围筛选
         * }</pre>
         * 
         * @param start 开始时间
         * @param end   结束时间
         * @return 答题总数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN ?1 AND ?2")
        Long countByDateRange(LocalDateTime start, LocalDateTime end);

        /**
         * 统计用户答题总数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.userId = ?1  -- 用户 ID 匹配
         * }</pre>
         * 
         * @param userId 用户 ID
         * @return 答题总数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1")
        Long countByUserId(Long userId);

        /**
         * 统计用户正确答题数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.userId = ?1     -- 用户 ID 匹配
         *   AND ar.correct = true  -- 仅统计正确记录
         * }</pre>
         * 
         * @param userId 用户 ID
         * @return 正确答题数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = true")
        Long countCorrectByUserId(Long userId);

        /**
         * 计算用户答题正确率
         * 
         * 通过单一方法计算准确率，避免多次数据库查询。
         * 
         * @param userId 用户ID
         * @return 正确率百分比
         */
        default Double calculateAccuracyRate(Long userId) {
                Long total = countByUserId(userId);
                Long correct = countCorrectByUserId(userId);
                return total > 0 ? (double) correct / total * 100 : 0.0;
        }

        /**
         * 统计用户在时间段内的正确答题数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.userId = ?1                     -- 用户 ID 匹配
         *   AND ar.correct = true                  -- 仅统计正确记录
         *   AND ar.answeredAt BETWEEN ?2 AND ?3    -- 时间范围筛选
         * }</pre>
         * 
         * @param userId 用户 ID
         * @param start  开始时间
         * @param end    结束时间
         * @return 正确答题数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = true AND ar.answeredAt BETWEEN ?2 AND ?3")
        Long countCorrectByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        /**
         * 统计用户在时间段内的答题总数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.userId = ?1                     -- 用户 ID 匹配
         *   AND ar.answeredAt BETWEEN ?2 AND ?3    -- 时间范围筛选
         * }</pre>
         * 
         * @param userId 用户 ID
         * @param start  开始时间
         * @param end    结束时间
         * @return 答题总数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3")
        Long countByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        /**
         * 查询用户在时间段内的答题记录
         * 
         * 合并了原有的两个分离的时间范围查询方法。
         * 
         * @param userId 用户ID
         * @param start  开始时间
         * @param end    结束时间
         * @return 答题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        /**
         * 统计时间段内的正确答题数（用于教师看板）
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.correct = true                  -- 仅统计正确记录
         *   AND ar.answeredAt BETWEEN ?1 AND ?2    -- 时间范围筛选
         * }</pre>
         * 
         * @param start 开始时间
         * @param end   结束时间
         * @return 正确答题数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.correct = true AND ar.answeredAt BETWEEN ?1 AND ?2")
        Long countCorrectByDateRange(LocalDateTime start, LocalDateTime end);

        /**
         * 按题目分组统计错题信息
         * 
         * <p>优化后的查询，使用 Pageable 参数支持分页。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT a.questionId,
         *        COUNT(a),                    -- 错题数量
         *        MAX(a.answeredAt),           -- 最近答题时间
         *        MAX(a.userAnswer)            -- 最近用户答案
         * FROM AnswerRecord a
         * WHERE a.userId = ?1                -- 用户 ID 匹配
         *   AND a.correct = false            -- 仅统计错误记录
         * GROUP BY a.questionId              -- 按题目分组
         * ORDER BY MAX(a.answeredAt) DESC    -- 按最近答题时间降序
         * }</pre>
         * 
         * @param userId  用户 ID
         * @param pageable 分页参数
         * @return 题目统计结果数组 [questionId, count, maxAnsweredAt, maxUserAnswer]
         */
        @Query("""
                        SELECT a.questionId,
                               COUNT(a),
                               MAX(a.answeredAt),
                               MAX(a.userAnswer)
                        FROM AnswerRecord a
                        WHERE a.userId = ?1 AND a.correct = false
                        GROUP BY a.questionId
                        ORDER BY MAX(a.answeredAt) DESC
                        """)
        java.util.List<Object[]> findWrongQuestionStatsByUserId(Long userId, Pageable pageable);

        /**
         * 统计用户错题的题目数量
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(DISTINCT a.questionId) FROM AnswerRecord a 
         * WHERE a.userId = ?1     -- 用户 ID 匹配
         *   AND a.correct = false -- 仅统计错误记录
         * }</pre>
         * 
         * @param userId 用户 ID
         * @return 错题题目数量
         */
        @Query("SELECT COUNT(DISTINCT a.questionId) FROM AnswerRecord a WHERE a.userId = ?1 AND a.correct = false")
        long countDistinctWrongQuestionsByUserId(Long userId);

        /**
         * 根据会话 ID 和用户 ID 统计答题总数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.sessionId = ?1  -- 会话 ID 匹配
         *   AND ar.userId = ?2     -- 用户 ID 匹配
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @param userId    用户 ID
         * @return 答题总数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2")
        Long countBySessionAndUser(String sessionId, Long userId);

        /**
         * 根据会话 ID 和用户 ID 统计正确答题数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.sessionId = ?1  -- 会话 ID 匹配
         *   AND ar.userId = ?2     -- 用户 ID 匹配
         *   AND ar.correct = true  -- 仅统计正确记录
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @param userId    用户 ID
         * @return 正确答题数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2 AND ar.correct = true")
        Long countCorrectBySessionAndUser(String sessionId, Long userId);

        /**
         * 查询答错某题的所有学生 ID
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT DISTINCT ar.userId FROM AnswerRecord ar 
         * WHERE ar.questionId = ?1     -- 题目 ID 匹配
         *   AND ar.correct = false     -- 仅统计错误记录
         * }</pre>
         * 
         * @param questionId 题目 ID
         * @return 学生 ID 列表
         */
        @Query("SELECT DISTINCT ar.userId FROM AnswerRecord ar WHERE ar.questionId = ?1 AND ar.correct = false")
        List<Long> findUserIdsByWrongQuestion(Long questionId);

        /**
         * 根据会话 ID 和用户 ID 查询答题记录
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.sessionId = ?1                  -- 会话 ID 匹配
         *   AND ar.userId = ?2                     -- 用户 ID 匹配
         * ORDER BY ar.answeredAt DESC              -- 按答题时间降序
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @param userId    用户 ID
         * @return 答题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findBySessionIdAndUserId(String sessionId, Long userId);

        /**
         * 根据时间范围查询答题记录
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.answeredAt BETWEEN ?1 AND ?2    -- 时间范围筛选
         * ORDER BY ar.answeredAt DESC              -- 按答题时间降序
         * }</pre>
         * 
         * @param start 开始时间
         * @param end   结束时间
         * @return 答题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN ?1 AND ?2 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByDateRange(LocalDateTime start, LocalDateTime end);

        /**
         * 统计时间段内错误原因词频
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar.errorReason, COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.correct = false                 -- 仅统计错误记录
         *   AND ar.answeredAt BETWEEN ?1 AND ?2    -- 时间范围筛选
         *   AND ar.errorReason IS NOT NULL         -- 排除空值
         *   AND ar.errorReason != ''               -- 排除空字符串
         * GROUP BY ar.errorReason                  -- 按错误原因分组
         * }</pre>
         * 
         * @param start 开始时间
         * @param end   结束时间
         * @return 错误原因统计数组 [errorReason, count]
         */
        @Query("SELECT ar.errorReason, COUNT(ar) FROM AnswerRecord ar WHERE ar.correct = false AND ar.answeredAt BETWEEN ?1 AND ?2 AND ar.errorReason IS NOT NULL AND ar.errorReason != '' GROUP BY ar.errorReason")
        List<Object[]> countErrorReasonsByDateRange(LocalDateTime start, LocalDateTime end);

        /**
         * 按题目统计答题情况（用于看板显示）
         * 
         * 查询指定时间范围内各题目的答题统计信息，包括答题次数、正确次数等。
         * 
         * @param startDate 开始时间
         * @param endDate   结束时间
         * @return 题目统计结果列表，每行包含：题目ID、题目内容、总答题数、正确数
         */
        @Query("""
                        SELECT q.id, q.content,
                               COUNT(ar.id) as totalAttempts,
                               SUM(CASE WHEN ar.correct = true THEN 1 ELSE 0 END) as correctCount
                        FROM AnswerRecord ar
                        JOIN Question q ON ar.questionId = q.id
                        WHERE ar.answeredAt BETWEEN ?1 AND ?2
                        GROUP BY q.id, q.content
                        ORDER BY totalAttempts DESC
                        """)
        List<Object[]> getQuestionStatisticsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

        /**
         * 预加载关联实体的查询方法 - 避免N+1查询问题
         * 
         * 一次性加载答题记录及其关联的题目和用户信息，
         * 显著提升查询性能，特别是在批量操作场景下。
         * 
         * @param userId 用户ID
         * @return 包含预加载关联数据的答题记录列表
         */
        @EntityGraph(attributePaths = { "question", "user" })
        List<AnswerRecord> findByUserIdOrderByAnsweredAtDesc(Long userId);

        /**
         * 按会话 ID 预加载关联实体查询
         * 
         * <p>用于查询特定学习会话的所有答题记录，
         * 同时预加载相关联的题目和用户信息。</p>
         * 
         * <p><strong>性能优化：</strong>使用 {@link EntityGraph} 预加载 {@code question} 和 {@code user} 关联</p>
         * 
         * @param sessionId 会话 ID
         * @return 包含预加载关联数据的答题记录列表
         * 
         * @see EntityGraph 预加载注解说明
         */
        @EntityGraph(attributePaths = { "question", "user" })
        List<AnswerRecord> findBySessionIdOrderByAnsweredAtDesc(String sessionId);

        /**
         * 按用户和时间范围预加载查询
         * 
         * <p>查询指定用户在时间范围内的答题记录，
         * 预加载关联的题目和用户信息以提高性能。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.userId = ?1                         -- 用户 ID 匹配
         *   AND ar.answeredAt BETWEEN ?2 AND ?3        -- 时间范围筛选
         * ORDER BY ar.answeredAt DESC                  -- 按答题时间降序
         * }</pre>
         * 
         * <p><strong>性能优化：</strong>使用 {@link EntityGraph} 预加载 {@code question} 和 {@code user} 关联</p>
         * 
         * @param userId 用户 ID
         * @param start  开始时间
         * @param end    结束时间
         * @return 包含预加载关联数据的答题记录列表
         * 
         * @see EntityGraph 预加载注解说明
         */
        @EntityGraph(attributePaths = { "question", "user" })
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3 ORDER BY ar.answeredAt DESC")
        List<AnswerRecord> findByUserIdAndDateRangeWithRelations(Long userId, LocalDateTime start, LocalDateTime end);

        /**
         * 查询错题记录并预加载关联信息
         * 
         * <p>专门用于错题本功能，只查询错误的答题记录，
         * 同时预加载相关的题目和用户信息。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.userId = ?1             -- 用户 ID 匹配
         *   AND ar.correct = false         -- 仅查询错误记录
         * ORDER BY ar.answeredAt DESC      -- 按答题时间降序
         * }</pre>
         * 
         * <p><strong>性能优化：</strong>使用 {@link EntityGraph} 预加载 {@code question} 和 {@code user} 关联</p>
         * 
         * @param userId 用户 ID
         * @return 错误答题记录列表（预加载关联数据）
         * 
         * @see EntityGraph 预加载注解说明
         */
        @EntityGraph(attributePaths = { "question", "user" })
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = false ORDER BY ar.answeredAt DESC")
        List<AnswerRecord> findWrongAnswersWithRelations(Long userId);

        /**
         * 根据用户 ID 和题目 ID 查询答题记录
         * 
         * @param userId     用户 ID
         * @param questionId 题目 ID
         * @return 答题记录 (如果存在)
         */
        Optional<AnswerRecord> findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(Long userId, Long questionId);

        /**
         * 根据会话 ID、用户 ID 和题目 ID 查询答题记录
         * 
         * @param sessionId  会话 ID
         * @param userId     用户 ID
         * @param questionId 题目 ID
         * @return 答题记录 (如果存在)
         */
        Optional<AnswerRecord> findTopBySessionIdAndUserIdAndQuestionIdOrderByAnsweredAtDesc(String sessionId,
                        Long userId, Long questionId);

        /**
         * 查询所有错误答题记录（按时间倒序）
         * 
         * <p>用于教师查询系统内所有用户的错题记录。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT ar FROM AnswerRecord ar 
         * WHERE ar.correct = false         -- 仅查询错误记录
         * ORDER BY ar.answeredAt DESC      -- 按答题时间降序
         * }</pre>
         * 
         * @return 错误答题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.correct = false ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByCorrectFalseOrderByAnsweredAtDesc();

        /**
         * 按用户和会话查询最新记录
         * 
         * 查询指定用户在特定会话中的所有答题记录，按答题时间倒序排列。
         * 用于会话维度的报告生成和分析。
         * 
         * @param userId    用户ID
         * @param sessionId 会话ID
         * @return 会话中的答题记录列表
         */
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = :userId AND ar.sessionId = :sessionId ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByUserIdAndSessionIdOrderByAnsweredAtDesc(
                        @org.springframework.data.repository.query.Param("userId") Long userId,
                        @org.springframework.data.repository.query.Param("sessionId") String sessionId);

        /**
         * 按会话查询所有记录（按时间升序）
         * 
         * 查询特定会话中的所有答题记录，按答题时间正序排列。
         * 用于按时间顺序展示会话中的答题过程。
         * 
         * @param sessionId 会话ID
         * @return 会话中的答题记录列表
         */
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findBySessionIdOrderByAnsweredAtAsc(String sessionId);

        /**
         * 查询用户的所有会话 ID
         * 
         * <p>获取指定用户的所有不同会话 ID，按会话时间倒序排列。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT DISTINCT ar.sessionId FROM AnswerRecord ar 
         * WHERE ar.userId = ?1             -- 用户 ID 匹配
         * ORDER BY ar.sessionId DESC       -- 按会话 ID 降序
         * }</pre>
         * 
         * @param userId 用户 ID
         * @return 会话 ID 列表
         */
        @Query("SELECT DISTINCT ar.sessionId FROM AnswerRecord ar WHERE ar.userId = ?1 ORDER BY ar.sessionId DESC")
        List<String> findDistinctSessionIdsByUserId(Long userId);

        /**
         * 批量删除指定时间范围内的答题记录
         * 
         * <p>用于教师的批量数据清理操作，需要事务支持。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * DELETE FROM AnswerRecord ar 
         * WHERE ar.answeredAt BETWEEN :startTime AND :endTime  -- 时间范围筛选
         * }</pre>
         * 
         * <p><strong>事务说明：</strong>使用 {@link Modifying} 和 {@link Transactional} 注解确保数据一致性</p>
         * 
         * @param startTime 开始时间
         * @param endTime   结束时间
         * @return 删除的记录数
         * 
         * @see Modifying 修改操作注解
         * @see Transactional 事务管理注解
         */
        @Modifying
        @Transactional
        @Query("DELETE FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN :startTime AND :endTime")
        int deleteByTimeRange(@org.springframework.data.repository.query.Param("startTime") LocalDateTime startTime,
                        @org.springframework.data.repository.query.Param("endTime") LocalDateTime endTime);

        /**
         * 统计指定题目的总答题次数
         * 
         * <p>用于计算错误率的分母。</p>
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(*) FROM AnswerRecord 
         * WHERE questionId = ?1  -- 题目 ID 匹配
         * }</pre>
         * 
         * @param questionId 题目 ID
         * @return 总答题次数
         */
        @Query("SELECT COUNT(*) FROM AnswerRecord WHERE questionId = ?1")
        Long countByQuestionId(Long questionId);

        /**
         * 最优的题目错误统计查询 - 数据库单次遍历方案
         * 
         * <p>直接在数据库层面完成筛选和统计，避免应用层遍历。
         * 利用 GROUP BY + HAVING 实现高效聚合。</p>
         * 
         * <p><strong>SQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT 
         *   q.id as questionId,                    -- 题目 ID
         *   q.content as content,                  -- 题目内容
         *   q.options as options,                  -- 选项
         *   q.correct_option as correctOption,     -- 正确选项
         *   q.explanation as explanation,          -- 解析
         *   q.difficulty as difficulty,            -- 难度（转换为中文）
         *   q.image_base64 as imageBase64,         -- 图片
         *   COUNT(ar.id) as errorCount,            -- 错误次数
         *   ROUND(COUNT(ar.id) * 100.0 / (         -- 错误率百分比
         *     SELECT COUNT(*) FROM answer_records ar_total 
         *     WHERE ar_total.question_id = q.id 
         *   ), 2) as errorRate 
         * FROM questions q 
         * INNER JOIN answer_records ar ON q.id = ar.question_id 
         * WHERE ar.correct = false                 -- 仅统计错误记录
         * GROUP BY q.id                            -- 按题目分组
         * HAVING COUNT(ar.id) > 0                  -- 至少有 1 次错误
         * ORDER BY errorCount DESC                 -- 按错误次数降序
         * }</pre>
         * 
         * <p><strong>性能优化：</strong>使用原生 SQL 查询，一次性获取所有统计信息</p>
         * 
         * <p><strong>返回数据：</strong></p>
         * <ul>
         * <li>questionId: 题目ID</li>
         * <li>content: 题目内容</li>
         * <li>options: 选项JSON</li>
         * <li>correctOption: 正确答案</li>
         * <li>explanation: 解析</li>
         * <li>difficulty: 难度</li>
         * <li>imageBase64: 图片</li>
         * <li>errorCount: 错误人次</li>
         * <li>correctCount: 正确人次（总人次 - 错误人次）</li>
         * <li>errorRate: 错误率百分比</li>
         * </ul>
         * 
         * @return 题目错误统计结果数组 [questionId, content, options, correctOption, explanation, difficulty, imageBase64, errorCount, correctCount, errorRate]
         */
        @Query(value = "SELECT " +
                        "q.id as questionId, " +
                        "q.content as content, " +
                        "q.options as options, " +
                        "q.correct_option as correctOption, " +
                        "q.explanation as explanation, " +
                        "CASE " +
                        "    WHEN q.difficulty = 1 THEN '简单' " +
                        "    WHEN q.difficulty = 2 THEN '较简单' " +
                        "    WHEN q.difficulty = 3 THEN '中等' " +
                        "    WHEN q.difficulty = 4 THEN '较难' " +
                        "    WHEN q.difficulty = 5 THEN '困难' " +
                        "    ELSE '未知' " +
                        "END as difficulty, " +
                        "q.image_base64 as imageBase64, " +
                        "COUNT(ar.id) as errorCount, " +
                        "(SELECT COUNT(*) FROM answer_records ar_total WHERE ar_total.question_id = q.id) - COUNT(ar.id) as correctCount, " +
                        "ROUND(COUNT(ar.id) * 100.0 / ( " +
                        "    SELECT COUNT(*) FROM answer_records ar_total " +
                        "    WHERE ar_total.question_id = q.id " +
                        "), 2) as errorRate " +
                        "FROM questions q " +
                        "INNER JOIN answer_records ar ON q.id = ar.question_id " +
                        "WHERE ar.correct = false " +
                        "GROUP BY q.id " +
                        "HAVING COUNT(ar.id) > 0 " +
                        "ORDER BY errorCount DESC", nativeQuery = true)
        List<Object[]> findQuestionErrorStatisticsOptimized();

        /**
         * 获取指定题目的学生错误详情
         * 
         * <p><strong>SQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT 
         *   u.id,              -- 学生 ID
         *   u.real_name,       -- 学生姓名
         *   u.class_name,      -- 班级
         *   ar.selected_option,-- 选择的选项
         *   ar.answered_at,    -- 答题时间
         *   ar.error_reason    -- 错误原因
         * FROM answer_records ar 
         * JOIN users u ON ar.user_id = u.id 
         * WHERE ar.question_id = :questionId     -- 题目 ID 匹配
         *   AND ar.correct = false               -- 仅查询错误记录
         * ORDER BY ar.answered_at DESC           -- 按答题时间降序
         * }</pre>
         * 
         * <p><strong>查询说明：</strong>使用原生 SQL 查询，直接关联用户表获取详细信息</p>
         * 
         * @param questionId 题目 ID
         * @return 学生错误详情数组 [userId, realName, className, selectedOption, answeredAt, errorReason]
         */
        @Query(value = "SELECT " +
                        "u.id, " +
                        "u.real_name, " +
                        "u.class_name, " +
                        "ar.selected_option, " +
                        "ar.answered_at, " +
                        "ar.error_reason " +
                        "FROM answer_records ar " +
                        "JOIN users u ON ar.user_id = u.id " +
                        "WHERE ar.question_id = :questionId AND ar.correct = false " +
                        "ORDER BY ar.answered_at DESC", nativeQuery = true)
        List<Object[]> findStudentErrorsByQuestionId(
                        @org.springframework.data.repository.query.Param("questionId") Long questionId);

        @Query("SELECT DISTINCT ar.userId FROM AnswerRecord ar")
        List<Long> findDistinctUserIds();
        
        /**
         * 统计有作答记录的学生人数（去重）
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(DISTINCT ar.userId) FROM AnswerRecord ar 
         * WHERE ar.userId IS NOT NULL  -- 排除空值
         * }</pre>
         * 
         * <p><strong>使用场景：</strong></p>
         * <ul>
         * <li>教师端统计参与答题的学生总数</li>
         * <li>全局答题数据概览</li>
         * </ul>
         * 
         * @return 有作答记录的学生人数
         */
        @Query("SELECT COUNT(DISTINCT ar.userId) FROM AnswerRecord ar WHERE ar.userId IS NOT NULL")
        long countDistinctUserIds();
        
        /**
         * 统计会话中的答题记录数
         * 
         * <p><strong>JPQL 查询逻辑：</strong></p>
         * <pre>{@code
         * SELECT COUNT(ar) FROM AnswerRecord ar 
         * WHERE ar.sessionId = ?1  -- 会话 ID 匹配
         *   AND ar.userId = ?2     -- 用户 ID 匹配
         * }</pre>
         * 
         * @param sessionId 会话 ID
         * @param userId    用户 ID
         * @return 答题记录数
         */
        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2")
        long countBySessionIdAndUserId(String sessionId, Long userId);

        /**
         * 统计指定会话下所有答题记录数（不区分用户）
         *
         * @param sessionId 会话 ID
         * @return 记录数
         */
        long countBySessionId(String sessionId);

        /**
         * 根据题目 ID 查询所有答题记录，按答题时间倒序排列，并预加载用户信息
         *
         * <p><strong>性能优化：</strong>使用 {@link EntityGraph} 预加载 {@code user} 关联</p>
         *
         * @param questionId 题目 ID
         * @return 答题记录列表
         * 
         * @see EntityGraph 预加载注解说明
         */
        @EntityGraph(attributePaths = { "user", "question" })
        List<AnswerRecord> findByQuestionIdOrderByAnsweredAtDesc(Long questionId);
}
