package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "class_students", uniqueConstraints = @UniqueConstraint(name = "uk_class_student", columnNames = "user_id"),
        indexes = @Index(name = "idx_class_students_class", columnList = "class_id"))
@Schema(description = "班级学生成员")
@Data
public class ClassStudent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();
}
