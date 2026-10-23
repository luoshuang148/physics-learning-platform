CREATE TABLE ai_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_history_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_id BIGINT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    prompt_sha256 VARCHAR(64) NOT NULL,
    input_sha256 VARCHAR(64) NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    retrieval_ms BIGINT NULL,
    model_ms BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_run_history (conversation_history_id),
    KEY idx_ai_run_user_event (user_id,event_id,created_at),
    CONSTRAINT fk_ai_run_history FOREIGN KEY (conversation_history_id) REFERENCES conversation_history(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_run_steps (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_name VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NULL,
    details_json JSON NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_run_step_order (run_id,step_order),
    CONSTRAINT fk_ai_run_step_run FOREIGN KEY (run_id) REFERENCES ai_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_message_sources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    citation_id VARCHAR(12) NOT NULL,
    chunk_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    title_snapshot VARCHAR(120) NOT NULL,
    page_no INT NULL,
    section_title VARCHAR(200) NULL,
    source_text LONGTEXT NOT NULL,
    source_sha256 VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_message_source_citation (run_id,citation_id),
    CONSTRAINT fk_ai_message_source_run FOREIGN KEY (run_id) REFERENCES ai_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
