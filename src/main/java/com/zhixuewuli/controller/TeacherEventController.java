package com.zhixuewuli.controller;

import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.TeacherEventResponse;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 教师事件管理控制器
 * 
 * 提供教师全局事件管理的 RESTful API 接口。
 * 实现事件发布、删除和查询功能，全局事件在整个系统中唯一。
 * 
 * <p>
 * 接口设计特点：
 * </p>
 * <ul>
 * <li>无状态设计：所有操作基于 JWT 中的用户身份</li>
 * <li>全局事件唯一：整个系统共享同一个教师事件</li>
 * <li>简洁 API：无需传递事件 ID，操作针对全局事件</li>
 * <li>标准响应：统一使用 Result 包装响应数据</li>
 * <li>完整文档：集成 Swagger API 文档</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEventService 教师事件服务接口
 * @see TeacherEventCreateRequest 事件创建请求 DTO
 * @see TeacherEventResponse 事件响应 DTO
 */
@RestController
@RequestMapping("/teacher/events")
@Tag(name = "教师事件管理", description = "教师事件发布和管理相关接口")
@RequiredArgsConstructor
@Slf4j
public class TeacherEventController {

    private final TeacherEventService teacherEventService;

    /**
     * 发布教师事件
     * 
     * 教师发布新的学习事件作为时间基准点。
     * 如果全局事件已存在，将返回 409 Conflict 错误码。
     * 
     * @param request       事件创建请求
     * @param authorization Authorization 头部，包含 JWT 令牌
     * @return Result<TeacherEventResponse> 创建的事件响应
     * 
     * @permission 教师权限
     * @apiNote 请求示例：
     *          POST /api/teacher/events
     *          Headers: Authorization: Bearer <jwt_token>
     *          Body: {
     *          "eventTitle": "新学期开始",
     *          "eventDescription": "2026 年春季学期正式开始",
     *          "eventTimestamp": "2026-03-01T08:00:00"
     *          }
     */
    @PostMapping
    @Operation(
        summary = "发布教师事件",
        description = "教师发布新的学习事件作为时间基准点，如果全局事件已存在将返回 409 错误码",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "发布成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "全局事件已存在")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TeacherEventResponse> publishEvent(
            @io.swagger.v3.oas.annotations.Parameter(description = "事件创建请求参数", required = true) @RequestBody @Valid TeacherEventCreateRequest request,
            HttpServletRequest httpRequest) {
    
        log.info("收到发布全局事件请求");
    
        // 从请求属性中获取用户 ID（由拦截器解析 JWT 提供）
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
    
        TeacherEventResponse response = teacherEventService.publishEvent(request, currentUserId);
        return Result.created("事件发布成功", response);
    }

    /**
     * 删除当前事件
     * 
     * 删除全局事件记录并清理关联数据（答案记录、对话历史、学生报告）。
     * 删除操作幂等：事件不存在也视为成功。
     * 
     * @param authorization Authorization 头部，包含 JWT 令牌
     * @return Result<Void> 删除结果响应
     * 
     * @permission 教师权限
     * @apiNote 请求示例：
     *          DELETE /api/teacher/events
     *          Headers: Authorization: Bearer <jwt_token>
     */
    @DeleteMapping
    @Operation(
        summary = "删除当前事件",
        description = "删除全局事件记录并清理关联数据，删除操作幂等",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<Void> deleteCurrentEvent(HttpServletRequest httpRequest) {
    
        log.info("收到删除全局事件请求");
    
        // 从请求属性中获取用户 ID（由拦截器解析 JWT 提供）
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
    
        teacherEventService.deleteCurrentEvent(currentUserId);
        return Result.success();
    }

    /**
     * 查询当前事件
     * 
     * 查询全局事件信息。所有认证用户可访问。
     * 如果事件不存在，将返回 null 数据。
     * 
     * @param authorization Authorization 头部，包含 JWT 令牌
     * @return Result<TeacherEventResponse> 事件响应，如果不存在则 data 为 null
     * 
     * @permission 所有认证用户
     * @apiNote 请求示例：
     *          GET /api/teacher/events
     *          Headers: Authorization: Bearer <jwt_token>
     */
    @GetMapping
    @Operation(
        summary = "查询当前事件",
        description = "查询全局事件信息，所有认证用户可访问，如果事件不存在则 data 为 null",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<TeacherEventResponse> getCurrentEvent(HttpServletRequest httpRequest) {

        log.info("收到查询全局事件请求");

        // 不再需要传递 userId，直接调用 getGlobalEvent()
        TeacherEventResponse response = teacherEventService.getGlobalEvent();
        return Result.success(response);
    }
}