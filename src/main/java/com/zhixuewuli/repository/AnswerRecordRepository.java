package com.zhixuewuli.repository;

import com.zhixuewuli.dto.response.AnswerRecordListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import com.zhixuewuli.dto.response.StudentAnswerStats;
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
        @Query("SELECT new com.zhixuewuli.dto.response.StudentAnswerStats(a.userId, COUNT(a), " +
                "SUM(CASE WHEN a.correct = false THEN 1 ELSE 0 END)) " +
                "FROM AnswerRecord a JOIN a.user u WHERE u.userType = com.zhixuewuli.entity.User.UserType.STUDENT " +
                "GROUP BY a.userId ORDER BY a.userId")
        List<StudentAnswerStats> summarizeStudents();

        interface Totals {
            Long getTotalAttempts();Long getCorrectAttempts();Long getAssignmentAnswers();
            Long getAssignmentCorrect();Long getParticipatingStudents();Long getPracticeAttempts();
        }
        @Query("select count(a) as totalAttempts,coalesce(sum(case when a.correct=true then 1 else 0 end),0) as correctAttempts,"+
          "coalesce(sum(case when a.practice=false then 1 else 0 end),0) as assignmentAnswers,"+
          "coalesce(sum(case when a.practice=false and a.correct=true then 1 else 0 end),0) as assignmentCorrect,"+
          "count(distinct case when a.practice=false and u.userType=com.zhixuewuli.entity.User.UserType.STUDENT and u.active=true then a.userId else null end) as participatingStudents,"+
          "coalesce(sum(case when a.practice=true then 1 else 0 end),0) as practiceAttempts "+
          "from AnswerRecord a join a.user u where (:eventId is null or a.eventId=:eventId)")
        Totals summarize(@Param("eventId") Long eventId);
        @Query(value="select count(*) from (select a.user_id from answer_records a join users u on u.id=a.user_id "+
           "where a.event_id=:eventId and a.practice=false and u.user_type='STUDENT' and u.active=true "+
           "group by a.user_id having count(distinct a.question_id)>=:questionCount) completed",nativeQuery=true)
        long countCompletedStudents(@Param("eventId") Long eventId,
           @Param("questionCount") long questionCount);

        interface TaskStudentStatsRow {
            Long getUserId();
            Long getAnsweredQuestions();
            Long getCorrectQuestions();
            java.time.LocalDateTime getLastAnsweredAt();
        }

        interface TaskQuestionStatsRow {
            Long getQuestionId();
            Long getAnsweredStudents();
            Long getCorrectStudents();
        }

        @Query("select a.userId as userId, count(distinct a.questionId) as answeredQuestions, " +
                "coalesce(sum(case when a.correct=true then 1 else 0 end),0) as correctQuestions, max(a.answeredAt) as lastAnsweredAt " +
                "from AnswerRecord a join a.user u where a.eventId=:eventId and a.practice=false " +
                "and u.active=true and u.userType=com.zhixuewuli.entity.User.UserType.STUDENT group by a.userId")
        List<TaskStudentStatsRow> findTaskStudentStats(@Param("eventId") Long eventId);

        @Query("select a.questionId as questionId, count(distinct a.userId) as answeredStudents, " +
                "coalesce(sum(case when a.correct=true then 1 else 0 end),0) as correctStudents " +
                "from AnswerRecord a join a.user u where a.eventId=:eventId and a.practice=false " +
                "and u.active=true and u.userType=com.zhixuewuli.entity.User.UserType.STUDENT group by a.questionId")
        List<TaskQuestionStatsRow> findTaskQuestionStats(@Param("eventId") Long eventId);

        interface TaskOverviewStatsRow {
            Long getEventId();
            Long getAnsweredQuestions();
            Long getCorrectQuestions();
        }

        @Query("select a.eventId as eventId, count(distinct a.questionId) as answeredQuestions, " +
                "coalesce(sum(case when a.correct=true then 1 else 0 end),0) as correctQuestions " +
                "from AnswerRecord a join a.user u where a.eventId in :eventIds and a.practice=false " +
                "and u.active=true and u.userType=com.zhixuewuli.entity.User.UserType.STUDENT " +
                "group by a.eventId, a.userId")
        List<TaskOverviewStatsRow> findTaskOverviewStats(@Param("eventIds") java.util.Collection<Long> eventIds);
        @Query("select count(a) from AnswerRecord a where (:eventId is null or a.eventId=:eventId) and a.correct=true "+
          "and not exists(select b.id from AnswerRecord b where b.userId=a.userId and b.questionId=a.questionId "+
          "and (b.eventId=a.eventId or (b.eventId is null and a.eventId is null)) and b.attemptOrder>a.attemptOrder)")
        long countMasteredQuestions(@Param("eventId") Long eventId);
        Optional<AnswerRecord> findByUserIdAndRequestId(Long userId,String requestId);
        boolean existsByQuestionId(Long questionId);
        boolean existsByUserIdAndEventIdAndQuestionIdAndPracticeFalse(Long userId,Long eventId,Long questionId);
        @Query("select distinct a.questionId from AnswerRecord a where a.userId=:userId and a.eventId=:eventId and a.practice=false")
        java.util.Set<Long> findCompletedAssignmentQuestionIds(@Param("userId") Long userId, @Param("eventId") Long eventId);
        List<AnswerRecord> findByUserIdAndEventIdOrderByAttemptOrderDesc(Long userId,Long eventId);
        @Query("SELECT a FROM AnswerRecord a WHERE a.userId=:userId AND EXISTS (SELECT x.id FROM AnswerRecord x " +
          "WHERE x.userId=a.userId AND x.questionId=a.questionId AND (x.eventId=a.eventId OR (x.eventId IS NULL AND a.eventId IS NULL)) AND x.correct=false) " +
          "AND NOT EXISTS (SELECT b.id FROM AnswerRecord b WHERE b.userId=a.userId AND b.questionId=a.questionId " +
          "AND (b.eventId=a.eventId OR (b.eventId IS NULL AND a.eventId IS NULL)) AND b.attemptOrder>a.attemptOrder) ORDER BY a.attemptOrder DESC")
        @EntityGraph(attributePaths="question")
        List<AnswerRecord> findPracticeReview(@Param("userId") Long userId);

        @Query(value = "SELECT new com.zhixuewuli.dto.response.AnswerRecordSummaryResponse(" +
                "a.id,a.eventId,a.practice,a.attemptOrder,a.userId,a.questionId,a.sessionId,a.userAnswer,a.correct," +
                "substring(coalesce(a.contentSnapshot,q.content),1,160),a.answeredAt,a.timestampUtc) " +
                "FROM AnswerRecord a LEFT JOIN a.question q " +
                "WHERE a.userId=:userId AND (:wrongOnly=false OR a.correct=false) " +
                "AND (:keyword='' OR LOWER(coalesce(a.contentSnapshot,q.content)) LIKE LOWER(:keyword) ESCAPE '!') " +
                "ORDER BY a.answeredAt DESC, a.attemptOrder DESC",
                countQuery = "SELECT COUNT(a) FROM AnswerRecord a LEFT JOIN a.question q " +
                "WHERE a.userId=:userId AND (:wrongOnly=false OR a.correct=false) " +
                "AND (:keyword='' OR LOWER(coalesce(a.contentSnapshot,q.content)) LIKE LOWER(:keyword) ESCAPE '!')")
        Page<com.zhixuewuli.dto.response.AnswerRecordSummaryResponse> findHistoryPage(
                @Param("userId") Long userId, @Param("wrongOnly") boolean wrongOnly,
                @Param("keyword") String keyword, Pageable pageable);

        @Query(value = "SELECT new com.zhixuewuli.dto.response.AnswerRecordSummaryResponse(" +
                "a.id,a.eventId,a.practice,a.attemptOrder,a.userId,a.questionId,a.sessionId,a.userAnswer,a.correct," +
                "substring(coalesce(a.contentSnapshot,q.content),1,160),a.answeredAt,a.timestampUtc) " +
                "FROM AnswerRecord a LEFT JOIN a.question q WHERE a.userId=:userId " +
                "AND EXISTS (SELECT x.id FROM AnswerRecord x WHERE x.userId=a.userId AND x.questionId=a.questionId " +
                "AND (x.eventId=a.eventId OR (x.eventId IS NULL AND a.eventId IS NULL)) AND x.correct=false) " +
                "AND NOT EXISTS (SELECT b.id FROM AnswerRecord b WHERE b.userId=a.userId AND b.questionId=a.questionId " +
                "AND (b.eventId=a.eventId OR (b.eventId IS NULL AND a.eventId IS NULL)) AND b.attemptOrder>a.attemptOrder) " +
                "AND (:keyword='' OR LOWER(coalesce(a.contentSnapshot,q.content)) LIKE LOWER(:keyword) ESCAPE '!') " +
                "ORDER BY a.answeredAt DESC, a.attemptOrder DESC",
                countQuery = "SELECT COUNT(a) FROM AnswerRecord a LEFT JOIN a.question q WHERE a.userId=:userId " +
                "AND EXISTS (SELECT x.id FROM AnswerRecord x WHERE x.userId=a.userId AND x.questionId=a.questionId " +
                "AND (x.eventId=a.eventId OR (x.eventId IS NULL AND a.eventId IS NULL)) AND x.correct=false) " +
                "AND NOT EXISTS (SELECT b.id FROM AnswerRecord b WHERE b.userId=a.userId AND b.questionId=a.questionId " +
                "AND (b.eventId=a.eventId OR (b.eventId IS NULL AND a.eventId IS NULL)) AND b.attemptOrder>a.attemptOrder) " +
                "AND (:keyword='' OR LOWER(coalesce(a.contentSnapshot,q.content)) LIKE LOWER(:keyword) ESCAPE '!')")
        Page<com.zhixuewuli.dto.response.AnswerRecordSummaryResponse> findPracticeReviewPage(
                @Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);



        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByEventIdOrderByAnsweredAtDesc(Long eventId);

        @Override
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findAll();

        @Query(value = "SELECT new com.zhixuewuli.dto.response.AnswerRecordListRow(" +
                "a.id,a.userId,a.questionId,a.sessionId,a.userAnswer,a.correct,a.answeredAt," +
                "u.realName,coalesce(a.contentSnapshot,q.content),case when a.contentSnapshot is null then q.options else a.optionsSnapshot end,case when a.contentSnapshot is null then q.correctOption else a.correctSnapshot end,case when a.contentSnapshot is null then q.explanation else a.analysisSnapshot end,case when a.contentSnapshot is null then q.difficulty else a.difficultySnapshot end,a.timestampUtc) " +
                "FROM AnswerRecord a JOIN a.question q JOIN a.user u " +
                "WHERE (:wrongOnly = false OR a.correct = false) AND (:eventId is null OR a.eventId=:eventId) " +
                "AND LOWER(u.realName) LIKE LOWER(:name) ESCAPE '!' " +
                "ORDER BY a.answeredAt DESC, a.id DESC",
                countQuery = "SELECT COUNT(a) FROM AnswerRecord a JOIN a.user u " +
                "WHERE (:wrongOnly = false OR a.correct = false) AND (:eventId is null OR a.eventId=:eventId) " +
                "AND LOWER(u.realName) LIKE LOWER(:name) ESCAPE '!'")
        Page<AnswerRecordListRow> findTeacherPage(
                @Param("wrongOnly") boolean wrongOnly,
                @Param("name") String name, @Param("eventId") Long eventId, Pageable pageable);

        AnswerRecord findBySessionIdAndQuestionId(String sessionId, Long questionId);

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = false ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findWrongAnswersByUserId(Long userId);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN ?1 AND ?2")
        Long countByDateRange(LocalDateTime start, LocalDateTime end);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1")
        Long countByUserId(Long userId);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = true")
        Long countCorrectByUserId(Long userId);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = false")
        long countWrongByUserId(Long userId);


        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.correct = false")
        long countWrong();

        default Double calculateAccuracyRate(Long userId) {
                Long total = countByUserId(userId);
                Long correct = countCorrectByUserId(userId);
                return total > 0 ? (double) correct / total * 100 : 0.0;
        }

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = true AND ar.answeredAt BETWEEN ?2 AND ?3")
        Long countCorrectByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3")
        Long countByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByUserIdAndDateRange(Long userId, LocalDateTime start, LocalDateTime end);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.correct = true AND ar.answeredAt BETWEEN ?1 AND ?2")
        Long countCorrectByDateRange(LocalDateTime start, LocalDateTime end);

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
        List<Object[]> findWrongQuestionStatsByUserId(Long userId, Pageable pageable);

        @Query("SELECT COUNT(DISTINCT a.questionId) FROM AnswerRecord a WHERE a.userId = ?1 AND a.correct = false")
        long countDistinctWrongQuestionsByUserId(Long userId);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2")
        Long countBySessionAndUser(String sessionId, Long userId);

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2 AND ar.correct = true")
        Long countCorrectBySessionAndUser(String sessionId, Long userId);

        @Query("SELECT DISTINCT ar.userId FROM AnswerRecord ar WHERE ar.questionId = ?1 AND ar.correct = false")
        List<Long> findUserIdsByWrongQuestion(Long questionId);

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findBySessionIdAndUserId(String sessionId, Long userId);

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN ?1 AND ?2 ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByDateRange(LocalDateTime start, LocalDateTime end);

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

        @EntityGraph(attributePaths = { "question", "user" })
        List<AnswerRecord> findByUserIdOrderByAnsweredAtDesc(Long userId);

        @EntityGraph(attributePaths = { "question", "user" })
        List<AnswerRecord> findBySessionIdOrderByAnsweredAtDesc(String sessionId);

        @EntityGraph(attributePaths = { "question", "user" })
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.answeredAt BETWEEN ?2 AND ?3 ORDER BY ar.answeredAt DESC")
        List<AnswerRecord> findByUserIdAndDateRangeWithRelations(Long userId, LocalDateTime start, LocalDateTime end);

        @EntityGraph(attributePaths = { "question", "user" })
        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = ?1 AND ar.correct = false ORDER BY ar.answeredAt DESC")
        List<AnswerRecord> findWrongAnswersWithRelations(Long userId);

        Optional<AnswerRecord> findTopByUserIdAndQuestionIdOrderByAnsweredAtDesc(Long userId, Long questionId);

        Optional<AnswerRecord> findTopBySessionIdAndUserIdAndQuestionIdOrderByAnsweredAtDesc(String sessionId,
                        Long userId, Long questionId);

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.correct = false ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByCorrectFalseOrderByAnsweredAtDesc();

        @Query("SELECT ar FROM AnswerRecord ar WHERE ar.userId = :userId AND ar.sessionId = :sessionId ORDER BY ar.answeredAt DESC")
        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findByUserIdAndSessionIdOrderByAnsweredAtDesc(
                        @Param("userId") Long userId,
                        @Param("sessionId") String sessionId);

        @EntityGraph(attributePaths = "question")
        List<AnswerRecord> findBySessionIdOrderByAnsweredAtAsc(String sessionId);

        @Query("SELECT ar.sessionId FROM AnswerRecord ar WHERE ar.userId = :userId " +
                "ORDER BY ar.answeredAt DESC, ar.attemptOrder DESC")
        List<String> findLatestSessionIdsByUserId(@Param("userId") Long userId, Pageable pageable);

        @Modifying
        @Transactional
        @Query("DELETE FROM AnswerRecord ar WHERE ar.answeredAt BETWEEN :startTime AND :endTime")
        int deleteByTimeRange(@Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);

        @Query("SELECT COUNT(*) FROM AnswerRecord WHERE questionId = ?1")
        Long countByQuestionId(Long questionId);

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

        @Query(value = "SELECT " +
                        "u.id, " +
                        "u.real_name, " +
                        "u.class_name, " +
                        "ar.selected_option, " +
                        "ar.answered_at " +
                        "FROM answer_records ar " +
                        "JOIN users u ON ar.user_id = u.id " +
                        "WHERE ar.question_id = :questionId AND ar.correct = false " +
                        "ORDER BY ar.answered_at DESC", nativeQuery = true)
        List<Object[]> findStudentErrorsByQuestionId(
                        @Param("questionId") Long questionId);

        @Query("SELECT DISTINCT ar.userId FROM AnswerRecord ar")
        List<Long> findDistinctUserIds();

        @Query("SELECT COUNT(DISTINCT ar.userId) FROM AnswerRecord ar JOIN ar.user u " +
                "WHERE ar.userId IS NOT NULL AND u.userType = com.zhixuewuli.entity.User.UserType.STUDENT AND u.active = true")
        long countDistinctActiveStudentIds();

        @Query("SELECT COUNT(ar) FROM AnswerRecord ar WHERE ar.sessionId = ?1 AND ar.userId = ?2")
        long countBySessionIdAndUserId(String sessionId, Long userId);

        long countBySessionId(String sessionId);

        @EntityGraph(attributePaths = { "user", "question" })
        List<AnswerRecord> findByQuestionIdOrderByAnsweredAtDesc(Long questionId);
}
