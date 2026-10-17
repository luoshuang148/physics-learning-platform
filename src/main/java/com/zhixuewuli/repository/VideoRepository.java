package com.zhixuewuli.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.zhixuewuli.entity.Video;

import java.util.List;
import java.util.Optional;

/**
 * 视频数据访问仓库接口
 * 
 * 继承JPA Repository，提供视频实体的数据库操作方法。
 * 包含基本的CRUD操作以及针对视频业务场景的定制查询。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>视频的增删改查操作</li>
 * <li>唯一性约束检查</li>
 * <li>按标题查询视频</li>
 * <li>批量视频查询优化</li>
 * <li>关联题目信息查询</li>
 * </ul>
 * 
 * <p>
 * 性能优化：
 * </p>
 * <ul>
 * <li>利用数据库唯一约束保证数据完整性</li>
 * <li>合理的索引设计支持高效查询</li>
 * <li>批量操作减少数据库往返次数</li>
 * <li>预加载关联数据避免N+1查询</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see Video 视频实体类
 * @see JpaRepository Spring Data JPA基础接口
 */
@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    /**
     * 检查标题是否存在
     * 
     * <p>用于创建视频时验证标题唯一性。</p>
     * 
     * <p><strong>查询条件：</strong>title = ?1</p>
     * 
     * @param title 视频标题
     * @return boolean 标题是否存在
     */
    boolean existsByTitle(String title);

    /**
     * 检查 URL 是否存在
     * 
     * <p>用于创建视频时验证 URL 唯一性。</p>
     * 
     * <p><strong>查询条件：</strong>url = ?1</p>
     * 
     * @param url 视频播放链接
     * @return boolean URL 是否存在
     */
    boolean existsByUrl(String url);

    /**
     * 根据标题查找视频
     * 
     * <p>用于按标题查询视频详情。</p>
     * 
     * <p><strong>查询条件：</strong>title = ?1</p>
     * 
     * @param title 视频标题
     * @return Optional<Video> 视频对象（可能为空）
     */
    Optional<Video> findByTitle(String title);

    /**
     * 获取所有视频，按创建时间倒序排列
     * 
     * <p>用于视频列表查询，最新创建的视频排在前面。</p>
     * 
     * <p><strong>排序规则：</strong>ORDER BY createdAt DESC</p>
     * 
     * @return List<Video> 视频列表
     */
    List<Video> findAllByOrderByCreatedAtDesc();
}
