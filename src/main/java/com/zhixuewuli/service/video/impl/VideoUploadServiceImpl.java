package com.zhixuewuli.service.video.impl;

import com.zhixuewuli.common.exception.video.VideoBusinessException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.response.VideoUploadResponse;
import com.zhixuewuli.service.video.VideoUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class VideoUploadServiceImpl implements VideoUploadService {

    @Value("${app.video.storage-path}")
    private String storagePath;

    @Value("${app.video.access-path}")
    private String accessPath;

    @Value("${app.video.max-file-size}")
    private Long maxFileSize;

    @Autowired
    private UserPermissionUtil userPermissionUtil;

    @Override
    public VideoUploadResponse uploadVideo(MultipartFile file, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);

        if (file.isEmpty()) {
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "文件不能为空");
        }

        if (file.getSize() > maxFileSize) {
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "文件大小超过限制");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        if (!"mp4".equalsIgnoreCase(extension)) {
            throw new VideoBusinessException(ResultCode.VIDEO_FORMAT_UNSUPPORTED, "仅支持mp4格式，当前格式: " + extension);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new VideoBusinessException(ResultCode.VIDEO_FORMAT_UNSUPPORTED, "无效的文件类型");
        }

        if (originalFilename != null) {
            originalFilename = originalFilename.replaceAll("[\\\\/:*?\"<>|\0]", "_");
        }

        if (file.getSize() < 1024) {
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "文件过小或为空");
        }

        String uniqueFilename = UUID.randomUUID() + "_" + System.currentTimeMillis() + "." + extension;
        Path basePath = Paths.get(storagePath).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(uniqueFilename).normalize();

        try {
            Files.createDirectories(basePath);
            file.transferTo(targetPath.toFile());
        } catch (Exception e) {
            log.error("视频文件保存失败: {}", e.getMessage(), e);
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "文件保存失败: " + e.getMessage());
        }

        File savedFile = targetPath.toFile();
        if (!savedFile.exists() || savedFile.length() != file.getSize()) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (Exception ignored) {
            }
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "文件写入验证失败");
        }

        return VideoUploadResponse.builder()
                .url(accessPath + "/" + uniqueFilename)
                .originalFilename(originalFilename)
                .fileSize(file.getSize())
                .build();
    }

    @Override
    public void deleteVideoFile(String url, Long userId) {
        userPermissionUtil.checkTeacherPermission(userId);
        if (url == null || url.isBlank()) {
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "视频路径不能为空");
        }
        deleteVideoFileByUrl(url);
    }

    @Override
    public void deleteVideoFileByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        if (url.contains("..") || !url.startsWith(accessPath + "/")) {
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "无效的视频路径");
        }

        String filename = url.replace(accessPath + "/", "");
        Path basePath = Paths.get(storagePath).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(filename).normalize();
        File file = filePath.toFile();

        try {
            String canonicalPath = file.getCanonicalPath();
            if (!canonicalPath.startsWith(basePath.toFile().getCanonicalPath())) {
                throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "无效的视频路径");
            }

            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    log.warn("视频文件删除失败: {}", file.getAbsolutePath());
                }
            }
        } catch (VideoBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除视频文件时发生错误: {}", e.getMessage(), e);
            throw new VideoBusinessException(ResultCode.VIDEO_UPLOAD_FAILED, "删除视频文件失败: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
