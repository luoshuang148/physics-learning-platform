package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.common.security.TeacherOnly;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionIndexStatus;
import com.zhixuewuli.entity.QuestionIndexJob;
import com.zhixuewuli.repository.QuestionIndexJobRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.platformsearch.QuestionSourceIndexService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师题库索引状态与重试。复用题目编辑权限，仅教师可调用。
 */
@RestController
@RequestMapping("/questions/teacher")
@Tag(name = "题库索引管理", description = "索引状态查看与重试")
@RequiredArgsConstructor
public class QuestionIndexController {

    private final QuestionRepository questions;
    private final QuestionIndexJobRepository jobs;
    private final QuestionSourceIndexService indexService;

    /** GET /questions/teacher/{id}/index-status */
    @GetMapping("/{id}/index-status")
    @TeacherOnly
    public Result<QuestionIndexStatus> indexStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        var question = questions.findById(id)
                .orElseThrow(() -> new com.zhixuewuli.common.exception.question.QuestionNotFoundException(id));
        List<QuestionIndexJob> list = jobs.findByQuestionIdOrderByUpdatedAtDesc(id);
        if (list.isEmpty()) {
            return Result.success(new QuestionIndexStatus(id, question.getAiContentRevision(),
                    question.isAiPracticeEnabled() ? "PENDING" : "DISABLED", null));
        }
        // 取最新与当前 revision 匹配的任务；否则报 OBSOLETE
        var current = list.stream()
                .filter(j -> j.getRevision() == question.getAiContentRevision()
                        && QuestionSourceIndexService.JOB_INDEX.equals(j.getJobType()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            return Result.success(new QuestionIndexStatus(id, question.getAiContentRevision(),
                    question.isAiPracticeEnabled() ? "PENDING" : "DISABLED", null));
        }
        return Result.success(new QuestionIndexStatus(id, current.getRevision(),
                current.getStatus().name(), current.getFailureCode()));
    }

    /** POST /questions/teacher/{id}/reindex — 幂等排队 */
    @PostMapping("/{id}/reindex")
    @TeacherOnly
    public Result<QuestionIndexStatus> reindex(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        var question = questions.findById(id)
                .orElseThrow(() -> new com.zhixuewuli.common.exception.question.QuestionNotFoundException(id));
        var job = indexService.enqueueCurrent(id, question.getAiContentRevision());
        return Result.success("已重新排队索引", new QuestionIndexStatus(id, job.getRevision(),
                job.getStatus().name(), job.getFailureCode()));
    }
}
