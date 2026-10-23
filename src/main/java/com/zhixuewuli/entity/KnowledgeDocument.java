package com.zhixuewuli.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Entity @Table(name="knowledge_documents") @Data @EqualsAndHashCode(callSuper=false)
public class KnowledgeDocument extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="class_id",nullable=false) private Long classId;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(nullable=false,length=120) private String title;
    @Column(name="storage_key",nullable=false,length=80) private String storageKey;
    @Column(name="media_type",nullable=false,length=40) private String mediaType;
    @Column(name="content_sha256",nullable=false,length=64) private String contentSha256;
    @Column(name="active_revision_id") private Long activeRevisionId;
    @Column(name="withdrawn_at") private java.time.LocalDateTime withdrawnAt;
    @Version @Column(name="lock_version",nullable=false) private Long lockVersion=0L;
}
