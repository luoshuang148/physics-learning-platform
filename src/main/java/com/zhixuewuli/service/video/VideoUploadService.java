package com.zhixuewuli.service.video;

import com.zhixuewuli.dto.response.VideoUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VideoUploadService {
    /**
     * 上传视频文件到本地存储
     * @param file 上传的文件
     * @param userId 当前用户ID（用于权限校验）
     * @return 上传响应对包含访问URL
     */
    VideoUploadResponse uploadVideo(MultipartFile file, Long userId);
    
    /**
     * 删除视频物理文件
     * @param url 视频访问URL
     * @param userId 当前用户ID（用于权限校验）
     */
    void deleteVideoFile(String url, Long userId);

    /**
     * 删除视频物理文件（内部调用，无权限校验）
     * @param url 视频访问URL
     */
    void deleteVideoFileByUrl(String url);
}
