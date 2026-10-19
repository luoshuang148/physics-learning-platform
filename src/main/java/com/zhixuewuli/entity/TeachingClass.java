package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** A teaching class. One student belongs to one primary class; teachers may belong to many. */
@Entity
@Table(name = "classes", indexes = @Index(name = "idx_classes_status", columnList = "status"))
@Schema(description = "教学班级")
@Data
@EqualsAndHashCode(callSuper = false)
public class TeachingClass extends BaseTimeEntity {
    public enum Status { ACTIVE, INACTIVE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    @NotBlank(message = "班级名称不能为空")
    @Size(max = 80, message = "班级名称不能超过80字符")
    private String name;

    @Column(length = 30)
    @Size(max = 30, message = "年级不能超过30字符")
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
}
