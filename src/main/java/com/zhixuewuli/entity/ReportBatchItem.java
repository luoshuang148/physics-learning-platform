package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批量报告子任务（一名学生一条）。
 *
 * <p>状态机：PENDING → PROCESSING → SUCCEEDED / FAILED / SKIPPED；临时失败回落
 * PENDING 走延迟重投。所有状态流转使用带条件的 UPDATE 认领，重复消息不会重复计数或重复保存。</p>
 */
@Entity
@Table(name = "report_batch_items", indexes = {
        @Index(name = "idx_report_batch_items_batch_status", columnList = "batch_id, status"),
        @Index(name = "idx_report_batch_items_status_lease", columnList = "status, lease_expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_report_batch_item", columnNames = {"batch_id", "user_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportBatchItem extends BaseTimeEntity {

    public enum Status { PENDING, PROCESSING, SUCCEEDED, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "子任务 ID")
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "student_name", nullable = false, length = 100)
    private String studentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "manual_retry_count", nullable = false)
    private int manualRetryCount;

    @Column(name = "execution_id", length = 36, columnDefinition = "char(36)")
    private String executionId;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "input_digest", length = 64, columnDefinition = "char(64)")
    private String inputDigest;

    @Column(name = "input_revision", length = 60)
    private String inputRevision;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "skip_reason", length = 200)
    private String skipReason;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
