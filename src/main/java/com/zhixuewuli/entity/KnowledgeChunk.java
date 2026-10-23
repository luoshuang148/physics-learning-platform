package com.zhixuewuli.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Entity @Table(name="knowledge_chunks",uniqueConstraints=@UniqueConstraint(name="uk_knowledge_chunk_ordinal",columnNames={"revision_id","ordinal_no"})) @Data @EqualsAndHashCode(callSuper=false)
public class KnowledgeChunk extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="revision_id",nullable=false) private Long revisionId;
    @Column(name="ordinal_no",nullable=false) private Integer ordinalNo;
    @Column(name="page_no") private Integer pageNo;
    @Column(name="section_title",length=200) private String sectionTitle;
    @Lob @Column(name="chunk_text",nullable=false,columnDefinition="LONGTEXT") private String text;
    @Column(name="content_sha256",nullable=false,length=64) private String contentSha256;
}
