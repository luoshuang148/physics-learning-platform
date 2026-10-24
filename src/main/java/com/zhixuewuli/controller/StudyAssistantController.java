package com.zhixuewuli.controller;

import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.dto.request.AssistantMessageRequest;
import com.zhixuewuli.dto.request.AssistantIntentRequest;
import com.zhixuewuli.service.ai.assistant.AssistantIntentService;
import com.zhixuewuli.entity.AssistantConversation;
import com.zhixuewuli.entity.AssistantMessage;
import com.zhixuewuli.service.ai.assistant.AssistantConversationService;
import com.zhixuewuli.service.ai.assistant.PlatformQaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一学习助手端点（设计 §8）。
 * 身份由服务端决定；工具不接受模型传入的 userId。
 */
@RestController
@RequestMapping("/ai/student/assistant")
@Tag(name = "学习助手", description = "RAG 问答、会话与历史")
@RequiredArgsConstructor
public class StudyAssistantController {

    private final PlatformQaService qaService;
    private final AssistantConversationService conversations;
    private final AssistantIntentService intents;

    @PostMapping("/resolve")
    public Result<AssistantIntentService.Resolution> resolve(@Valid @RequestBody AssistantIntentRequest request) {
        return Result.success(intents.resolve(request));
    }

    @PostMapping("/messages")
    public SseEmitter messages(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody AssistantMessageRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        // 异步执行，避免阻塞请求线程
        new Thread(() -> qaService.stream(user.id(), request, emitter), "assistant-sse").start();
        return emitter;
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Map<String, Object>> rows = conversations.recent(user.id()).stream().map(this::toRow).toList();
        return Result.success(rows);
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<Map<String, Object>>> conversationMessages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Map<String, Object>> rows = conversations.pageMessages(user.id(), id, page, size).stream()
                .map(this::toMessageRow).toList();
        return Result.success(rows);
    }

    private Map<String, Object> toRow(AssistantConversation conversation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", conversation.getId());
        row.put("mode", conversation.getMode());
        row.put("eventId", conversation.getEventId());
        row.put("recordId", conversation.getRecordId());
        row.put("title", conversation.getTitle());
        row.put("updatedAt", conversation.getUpdatedAt());
        return row;
    }

    private Map<String, Object> toMessageRow(AssistantMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("seq", message.getSeq());
        row.put("role", message.getRole());
        row.put("content", message.getContent());
        row.put("mode", message.getMode());
        row.put("status", message.getStatus());
        row.put("sourcesJson", message.getSourcesJson());
        row.put("createdAt", message.getCreatedAt());
        return row;
    }
}
