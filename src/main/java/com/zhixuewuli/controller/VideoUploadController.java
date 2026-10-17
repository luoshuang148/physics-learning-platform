package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.response.VideoUploadResponse;
import com.zhixuewuli.service.video.VideoUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/video/upload")
@Tag(name = "视频上传", description = "视频文件上传相关接口")
@Slf4j
public class VideoUploadController {
    
    @Autowired
    private VideoUploadService videoUploadService;
    
    @PostMapping
    @Operation(summary = "上传视频文件", description = "上传视频到本地存储，返回访问URL")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<VideoUploadResponse> uploadVideo(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始上传视频，文件名：{}", username, file.getOriginalFilename());
        
        VideoUploadResponse response = videoUploadService.uploadVideo(file, userId);
        log.info("用户 {} 视频上传成功，URL：{}", username, response.getUrl());
        
        return Result.created("视频上传成功", response);
    }
    
    @DeleteMapping
    @Operation(summary = "删除视频文件", description = "根据URL删除云服务器上的视频物理文件")
    public Result<Void> deleteVideoFile(
            @RequestParam String url,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 删除视频文件: {}", username, url);
        
        videoUploadService.deleteVideoFile(url, userId);
        log.info("用户 {} 视频文件删除成功", username);
        
        return Result.success();
    }
}
