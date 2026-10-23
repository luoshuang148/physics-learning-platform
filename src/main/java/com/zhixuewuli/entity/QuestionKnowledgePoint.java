package com.zhixuewuli.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_knowledge_points", uniqueConstraints =
        @UniqueConstraint(name = "uk_question_knowledge_point", columnNames = {"question_id", "knowledge_point_code"}))
@Getter
@NoArgsConstructor
public class QuestionKnowledgePoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "knowledge_point_code", nullable = false, length = 100)
    private String knowledgePointCode;

    public QuestionKnowledgePoint(Question question, String knowledgePointCode) {
        this.question = question;
        this.knowledgePointCode = knowledgePointCode;
    }
}
