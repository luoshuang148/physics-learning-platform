-- Task 6: 计划条目快照与跨题补练归属
-- 原 eventId 仅表示诊断来源；补练记录 practice=true, event_id=null，并关联 planItemId。

ALTER TABLE learning_plan_items
    ADD COLUMN question_revision BIGINT NULL COMMENT '激活时的题目修订号；老条目保持 NULL',
    ADD COLUMN question_snapshot JSON NULL COMMENT '激活前完整题目快照；老条目保持 NULL',
    ADD COLUMN question_options_snapshot LONGTEXT NULL COMMENT '选项快照（JSON 字符串）',
    ADD COLUMN question_correct_option VARCHAR(50) NULL COMMENT '正确选项快照（评分用）';

CREATE TABLE learning_plan_attempt_links (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    plan_item_id BIGINT NOT NULL,
    answer_record_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_plan_attempt_record (answer_record_id),
    UNIQUE KEY uk_plan_attempt_request (user_id, request_id),
    KEY idx_plan_attempt_item (plan_item_id, created_at),
    CONSTRAINT fk_plan_attempt_item FOREIGN KEY (plan_item_id) REFERENCES learning_plan_items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
