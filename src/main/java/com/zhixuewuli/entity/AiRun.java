package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity @Table(name="ai_runs",uniqueConstraints=@UniqueConstraint(name="uk_ai_run_history",columnNames="conversation_history_id"))
@Data @EqualsAndHashCode(callSuper=false)
public class AiRun extends BaseTimeEntity {
    public enum Status { SUCCEEDED, FAILED, CANCELLED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="conversation_history_id",nullable=false) private Long conversationHistoryId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="event_id") private Long eventId;
    @Column(nullable=false,length=20) private String mode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false,length=40) private String outcome;
    @Column(name="model_name",nullable=false,length=120) private String modelName;
    @Column(name="prompt_sha256",nullable=false,length=64) private String promptSha256;
    @Column(name="input_sha256",nullable=false,length=64) private String inputSha256;
    @Column(name="prompt_tokens") private Integer promptTokens;
    @Column(name="completion_tokens") private Integer completionTokens;
    @Column(name="total_tokens") private Integer totalTokens;
    @Column(name="retrieval_ms") private Long retrievalMs;
    @Column(name="model_ms") private Long modelMs;
}
