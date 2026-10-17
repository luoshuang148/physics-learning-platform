package com.zhixuewuli.service.video;

import com.zhixuewuli.common.exception.video.VideoAlreadyExistsException;
import com.zhixuewuli.common.exception.video.VideoNotFoundException;
import com.zhixuewuli.dto.request.VideoCreateRequest;
import com.zhixuewuli.dto.request.VideoUpdateRequest;
import com.zhixuewuli.entity.Video;

import java.util.List;

/**
 * 视频服务接口
 * 
 * 提供视频管理的核心业务逻辑
 * 包含视频的增删改查等完整功能
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>视频创建与唯一性验证</li>
 * <li>视频删除（支持ID和标题两种方式）</li>
 * <li>视频更新（全量覆盖策略）</li>
 * <li>视频查询（单个和批量）</li>
 * <li>数据完整性保障</li>
 * </ul>
 * 
 * <p>
 * 业务规则：
 * </p>
 * <ul>
 * <li>视频标题和URL必须全局唯一</li>
 * <li>更新操作采用全量覆盖策略</li>
 * <li>删除操作会级联影响关联的题目</li>
 * <li>所有操作都有完整的日志记录</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see Video 视频实体类
 * @see VideoCreateRequest 视频创建请求DTO
 * @see VideoUpdateRequest 视频更新请求DTO
 */
public interface VideoService {

    /**
     * 创建新视频
     * 
     * @param request 视频创建请求
     * @param userId  当前用户 ID（用于权限检查）
     * @return 创建的视频实体
     * @throws VideoAlreadyExistsException     当标题或 URL 已存在时抛出
     * @throws InsufficientPermissionException 当用户无教师权限时抛出
     * 
     * @implSpec 验证标题和 URL 的唯一性
     * @implSpec 检查用户权限（仅教师可创建）
     * @implSpec 记录创建操作日志
     * @implSpec 返回完整的视频实体信息
     */
    Video createVideo(VideoCreateRequest request, Long userId);

    /**
     * 根据 ID 删除视频
     * 
     * @param id     视频 ID
     * @param userId 当前用户 ID（用于权限检查）
     * @throws VideoNotFoundException          当视频不存在时抛出
     * @throws InsufficientPermissionException 当用户无教师权限时抛出
     * 
     * @implSpec 验证视频存在性
     * @implSpec 检查用户权限（仅教师可删除）
     * @implSpec 记录删除操作日志
     * @implSpec 支持级联删除关联数据
     */
    void deleteVideoById(Long id, Long userId);

    /**
     * 根据标题删除视频
     * 
     * @param title  视频标题
     * @param userId 当前用户 ID（用于权限检查）
     * @throws VideoNotFoundException          当视频不存在时抛出
     * @throws InsufficientPermissionException 当用户无教师权限时抛出
     * 
     * @implSpec 验证视频存在性
     * @implSpec 检查用户权限（仅教师可删除）
     * @implSpec 记录删除操作日志
     * @implSpec 支持级联删除关联数据
     */
    void deleteVideoByTitle(String title, Long userId);

    /**
     * 更新视频信息（全量覆盖）
     * 
     * @param id      视频 ID
     * @param request 更新请求
     * @param userId  当前用户 ID（用于权限检查）
     * @return 更新后的视频实体
     * @throws VideoNotFoundException          当视频不存在时抛出
     * @throws VideoAlreadyExistsException     当新标题或 URL 已存在时抛出
     * @throws InsufficientPermissionException 当用户无教师权限时抛出
     * 
     * @implSpec 验证视频存在性
     * @implSpec 检查用户权限（仅教师可更新）
     * @implSpec 检查更新后唯一性约束
     * @implSpec 采用全量覆盖更新策略
     * @implSpec 记录更新操作日志
     */
    Video updateVideo(Long id, VideoUpdateRequest request, Long userId);

    /**
     * 根据ID获取视频
     * 
     * @param id 视频ID
     * @return 视频实体
     * @throws VideoNotFoundException 当视频不存在时抛出
     * 
     * @implSpec 验证视频存在性
     * @implSpec 记录查询操作日志
     * @implSpec 只读事务优化性能
     */
    Video getVideoById(Long id);

    /**
     * 根据标题获取视频
     * 
     * @param title 视频标题
     * @return 视频实体
     * @throws VideoNotFoundException 当视频不存在时抛出
     * 
     * @implSpec 验证视频存在性
     * @implSpec 记录查询操作日志
     * @implSpec 只读事务优化性能
     */
    Video getVideoByTitle(String title);

    /**
     * 获取所有视频
     * 
     * @return 视频列表，按创建时间倒序排列
     * 
     * @implSpec 只读事务优化性能
     * @implSpec 按创建时间倒序排序
     * @implSpec 记录批量查询日志
     */
    List<Video> getAllVideos();
}