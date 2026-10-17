package com.zhixuewuli.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zhixuewuli.entity.Experiment;

import java.util.List;
import java.util.Optional;

/**
 * 实验配置数据访问仓库接口
 * 
 * <p>
 * 继承 JPA Repository，提供实验实体的数据库操作方法。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>实验配置的增删改查操作</li>
 * <li>按名称查询实验</li>
 * <li>按序号排序查询</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Repository
public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    
    /**
     * 根据名称查找实验
     * 
     * <p><strong>查询条件：</strong>name = ?1</p>
     * 
     * @param name 实验名称
     * @return Optional<Experiment> 实验对象（可能为空）
     */
    Optional<Experiment> findByName(String name);
    
    /**
     * 检查实验名称是否存在
     * 
     * <p><strong>查询条件：</strong>name = ?1</p>
     * 
     * @param name 实验名称
     * @return boolean 是否存在该名称的实验
     */
    boolean existsByName(String name);
    
    /**
     * 根据序号查找实验
     * 
     * <p><strong>查询条件：</strong>orderNumber = ?1</p>
     * 
     * @param orderNumber 实验序号
     * @return Optional<Experiment> 实验对象（可能为空）
     */
    Optional<Experiment> findByOrderNumber(Integer orderNumber);
    
    /**
     * 检查实验序号是否存在
     * 
     * <p><strong>查询条件：</strong>orderNumber = ?1</p>
     * 
     * @param orderNumber 实验序号
     * @return boolean 是否存在该序号的实验
     */
    boolean existsByOrderNumber(Integer orderNumber);
    
    /**
     * 获取所有实验并按序号正序排序
     * 
     * <p><strong>排序规则：</strong>ORDER BY orderNumber ASC</p>
     * 
     * @return List<Experiment> 实验列表，按 orderNumber 升序排列
     */
    List<Experiment> findAllByOrderByOrderNumberAsc();
    
    /**
     * 获取所有实验并按创建时间倒序排序（备用）
     * 
     * <p><strong>排序规则：</strong>ORDER BY createdAt DESC</p>
     * 
     * @return List<Experiment> 实验列表，按创建时间降序排列
     */
    List<Experiment> findAllByOrderByCreatedAtDesc();
}
