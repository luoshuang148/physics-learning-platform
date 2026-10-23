package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 学习助手会话：按 userId+mode+eventId+recordId 绑定。 */
@Entity
@Table(name = "assistant_conversations", indexes = {
        @Index(name = "idx_assistant_conv_owner", columnList = "user_id, updated_at"),
        @Index(name = "idx_assistant_conv_bind", columnList = "user_id, mode, event_id, record_id")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class AssistantConversation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** GENERAL | PLATFORM */
    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "record_id")
    private Long recordId;

    @Column(length = 200)
    private String title;
}
