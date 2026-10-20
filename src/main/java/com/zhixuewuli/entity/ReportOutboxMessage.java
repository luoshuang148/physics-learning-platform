package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 事务性发件箱记录。
 *
 * <p>业务事务提交前写入同一数据库事务；发布器以带租约的条件更新认领后投递，
 * 收到 broker confirm 才标记 PUBLISHED。崩溃后租约到期由其他实例接管。</p>
 */
@Entity
@Table(name = "report_outbox", indexes = {
        @Index(name = "idx_report_outbox_due", columnList = "status, next_attempt_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_report_outbox_message", columnNames = {"message_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportOutboxMessage extends BaseTimeEntity {

    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_item_id")
    private Long workItemId;

    @Column(name = "message_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String messageId;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
