package com.zhixuewuli.repository;

import com.zhixuewuli.entity.QuestionSearchUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionSearchUnitRepository extends JpaRepository<QuestionSearchUnit, Long> {

    List<QuestionSearchUnit> findByQuestionIdAndRevisionOrderByPartAscOrdinalNoAsc(Long questionId, Long revision);

    @Query("select u from QuestionSearchUnit u where u.questionId = :questionId and u.revision = :revision")
    List<QuestionSearchUnit> findUnits(@Param("questionId") Long questionId, @Param("revision") Long revision);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from QuestionSearchUnit u where u.questionId = :questionId and u.revision < :throughRevision")
    int deleteByQuestionIdAndRevisionLessThan(@Param("questionId") Long questionId, @Param("throughRevision") Long throughRevision);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from QuestionSearchUnit u where u.questionId = :questionId")
    int deleteByQuestionId(@Param("questionId") Long questionId);

    long countByQuestionIdAndRevision(Long questionId, Long revision);

    /**
     * 中文关键词检索（MySQL FULLTEXT / 可测试分词适配器）。
     * 仅在给定授权候选集合内检索；candidateIds 为空时返回空。
     */
    @Query(value = """
            SELECT u.* FROM question_search_units u
            WHERE u.question_id IN (:candidateIds)
              AND MATCH(u.unit_text) AGAINST (:query IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(u.unit_text) AGAINST (:query IN NATURAL LANGUAGE MODE) DESC, u.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<QuestionSearchUnit> searchByFullText(@Param("query") String query,
                                              @Param("candidateIds") List<Long> candidateIds,
                                              @Param("limit") int limit);

    /**
     * 兜底关键词适配器：LIKE 分词（禁止声称是全文检索）。
     * 仅用于 FULLTEXT 不可用时的显式降级路径，actualMode 记为 lexical_only。
     */
    @Query(value = """
            SELECT u.* FROM question_search_units u
            WHERE u.question_id IN (:candidateIds)
              AND (u.unit_text LIKE CONCAT('%', :token, '%'))
            ORDER BY u.question_id ASC, u.ordinal_no ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<QuestionSearchUnit> searchByTokenLike(@Param("token") String token,
                                               @Param("candidateIds") List<Long> candidateIds,
                                               @Param("limit") int limit);
}
