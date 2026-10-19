-- Run once after V6 with application writes stopped. Historical membership cannot be reconstructed exactly.
CREATE TABLE task_recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    CONSTRAINT uk_task_recipient UNIQUE(event_id,user_id),
    CONSTRAINT fk_recipient_event FOREIGN KEY(event_id) REFERENCES teacher_events(id) ON DELETE RESTRICT,
    CONSTRAINT fk_recipient_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Explicit migration baseline: current active students plus known original participants.
INSERT INTO task_recipients(event_id,user_id,source)
SELECT e.id,u.id,'MIGRATED' FROM teacher_events e CROSS JOIN users u
WHERE e.status <> 'DRAFT' AND
((u.user_type='STUDENT' AND u.active=true) OR
EXISTS(SELECT 1 FROM answer_records a WHERE a.event_id=e.id AND a.user_id=u.id AND a.practice=false));
