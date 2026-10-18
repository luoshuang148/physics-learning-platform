package com.zhixuewuli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Content-addressed question image; identical images share one immutable row. */
@Entity
@Table(name = "question_images")
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionImage extends BaseTimeEntity {
    @Id
    @Column(name = "content_hash", length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;
}
