package com.zhixuewuli.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zhixuewuli.entity.Question;

import java.util.List;

/**
 * 题目数据访问仓库接口
 * 
 * 继承 JPA Repository，提供题目实体的数据库操作方法。
 * 包含基本的 CRUD 操作以及针对业务场景的定制查询。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>题目的增删改查操作</li>
 * <li>批量题目查询优化</li>
 * </ul>
 * 
 * <p>
 * 性能优化：
 * </p>
 * <ul>
 * <li>合理的索引设计支持高效查询</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see Question 题目实体类
 * @see JpaRepository Spring Data JPA 基础接口
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    @Query("select new com.zhixuewuli.dto.response.QuestionCatalogRow(q.id, substring(q.content,1,180), q.correctOption, q.difficulty, " +
            "case when q.imageBase64 is not null and q.imageBase64 <> '' then true else false end) from Question q " +
            "where (:difficulty is null or q.difficulty=:difficulty) and " +
            "(lower(q.content) like :pattern escape '!' or str(q.id) like :pattern escape '!') order by q.id desc")
    org.springframework.data.domain.Page<com.zhixuewuli.dto.response.QuestionCatalogRow> catalog(
            @Param("pattern") String pattern, @Param("difficulty") Integer difficulty, org.springframework.data.domain.Pageable pageable);

    /**
     * 检查题目内容是否存在
     * 
     * <p>用于验证题目内容的全局唯一性。</p>
     * 
     * <p><strong>查询条件：</strong>content = ?1</p>
     * 
     * @param content 题目内容
     * @return boolean 是否存在相同内容的题目
     */
    boolean existsByContent(String content);

    /**
     * 批量查询题目信息
     * 
     * @param ids 题目 ID 列表
     * @return List<Question> 题目列表
     */
    List<Question> findByIdIn(List<Long> ids);

    /**
     * 获取所有题目并按创建时间倒序排列
     * 
     * @return List<Question> 题目列表，按创建时间降序排列
     */
    List<Question> findAllByOrderByCreatedAtDesc();

    /**
     * 批量查询题目信息
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT q FROM Question q 
     * WHERE q.id IN :questionIds  -- 题目 ID 列表匹配
     * }</pre>
     * 
     * @param questionIds 题目 ID 列表
     * @return List<Question> 题目列表
     */
    @Query("SELECT q FROM Question q WHERE q.id IN :questionIds")
    List<Question> findQuestionsByIds(@Param("questionIds") List<Long> questionIds);

    /**
     * 验证题目是否存在
     * 
     * <p><strong>查询条件：</strong>id = ?1</p>
     * 
     * @param questionId 题目 ID
     * @return boolean 题目是否存在
     */
    boolean existsById(Long questionId);
}
