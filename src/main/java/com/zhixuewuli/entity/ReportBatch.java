package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批量学生报告批次。
 *
 * <p>教师对某个已截止教学任务的应参与名单发起一次批量生成，本表保存批次级
 * 权限范围、幂等键与整体状态。名单与进度只由 {@link ReportBatchItem} 聚合得出，
 * 不维护可被重复消费多计的计数器。</p>
 */
@Entity
@Table(name = "report_batches", indexes = {
        @Index(name = "idx_report_batches_teacher", columnList = "teacher_id, created_at"),
        @Index(name = "idx_report_batches_class", columnList = "class_id, created_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_report_batches_idempotency", columnNames = {"teacher_id", "idempotency_key"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportBatch extends BaseTimeEntity {

    public enum Status { PENDING, PROCESSING, SUCCEEDED, PARTIAL_FAILED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "批次 ID")
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
