package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.dto.response.QuestionErrorDetailsResponse;
import com.zhixuewuli.dto.response.QuestionErrorSummaryResponse;
import com.zhixuewuli.service.ai.AliyunAIReportService;
import com.zhixuewuli.service.questionErrorAnalysis.QuestionErrorAnalysisService;
import com.zhixuewuli.repository.QuestionRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 题目错误分析控制器
 * 
 * 提供教师端错题分析功能的RESTful API接口。
 * 支持两个维度的错题信息查询：
 * 1. 题目维度：统计每道题的错误情况
 * 2. 学生维度：展示指定题目答错的学生详细信息
 * 
 * <p>
 * 核心接口：
 * </p>
 * <ul>
 * <li>GET /api/question-error-analysis/statistics - 获取题目错误统计</li>
 * <li>GET /api/question-error-analysis/{questionId}/details -
 * 获取题目学生错误详情</li>
 * <li>POST /api/question-error-analysis/stream - 教师题目分析流式生成（纯 AI 分析）</li>
 * </ul>
 * 
 * <p>
 * 安全特性：
 * </p>
 * <ul>
 * <li>集成JWT认证拦截器</li>
 * <li>教师角色权限验证</li>
 * <li>数据访问权限控制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionErrorAnalysisService 错题分析服务
 */
@RestController
@RequestMapping("/question-error-analysis")
@Tag(name = "题目错误分析", description = "教师端错题分析相关接口")
@RequiredArgsConstructor
@Slf4j
public class QuestionErrorAnalysisController {

        private final QuestionErrorAnalysisService questionErrorAnalysisService;
        private final AliyunAIReportService aliyunAIReportService;
        private final UserPermissionUtil userPermissionUtil;
        private final QuestionRepository questionRepository;

        /**
         * 获取所有题目的错误统计信息
         * 
         * 返回按错误人次降序排列的题目错误统计列表，
         * 仅包含至少有一个错误记录的题目。
         * 
         * @param teacherId 教师ID（从JWT Token中提取）
         * @return Result<List<QuestionErrorSummaryResponse>> 题目错误统计结果
         * 
         * @api.example 请求示例:
         *              GET /api/question-error-analysis/statistics
         *              Headers: Authorization: Bearer <jwt-token>
         * 
         * @api.response 成功响应示例:
         *               {
         *               "code": 200,
         *               "message": "success",
         *               "data": [
         *               {
         *               "questionId": 1,
         *               "questionContent": "质量为2kg的物体...",
         *               "questionOptions": ["A选项", "B选项", "C选项", "D选项"],
         *               "correctAnswer": "B",
         *               "questionAnalysis": "根据牛顿第二定律...",
         *               "difficulty": "中等",
         *               "imageBase64": "data:image/png;base64,...",
         *               "errorCount": 15,
         *               "errorRate": 75.0
         *               }
         *               ]
         *               }
         */
        @GetMapping("/statistics")
        @Operation(
            summary = "获取题目错误统计",
            description = "返回所有有错误记录的题目统计信息，按错误人次降序排列",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
            }
        )
        public Result<List<QuestionErrorSummaryResponse>> getQuestionErrorStatistics(
                        @io.swagger.v3.oas.annotations.Parameter(description = "教师 ID（从请求属性中自动提取）", required = true) @RequestAttribute("userId") Long teacherId) {

                log.info("收到题目错误统计查询请求 - TeacherId: {}", teacherId);

                List<QuestionErrorSummaryResponse> result = questionErrorAnalysisService
                                .getAllQuestionErrorStatistics(teacherId);

                log.info("题目错误统计查询完成 - 返回{}条记录", result.size());
                return Result.success(result);
        }

        @GetMapping("/{questionId}/details")
        @Operation(
            summary = "获取题目错误详情",
            description = "返回指定题目的学生错误列表（单次查询优化）",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
            }
        )
        public Result<QuestionErrorDetailsResponse> getQuestionErrorDetails(
                        @io.swagger.v3.oas.annotations.Parameter(description = "题目 ID", required = true) @PathVariable Long questionId,
                        @io.swagger.v3.oas.annotations.Parameter(description = "教师 ID（从请求属性中自动提取）", required = true) @RequestAttribute("userId") Long teacherId) {

                log.info("获取题目错误详情 - TeacherId: {}, QuestionId: {}", teacherId, questionId);

                QuestionErrorDetailsResponse result = questionErrorAnalysisService
                                .getQuestionErrorDetails(questionId, teacherId);

                log.info("题目错误详情查询完成 - 返回{}条学生记录", result.getStudentList().size());
                return Result.success(result);
        }

        @PostMapping("/stream")
        @Operation(
            summary = "教师题目分析流式生成",
            description = "纯 AI 分析，不包含初始化数据",
            responses = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "请求成功，返回 SSE 流"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
            }
        )
        public SseEmitter streamTeacherQuestionAnalysis(
                        @io.swagger.v3.oas.annotations.Parameter(description = "AI 问答请求参数（需包含题目 ID）", required = true) @Valid @RequestBody AIAskRequest request,
                        HttpServletRequest httpRequest) {
                // 1. 参数验证：由@Valid 注解自动完成，失败时 Spring 抛出 MethodArgumentNotValidException
                // 全局异常处理器会捕获并返回 400 错误，不建立 SSE 连接

                // 2. 从请求属性获取教师 ID 和会话 ID（由拦截器设置）
                Long teacherId = (Long) httpRequest.getAttribute("userId");
                String sessionId = (String) httpRequest.getAttribute("sessionId");

                // 3. 控制器层权限验证前置（快速失败）
                // 验证失败直接抛出异常，不创建 emitter，避免无效连接开销
                userPermissionUtil.checkTeacherPermission(teacherId);

                // ✅ 4. 新增：题目存在性验证（教师题目分析专属）
                questionRepository.findById(request.getQuestionId())
                        .orElseThrow(() -> new com.zhixuewuli.common.exception.question.QuestionNotFoundException(request.getQuestionId()));

                // 5. 创建 emitter（到这里说明所有验证通过）
                SseEmitter emitter = aliyunAIReportService.createConfiguredEmitter();

                // 6. 调用服务（已移除初始化数据发送）
                questionErrorAnalysisService.streamTeacherQuestionAnalysis(request, emitter, teacherId, sessionId);

                return emitter;
        }
}