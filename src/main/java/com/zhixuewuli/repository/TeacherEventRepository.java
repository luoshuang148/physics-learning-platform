package com.zhixuewuli.repository;

import com.zhixuewuli.entity.TeacherEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 教师事件数据访问接口
 * 
 * 提供教师事件的增删改查操作，包括全局事件的查询和删除操作。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEvent 教师事件实体类
 */
@Repository
public interface TeacherEventRepository extends JpaRepository<TeacherEvent, Long> {

    /**
     * 根据教师 ID 查找事件
     * 
     * <p>查询指定教师的最新事件记录。</p>
     * 
     * <p><strong>查询条件：</strong>teacherId = ?1</p>
     * 
     * @param teacherId 教师 ID
     * @return Optional<TeacherEvent> 事件对象（如果存在）
     */
    Optional<TeacherEvent> findByTeacherId(Long teacherId);

    /**
     * 检查教师是否存在事件
     * 
     * <p>验证指定教师是否已经发布了事件。</p>
     * 
     * <p><strong>查询条件：</strong>teacherId = ?1</p>
     * 
     * @param teacherId 教师 ID
     * @return boolean 是否存在事件
     */
    boolean existsByTeacherId(Long teacherId);

    /**
     * 删除指定教师的事件
     * 
     * <p>删除指定教师的所有事件记录。</p>
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * DELETE FROM TeacherEvent te 
     * WHERE te.teacherId = :teacherId  -- 教师 ID 匹配
     * }</pre>
     * 
     * <p><strong>事务说明：</strong>使用 {@link Modifying} 和 {@link Transactional} 注解确保数据一致性</p>
     * 
     * @param teacherId 教师 ID
     * @return int 删除的记录数
     * 
     * @see Modifying 修改操作注解
     * @see Transactional 事务管理注解
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TeacherEvent te WHERE te.teacherId = :teacherId")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);

    // ==================== 全局事件相关方法（新增） ====================

    /**
     * 查询全局事件
     * 
     * <p>查找 is_global=true 的事件记录。</p>
     * 
     * <p><strong>查询条件：</strong>isGlobal = true</p>
     * 
     * @return Optional<TeacherEvent> 全局事件对象（如果存在）
     */
    Optional<TeacherEvent> findByIsGlobalTrue();

    /**
     * 检查全局事件是否存在
     * 
     * <p>验证系统中是否已经发布了全局事件。</p>
     * 
     * <p><strong>查询条件：</strong>isGlobal = true</p>
     * 
     * @return boolean 是否存在全局事件
     */
    boolean existsByIsGlobalTrue();

    /**
     * 删除所有事件
     * 
     * <p>删除表中的所有事件记录（用于全局事件删除场景）。</p>
     * 
     * <p><strong>JPQL 查询逻辑：</strong></p>
     * <pre>{@code
     * DELETE FROM TeacherEvent  -- 无条件删除所有记录
     * }</pre>
     * 
     * <p><strong>事务说明：</strong>使用 {@link Modifying} 和 {@link Transactional} 注解确保数据一致性</p>
     * 
     * <p><strong>警告：</strong>此方法会删除所有数据，谨慎使用！</p>
     * 
     * @return int 删除的记录数
     * 
     * @see Modifying 修改操作注解
     * @see Transactional 事务管理注解
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TeacherEvent")
    int deleteAllEvents();
}