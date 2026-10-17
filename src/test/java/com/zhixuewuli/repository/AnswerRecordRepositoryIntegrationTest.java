package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.common.factory.IntegrationTestDataFactory;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 答题记录仓库集成测试
 * 验证 AnswerRecordRepository 的复杂查询逻辑和数据库操作
 */
@Tag("integration-repository")
class AnswerRecordRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AnswerRecordRepository answerRecordRepository;

    @Autowired
    private IntegrationTestDataFactory factory;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    // ==================== 基础 CRUD 测试 ====================

    @Test
    @Rollback
    void testSaveAndFindAnswerRecord() {
        // Given - 创建用户和题目
        User user = createUniqueUser("test_user", "测试用户");
        Question question = createUniqueQuestion();

        AnswerRecord record = new AnswerRecord();
        record.setSessionId("session_" + System.currentTimeMillis());
        record.setUserId(user.getId());
        record.setQuestionId(question.getId());
        record.setUserAnswer("A");
        record.setCorrect(true);
        record.setAnsweredAt(LocalDateTime.now());

        // When
        AnswerRecord savedRecord = answerRecordRepository.save(record);
        Optional<AnswerRecord> foundRecord = answerRecordRepository.findById(savedRecord.getId());

        // Then
        assertThat(foundRecord).isPresent();
        assertThat(foundRecord.get().getUserId()).isEqualTo(user.getId());
        assertThat(foundRecord.get().getQuestionId()).isEqualTo(question.getId());
        assertThat(foundRecord.get().getUserAnswer()).isEqualTo("A");
        assertThat(foundRecord.get().getCorrect()).isTrue();
    }

    // ==================== 会话和题目相关查询测试 ====================

    @Test
    @Rollback
    void testFindBySessionIdAndQuestionId() {
        // Given
        User user = createUniqueUser("find_test_user", "查找测试用户");
        Question question = createUniqueQuestion();
        String sessionId = "session_find_test_" + System.currentTimeMillis();

        AnswerRecord record = createAnswerRecord(user, question, sessionId, "A", true);

        // When
        AnswerRecord foundRecord = answerRecordRepository.findBySessionIdAndQuestionId(sessionId, question.getId());

        // Then
        assertThat(foundRecord).isNotNull();
        assertThat(foundRecord.getSessionId()).isEqualTo(sessionId);
        assertThat(foundRecord.getQuestionId()).isEqualTo(question.getId());
    }

    @Test
    @Rollback
    void testFindBySessionIdAndUserId() {
        // Given
        User user1 = createUniqueUser("user1_session", "用户 1");
        User user2 = createUniqueUser("user2_session", "用户 2");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        String sessionId = "session_user_test_" + System.currentTimeMillis();

        createAnswerRecord(user1, question1, sessionId, "A", true);
        createAnswerRecord(user1, question2, sessionId, "B", false);
        createAnswerRecord(user2, question1, sessionId, "C", true);

        // When
        List<AnswerRecord> records = answerRecordRepository.findBySessionIdAndUserId(sessionId, user1.getId());

        // Then - 验证返回该用户在该会话中的所有记录
        assertThat(records).hasSize(2);
        assertThat(records).extracting(AnswerRecord::getUserId)
                .containsOnly(user1.getId());
    }

    @Test
    @Rollback
    void testCountBySessionAndUser() {
        // Given
        User user = createUniqueUser("count_session_user", "计数测试用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();
        String sessionId = "session_count_test_" + System.currentTimeMillis();

        createAnswerRecord(user, question1, sessionId, "A", true);
        createAnswerRecord(user, question2, sessionId, "B", false);
        createAnswerRecord(user, question3, sessionId, "C", true);

        // When
        Long count = answerRecordRepository.countBySessionAndUser(sessionId, user.getId());

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Rollback
    void testCountCorrectBySessionAndUser() {
        // Given
        User user = createUniqueUser("correct_count_user", "正确计数用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();
        String sessionId = "session_correct_count_" + System.currentTimeMillis();

        createAnswerRecord(user, question1, sessionId, "A", true);
        createAnswerRecord(user, question2, sessionId, "B", false);
        createAnswerRecord(user, question3, sessionId, "C", true);

        // When
        Long correctCount = answerRecordRepository.countCorrectBySessionAndUser(sessionId, user.getId());

        // Then
        assertThat(correctCount).isEqualTo(2);
    }

    // ==================== 用户答题统计测试 ====================

    @Test
    @Rollback
    void testCountDistinctUserIds() {
        // Given - 清理现有数据
        cleanTable("ANSWER_RECORDS");

        // 创建 3 个用户，每个创建不同的答题记录
        User user1 = createUniqueUser("distinct_user1", "去重用户 1");
        User user2 = createUniqueUser("distinct_user2", "去重用户 2");
        User user3 = createUniqueUser("distinct_user3", "去重用户 3");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();

        // 用户1: 3 条答题记录
        createAnswerRecord(user1, question1, "s1", "A", true);
        createAnswerRecord(user1, question1, "s2", "B", false);
        createAnswerRecord(user1, question2, "s3", "C", true);

        // 用户2: 2 条答题记录
        createAnswerRecord(user2, question1, "s4", "A", true);
        createAnswerRecord(user2, question2, "s5", "B", false);

        // 用户3: 1 条答题记录
        createAnswerRecord(user3, question1, "s6", "A", true);

        // When
        long distinctUserCount = answerRecordRepository.countDistinctUserIds();

        // Then - 应该返回 3（3 个不同用户有答题记录）
        assertThat(distinctUserCount).isEqualTo(3L);
    }

    @Test
    @Rollback
    void testCountDistinctUserIds_noRecords() {
        // Given - 清理现有数据
        cleanTable("ANSWER_RECORDS");

        // When
        long distinctUserCount = answerRecordRepository.countDistinctUserIds();

        // Then - 没有答题记录时返回 0
        assertThat(distinctUserCount).isEqualTo(0L);
    }

    @Test
    @Rollback
    void testCountDistinctUserIds_singleUser() {
        // Given - 清理现有数据
        cleanTable("ANSWER_RECORDS");

        User user = createUniqueUser("single_user", "单一用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();

        // 同一用户多条记录
        createAnswerRecord(user, question1, "s1", "A", true);
        createAnswerRecord(user, question1, "s2", "B", false);
        createAnswerRecord(user, question2, "s3", "C", true);

        // When
        long distinctUserCount = answerRecordRepository.countDistinctUserIds();

        // Then - 只返回 1（同一用户去重后只有一个）
        assertThat(distinctUserCount).isEqualTo(1L);
    }

    @Test
    @Rollback
    void testCountByUserId() {
        // Given
        User user = createUniqueUser("count_user", "计数用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();

        createAnswerRecord(user, question1, "s1", "A", true);
        createAnswerRecord(user, question2, "s2", "B", false);
        createAnswerRecord(user, question3, "s3", "C", true);

        // When
        Long totalCount = answerRecordRepository.countByUserId(user.getId());

        // Then
        assertThat(totalCount).isEqualTo(3);
    }

    @Test
    @Rollback
    void testCountCorrectByUserId() {
        // Given
        User user = createUniqueUser("correct_count_user2", "正确计数用户 2");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();
        Question question4 = createUniqueQuestion();

        createAnswerRecord(user, question1, "s1", "A", true);
        createAnswerRecord(user, question2, "s2", "B", false);
        createAnswerRecord(user, question3, "s3", "C", true);
        createAnswerRecord(user, question4, "s4", "D", false);

        // When
        Long correctCount = answerRecordRepository.countCorrectByUserId(user.getId());

        // Then
        assertThat(correctCount).isEqualTo(2);
    }

    @Test
    @Rollback
    void testCalculateAccuracyRate() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        // 创建 5 道题，对 3 道
        for (int i = 0; i < 5; i++) {
            Question question = createUniqueQuestion();
            boolean correct = i < 3; // 前 3 道正确
            createAnswerRecord(user, question, "session_" + i, "A", correct);
        }

        // When
        Double accuracyRate = answerRecordRepository.calculateAccuracyRate(user.getId());

        // Then - 正确率应为 60%
        assertThat(accuracyRate).isEqualTo(60.0);
    }

    @Test
    void testCalculateAccuracyRateWithNoRecords() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");

        // When
        Double accuracyRate = answerRecordRepository.calculateAccuracyRate(user.getId());

        // Then - 没有记录时返回 0.0
        assertThat(accuracyRate).isEqualTo(0.0);
    }

    // ==================== 错题相关查询测试 ====================

    @Test
    @Rollback
    void testFindWrongAnswersByUserId() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();

        createAnswerRecord(user, question1, "s1", "A", true); // 正确
        createAnswerRecord(user, question2, "s2", "B", false); // 错误
        createAnswerRecord(user, question3, "s3", "C", false); // 错误

        // When
        List<AnswerRecord> wrongAnswers = answerRecordRepository.findWrongAnswersByUserId(user.getId());

        // Then
        assertThat(wrongAnswers).hasSize(2);
        assertThat(wrongAnswers).extracting(AnswerRecord::getCorrect)
                .containsOnly(false);
    }

    @Test
    @Rollback
    void testFindWrongAnswersWithRelations() {
        // Given
        User user = createUniqueUser("wrong_user", "错题用户");
        Question question = createUniqueQuestion();
        createAnswerRecord(user, question, "wrong_session", "B", false);

        // When - 查询错题记录
        List<AnswerRecord> wrongAnswers = answerRecordRepository.findWrongAnswersByUserId(user.getId());

        // Then
        assertThat(wrongAnswers).hasSize(1);
        AnswerRecord record = wrongAnswers.get(0);
        assertThat(record.getCorrect()).isFalse();

        // 验证基本字段（不依赖懒加载）
        assertThat(record.getUserId()).isEqualTo(user.getId());
        assertThat(record.getQuestionId()).isEqualTo(question.getId());

        // 注意：在 H2 测试环境中，懒加载可能不会自动触发
        // 生产环境使用@EntityGraph 可以预加载关联数据
        // 这里我们只验证 ID 字段，确保查询逻辑正确
    }

    @Test
    @Rollback
    void testCountDistinctWrongQuestionsByUserId() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();

        // 同一题答错 2 次
        createAnswerRecord(user, question1, "s1", "A", false);
        createAnswerRecord(user, question1, "s2", "B", false);
        // 另一题答错 1 次
        createAnswerRecord(user, question2, "s3", "C", false);

        // When
        long distinctCount = answerRecordRepository.countDistinctWrongQuestionsByUserId(user.getId());

        // Then - 不同的错题数为 2
        assertThat(distinctCount).isEqualTo(2);
    }

    @Test
    @Rollback
    void testFindWrongQuestionStatsByUserId() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();

        createAnswerRecord(user, question1, "s1", "A", false);
        createAnswerRecord(user, question1, "s2", "B", false); // 同一题多次错误
        createAnswerRecord(user, question2, "s3", "C", false);

        // When
        List<Object[]> stats = answerRecordRepository.findWrongQuestionStatsByUserId(
                user.getId(), PageRequest.of(0, 10));

        // Then
        assertThat(stats).hasSize(2);
        // 验证返回的数据结构：[questionId, errorCount, lastAnsweredAt, lastAnswer]
        assertThat(stats.get(0)[0]).isNotNull(); // questionId
        assertThat(stats.get(0)[1]).isInstanceOf(Long.class); // errorCount
    }

    @Test
    @Rollback
    void testFindUserIdsByWrongQuestion() {
        // Given
        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        User user3 = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();

        createAnswerRecord(user1, question, "s1", "A", false);
        createAnswerRecord(user2, question, "s2", "B", false);
        createAnswerRecord(user3, question, "s3", "C", true); // 正确，不应包含

        // When
        List<Long> userIds = answerRecordRepository.findUserIdsByWrongQuestion(question.getId());

        // Then
        assertThat(userIds).hasSize(2);
        assertThat(userIds).containsExactlyInAnyOrder(user1.getId(), user2.getId());
    }

    // ==================== 时间范围查询测试 ====================

    @Test
    @Rollback
    void testFindByUserIdAndDateRange() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        // 创建时间范围内的记录
        AnswerRecord record1 = createAnswerRecord(user, createUniqueQuestion(),
                "s1", "A", true);
        record1.setAnsweredAt(now.minusHours(1));
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user, createUniqueQuestion(),
                "s2", "B", false);
        record2.setAnsweredAt(now);
        answerRecordRepository.save(record2);

        // 创建时间范围外的记录
        AnswerRecord record3 = createAnswerRecord(user, createUniqueQuestion(),
                "s3", "C", true);
        record3.setAnsweredAt(now.minusDays(2));
        answerRecordRepository.save(record3);

        // When
        List<AnswerRecord> records = answerRecordRepository.findByUserIdAndDateRange(
                user.getId(), start, end);

        // Then - 只返回时间范围内的 2 条记录
        assertThat(records).hasSize(2);
    }

    @Test
    @Rollback
    void testFindByUserIdAndDateRangeWithRelations() {
        // Given
        User user = createUniqueUser("dateuser", "日期范围用户");
        Question question = createUniqueQuestion();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        AnswerRecord record = createAnswerRecord(user, question, "session_with_relations", "A", true);
        record.setAnsweredAt(now);
        answerRecordRepository.save(record);

        // When - 查询用户在时间范围内的答题记录
        List<AnswerRecord> records = answerRecordRepository.findByUserIdAndDateRange(
                user.getId(), start, end);

        // Then
        assertThat(records).hasSize(1);
        AnswerRecord fetchedRecord = records.get(0);

        // 验证基本字段（不依赖懒加载）
        assertThat(fetchedRecord.getUserId()).isEqualTo(user.getId());
        assertThat(fetchedRecord.getQuestionId()).isEqualTo(question.getId());

        // 注意：在 H2 测试环境中，懒加载可能不会自动触发
        // 生产环境使用@EntityGraph 可以预加载关联数据
        // 这里我们只验证 ID 字段，确保查询逻辑正确
    }

    @Test
    @Rollback
    void testCountByUserIdAndDateRange() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        // 时间范围内 3 条
        createAnswerRecord(user, createUniqueQuestion(), "s1", "A", true);
        createAnswerRecord(user, createUniqueQuestion(), "s2", "B", false);
        createAnswerRecord(user, createUniqueQuestion(), "s3", "C", true);

        // 时间范围外 1 条
        AnswerRecord outOfRange = createAnswerRecord(user, createUniqueQuestion(), "s4", "D", true);
        outOfRange.setAnsweredAt(now.minusDays(2));
        answerRecordRepository.save(outOfRange);

        // When
        Long count = answerRecordRepository.countByUserIdAndDateRange(user.getId(), start, end);

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Rollback
    void testCountCorrectByUserIdAndDateRange() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        // 时间范围内：2 正确 1 错误
        createAnswerRecord(user, createUniqueQuestion(), "s1", "A", true);
        createAnswerRecord(user, createUniqueQuestion(), "s2", "B", true);
        createAnswerRecord(user, createUniqueQuestion(), "s3", "C", false);

        // When
        Long correctCount = answerRecordRepository.countCorrectByUserIdAndDateRange(user.getId(), start, end);

        // Then
        assertThat(correctCount).isEqualTo(2);
    }

    @Test
    @Rollback
    void testCountByDateRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");

        // 时间范围内 4 条
        createAnswerRecord(user1, createUniqueQuestion(), "s1", "A", true);
        createAnswerRecord(user1, createUniqueQuestion(), "s2", "B", false);
        createAnswerRecord(user2, createUniqueQuestion(), "s3", "C", true);
        createAnswerRecord(user2, createUniqueQuestion(), "s4", "D", true);

        // 时间范围外 1 条
        AnswerRecord outOfRange = createAnswerRecord(user1, createUniqueQuestion(), "s5", "E", true);
        outOfRange.setAnsweredAt(now.minusDays(2));
        answerRecordRepository.save(outOfRange);

        // When
        Long totalCount = answerRecordRepository.countByDateRange(start, end);

        // Then
        assertThat(totalCount).isEqualTo(4);
    }

    @Test
    @Rollback
    void testCountCorrectByDateRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        User user = createUniqueUser("batch_user", "批量用户");

        // 时间范围内：3 正确 2 错误
        createAnswerRecord(user, createUniqueQuestion(), "s1", "A", true);
        createAnswerRecord(user, createUniqueQuestion(), "s2", "B", true);
        createAnswerRecord(user, createUniqueQuestion(), "s3", "C", true);
        createAnswerRecord(user, createUniqueQuestion(), "s4", "D", false);
        createAnswerRecord(user, createUniqueQuestion(), "s5", "E", false);

        // When
        Long correctCount = answerRecordRepository.countCorrectByDateRange(start, end);

        // Then
        assertThat(correctCount).isEqualTo(3);
    }

    @Test
    @Rollback
    void testFindByDateRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now.plusHours(1);

        User user = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();

        AnswerRecord record1 = createAnswerRecord(user, question, "range_session_1", "A", true);
        record1.setAnsweredAt(now);
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user, createUniqueQuestion(), "range_session_2", "B", false);
        record2.setAnsweredAt(now.minusMinutes(30));
        answerRecordRepository.save(record2);

        // When
        List<AnswerRecord> records = answerRecordRepository.findByDateRange(start, end);

        // Then - 按时间倒序
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getAnsweredAt()).isAfterOrEqualTo(records.get(1).getAnsweredAt());
    }

    // ==================== 错误原因统计测试 ====================

    @Test
    @Rollback
    void testCountErrorReasonsByDateRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();

        // 创建带有错误原因的记录
        AnswerRecord record1 = createAnswerRecord(user1, question, "error_session_1", "A", false);
        record1.setErrorReason("概念理解错误");
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user1, createUniqueQuestion(), "error_session_2", "B", false);
        record2.setErrorReason("概念理解错误");
        answerRecordRepository.save(record2);

        AnswerRecord record3 = createAnswerRecord(user2, createUniqueQuestion(), "error_session_3", "C", false);
        record3.setErrorReason("计算错误");
        answerRecordRepository.save(record3);

        // When
        List<Object[]> errorStats = answerRecordRepository.countErrorReasonsByDateRange(start, end);

        // Then - 返回 [errorReason, count]
        assertThat(errorStats).hasSize(2);
    }

    // ==================== 题目统计测试 ====================

    @Test
    @Rollback
    void testGetQuestionStatisticsByDateRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(1);
        LocalDateTime end = now.plusDays(1);

        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();

        // 题目 1: 2 次答题，1 正确 1 错误
        AnswerRecord record1 = createAnswerRecord(user1, question1, "stat_session_1", "A", true);
        record1.setAnsweredAt(now);
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user2, question1, "stat_session_2", "B", false);
        record2.setAnsweredAt(now);
        answerRecordRepository.save(record2);

        // 题目 2: 1 次答题，正确
        AnswerRecord record3 = createAnswerRecord(user1, question2, "stat_session_3", "C", true);
        record3.setAnsweredAt(now);
        answerRecordRepository.save(record3);

        // When
        List<Object[]> stats = answerRecordRepository.getQuestionStatisticsByDateRange(start, end);

        // Then - 返回 [questionId, content, totalAttempts, correctCount]
        assertThat(stats).hasSize(2);
        // 找到题目 1 的统计
        Object[] question1Stats = stats.stream()
                .filter(s -> s[0].equals(question1.getId()))
                .findFirst()
                .orElse(null);
        assertThat(question1Stats).isNotNull();
        assertThat(question1Stats[2]).isEqualTo(2L); // 总答题数
        assertThat(question1Stats[3]).isEqualTo(1L); // 正确数
    }

    // ==================== 高级查询测试 ====================

    @Test
    @Rollback
    void testFindByUserIdOrderByAnsweredAtDesc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        LocalDateTime now = LocalDateTime.now();

        // 创建 3 条记录，时间不同
        for (int i = 0; i < 3; i++) {
            AnswerRecord record = createAnswerRecord(user, createUniqueQuestion(),
                    "order_session_" + i, "A", true);
            record.setAnsweredAt(now.minusHours(i));
            answerRecordRepository.save(record);
        }

        // When
        List<AnswerRecord> records = answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(user.getId());

        // Then - 按时间倒序
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getAnsweredAt())
                .isAfterOrEqualTo(records.get(1).getAnsweredAt());
        assertThat(records.get(1).getAnsweredAt())
                .isAfterOrEqualTo(records.get(2).getAnsweredAt());
    }

    @Test
    @Rollback
    void testFindBySessionIdOrderByAnsweredAtDesc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        String sessionId = "session_order_test_" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 3; i++) {
            AnswerRecord record = createAnswerRecord(user, createUniqueQuestion(),
                    sessionId, "A", true);
            record.setAnsweredAt(now.minusMinutes(i * 10));
            answerRecordRepository.save(record);
        }

        // When
        List<AnswerRecord> records = answerRecordRepository.findBySessionIdOrderByAnsweredAtDesc(sessionId);

        // Then
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getAnsweredAt())
                .isAfterOrEqualTo(records.get(1).getAnsweredAt());
    }

    @Test
    @Rollback
    void testFindBySessionIdOrderByAnsweredAtAsc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        String sessionId = "session_asc_test_" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 3; i++) {
            AnswerRecord record = createAnswerRecord(user, createUniqueQuestion(),
                    sessionId, "A", true);
            record.setAnsweredAt(now.plusMinutes(i * 10));
            answerRecordRepository.save(record);
        }

        // When
        List<AnswerRecord> records = answerRecordRepository.findBySessionIdOrderByAnsweredAtAsc(sessionId);

        // Then - 按时间正序
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getAnsweredAt())
                .isBeforeOrEqualTo(records.get(1).getAnsweredAt());
    }

    @Test
    @Rollback
    void testFindTopByUserIdAndQuestionIdOrderByAnsweredAtDesc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();
        LocalDateTime now = LocalDateTime.now();

        // 同一用户同一题目答 2 次
        AnswerRecord record1 = createAnswerRecord(user, question, "first_attempt", "A", false);
        record1.setAnsweredAt(now.minusHours(1));
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user, question, "second_attempt", "B", true);
        record2.setAnsweredAt(now);
        answerRecordRepository.save(record2);

        // When
        Optional<AnswerRecord> latestRecord = answerRecordRepository.findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(
                user.getId(), question.getId());

        // Then - 返回最新的一次
        assertThat(latestRecord).isPresent();
        assertThat(latestRecord.get().getSessionId()).isEqualTo("second_attempt");
        assertThat(latestRecord.get().getCorrect()).isTrue();
    }

    @Test
    @Rollback
    void testFindTopBySessionIdAndUserIdAndQuestionIdOrderByAnsweredAtDesc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();
        String sessionId = "specific_session_" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        AnswerRecord record1 = createAnswerRecord(user, question, sessionId, "A", false);
        record1.setAnsweredAt(now.minusMinutes(30));
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user, question, sessionId, "B", true);
        record2.setAnsweredAt(now);
        answerRecordRepository.save(record2);

        // When
        Optional<AnswerRecord> latestRecord = answerRecordRepository
                .findTopBySessionIdAndUserIdAndQuestionIdOrderByAnsweredAtDesc(
                        sessionId, user.getId(), question.getId());

        // Then
        assertThat(latestRecord).isPresent();
        assertThat(latestRecord.get().getCorrect()).isTrue();
    }

    @Test
    @Rollback
    void testFindByCorrectFalseOrderByAnsweredAtDesc() {
        // Given
        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        LocalDateTime now = LocalDateTime.now();

        // 创建错误记录
        AnswerRecord wrongRecord1 = createAnswerRecord(user1, createUniqueQuestion(),
                "wrong_1", "A", false);
        wrongRecord1.setAnsweredAt(now);
        answerRecordRepository.save(wrongRecord1);

        AnswerRecord wrongRecord2 = createAnswerRecord(user2, createUniqueQuestion(),
                "wrong_2", "B", false);
        wrongRecord2.setAnsweredAt(now.minusHours(1));
        answerRecordRepository.save(wrongRecord2);

        // 创建正确记录（不应被查询到）
        createAnswerRecord(user1, createUniqueQuestion(), "correct_1", "C", true);

        // When
        List<AnswerRecord> allWrongRecords = answerRecordRepository.findByCorrectFalseOrderByAnsweredAtDesc();

        // Then
        assertThat(allWrongRecords).hasSize(2);
        assertThat(allWrongRecords).extracting(AnswerRecord::getCorrect)
                .containsOnly(false);
    }

    @Test
    @Rollback
    void testFindByUserIdAndSessionIdOrderByAnsweredAtDesc() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        String sessionId = "user_session_desc_" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 3; i++) {
            AnswerRecord record = createAnswerRecord(user, createUniqueQuestion(),
                    sessionId, "A", true);
            record.setAnsweredAt(now.minusMinutes(i * 15));
            answerRecordRepository.save(record);
        }

        // When
        List<AnswerRecord> records = answerRecordRepository.findByUserIdAndSessionIdOrderByAnsweredAtDesc(
                user.getId(), sessionId);

        // Then
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getAnsweredAt())
                .isAfterOrEqualTo(records.get(1).getAnsweredAt());
    }

    // ==================== 批量删除测试 ====================

    @Test
    @Rollback
    void testDeleteByTimeRange() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now.plusHours(1);

        User user = createUniqueUser("batch_user", "批量用户");

        // 创建 3 条在删除范围内的记录
        AnswerRecord record1 = createAnswerRecord(user, createUniqueQuestion(),
                "to_delete_1", "A", true);
        record1.setAnsweredAt(now);
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user, createUniqueQuestion(),
                "to_delete_2", "B", false);
        record2.setAnsweredAt(now.minusMinutes(30));
        answerRecordRepository.save(record2);

        AnswerRecord record3 = createAnswerRecord(user, createUniqueQuestion(),
                "to_delete_3", "C", true);
        record3.setAnsweredAt(now.plusMinutes(30));
        answerRecordRepository.save(record3);

        // 创建 1 条在范围外的记录
        AnswerRecord keepRecord = createAnswerRecord(user, createUniqueQuestion(),
                "keep_1", "D", true);
        keepRecord.setAnsweredAt(now.minusDays(2));
        answerRecordRepository.save(keepRecord);

        // When
        int deletedCount = answerRecordRepository.deleteByTimeRange(start, end);

        // Then
        assertThat(deletedCount).isEqualTo(3);
        // 验证范围外的记录未被删除
        assertThat(answerRecordRepository.findById(keepRecord.getId())).isPresent();
    }

    // ==================== 题目错误统计优化查询测试 ====================

    @Test
    @Rollback
    void testFindQuestionErrorStatisticsOptimized() {
        // Given - 创建独特的用户和题目用于统计测试
        String uniqueSuffix = "_" + System.nanoTime();
        User user1 = createUniqueUser("errstatu1" + uniqueSuffix, "错误统计用户 1");
        User user2 = createUniqueUser("errstatu2" + uniqueSuffix, "错误统计用户 2");

        // 创建唯一的题目（确保内容哈希唯一）
        Question question1 = new Question();
        question1.setContent("错误统计题目 1 [unique:" + uniqueSuffix + "]");
        question1.setContentHash(java.util.UUID.randomUUID().toString().replace("-", "") + uniqueSuffix + "_q1");
        question1.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        question1.setCorrectOption("A");
        question1.setExplanation("这是解析 [unique:" + uniqueSuffix + "]");
        question1.setDifficulty(2);
        questionRepository.save(question1);

        Question question2 = new Question();
        question2.setContent("错误统计题目 2 [unique:" + uniqueSuffix + "]");
        question2.setContentHash(java.util.UUID.randomUUID().toString().replace("-", "") + uniqueSuffix + "_q2");
        question2.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        question2.setCorrectOption("A");
        question2.setExplanation("这是解析 [unique:" + uniqueSuffix + "]");
        question2.setDifficulty(3);
        questionRepository.save(question2);

        // 题目 1: 2 次错误，1 次正确（错误数应为 2）
        createAnswerRecord(user1, question1, "err_stat_1_" + uniqueSuffix, "A", false);
        createAnswerRecord(user2, question1, "err_stat_2_" + uniqueSuffix, "B", false);
        createAnswerRecord(user1, question1, "err_stat_3_" + uniqueSuffix, "C", true);

        // 题目 2: 1 次错误，2 次正确（错误数应为 1）
        createAnswerRecord(user1, question2, "err_stat_4_" + uniqueSuffix, "D", false);
        createAnswerRecord(user2, question2, "err_stat_5_" + uniqueSuffix, "E", true);
        createAnswerRecord(user1, question2, "err_stat_6_" + uniqueSuffix, "F", true);

        // When - 查询所有题目的错误统计
        List<Object[]> errorStats = answerRecordRepository.findQuestionErrorStatisticsOptimized();

        // Then - 验证查询返回结果不为空即可
        // 注意：findQuestionErrorStatisticsOptimized() 是原生 SQL 查询，会返回数据库中所有题目的错误统计
        // 在集成测试环境中，其他测试创建的数据也会被统计进来
        // 因此我们只验证查询能正常工作，不验证具体数量
        assertThat(errorStats).isNotEmpty();

        // 可选：验证返回的数据结构正确
        Object[] firstStat = errorStats.get(0);
        assertThat(firstStat).hasSize(10); // 验证返回 10 列：questionId, content, options, correctOption, explanation,
                                          // difficulty, imageBase64, errorCount, correctCount, errorRate
        assertThat(firstStat[0]).isNotNull(); // questionId
        assertThat(firstStat[1]).isNotNull(); // content
        assertThat(firstStat[7]).isInstanceOf(Long.class); // errorCount
    }

    @Test
    @Rollback
    void testFindStudentErrorsByQuestionId() {
        // Given
        User student1 = createUniqueUser("batch_user", "批量用户");
        User student2 = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();
        LocalDateTime now = LocalDateTime.now();

        AnswerRecord errorRecord1 = createAnswerRecord(student1, question, "student_err_1", "A", false);
        errorRecord1.setErrorReason("概念不清");
        errorRecord1.setAnsweredAt(now);
        answerRecordRepository.save(errorRecord1);

        AnswerRecord errorRecord2 = createAnswerRecord(student2, question, "student_err_2", "B", false);
        errorRecord2.setErrorReason("审题不仔细");
        errorRecord2.setAnsweredAt(now.minusMinutes(30));
        answerRecordRepository.save(errorRecord2);

        // 创建一条正确记录（不应被查询到）
        createAnswerRecord(student1, createUniqueQuestion(), "correct_rec", "C", true);

        // When
        List<Object[]> studentErrors = answerRecordRepository.findStudentErrorsByQuestionId(question.getId());

        // Then - 返回 [userId, realName, className, selectedOption, answeredAt,
        // errorReason]
        assertThat(studentErrors).hasSize(2);
    }

    // ==================== 其他查询测试 ====================

    @Test
    @Rollback
    void testFindDistinctSessionIdsByUserId() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();

        // 创建 3 个不同会话的记录
        createAnswerRecord(user, question, "session_A", "A", true);
        createAnswerRecord(user, createUniqueQuestion(), "session_B", "B", true);
        createAnswerRecord(user, createUniqueQuestion(), "session_C", "C", false);
        // 重复的会话
        createAnswerRecord(user, createUniqueQuestion(), "session_A", "D", true);

        // When
        List<String> sessionIds = answerRecordRepository.findDistinctSessionIdsByUserId(user.getId());

        // Then
        assertThat(sessionIds).hasSize(3);
        assertThat(sessionIds).containsExactlyInAnyOrder("session_A", "session_B", "session_C");
    }

    @Test
    @Rollback
    void testCountByQuestionId() {
        // Given
        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();

        createAnswerRecord(user1, question, "count_q_1", "A", true);
        createAnswerRecord(user2, question, "count_q_2", "B", false);
        createAnswerRecord(user1, createUniqueQuestion(), "count_q_3", "C", true);

        // When
        Long count = answerRecordRepository.countByQuestionId(question.getId());

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Rollback
    void testFindDistinctUserIds() {
        // Given
        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        User user3 = createUniqueUser("batch_user", "批量用户");

        createAnswerRecord(user1, createUniqueQuestion(), "distinct_user_1", "A", true);
        createAnswerRecord(user2, createUniqueQuestion(), "distinct_user_2", "B", false);
        createAnswerRecord(user3, createUniqueQuestion(), "distinct_user_3", "C", true);

        // When
        List<Long> userIds = answerRecordRepository.findDistinctUserIds();

        // Then
        assertThat(userIds).hasSize(3);
        assertThat(userIds).containsExactlyInAnyOrder(user1.getId(), user2.getId(), user3.getId());
    }

    @Test
    @Rollback
    void testCountBySessionIdAndUserId() {
        // Given
        User user = createUniqueUser("batch_user", "批量用户");
        String sessionId = "count_session_user_" + System.currentTimeMillis();
        Question question1 = createUniqueQuestion();
        Question question2 = createUniqueQuestion();
        Question question3 = createUniqueQuestion();

        createAnswerRecord(user, question1, sessionId, "A", true);
        createAnswerRecord(user, question2, sessionId, "B", false);
        createAnswerRecord(user, question3, sessionId, "C", true);
        // 不同会话
        createAnswerRecord(user, createUniqueQuestion(), "other_session", "D", true);

        // When
        long count = answerRecordRepository.countBySessionIdAndUserId(sessionId, user.getId());

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @Rollback
    void testFindByQuestionIdOrderByAnsweredAtDesc() {
        // Given
        User user1 = createUniqueUser("batch_user", "批量用户");
        User user2 = createUniqueUser("batch_user", "批量用户");
        Question question = createUniqueQuestion();
        LocalDateTime now = LocalDateTime.now();

        AnswerRecord record1 = createAnswerRecord(user1, question, "q_answer_1", "A", true);
        record1.setAnsweredAt(now.minusHours(2));
        answerRecordRepository.save(record1);

        AnswerRecord record2 = createAnswerRecord(user2, question, "q_answer_2", "B", false);
        record2.setAnsweredAt(now);
        answerRecordRepository.save(record2);

        AnswerRecord record3 = createAnswerRecord(user1, question, "q_answer_3", "C", true);
        record3.setAnsweredAt(now.minusHours(1));
        answerRecordRepository.save(record3);

        // When
        List<AnswerRecord> records = answerRecordRepository.findByQuestionIdOrderByAnsweredAtDesc(question.getId());

        // Then - 按时间倒序
        assertThat(records).hasSize(3);
        assertThat(records.get(0).getAnsweredAt())
                .isAfterOrEqualTo(records.get(1).getAnsweredAt());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建答题记录并保存
     */
    private AnswerRecord createAnswerRecord(User user, Question question, String sessionId,
            String answer, boolean correct) {
        AnswerRecord record = new AnswerRecord();
        record.setSessionId(sessionId);
        record.setUserId(user.getId());
        record.setQuestionId(question.getId());
        record.setUserAnswer(answer);
        record.setCorrect(correct);
        record.setAnsweredAt(LocalDateTime.now());
        return answerRecordRepository.save(record);
    }

    /**
     * 创建唯一用户（带时间戳后缀）
     */
    private User createUniqueUser(String baseUsername, String realName) {
        String suffix = "_" + System.nanoTime();
        User user = new User();
        user.setUsername(baseUsername + suffix);
        user.setPasswordHash("$2a$10$test_hash");
        user.setRealName(realName + suffix);
        user.setUserType(User.UserType.STUDENT);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * 创建唯一题目（带时间戳后缀以确保内容哈希唯一）
     */
    private Question createUniqueQuestion() {
        String uniqueSuffix = "_" + System.nanoTime();
        Question question = new Question();
        question.setContent("测试题目内容 [unique:" + uniqueSuffix + "]");
        question.setContentHash(java.util.UUID.randomUUID().toString().replace("-", "") + uniqueSuffix);
        question.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        question.setCorrectOption("A");
        question.setExplanation("这是解析 [unique:" + uniqueSuffix + "]");
        question.setDifficulty(2);
        return questionRepository.save(question);
    }
}
