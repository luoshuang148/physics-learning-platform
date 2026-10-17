package com.zhixuewuli.service.video.impl;

import com.zhixuewuli.common.exception.video.VideoAlreadyExistsException;
import com.zhixuewuli.common.exception.video.VideoNotFoundException;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.VideoCreateRequest;
import com.zhixuewuli.dto.request.VideoUpdateRequest;
import com.zhixuewuli.entity.Video;
import com.zhixuewuli.repository.VideoRepository;
import com.zhixuewuli.service.video.VideoService;
import com.zhixuewuli.service.video.VideoUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频服务实现类
 * 
 * 实现视频管理的核心业务逻辑
 * 提供完整的视频增删改查功能
 * 
 * <p>
 * 实现特点：
 * </p>
 * <ul>
 * <li>严格的事务管理确保数据一致性</li>
 * <li>完善的日志记录便于问题追踪</li>
 * <li>详细的异常处理和业务规则验证</li>
 * <li>性能优化的只读事务配置</li>
 * <li>全量覆盖更新策略的精确实现</li>
 * </ul>
 * 
 * <p>
 * 事务管理：
 * </p>
 * <ul>
 * <li>写操作使用默认事务传播行为</li>
 * <li>读操作使用readOnly=true优化性能</li>
 * <li>异常情况下自动回滚事务</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoService 视频服务接口
 * @see VideoRepository 视频数据访问接口
 */
@Service
@Transactional
@Slf4j
public class VideoServiceImpl implements VideoService {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserPermissionUtil userPermissionUtil;

    @Autowired
    private VideoUploadService videoUploadService;

    @Value("${app.video.storage-path}")
    private String videoStoragePath;

    @Override
    public Video createVideo(VideoCreateRequest request, Long userId) {
        log.info("开始创建视频，用户 ID: {}, 标题：{}", userId, request.getTitle());

        // 检查教师权限
        userPermissionUtil.checkTeacherPermission(userId);

        // 验证标题唯一性
        if (videoRepository.existsByTitle(request.getTitle())) {
            log.warn("视频标题已存在: {}", request.getTitle());
            throw new VideoAlreadyExistsException("标题", request.getTitle());
        }

        // 验证URL唯一性
        if (videoRepository.existsByUrl(request.getUrl())) {
            log.warn("视频URL已存在: {}", request.getUrl());
            throw new VideoAlreadyExistsException("播放链接", request.getUrl());
        }

        Video video = new Video();
        video.setTitle(request.getTitle());
        video.setUrl(request.getUrl());
        video.setKnowledgePoint(request.getKnowledgePoint());
        video.setCoverUrl(request.getCoverUrl());
        video.setDuration(request.getDuration());
        video.setDescription(request.getDescription());
        video.setQuestionId(request.getQuestionId());

        Video savedVideo = videoRepository.save(video);
        log.info("视频创建成功，ID: {}, 关联题目 ID: {}", savedVideo.getId(), savedVideo.getQuestionId());
        return savedVideo;
    }

