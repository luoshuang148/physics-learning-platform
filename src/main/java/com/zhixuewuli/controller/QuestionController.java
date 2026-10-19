package com.zhixuewuli.controller;

import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.QuestionCreateRequest;
import com.zhixuewuli.dto.request.QuestionUpdateRequest;
import com.zhixuewuli.dto.response.QuestionResponse;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.service.question.QuestionService;
import com.zhixuewuli.common.util.OptionMappingUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目管理控制器
 * 
 * 提供题目增删改查的RESTful API接口
 * 实现教师角色的题目管理功能和学生角色的题目查询功能
 * 
 * <p>
 * 权限控制：
 * </p>
 * <ul>
 * <li>POST/PUT/DELETE操作：仅限TEACHER角色</li>
 * <li>GET /api/questions 操作：仅限TEACHER角色</li>
 * <li>GET /api/questions/{id} 操作：TEACHER和STUDENT角色均可访问</li>
 * <li>所有操作都需要JWT认证</li>
 * </ul>
 * 
 * <p>
 * API端点：
 * </p>
 * <ul>
 * <li>POST /api/questions - 创建题目</li>
 * <li>DELETE /api/questions/{id} - 根据ID删除题目</li>
 * <li>PUT /api/questions - 更新题目</li>
 * <li>GET /api/questions/{id} - 根据ID获取题目（无需认证）</li>
 * <li>GET /api/questions - 获取所有题目</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionService 题目服务接口
 * @see QuestionResponse 题目响应DTO
 */
@RestController
@RequestMapping("/questions")
@Tag(name = "题目管理", description = "题目增删改查相关接口")
@Slf4j
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final OptionMappingUtil optionMappingUtil;
    private final com.zhixuewuli.service.question.AnswerAccessPolicy answerAccess;
    private final com.zhixuewuli.service.question.QuestionCatalogService catalog;

    @GetMapping("/teacher/page")
    @TeacherOnly
    public Result<com.zhixuewuli.common.result.PageResult<com.zhixuewuli.dto.response.QuestionCatalogRow>> page(
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String search, @RequestParam(required=false) Integer difficulty) {
        return Result.success(catalog.page(page,size,search,difficulty));
    }

    @PostMapping("/teacher/create")
    @Operation(
        summary = "创建题目",
        description = "教师身份创建新的练习题目",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    @TeacherOnly
    public Result<QuestionResponse> createQuestion(
            @io.swagger.v3.oas.annotations.Parameter(description = "题目创建请求参数", required = true) @Valid @RequestBody QuestionCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String username = user.username();
        log.info("用户 {} 开始创建题目", username);

        Question question = questionService.createQuestion(request);
        QuestionResponse response = convertToResponse(question);

        log.info("用户 {} 题目创建成功，ID: {}", username, question.getId());
        return Result.created("题目创建成功", response);
    }

    @DeleteMapping("/teacher/delete/{id}")
    @Operation(
        summary = "删除题目",
        description = "根据ID删除指定题目",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
        }
    )
    @TeacherOnly
    public Result<Void> deleteQuestion(
            @io.swagger.v3.oas.annotations.Parameter(description = "题目 ID", required = true) @PathVariable @Positive(message = "题目ID必须为正数") Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String username = user.username();
        log.info("用户 {} 开始删除题目，ID: {}", username, id);

        questionService.deleteQuestionById(id);

        log.info("用户 {} 题目删除成功，ID: {}", username, id);
        return Result.success();
    }

    @PutMapping("/teacher/update")
    @Operation(
        summary = "更新题目",
        description = "全量覆盖更新题目信息",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
        }
    )
    @TeacherOnly
    public Result<QuestionResponse> updateQuestion(
            @io.swagger.v3.oas.annotations.Parameter(description = "题目更新请求参数", required = true) @Valid @RequestBody QuestionUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        String username = user.username();
        log.info("用户 {} 开始更新题目，ID: {}", username, request.getId());

        Question question = questionService.updateQuestion(request);
        QuestionResponse response = convertToResponse(question);

        log.info("用户 {} 题目更新成功，ID: {}", username, question.getId());
        return Result.success("题目更新成功", response);
    }

    @GetMapping("/teacher/get/{id}")
    @Operation(
        summary = "获取题目详情",
        description = "根据ID获取题目信息；学生只能看到已开放答案的受保护字段",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目不存在")
        }
    )
    public Result<QuestionResponse> getQuestionById(
            @io.swagger.v3.oas.annotations.Parameter(description = "题目 ID", required = true) @PathVariable @Positive(message = "题目ID必须为正数") Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        log.info("查询题目详情，ID: {}", id);

        Question question = questionService.getQuestionById(id);
        QuestionResponse response = answerAccess.forBank(user.id(), convertToResponse(question));

        return Result.success(response);
    }

    @GetMapping("/teacher/get/all")
    @Operation(
        summary = "获取所有题目",
        description = "批量获取数据库中所有题目",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<List<QuestionResponse>> getAllQuestions(@AuthenticationPrincipal AuthenticatedUser user) {

        Long userId = user.id();
        String username = user.username();
        log.info("用户 {} ，ID{}查询所有题目", username, userId);

        List<Question> questions = questionService.getAllQuestions(userId);
        List<QuestionResponse> responses = questions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return Result.success(answerAccess.forBank(userId, responses));
    }

    /**
     * 转换题目实体为响应DTO
     * 
     * @param question 题目实体对象
     * @return QuestionResponse 题目响应DTO
     */
    private QuestionResponse convertToResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .content(question.getContent())
                .options(optionMappingUtil.jsonToOptions(question.getOptions()))
                .correctOption(question.getCorrectOption())
                .explanation(question.getExplanation())
                .difficulty(question.getDifficulty())
                .imageBase64(question.getImageBase64())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }
}
