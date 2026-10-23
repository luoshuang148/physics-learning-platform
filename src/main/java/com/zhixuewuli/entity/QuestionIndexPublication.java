package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目索引发布记录：写入向量且源题修订仍匹配才发布。
 * 不使用 BaseTimeEntity：表只保留 published_at。
 */
@Entity
@Table(name = "question_index_publications")
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionIndexPublication {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Column(nullable = false)
    private Long revision;

    @Column(name = "index_version", nullable = false, length = 64)
    private String indexVersion;

    @Column(name = "published_at", nullable = false)
    private java.time.LocalDateTime publishedAt = java.time.LocalDateTime.now();
}
