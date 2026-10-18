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
 * 提供教师教学任务管理的 RESTful API 接口。
 * 单班内允许并行任务，任务按草稿、已发布、已截止、已归档流转。
 * 
 * <p>
 * 接口设计特点：
 * </p>
 * <ul>
 * <li>无状态设计：所有操作基于 JWT 中的用户身份</li>
 * <li>并行任务：同一班级可同时存在多个已发布任务</li>
 * <li>生命周期：草稿、发布、截止、归档</li>
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

    @GetMapping("/history")
    public Result<com.zhixuewuli.common.result.PageResult<TeacherEventResponse>> history(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Result.success(teacherEventService.getEventHistory((Long) request.getAttribute("userId"), page, size));
    }


    /**
     * 发布教师事件
     * 
     * 教师发布新的学习事件作为时间基准点。
     * 默认直接发布；传入 status=DRAFT 可保存草稿。
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
        description = "教师创建教学任务，默认直接发布，也可以保存为草稿",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "发布成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "任务参数不合法")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<TeacherEventResponse> publishEvent(
            @io.swagger.v3.oas.annotations.Parameter(description = "事件创建请求参数", required = true) @RequestBody @Valid TeacherEventCreateRequest request,
            HttpServletRequest httpRequest) {
    
        log.info("收到创建教学任务请求");
    
        // 从请求属性中获取用户 ID（由拦截器解析 JWT 提供）
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
    
        TeacherEventResponse response = teacherEventService.publishEvent(request, currentUserId);
        return Result.created("事件发布成功", response);
    }

    /**
     * 删除当前事件
     * 
     * 归档最新发布任务，保留学习记录和报告。
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
        summary = "归档最新发布任务",
        description = "兼容旧入口：归档最新发布任务并保留学习数据，操作幂等",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<Void> deleteCurrentEvent(HttpServletRequest httpRequest, @RequestParam Long expectedEventId) {
    
        log.info("收到归档任务请求");
    
        // 从请求属性中获取用户 ID（由拦截器解析 JWT 提供）
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
    
        teacherEventService.archiveEvent(expectedEventId,currentUserId);
        return Result.success();
    }

    /**
     * 查询当前事件
     * 
     * 查询最新发布任务，用于兼容旧调用；新前端使用 /all 或 /available。
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
        description = "查询最新发布任务，用于兼容旧调用；不存在时 data 为 null",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<TeacherEventResponse> getCurrentEvent(HttpServletRequest httpRequest) {

        log.info("收到查询最新任务请求");

        // 不再需要传递 userId，直接调用 getGlobalEvent()
        TeacherEventResponse response = teacherEventService.getGlobalEvent();
        return Result.success(response);
    }

    @GetMapping("/all")
    @Operation(summary = "教师任务列表", description = "返回当前教师所有未归档任务，支持并行任务管理")
    public Result<java.util.List<TeacherEventResponse>> allTasks(HttpServletRequest request) {
        return Result.success(teacherEventService.listTeacherTasks((Long) request.getAttribute("userId")));
    }

    @GetMapping("/available")
    @Operation(summary = "学生可用任务列表", description = "返回已发布任务，包含未开始、进行中和已截止状态")
    public Result<java.util.List<TeacherEventResponse>> availableTasks(HttpServletRequest request) {
        return Result.success(teacherEventService.listStudentTasks((Long) request.getAttribute("userId")));
    }

    @PutMapping("/{id}/draft")
    public Result<TeacherEventResponse> updateDraft(@PathVariable Long id, @Valid @RequestBody TeacherEventCreateRequest payload,
            HttpServletRequest request) {
        return Result.success(teacherEventService.updateDraft(id, payload, (Long) request.getAttribute("userId")));
    }

    @PostMapping("/{id}/publish")
    public Result<TeacherEventResponse> publishDraft(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(teacherEventService.publishTask(id, (Long) request.getAttribute("userId")));
    }

    @PostMapping("/{id}/close")
    public Result<TeacherEventResponse> closeTask(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(teacherEventService.closeTask(id, (Long) request.getAttribute("userId")));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "作业详情统计", description = "单次作业的参与/完成/未交、学生汇总和每题正确率")
    public Result<com.zhixuewuli.dto.response.TaskDetailResponse> taskStats(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(teacherEventService.getTaskDetail(id, (Long) request.getAttribute("userId")));
    }

    @GetMapping("/{id}")
    public Result<TeacherEventResponse> getEvent(@PathVariable Long id, HttpServletRequest request){
        return Result.success(teacherEventService.getEvent(id, (Long) request.getAttribute("userId")));
    }
    @GetMapping("/{id}/questions")
    public Result<java.util.List<com.zhixuewuli.dto.response.QuestionResponse>> getQuestions(@PathVariable Long id,HttpServletRequest request){
        return Result.success(teacherEventService.getEventQuestions(id,(Long)request.getAttribute("userId")));
    }
    @PutMapping("/{id}/replace")
    public Result<TeacherEventResponse> replace(@PathVariable Long id,@Valid @RequestBody TeacherEventCreateRequest payload,HttpServletRequest request){
        return Result.success(teacherEventService.replaceEvent(id,payload,(Long)request.getAttribute("userId")));
    }
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable Long id,HttpServletRequest request){
        teacherEventService.archiveEvent(id,(Long)request.getAttribute("userId"));return Result.success();
    }
}