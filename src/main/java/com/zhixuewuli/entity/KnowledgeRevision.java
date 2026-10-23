package com.zhixuewuli.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Entity @Table(name="knowledge_revisions") @Data @EqualsAndHashCode(callSuper=false)
public class KnowledgeRevision extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    public enum Status { UPLOADED, INDEXING, READY, FAILED }
    @Column(name="document_id",nullable=false) private Long documentId;
    @Column(name="content_sha256",nullable=false,length=64) private String contentSha256;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.UPLOADED;
    @Column(name="extractor_version",length=50) private String extractorVersion;
    @Column(name="chunker_version",length=50) private String chunkerVersion;
    @Column(name="index_version",length=80) private String indexVersion;
    @Column(name="embedding_model",length=120) private String embeddingModel;
    private Integer dimensions;
    @Column(name="lease_owner",length=100) private String leaseOwner;
    @Column(name="lease_expires_at") private java.time.LocalDateTime leaseExpiresAt;
    @Column(name="retry_count",nullable=false) private int retryCount;
    @Column(name="error_code",length=80) private String errorCode;
    @Version @Column(name="lock_version",nullable=false) private Long lockVersion=0L;
}
