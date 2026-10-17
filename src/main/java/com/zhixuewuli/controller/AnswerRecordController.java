package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.dto.response.AnswerRecordBatchResponse;
import com.zhixuewuli.dto.response.AnswerStatsResponse;

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
 * 答题记录控制器
 * 
 * 提供答题记录管理的RESTful API接口，包括学生功能和教师管理功能。
 * 实现完整的权限控制和数据安全保护。
 * 
 * <p>
 * API分类：
 * </p>
 * <ul>
 * <li><strong>学生功能</strong>: 提交答题、查询个人记录</li>
 * <li><strong>权限查询</strong>: 按权限查询用户记录</li>
 * <li><strong>教师管理</strong>: 数据管理、统计分析</li>
 * </ul>
 * 
 * <p>
 * 安全特性：
 * </p>
 * <ul>
 * <li>所有接口都需要用户认证</li>
 * <li>敏感操作需要特定角色权限</li>
 * <li>数据访问遵循最小权限原则</li>
 * <li>操作日志完整记录</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AnswerRecordService 答题记录服务
 */
@Slf4j
@RestController
@RequestMapping("/answer-records")
@RequiredArgsConstructor
@Tag(name = "答题记录", description = "答题记录管理相关接口")
public class AnswerRecordController {

    private final AnswerRecordService answerRecordService;

