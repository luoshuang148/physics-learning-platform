package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 助手消息（用户问题 / 公开回答）。 */
@Entity
@Table(name = "assistant_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_assistant_msg", columnNames = {"conversation_id", "seq"})
})
@Data
@EqualsAndHashCode(callSuper = false)
public class AssistantMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private int seq;

    /** USER | ASSISTANT */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(nullable = false, length = 20)
    private String mode;

    /** COMPLETED | CANCELLED | FAILED */
    @Column(nullable = false, length = 20)
    private String status = "COMPLETED";

    /** 公开来源快照 JSON */
    @Column(name = "sources_json", columnDefinition = "JSON")
    private String sourcesJson;
}
