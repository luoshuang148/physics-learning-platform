package com.zhixuewuli.controller;

import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.entity.ConversationHistory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.StudentReportResponse;
import com.zhixuewuli.service.ai.ZhiPuAIAssistantService;
import com.zhixuewuli.service.ai.AliyunAIReportService;
import com.zhixuewuli.repository.QuestionRepository;

import java.util.List;

/**
 * AI助手控制器
 * 
 * 统一处理所有AI相关功能请求的入口控制器，
 * 整合智谱AI和阿里AI两大服务模块。
 * 
 * <p>
 * 核心职责：
 * </p>
 * <ul>
 * <li>接收并验证AI功能相关请求</li>
 * <li>路由请求到相应的AI服务模块</li>
 * <li>处理流式响应和异常情况</li>
 * <li>维护会话状态和上下文管理</li>
 * </ul>
 * 
 * <p>
 * 功能模块：
 * </p>
 * <ul>
 * <li>智谱AI模块：实时问答、错题分析</li>
 * <li>阿里AI模块：学生报告、教师分析</li>
 * <li>会话管理：上下文维护、历史查询</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see ZhiPuAIAssistantService 智谱AI服务
 * @see AliyunAIReportService 阿里AI服务
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI助手", description = "AI智能助手相关接口")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AIController {

    private final ZhiPuAIAssistantService zhiPuAIAssistantService;
    private final AliyunAIReportService aliyunAIReportService;
    private final QuestionRepository questionRepository;

    // === 智谱AI功能接口 ===

    // === 实时问答接口 ===
    @PostMapping("/student/realtimeQA")
    @Operation(
        summary = "学生实时问答",
        description = "学生提交问题并获取流式 AI 回答",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功，返回 SSE 流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非学生身份）")
        }
    )
    public SseEmitter streamQuestionAnswer(
            @io.swagger.v3.oas.annotations.Parameter(description = "AI 问答请求参数", required = true) @Valid @RequestBody AIAskRequest request,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {

        Long userId = user.id();
        String sessionId = (String) httpRequest.getAttribute("sessionId");

        // 前置校验：失败直接抛出异常，不创建 SSE 连接
        zhiPuAIAssistantService.validateAIAskRequest(request, userId, sessionId, false);

        // 校验通过后创建 SSE 连接
        SseEmitter emitter = zhiPuAIAssistantService.createConfiguredEmitter();

        // 调用服务方法
        zhiPuAIAssistantService.streamQAResponse(
                request,
                emitter,
                userId,
                sessionId);

        return emitter;
    }

    // === 错题分析接口 ===
    @PostMapping("/student/wrong-question-analysis")
    @Operation(
        summary = "学生错题分析",
        description = "学生对指定题目进行流式 AI 错题分析",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功，返回 SSE 流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非学生身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
        }
    )
    public SseEmitter streamWrongQuestionAnalysis(
            @io.swagger.v3.oas.annotations.Parameter(description = "AI 问答请求参数（需包含题目 ID）", required = true) @Valid @RequestBody AIAskRequest request,
            @AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest) {

        Long userId = user.id();
        String sessionId = (String) httpRequest.getAttribute("sessionId");

        // 前置校验：失败直接抛出异常，不创建 SSE 连接
        zhiPuAIAssistantService.validateAIAskRequest(request, userId, sessionId, true);

        // 校验通过后创建 SSE 连接
        SseEmitter emitter = zhiPuAIAssistantService.createConfiguredEmitter();

        // 调用服务方法
        zhiPuAIAssistantService.streamWrongQuestionAnalysis(
                request,
                emitter,
                userId,
                sessionId);

        return emitter;
    }

    // === 阿里AI功能接口 ===

    @GetMapping("/student/report")
    @Operation(
        summary = "学生个人报告流式生成",
        description = "生成当前学生的个人学习报告（流式响应）",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功，返回 SSE 流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非学生身份）")
        }
    )
    public SseEmitter streamStudentReport(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest httpRequest, @RequestParam(required = false) Long eventId) {
        Long userId = user.id();
        String sessionId = (String) httpRequest.getAttribute("sessionId");

        // 前置校验
        aliyunAIReportService.validateStudentReportRequest(sessionId, userId);

        // 校验通过后创建 SSE 连接
        SseEmitter emitter = aliyunAIReportService.createConfiguredEmitter();

        // 调用服务方法
        aliyunAIReportService.streamStudentReport(sessionId, emitter, userId, eventId);

        return emitter;
    }

    @GetMapping("/teacher/report")
    @Operation(
        summary = "教师综合分析流式生成",
        description = "生成教师的综合教学分析报告（流式响应）",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功，返回 SSE 流"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    @TeacherOnly
    public SseEmitter streamTeacherAnalysis(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) Long eventId) {
        Long userId = user.id();

        // 前置校验
        aliyunAIReportService.validateTeacherAnalysisRequest(userId);

        // 校验通过后创建 SSE 连接
        SseEmitter emitter = aliyunAIReportService.createConfiguredEmitter();

        // 调用服务方法
        aliyunAIReportService.streamTeacherAnalysis(emitter, userId, eventId);

        return emitter;
    }

    // === 会话管理接口 ===

    @GetMapping("/session/history")
    @Operation(
        summary = "获取会话历史记录",
        description = "获取当前会话的 AI 问答历史记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<List<ConversationHistory>> getSessionHistory(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request,
            @RequestParam(required = false) String recordId) {
        Long userId = user.id();
        String sessionId = (String) request.getAttribute("sessionId");
        List<ConversationHistory> history = zhiPuAIAssistantService.getSessionHistory(sessionId, userId, recordId);
        return Result.success(history);
    }

    @DeleteMapping("/session")
    @Operation(
        summary = "清除会话",
        description = "清空当前会话的所有历史记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "清除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<Void> clearSession(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest request) {
        Long userId = user.id();
        String sessionId = (String) request.getAttribute("sessionId");
        zhiPuAIAssistantService.clearSession(sessionId, userId);
        return Result.success();
    }

    /**
     * 获取学生最新报告
     * 
     * 教师获取指定学生的最新学习报告。
     * 学生ID通过请求参数传递，教师ID通过拦截器从HTTP请求中获取。
     * 
     * @param studentId 学生用户ID（请求参数）
     * @param request   HTTP请求对象（包含教师ID）
     * @return Result<StudentReportResponse> 学生最新报告响应
     */
    @GetMapping("/teacher/report/{studentId}")
    @Operation(
        summary = "获取学生最新报告",
        description = "教师获取指定学生的最新学习报告",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "学生不存在或无报告数据")
        }
    )
    @TeacherOnly
    public Result<StudentReportResponse> getStudentLatestReport(
            @io.swagger.v3.oas.annotations.Parameter(description = "学生用户 ID", required = true) @PathVariable Long studentId,
            @RequestParam(required = false) Long eventId) {
        StudentReportResponse response = aliyunAIReportService.getStudentLatestReport(studentId, eventId);
        return Result.success(response);
    }

}
