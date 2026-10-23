package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /ai/student/assistant/messages 请求。
 * 错题模式通过 recordId 自动解析上下文；显式 eventId 与记录冲突则拒绝。
 */
@Data
public class AssistantMessageRequest {

    @NotNull(message = "问题不能为空")
    @Size(min = 1, max = 800, message = "问题长度 1～800 字")
    private String question;

    /** GENERAL | PLATFORM */
    @NotNull(message = "模式不能为空")
    private String mode = "GENERAL";

    private Long conversationId;

    private Long eventId;

    private Long recordId;

    @NotNull(message = "requestId 不能为空")
    private String requestId;
}
