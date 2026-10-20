package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_teachers", uniqueConstraints = @UniqueConstraint(name = "uk_class_teacher", columnNames = {"class_id", "teacher_id"}),
        indexes = @Index(name = "idx_class_teachers_teacher", columnList = "teacher_id"))
@Schema(description = "班级教师成员")
@Data
public class ClassTeacher {
    public enum Role { OWNER, CO_TEACHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CO_TEACHER;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
