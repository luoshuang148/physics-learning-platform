package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.entity.AssistantConversation;
import com.zhixuewuli.entity.AssistantMessage;
import com.zhixuewuli.repository.AssistantConversationRepository;
import com.zhixuewuli.repository.AssistantMessageRepository;
import com.zhixuewuli.repository.AssistantRunRepository;
import com.zhixuewuli.entity.AssistantRun;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 助手会话与历史持久化。
 * 会话按 userId+mode+eventId+recordId 绑定；上下文变化新建会话。
 * 每轮重新校验历史来源，已失去权限的内容不得送入新模型请求。
 */
@Service
@RequiredArgsConstructor
public class AssistantConversationService {

    public static final int MAX_CONVERSATIONS = 30;
    public static final int PAGE_SIZE = 50;

    private final AssistantConversationRepository conversations;
    private final AssistantMessageRepository messages;
    private final AssistantRunRepository runs;

    @Transactional
    public AssistantConversation resolveConversation(Long userId, String mode, Long eventId, Long recordId) {
        if (userId == null || mode == null || mode.isBlank()) {
            throw new IllegalArgumentException("USER_AND_MODE_REQUIRED");
        }
        Optional<AssistantConversation> existing =
                conversations.findFirstByUserIdAndModeAndEventIdAndRecordIdOrderByUpdatedAtDesc(
                        userId, mode, eventId, recordId);
        return existing.orElseGet(() -> {
            AssistantConversation conversation = new AssistantConversation();
            conversation.setUserId(userId);
            conversation.setMode(mode);
            conversation.setEventId(eventId);
            conversation.setRecordId(recordId);
            return conversations.save(conversation);
        });
    }

    @Transactional(readOnly = true)
    public List<AssistantConversation> recent(Long userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, MAX_CONVERSATIONS));
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> pageMessages(Long userId, Long conversationId, int page, int size) {
        var conversation = conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new com.zhixuewuli.common.exception.BusinessException("会话不存在"));
        int pageSize = Math.min(Math.max(1, size), PAGE_SIZE);
        return messages.findByConversationIdOrderBySeqAsc(conversation.getId(), PageRequest.of(page, pageSize));
    }

    @Transactional
    public AssistantMessage appendUserMessage(Long conversationId, String mode, String question) {
        AssistantMessage message = new AssistantMessage();
        message.setConversationId(conversationId);
        message.setSeq(nextSeq(conversationId));
        message.setRole("USER");
        message.setContent(question);
        message.setMode(mode);
        message.setStatus("COMPLETED");
        return messages.save(message);
    }

    @Transactional
    public AssistantMessage appendAssistantMessage(Long conversationId, String mode, String content,
                                                   String sourcesJson, String status) {
        AssistantMessage message = new AssistantMessage();
        message.setConversationId(conversationId);
        message.setSeq(nextSeq(conversationId));
        message.setRole("ASSISTANT");
        message.setContent(content == null ? "" : content);
        message.setMode(mode);
        message.setStatus(status == null ? "COMPLETED" : status);
        message.setSourcesJson(sourcesJson);
        return messages.save(message);
    }

    private int nextSeq(Long conversationId) {
        return messages.countByConversationId(conversationId) + 1;
    }

    @Transactional
    public AssistantRun startRun(String runId, String requestId, Long conversationId, Long userId, String mode) {
        var existing = runs.findByUserIdAndRequestId(userId, requestId);
        if (existing.isPresent()) return existing.get();
        AssistantRun run = new AssistantRun();
        run.setRunId(runId);
        run.setRequestId(requestId);
        run.setConversationId(conversationId);
        run.setUserId(userId);
        run.setMode(mode);
        run.setStatus("RUNNING");
        return runs.save(run);
    }

    @Transactional
    public void finishRun(String runId, String status, String failureCode) {
        runs.findByRunId(runId).ifPresent(run -> {
            run.setStatus(status);
            run.setFailureCode(failureCode);
            runs.save(run);
        });
    }

    @Transactional(readOnly = true)
    public Optional<AssistantRun> findByRequestId(Long userId, String requestId) {
        return runs.findByUserIdAndRequestId(userId, requestId);
    }
}