    /**
     * 提交答题记录（学生功能）
     * 
     * 学生用户提交答题结果，系统自动判断正误并保存记录。
     * 返回答题时间和结果信息。
     */
    @PostMapping("/student/submit")
    @Operation(
        summary = "提交答题记录",
        description = "学生用户提交答题结果，系统自动判断正误并保存记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "提交成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非学生身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "题目或会话不存在")
        }
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AnswerRecordResponse> submitAnswer(
            @io.swagger.v3.oas.annotations.Parameter(description = "答题记录提交请求参数", required = true) @RequestBody @Valid AnswerRecordSubmitRequest request,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        String sessionId = (String) httpRequest.getAttribute("sessionId");

        AnswerRecordResponse response = answerRecordService.submitAnswerRecord(
                request, currentUserId, sessionId);

        return Result.created("答题记录提交成功", response);
    }

    /**
     * 获取当前用户所有答题记录（学生常用）
     * 
     * 查询当前登录用户的所有答题历史记录。
     */
    @GetMapping("/student/my")
    @Operation(
        summary = "获取我的答题记录",
        description = "查询当前登录用户的所有答题历史记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<AnswerRecordBatchResponse> getMyAnswerRecords(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getCurrentUserAnswerRecords(currentUserId);
        return Result.success(response);
    }

    /**
     * 获取当前用户错题记录（学生功能）
     * 
     * 查询当前登录用户的所有错误答题记录。
     */
    @GetMapping("/student/my/wrong")
    @Operation(
        summary = "获取我的错题记录",
        description = "查询当前登录用户的所有错误答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
        }
    )
    public Result<AnswerRecordBatchResponse> getMyWrongAnswers(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getCurrentUserWrongAnswers(currentUserId);
        return Result.success(response);
    }

    /**
     * 获取指定用户答题记录（权限控制）
     * 
     * 根据权限查询指定用户的答题记录。
     * 学生只能查询自己的记录，教师可以查询任意用户记录。
     */
    @GetMapping("/user/{userId}")
    @Operation(
        summary = "获取用户答题记录",
        description = "根据权限查询指定用户的答题记录，学生只能查询自己的记录，教师可以查询任意用户记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（无权查看他人记录）")
        }
    )
    public Result<AnswerRecordBatchResponse> getUserAnswerRecords(
            @io.swagger.v3.oas.annotations.Parameter(description = "用户 ID", required = true) @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getUserAnswerRecords(userId, currentUserId);
        return Result.success(response);
    }

    /**
     * 获取指定用户错题记录（权限控制）
     * 
     * 根据权限查询指定用户的错题记录。
     * 学生只能查询自己的错题，教师可以查询任意用户错题。
     */
    @GetMapping("/user/wrong/{userId}")
    @Operation(
        summary = "获取用户错题记录",
        description = "根据权限查询指定用户的错题记录，学生只能查询自己的错题，教师可以查询任意用户错题",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（无权查看他人错题）")
        }
    )
    public Result<AnswerRecordBatchResponse> getUserWrongAnswers(
            @io.swagger.v3.oas.annotations.Parameter(description = "用户 ID", required = true) @PathVariable Long userId,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getWrongAnswersByUser(userId, currentUserId);
        return Result.success(response);
    }

    /**
     * 删除单条记录（教师专属）
     * 
     * 教师删除指定的答题记录。
     * 需要教师权限验证。
     */
    @DeleteMapping("/teacher/{recordId}")
    @Operation(
        summary = "删除答题记录",
        description = "教师删除指定的答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "记录不存在")
        }
    )
    public Result<Void> deleteAnswerRecord(
            @io.swagger.v3.oas.annotations.Parameter(description = "答题记录 ID", required = true) @PathVariable String recordId,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        answerRecordService.deleteAnswerRecord(recordId, currentUserId);
        return Result.success();
    }

    /**
     * 批量删除记录（教师专属）
     * 
     * 教师批量删除指定时间范围内的答题记录。
     * 需要教师权限验证和事务保护。
     */
    @DeleteMapping("/teacher/batch")
    @Operation(
        summary = "批量删除答题记录",
        description = "教师批量删除指定时间范围内的答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<Void> batchDelete(
            @io.swagger.v3.oas.annotations.Parameter(description = "批量删除请求参数（包含时间范围）", required = true) @RequestBody @Valid RecordBatchDeleteRequest request,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        answerRecordService.batchDeleteByTimeRange(request, currentUserId);
        return Result.success();
    }

    /**
     * 统计总记录数（教师功能）
     * 
     * 教师查询整个系统的答题记录总数。
     * 需要教师权限验证。
     */
    @GetMapping("/teacher/count")
    @Operation(
        summary = "统计系统总答题记录数",
        description = "教师查询整个系统的答题记录总数",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<Long> countTotalAnswers(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        Long totalCount = answerRecordService.countTotalAnswers(currentUserId);
        return Result.success(totalCount);
    }

    /**
     * 获取所有用户答题记录（教师功能）
     * 
     * 教师查询系统内所有用户的答题记录。
     * 需要教师权限验证。
     */
    @org.springframework.beans.factory.annotation.Autowired private com.zhixuewuli.repository.AnswerRecordRepository taskAnswerRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.zhixuewuli.common.util.QuestionResponseConverter taskAnswerConverter;
    @GetMapping("/student/task/{eventId}")
    public Result<AnswerRecordBatchResponse> taskAnswers(@PathVariable Long eventId,HttpServletRequest request){
        var rows=taskAnswerRepository.findByUserIdAndEventIdOrderByAttemptOrderDesc((Long)request.getAttribute("userId"),eventId);
        var data=rows.stream().map(row->taskAnswerConverter.convertToFullResponseDTO(row,row.getQuestion())).toList();
        return Result.success(AnswerRecordBatchResponse.builder().records(data).totalCount((long)data.size()).build());
    }
    @GetMapping("/student/practice-review")
    public Result<AnswerRecordBatchResponse> practiceReview(HttpServletRequest request){
        var data=taskAnswerRepository.findPracticeReview((Long)request.getAttribute("userId")).stream()
            .map(row->taskAnswerConverter.convertToFullResponseDTO(row,row.getQuestion())).toList();
        return Result.success(AnswerRecordBatchResponse.builder().records(data).totalCount((long)data.size()).build());
    }

    @GetMapping("/teacher/page")
    @Operation(summary = "教师分页查询答题记录")
    public Result<com.zhixuewuli.common.result.PageResult<AnswerRecordResponse>> getTeacherPage(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean wrongOnly,
            @RequestParam(defaultValue = "") String studentName, HttpServletRequest request) {
        return Result.success(answerRecordService.getTeacherAnswerPage(
                (Long) request.getAttribute("userId"), page, size, wrongOnly, studentName));
    }

    @GetMapping("/teacher/all")
    @Operation(
        summary = "获取所有用户答题记录",
        description = "教师查询系统内所有用户的答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<AnswerRecordBatchResponse> getAllUsersAnswerRecords(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getAllUsersAnswerRecords(currentUserId);
        return Result.success(response);
    }

    /**
     * 获取所有用户错题记录（教师功能）
     * 
     * 教师查询系统内所有用户的错题记录。
     * 需要教师权限验证。
     */
    @GetMapping("/teacher/wrong/all")
    @Operation(
        summary = "获取所有用户错题记录",
        description = "教师查询系统内所有用户的错题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<AnswerRecordBatchResponse> getAllUsersWrongAnswers(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getAllUsersWrongAnswers(currentUserId);
        return Result.success(response);
    }

    /**
     * 获取指定会话的答题记录（学生/教师功能）
     * 
     * 根据会话ID获取该会话内的所有答题记录。
     * 学生只能查询自己的会话，教师可以查询任意会话。
     */
    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "获取会话答题记录",
        description = "根据会话 ID 获取该会话内的所有答题记录，学生只能查询自己的会话，教师可以查询任意会话",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（无权查看该会话）")
        }
    )
    public Result<AnswerRecordBatchResponse> getSessionAnswerRecords(
            @io.swagger.v3.oas.annotations.Parameter(description = "会话 ID", required = true) @PathVariable String sessionId,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getSessionAnswerRecords(sessionId, currentUserId);
        return Result.success("会话答题记录查询成功", response);
    }

    /**
     * 获取学生最近一次会话的答题记录（教师功能）
     * 
     * 教师查询指定学生的最近一次学习会话中的所有答题记录。
     * 自动查找学生最近的会话并返回相关记录。
     */
    @GetMapping("/teacher/{studentId}/latest-session")
    @Operation(
        summary = "获取学生最近会话记录",
        description = "教师查询指定学生的最近一次学习会话中的所有答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "学生不存在或无会话记录")
        }
    )
    public Result<AnswerRecordBatchResponse> getStudentLatestSessionRecords(
            @io.swagger.v3.oas.annotations.Parameter(description = "学生用户 ID", required = true) @PathVariable Long studentId,
            HttpServletRequest httpRequest) {

        Long teacherId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getStudentLatestSessionRecords(studentId, teacherId);
        return Result.success("学生最近会话记录查询成功", response);
    }

    /**
     * 根据学生姓名获取最近一次会话的答题记录（教师功能）
     * 
     * 教师通过学生真实姓名查询其最近一次学习会话中的所有答题记录。
     * 系统会先根据姓名精确查找学生，然后获取其最近会话记录。
     */
    @GetMapping("/teacher/realname/{studentName}/latest-session")
    @Operation(
        summary = "根据姓名获取学生最近会话记录",
        description = "教师通过学生真实姓名查询其最近一次学习会话中的所有答题记录",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "学生不存在或无会话记录")
        }
    )
    public Result<AnswerRecordBatchResponse> getStudentLatestSessionRecordsByName(
            @io.swagger.v3.oas.annotations.Parameter(description = "学生真实姓名", required = true) @PathVariable String studentName,
            HttpServletRequest httpRequest) {

        Long teacherId = (Long) httpRequest.getAttribute("userId");
        AnswerRecordBatchResponse response = answerRecordService.getStudentLatestSessionRecordsByName(studentName,
                teacherId);
        return Result.success("学生最近会话记录查询成功", response);
    }

    /**
     * 获取答题统计数据（教师功能）
     * 
     * 教师查询答题统计数据，包括有答题记录的学生人数和总学生人数。
     * 用于教师端展示答题覆盖率等统计信息。
     * 需要教师权限验证。
     */
    @GetMapping("/teacher/stats")
    @Operation(
        summary = "获取答题统计数据",
        description = "获取有答题记录的学生人数和总学生人数",
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足（非教师身份）")
        }
    )
    public Result<AnswerStatsResponse> getAnswerStats(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("userId");
        AnswerStatsResponse response = answerRecordService.getAnswerStats(currentUserId);
        return Result.success(response);
    }
}