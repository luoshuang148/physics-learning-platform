CREATE TABLE learning_plan_previews (
    run_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    source_answer_revision VARCHAR(64) NOT NULL,
    plan_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_learning_preview_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_learning_preview_event FOREIGN KEY (event_id) REFERENCES teacher_events(id),
    KEY idx_learning_preview_owner (user_id,event_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE learning_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    source_run_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    source_answer_revision VARCHAR(64) NOT NULL,
    days INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    activated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_learning_plan_request (user_id,request_id),
    UNIQUE KEY uk_learning_plan_run (user_id,source_run_id),
    KEY idx_learning_plan_owner (user_id,status,updated_at),
    CONSTRAINT fk_learning_plan_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_learning_plan_event FOREIGN KEY (event_id) REFERENCES teacher_events(id),
    CONSTRAINT fk_learning_plan_preview FOREIGN KEY (source_run_id) REFERENCES learning_plan_previews(run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE learning_plan_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    day_no INT NOT NULL,
    question_id BIGINT NOT NULL,
    question_content_snapshot LONGTEXT NOT NULL,
    knowledge_point_code VARCHAR(64) NOT NULL,
    reason_source_ids JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_learning_plan_item_plan FOREIGN KEY (plan_id) REFERENCES learning_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_learning_plan_item_question FOREIGN KEY (question_id) REFERENCES questions(id),
    UNIQUE KEY uk_learning_plan_question (plan_id,question_id),
    KEY idx_learning_plan_item_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
