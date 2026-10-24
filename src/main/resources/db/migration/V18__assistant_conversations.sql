-- Task 4: 学习助手会话、消息与运行持久化
-- 会话按 userId+mode+eventId+recordId 绑定；上下文变化新建会话。

CREATE TABLE assistant_conversations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL COMMENT 'GENERAL | PLATFORM',
    event_id BIGINT NULL,
    record_id BIGINT NULL,
    title VARCHAR(200) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_assistant_conv_owner (user_id, updated_at),
    KEY idx_assistant_conv_bind (user_id, mode, event_id, record_id),
    CONSTRAINT fk_assistant_conv_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assistant_messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    seq INT NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER | ASSISTANT',
    content LONGTEXT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED | CANCELLED | FAILED',
    sources_json JSON NULL COMMENT '公开来源快照',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_assistant_msg (conversation_id, seq),
    CONSTRAINT fk_assistant_msg_conv FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assistant_runs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'RUNNING | COMPLETED | CANCELLED | FAILED',
    prompt_version VARCHAR(40) NOT NULL,
    index_version VARCHAR(64) NULL,
    retrieval_mode VARCHAR(32) NULL,
    candidate_ids_json JSON NULL,
    source_ids_json JSON NULL,
    retrieval_ms BIGINT NULL,
    first_token_ms BIGINT NULL,
    total_ms BIGINT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    failure_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_assistant_run (run_id),
    UNIQUE KEY uk_assistant_run_request (user_id, request_id),
    KEY idx_assistant_run_conv (conversation_id, created_at),
    CONSTRAINT fk_assistant_run_conv FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_assistant_run_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
