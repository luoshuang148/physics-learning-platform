CREATE TABLE learning_assistant_runs (
    run_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    execution_mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    model_calls INT NOT NULL DEFAULT 0,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    usage_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    estimated_cost DECIMAL(20,12) NULL,
    cost_currency VARCHAR(8) NULL,
    pricing_version VARCHAR(64) NULL,
    CONSTRAINT fk_learning_assistant_run_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_learning_assistant_run_event FOREIGN KEY (event_id) REFERENCES teacher_events(id),
    KEY idx_learning_assistant_run_owner (user_id,started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE learning_assistant_run_steps (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    step_no INT NOT NULL,
    step_name VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_learning_assistant_step_run FOREIGN KEY (run_id) REFERENCES learning_assistant_runs(run_id) ON DELETE CASCADE,
    UNIQUE KEY uk_learning_assistant_step (run_id,step_no),
    KEY idx_learning_assistant_step_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
