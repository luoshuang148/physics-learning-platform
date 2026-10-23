package com.zhixuewuli.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity @Table(name="ai_message_sources",uniqueConstraints=@UniqueConstraint(name="uk_ai_message_source_citation",columnNames={"run_id","citation_id"}))
@Data @EqualsAndHashCode(callSuper=false)
public class AiMessageSource extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="run_id",nullable=false) private Long runId;
    @Column(name="citation_id",nullable=false,length=12) private String citationId;
    @Column(name="chunk_id",nullable=false) private Long chunkId;
    @Column(name="document_id",nullable=false) private Long documentId;
    @Column(name="revision_id",nullable=false) private Long revisionId;
    @Column(name="title_snapshot",nullable=false,length=120) private String titleSnapshot;
    @Column(name="page_no") private Integer pageNo;
    @Column(name="section_title",length=200) private String sectionTitle;
    @Lob @Column(name="source_text",nullable=false,columnDefinition="LONGTEXT") private String sourceText;
    @Column(name="source_sha256",nullable=false,length=64) private String sourceSha256;
}
