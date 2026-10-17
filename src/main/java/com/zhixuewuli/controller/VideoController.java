package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.VideoCreateRequest;
import com.zhixuewuli.dto.request.VideoUpdateRequest;
import com.zhixuewuli.dto.response.VideoResponse;
import com.zhixuewuli.entity.Video;
import com.zhixuewuli.service.video.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 视频管理控制器
 * 
 * 提供视频增删改查的RESTful API接口
 * 实现教师角色的视频管理功能和学生角色的视频查询功能
 * 
 * <p>
 * 权限控制：
 * </p>
 * <ul>
 * <li>POST/PUT/DELETE操作：仅限TEACHER角色</li>
 * <li>GET操作：TEACHER和STUDENT角色均可访问</li>
 * <li>所有操作都需要JWT认证</li>
 * </ul>
 * 
 * <p>
 * API端点：
 * </p>
 * <ul>
 * <li>POST /api/videos - 创建视频</li>
 * <li>DELETE /api/videos/{id} - 根据ID删除视频</li>
 * <li>DELETE /api/videos/title/{title} - 根据标题删除视频</li>
 * <li>PUT /api/videos/{id} - 更新视频</li>
 * <li>GET /api/videos/{id} - 根据ID获取视频</li>
 * <li>GET /api/videos/title/{title} - 根据标题获取视频</li>
 * <li>GET /api/videos - 获取所有视频</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoService 视频服务接口
 * @see VideoResponse 视频响应DTO
 */
@RestController
@RequestMapping("/video/teacher")
@Tag(name = "视频管理", description = "视频增删改查相关接口")
@Slf4j
public class VideoController {

    @Autowired
    private VideoService videoService;

    @PostMapping
    @Operation(
        summary = "创建视频",
        description = "教师身份创建新的教学视频",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<VideoResponse> createVideo(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频创建请求参数", required = true) @Valid @RequestBody VideoCreateRequest request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始创建视频，标题：{}", username, request.getTitle());

        Video video = videoService.createVideo(request, userId);
        VideoResponse response = convertToResponse(video);

        log.info("用户 {} 视频创建成功，ID: {}", username, video.getId());

        return Result.created("视频创建成功", response);
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "删除视频",
        description = "根据ID删除指定视频",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "视频不存在")
        }
    )
    public Result<Void> deleteVideo(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频 ID", required = true) @PathVariable @Positive(message = "视频 ID 必须为正数") Long id,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始删除视频，ID: {}", username, id);

        videoService.deleteVideoById(id, userId);

        log.info("用户 {} 视频删除成功，ID: {}", username, id);
        return Result.success();
    }

    @DeleteMapping("/title/{title}")
    @Operation(
        summary = "根据标题删除视频",
        description = "根据视频标题删除指定视频",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "视频不存在")
        }
    )
    public Result<Void> deleteVideoByTitle(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频标题", required = true) @PathVariable @NotBlank(message = "视频标题不能为空") String title,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始删除视频，标题：{}", username, title);

        videoService.deleteVideoByTitle(title, userId);

        log.info("用户 {} 视频删除成功，标题：{}", username, title);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "更新视频",
        description = "全量覆盖更新视频信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "视频不存在")
        }
    )
    public Result<VideoResponse> updateVideo(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频 ID", required = true) @PathVariable @Positive(message = "视频 ID 必须为正数") Long id,
            @io.swagger.v3.oas.annotations.Parameter(description = "视频更新请求参数", required = true) @Valid @RequestBody VideoUpdateRequest request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 开始更新视频，ID: {}", username, id);

        Video video = videoService.updateVideo(id, request, userId);
        VideoResponse response = convertToResponse(video);

        log.info("用户 {} 视频更新成功，ID: {}", username, video.getId());
        return Result.success("视频更新成功", response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "获取视频详情",
        description = "根据ID 获取视频详细信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "视频不存在")
        }
    )
    public Result<VideoResponse> getVideoById(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频 ID", required = true) @PathVariable @Positive(message = "视频ID必须为正数") Long id,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 查询视频详情，ID: {}", username, id);

        Video video = videoService.getVideoById(id);
        VideoResponse response = convertToResponse(video);

        return Result.success(response);
    }

    @GetMapping("/title/{title}")
    @Operation(
        summary = "根据标题获取视频",
        description = "根据视频标题获取视频信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "视频不存在")
        }
    )
    public Result<VideoResponse> getVideoByTitle(
            @io.swagger.v3.oas.annotations.Parameter(description = "视频标题", required = true) @PathVariable @NotBlank(message = "视频标题不能为空") String title,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 查询视频详情，标题: {}", username, title);

        Video video = videoService.getVideoByTitle(title);
        VideoResponse response = convertToResponse(video);

        return Result.success(response);
    }

    @GetMapping("/all")
    @Operation(
        summary = "获取所有视频",
        description = "批量获取数据库中所有视频",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
        }
    )
    public Result<List<VideoResponse>> getAllVideos(HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        log.info("用户 {} 查询所有视频", username);

        List<Video> videos = videoService.getAllVideos();
        List<VideoResponse> responses = videos.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return Result.success(responses);
    }

    /**
     * 转换视频实体为响应DTO
     * 
     * @param video 视频实体对象
     * @return VideoResponse 视频响应DTO
     */
    private VideoResponse convertToResponse(Video video) {
        return VideoResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .url(video.getUrl())
                .knowledgePoint(video.getKnowledgePoint())
                .coverUrl(video.getCoverUrl())
                .questionId(video.getQuestionId())
                .duration(video.getDuration())
                .description(video.getDescription())
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .build();
    }
}