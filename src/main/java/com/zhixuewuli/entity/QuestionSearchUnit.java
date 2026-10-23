package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目可检索单元（MySQL 侧当前有效文本/版本）。
 * 不加 questions 外键：题目删除后任务/墓碑仍可查询。
 */
@Entity
@Table(name = "question_search_units", uniqueConstraints = {
        @UniqueConstraint(name = "uk_question_unit", columnNames = {"question_id", "revision", "part", "ordinal_no"})
}, indexes = {
        @Index(name = "idx_question_unit_rev", columnList = "question_id, revision")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class QuestionSearchUnit extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(nullable = false)
    private Long revision;

    /** QUESTION | EXPLANATION */
    @Column(nullable = false, length = 20)
    private String part;

    @Column(name = "ordinal_no", nullable = false)
    private int ordinalNo;

    @Column(name = "unit_text", nullable = false, columnDefinition = "LONGTEXT")
    private String unitText;

    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String contentHash;

    /** JSON 数组字符串 */
    @Column(name = "knowledge_point_codes", columnDefinition = "JSON")
    private String knowledgePointCodes;

    private Integer difficulty;
}
