package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 助手运行记录（可观测性；不含模型内部推理）。 */
@Entity
@Table(name = "assistant_runs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_assistant_run", columnNames = {"run_id"}),
        @UniqueConstraint(name = "uk_assistant_run_request", columnNames = {"user_id", "request_id"})
})
@Data
@EqualsAndHashCode(callSuper = false)
public class AssistantRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String mode;

    /** RUNNING | COMPLETED | CANCELLED | FAILED */
    @Column(nullable = false, length = 20)
    private String status = "RUNNING";

    @Column(name = "prompt_version", nullable = false, length = 40)
    private String promptVersion = "platform-qa-v1";

    @Column(name = "index_version", length = 64)
    private String indexVersion;

    @Column(name = "retrieval_mode", length = 32)
    private String retrievalMode;

    @Column(name = "candidate_ids_json", columnDefinition = "JSON")
    private String candidateIdsJson;

    @Column(name = "source_ids_json", columnDefinition = "JSON")
    private String sourceIdsJson;

    @Column(name = "retrieval_ms")
    private Long retrievalMs;

    @Column(name = "first_token_ms")
    private Long firstTokenMs;

    @Column(name = "total_ms")
    private Long totalMs;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "failure_code", length = 64)
    private String failureCode;
}
