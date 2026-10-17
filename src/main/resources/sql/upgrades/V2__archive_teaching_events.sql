-- Back up the existing database first. Run once against the selected database.
-- If multiple active rows exist, resolve them explicitly before applying the unique index.
SELECT id, event_title FROM teacher_events WHERE is_global = TRUE;
ALTER TABLE teacher_events ADD COLUMN active_slot INT GENERATED ALWAYS AS
    (CASE WHEN is_global = TRUE THEN 1 ELSE NULL END), ADD UNIQUE KEY uk_active_event (active_slot);
ALTER TABLE answer_records ADD COLUMN event_id BIGINT NULL, ADD INDEX idx_answer_records_event (event_id),
    ADD CONSTRAINT fk_answer_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);
ALTER TABLE conversation_history ADD COLUMN event_id BIGINT NULL, ADD INDEX idx_conversation_history_event (event_id),
    ADD CONSTRAINT fk_history_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);
ALTER TABLE student_reports ADD COLUMN event_id BIGINT NULL, ADD INDEX idx_student_reports_event (event_id),
    ADD CONSTRAINT fk_report_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);
-- Legacy rows intentionally remain unassigned; no historical data is deleted or guessed.
