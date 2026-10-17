package com.zhixuewuli.repository;

import com.zhixuewuli.entity.StudentReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * 学生报告仓库接口
 * 
 * 提供学生报告的CRUD操作和常用查询方法。
 */
@Repository
public interface StudentReportRepository extends JpaRepository<StudentReport, Long> {

    /**
     * 根据用户 ID 查找该学生的所有报告（按创建时间倒序）
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT sr FROM StudentReport sr 
     * WHERE sr.userId = ?1               -- 用户 ID 匹配
     * ORDER BY sr.createdAt DESC         -- 按创建时间降序
     * }</pre>
     * 
     * @param userId 学生用户 ID
     * @return 报告列表
     */
    @Query("SELECT sr FROM StudentReport sr WHERE sr.userId = ?1 ORDER BY sr.createdAt DESC")
    List<StudentReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据会话 ID 查找报告
     * 
     * <p><strong>查询条件：</strong>sessionId = ?1</p>
     * 
     * @param sessionId 会话 ID
     * @return 报告列表
     */
    List<StudentReport> findBySessionId(String sessionId);

    /**
     * 根据用户 ID 和会话 ID 查找特定报告
     * 
     * <p><strong>查询条件：</strong></p>
     * <ul>
     *   <li>userId = ?1 - 用户 ID 匹配</li>
     *   <li>sessionId = ?2 - 会话 ID 匹配</li>
     * </ul>
     * 
     * @param userId    学生用户 ID
     * @param sessionId 会话 ID
     * @return 报告信息（如果存在）
     */
    Optional<StudentReport> findByUserIdAndSessionId(Long userId, String sessionId);

    /**
     * 查找用户的最新报告
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT sr FROM StudentReport sr 
     * WHERE sr.userId = ?1               -- 用户 ID 匹配
     * ORDER BY sr.createdAt DESC         -- 按创建时间降序
     * LIMIT 1                            -- 仅返回第一条
     * }</pre>
     * 
     * @param userId 学生用户 ID
     * @return 最新报告（如果存在）
     */
    @Query("SELECT sr FROM StudentReport sr WHERE sr.userId = ?1 ORDER BY sr.createdAt DESC LIMIT 1")
    Optional<StudentReport> findLatestByUserId(Long userId);
}