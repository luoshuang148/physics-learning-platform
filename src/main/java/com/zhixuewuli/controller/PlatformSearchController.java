package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.Evidence;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.QuestionHit;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.RetrievalMode;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchRequest;
import com.zhixuewuli.dto.platformsearch.PlatformRetrievalTypes.SearchResult;
import com.zhixuewuli.dto.request.PlatformSearchRequest;
import com.zhixuewuli.service.platformsearch.PlatformQuestionRetriever;
import com.zhixuewuli.service.platformsearch.PlatformSourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台题库检索与来源查看（无需生成模型）。
 * 仅做授权检索验收；用户身份由服务端决定，不接受模型指定 userId。
 */
@RestController
@RequestMapping("/ai/student")
@Tag(name = "平台题库检索", description = "自然语言搜题与来源查看")
@RequiredArgsConstructor
public class PlatformSearchController {

    private final PlatformQuestionRetriever retriever;
    private final PlatformSourceService sources;

    @PostMapping("/platform-search")
    public Result<Map<String, Object>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody PlatformSearchRequest request) {
        SearchRequest searchRequest = new SearchRequest(
                request.getQuery(),
                request.getEventId(),
                request.getKnowledgePointCodes(),
                request.getDifficulty(),
                request.isExcludeAttempted(),
                request.getLimit(),
                RetrievalMode.HYBRID);
        SearchResult result = retriever.retrieve(user.id(), searchRequest, () -> false);

        // 回源补全题目内容与来源文本（再次鉴权）
        List<Map<String, Object>> questions = result.questions().stream().map(hit -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", hit.questionId());
            row.put("revision", hit.revision());
            row.put("content", hit.content());
            row.put("difficulty", hit.difficulty());
            row.put("knowledgePointCodes", hit.knowledgePointCodes());
            row.put("sourceIds", hit.sourceIds());
            return row;
        }).toList();
        List<Map<String, Object>> sourceRows = result.sources().stream().map(this::toSourceRow).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status().name());
        body.put("actualMode", result.actualMode());
        body.put("durationMs", result.durationMs());
        body.put("questions", questions);
        body.put("sources", sourceRows);
        return Result.success(body);
    }

    @GetMapping("/platform-sources/{sourceId}")
    public Result<Map<String, Object>> source(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String sourceId) {
        Evidence evidence = sources.requireReadable(user.id(), sourceId);
        return Result.success(toSourceRow(evidence));
    }

    private Map<String, Object> toSourceRow(Evidence evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceId", evidence.sourceId());
        row.put("questionId", evidence.questionId());
        row.put("revision", evidence.revision());
        row.put("part", evidence.part());
        row.put("ordinal", evidence.ordinal());
        row.put("text", evidence.text());
        return row;
    }
}
