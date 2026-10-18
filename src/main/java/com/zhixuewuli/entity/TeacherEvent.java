package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 教学任务（作业）实体。单班结构下允许教师同时存在多个任务。
 */
@Entity
@Table(name = "teacher_events", indexes = {
        @Index(name = "idx_teacher_events_status", columnList = "status"),
        @Index(name = "idx_teacher_events_status_start", columnList = "status,start_at"),
        @Index(name = "idx_teacher_events_status_due", columnList = "status,due_at")
})
@Schema(description = "教学任务实体")
@Data
@EqualsAndHashCode(callSuper = false)
public class TeacherEvent extends BaseTimeEntity {

    public enum Status { DRAFT, PUBLISHED, CLOSED, ARCHIVED }

    @Column(name = "timestamp_utc", nullable = false, columnDefinition = "boolean default false")
    private boolean timestampUtc = true;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "任务 ID", example = "1")
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    @NotNull(message = "教师 ID 不能为空")
    private Long teacherId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    private User teacher;

    @Column(name = "teacher_name", nullable = false, length = 100)
    @NotBlank(message = "教师姓名不能为空")
    @Size(max = 100, message = "教师姓名长度不能超过100字符")
    private String teacherName;

    @Column(name = "event_title", nullable = false, length = 20)
    @NotBlank(message = "任务标题不能为空")
    @Size(max = 20, message = "任务标题长度不能超过20字符")
    private String eventTitle;

    @Column(name = "event_description", length = 100)
    @Size(max = 100, message = "任务描述长度不能超过100字符")
    private String eventDescription;

    @Column(name = "event_timestamp", nullable = false)
    @NotNull(message = "任务时间不能为空")
    private LocalDateTime eventTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PUBLISHED;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /**
     * 兼容旧调用方的派生字段，不再持久化。状态是唯一事实来源。
     */
    @Transient
    private Boolean isGlobal;

    public Boolean getIsGlobal() {
        return status == Status.PUBLISHED;
    }

    public void setIsGlobal(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            if (status == null) status = Status.PUBLISHED;
            return;
        }
        if (status == null || status == Status.PUBLISHED) status = Status.ARCHIVED;
    }

    public boolean isOpenAt(LocalDateTime now) {
        return status == Status.PUBLISHED
                && (startAt == null || !now.isBefore(startAt))
                && (dueAt == null || now.isBefore(dueAt));
    }

    public boolean isScheduledAt(LocalDateTime now) {
        return status == Status.PUBLISHED && startAt != null && now.isBefore(startAt);
    }

    public boolean isEndedAt(LocalDateTime now) {
        return status == Status.PUBLISHED && dueAt != null && !now.isBefore(dueAt);
    }
}
