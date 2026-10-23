-- Class-scoped knowledge; original files and vectors are managed separately.
CREATE TABLE knowledge_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    -- Immutable creator audit ID, not an access grant or a user deletion dependency.
    owner_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    storage_key VARCHAR(80) NOT NULL,
    media_type VARCHAR(40) NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    active_revision_id BIGINT NULL,
    withdrawn_at DATETIME(6) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_knowledge_storage_key (storage_key),
    KEY idx_knowledge_class_active (class_id, withdrawn_at, id),
    CONSTRAINT fk_knowledge_document_class FOREIGN KEY (class_id) REFERENCES classes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    extractor_version VARCHAR(50) NULL,
    chunker_version VARCHAR(50) NULL,
    index_version VARCHAR(80) NULL,
    embedding_model VARCHAR(120) NULL,
    dimensions INT NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME(6) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(80) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_knowledge_revision_document (document_id, id),
    KEY idx_knowledge_revision_recovery (status, lease_expires_at),
    CONSTRAINT fk_knowledge_revision_document FOREIGN KEY (document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    ordinal_no INT NOT NULL,
    page_no INT NULL,
    section_title VARCHAR(200) NULL,
    chunk_text LONGTEXT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_knowledge_chunk_ordinal (revision_id, ordinal_no),
    FULLTEXT KEY idx_knowledge_chunk_text (chunk_text) WITH PARSER ngram,
    CONSTRAINT fk_knowledge_chunk_revision FOREIGN KEY (revision_id) REFERENCES knowledge_revisions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE knowledge_documents ADD CONSTRAINT fk_knowledge_active_revision
    FOREIGN KEY (active_revision_id) REFERENCES knowledge_revisions(id) ON DELETE SET NULL;
