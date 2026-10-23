package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目索引持久化任务：租约 + 尝试次数 + 删除墓碑。
 * 一题一修订一种任务一行（uk_question_job），重试/重入更新该行。
 */
@Entity
@Table(name = "question_index_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_question_job", columnNames = {"question_id", "revision", "job_type"})
}, indexes = {
        @Index(name = "idx_question_job_runnable", columnList = "status, lease_expires_at, question_id"),
        @Index(name = "idx_question_job_question", columnList = "question_id, revision")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionIndexJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Long revision;

    /** INDEX | DELETE */
    @Column(name = "job_type", nullable = false, length = 20)
    private String jobType;

    /** PENDING | INDEXING | READY | FAILED | OBSOLETE | DELETED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private java.time.LocalDateTime leaseExpiresAt;

    @Column(name = "index_version", length = 64)
    private String indexVersion;

    public enum Status {
        PENDING, INDEXING, READY, FAILED, OBSOLETE, DELETED
    }
}
