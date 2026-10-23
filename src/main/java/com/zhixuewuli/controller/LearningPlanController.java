package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.dto.agent.LearningPlanView;
import com.zhixuewuli.dto.request.LearningPlanVersionRequest;
import com.zhixuewuli.dto.request.SaveLearningPlanRequest;
import com.zhixuewuli.service.ai.agent.LearningPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/learning-plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class LearningPlanController {
    private final LearningPlanService service;
    private final com.zhixuewuli.service.ai.agent.PlanPracticeService practice;

    @PostMapping
    public Result<LearningPlanView> save(@Valid @RequestBody SaveLearningPlanRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.created("复习计划已保存为草稿", service.saveDraft(user.id(), request.runId(), request.requestId()));
    }

    @GetMapping("/{id}")
    public Result<LearningPlanView> get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.get(user.id(), id));
    }

    @GetMapping
    public Result<java.util.List<LearningPlanView>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.list(user.id()));
    }

    @PostMapping("/{id}/activate")
    public Result<LearningPlanView> activate(@PathVariable Long id, @Valid @RequestBody LearningPlanVersionRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.activate(user.id(), id, request.expectedVersion()));
    }

    @PostMapping("/{id}/cancel")
    public Result<LearningPlanView> cancel(@PathVariable Long id, @Valid @RequestBody LearningPlanVersionRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(service.cancel(user.id(), id, request.expectedVersion()));
    }

    /** 获取本人有效计划条目的题干与选项（不含正确答案）。 */
    @GetMapping("/{id}/items/{itemId}/question")
    public Result<com.zhixuewuli.dto.response.QuestionResponse> practiceQuestion(
            @PathVariable Long id, @PathVariable Long itemId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(practice.question(user.id(), id, itemId));
    }

    /** 提交计划条目补练；服务端确定题目与归属，返回计分与最新进度。 */
    @PostMapping("/{id}/items/{itemId}/answers")
    public Result<java.util.Map<String, Object>> practiceAnswer(
            @PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody com.zhixuewuli.dto.request.PlanPracticeAnswerRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(practice.submit(user.id(), id, itemId, request));
    }
}