    @Override
    public void deleteVideoById(Long id, Long userId) {
        log.info("开始删除视频，用户 ID: {}, 视频 ID: {}", userId, id);

        // 检查教师权限
        userPermissionUtil.checkTeacherPermission(userId);

        Video video = videoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("尝试删除不存在的视频，ID: {}", id);
                    return new VideoNotFoundException(id);
                });

        String videoUrl = video.getUrl();
        videoRepository.delete(video);
        deletePhysicalVideoFile(videoUrl);
        log.info("视频删除成功，ID: {}", id);
    }

    @Override
    public void deleteVideoByTitle(String title, Long userId) {
        log.info("开始删除视频，用户 ID: {}, 标题：{}", userId, title);

        // 检查教师权限
        userPermissionUtil.checkTeacherPermission(userId);

        Video video = videoRepository.findByTitle(title)
                .orElseThrow(() -> {
                    log.warn("尝试删除不存在的视频，标题: {}", title);
                    return new VideoNotFoundException(title);
                });

        String videoUrl = video.getUrl();
        videoRepository.delete(video);
        deletePhysicalVideoFile(videoUrl);
        log.info("视频删除成功，标题: {}", title);
    }

    @Override
    public Video updateVideo(Long id, VideoUpdateRequest request, Long userId) {
        log.info("开始更新视频，用户 ID: {}, 视频 ID: {}", userId, id);

        // 检查教师权限
        userPermissionUtil.checkTeacherPermission(userId);

        Video existingVideo = videoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("尝试更新不存在的视频，ID: {}", id);
                    return new VideoNotFoundException(id);
                });

        // 如果标题发生变化，检查新标题的唯一性
        if (request.getTitle() != null && !request.getTitle().equals(existingVideo.getTitle())) {
            if (videoRepository.existsByTitle(request.getTitle())) {
                log.warn("更新视频时标题冲突，新标题: {}", request.getTitle());
                throw new VideoAlreadyExistsException("标题", request.getTitle());
            }
            existingVideo.setTitle(request.getTitle());
        } else if (request.getTitle() == null) {
            // 全量覆盖：新标题为null时清空原标题
            log.debug("全量覆盖更新：清空视频标题");
            existingVideo.setTitle(null);
        }

        // 如果URL发生变化，检查新URL的唯一性
        if (request.getUrl() != null && !request.getUrl().equals(existingVideo.getUrl())) {
            if (videoRepository.existsByUrl(request.getUrl())) {
                log.warn("更新视频时URL冲突，新URL: {}", request.getUrl());
                throw new VideoAlreadyExistsException("播放链接", request.getUrl());
            }
            existingVideo.setUrl(request.getUrl());
        } else if (request.getUrl() == null) {
            // 全量覆盖：新URL为null时清空原URL
            log.debug("全量覆盖更新：清空视频URL");
            existingVideo.setUrl(null);
        }

        // 全量覆盖其他字段（即使为null也要覆盖）
        existingVideo.setKnowledgePoint(request.getKnowledgePoint());
        existingVideo.setCoverUrl(request.getCoverUrl());
        existingVideo.setDuration(request.getDuration());
        existingVideo.setDescription(request.getDescription());
        existingVideo.setQuestionId(request.getQuestionId());
        
        Video updatedVideo = videoRepository.save(existingVideo);
        log.info("视频更新成功，ID: {}, 关联题目 ID: {}", updatedVideo.getId(), updatedVideo.getQuestionId());
        return updatedVideo;
    }

    @Override
    @Transactional(readOnly = true)
    public Video getVideoById(Long id) {
        log.debug("查询视频详情，ID: {}", id);
        return videoRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("查询不存在的视频，ID: {}", id);
                    return new VideoNotFoundException(id);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Video getVideoByTitle(String title) {
        log.debug("查询视频详情，标题: {}", title);
        return videoRepository.findByTitle(title)
                .orElseThrow(() -> {
                    log.warn("查询不存在的视频，标题: {}", title);
                    return new VideoNotFoundException(title);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Video> getAllVideos() {
        log.debug("查询所有视频");
        List<Video> videos = videoRepository.findAllByOrderByCreatedAtDesc();
        return videos != null ? videos : new ArrayList<>();
    }

    private void deletePhysicalVideoFile(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return;
        }

        try {
            String filename;
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                filename = Paths.get(new URI(videoUrl).getPath()).getFileName().toString();
            } else {
                filename = Paths.get(videoUrl).getFileName().toString();
            }

            if (filename == null || filename.isBlank()) {
                return;
            }

            Path filePath = Paths.get(videoStoragePath).toAbsolutePath().normalize().resolve(filename).normalize();
            File file = filePath.toFile();
            if (file.exists() && !file.delete()) {
                log.warn("视频文件删除失败: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("删除视频物理文件失败，url: {}", videoUrl, e);
        }
    }
}
