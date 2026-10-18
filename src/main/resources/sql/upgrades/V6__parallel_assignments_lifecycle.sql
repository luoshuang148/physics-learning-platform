-- Multiple parallel assignments + lifecycle. Run once after V5 with writes stopped.
ALTER TABLE teacher_events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ARCHIVED' AFTER event_timestamp,
    ADD COLUMN start_at DATETIME NULL AFTER status,
    ADD COLUMN due_at DATETIME NULL AFTER start_at;

UPDATE teacher_events SET status = CASE WHEN is_global = TRUE THEN 'PUBLISHED' ELSE 'ARCHIVED' END;

ALTER TABLE teacher_events
    DROP COLUMN active_slot,
    DROP COLUMN is_global;

ALTER TABLE teacher_events
    ADD INDEX idx_teacher_events_status (status),
    ADD INDEX idx_teacher_events_status_start (status, start_at),
    ADD INDEX idx_teacher_events_status_due (status, due_at);
