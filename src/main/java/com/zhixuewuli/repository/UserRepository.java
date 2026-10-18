package com.zhixuewuli.repository;

import com.zhixuewuli.entity.User;
import com.zhixuewuli.entity.User.UserType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true,clearAutomatically=true)
    @org.springframework.transaction.annotation.Transactional
    @Query("update User u set u.lastLogin=:time where u.id=:id and u.tokenVersion=:expectedVersion and u.active=true")
    int recordLoginIfVersionMatches(Long id, Long expectedVersion, java.time.LocalDateTime time);
    long countByUserTypeAndActiveTrue(UserType role);

    @Query("SELECT u FROM User u WHERE u.userType = com.zhixuewuli.entity.User.UserType.STUDENT AND u.active = true ORDER BY u.className, u.realName")
    List<User> findActiveStudents();
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true,clearAutomatically=true)
    @org.springframework.transaction.annotation.Transactional
    @Query("update User u set u.tokenVersion=u.tokenVersion+1 where u.id=:id")
    int incrementTokenVersion(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);


    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 根据班级查找学生列表（预加载答题记录）
     * 
     * <p>查询指定班级的所有活跃学生，同时预加载其答题记录集合，
     * 避免在遍历时触发 N+1 查询问题。</p>
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT u FROM User u 
     * WHERE u.className = ?1           -- 班级名称匹配
     *   AND u.userType = 'STUDENT'     -- 仅查询学生角色
     *   AND u.active = true            -- 仅查询活跃用户
     * }</pre>
     * 
     * <p><strong>性能优化：</strong>使用 {@link EntityGraph} 预加载 {@code answerRecords} 关联集合</p>
     * 
     * @param className 班级名称
     * @return 学生列表（包含预加载的答题记录）
     * 
     * @see EntityGraph 预加载注解说明
     */
    @EntityGraph(attributePaths = { "answerRecords" })
    @Query("SELECT u FROM User u WHERE u.className = ?1 AND u.userType = 'STUDENT' AND u.active = true")
    List<User> findActiveStudentsByClass(String className);

    /**
     * 统计班级学生人数
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT COUNT(u) FROM User u 
     * WHERE u.className = ?1           -- 班级名称匹配
     *   AND u.userType = 'STUDENT'     -- 仅统计学生角色
     *   AND u.active = true            -- 仅统计活跃用户
     * }</pre>
     * 
     * @param className 班级名称
     * @return 活跃学生人数
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.className = ?1 AND u.userType = 'STUDENT' AND u.active = true")
    Long countActiveStudentsByClass(String className);

    /**
     * 根据真实姓名查找用户（精确匹配）
     * 
     * 用于教师按学生姓名查询会话记录功能。
     * 由于real_name字段具有唯一性约束，返回Optional确保精确匹配。
     * 
     * @param realName 学生真实姓名
     * @return 用户信息（如果存在）
     */
    Optional<User> findByRealName(String realName);

    /**
     * 检查真实姓名是否存在
     * 
     * 用于用户更新时的唯一性约束检查。
     * 
     * @param realName 真实姓名
     * @return 是否存在
     */
    boolean existsByRealName(String realName);

    /**
     * 根据用户ID列表批量查询真实姓名
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT u.id, u.realName FROM User u WHERE u.id IN ?1
     * }</pre>
     * 
     * <p><strong>用途：</strong>用于异步任务中批量获取用户姓名，避免懒加载问题</p>
     * 
     * @param userIds 用户ID列表
     * @return 包含 [userId, realName] 的数组列表
     */
    @Query("SELECT u.id, u.realName FROM User u WHERE u.id IN ?1")
    List<Object[]> findIdsAndRealNamesByUserIds(List<Long> userIds);

    /**
     * 按用户类型统计数量
     *
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * SELECT COUNT(u) FROM User u WHERE u.userType = ?1
     * }</pre>
     *
     * <p><strong>用途：</strong>用于全局统计学生或教师人数</p>
     *
     * @param userType 用户类型（非空）
     * @return 符合条件的学生人数
     */
    long countByUserType(UserType userType);
}
