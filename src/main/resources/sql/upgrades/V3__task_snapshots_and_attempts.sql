-- Back up first; run once after V2 with writes stopped. MySQL 8.0+.
CREATE TABLE teacher_event_questions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_id BIGINT NOT NULL,
 question_id BIGINT NOT NULL,
 bank_question_id BIGINT NULL,
 position_number INT NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 snapshot_origin VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
 UNIQUE KEY uk_event_question(event_id,question_id),
 UNIQUE KEY uk_event_position(event_id,position_number),
 CONSTRAINT fk_task_bank_question FOREIGN KEY(bank_question_id) REFERENCES questions(id),
 CONSTRAINT fk_event_question_event FOREIGN KEY(event_id) REFERENCES teacher_events(id) ON DELETE CASCADE
);
-- Existing QIDS can only be reconstructed from the bank at upgrade time, never original history.
INSERT INTO teacher_event_questions(event_id,question_id,bank_question_id,position_number,snapshot_json,snapshot_origin)
SELECT e.id,t.question_id,q.id,t.ord-1,
 JSON_OBJECT('id',t.question_id,'content',COALESCE(q.content,'升级前题目已缺失'),
  'options',COALESCE(CAST(q.options AS CHAR),'{}'),'correctOption',q.correct_option,
  'explanation',q.explanation,'difficulty',COALESCE(q.difficulty,1),
  'explanationVideoMapping',CAST(q.explanation_video_mapping AS CHAR),'imageBase64',q.image_base64),
 CASE WHEN q.id IS NULL THEN 'MISSING' ELSE 'MIGRATED' END
FROM teacher_events e
JOIN JSON_TABLE(IF(e.event_description LIKE 'QIDS:%' AND JSON_VALID(CONCAT('[',SUBSTRING(e.event_description,6),']')),
 CONCAT('[',SUBSTRING(e.event_description,6),']'),'[]'),'$[*]' COLUMNS(ord FOR ORDINALITY,question_id BIGINT PATH '$' NULL ON ERROR)) t
LEFT JOIN questions q ON q.id=t.question_id
WHERE t.question_id IS NOT NULL;
ALTER TABLE answer_records ADD COLUMN request_id VARCHAR(64) NULL,
 ADD COLUMN practice BOOLEAN NOT NULL DEFAULT FALSE,
 ADD COLUMN question_snapshot LONGTEXT NULL,
 ADD COLUMN attempt_order BIGINT NULL,
 ADD UNIQUE KEY uk_user_request(user_id,request_id);
UPDATE answer_records a JOIN (SELECT id,ROW_NUMBER() OVER(ORDER BY answered_at,id) AS seq FROM answer_records) ordered
 ON ordered.id=a.id SET a.attempt_order=ordered.seq;
ALTER TABLE answer_records MODIFY attempt_order BIGINT NOT NULL AUTO_INCREMENT, ADD UNIQUE KEY uk_attempt_order(attempt_order);
-- Null event IDs are explicitly legacy/free practice, not current-task participation.
UPDATE answer_records SET practice=TRUE WHERE event_id IS NULL;

ALTER TABLE answer_records ADD COLUMN content_snapshot LONGTEXT NULL, ADD COLUMN options_snapshot LONGTEXT NULL,
 ADD COLUMN correct_snapshot VARCHAR(1) NULL, ADD COLUMN analysis_snapshot LONGTEXT NULL, ADD COLUMN difficulty_snapshot INT NULL;

-- Invalidate tokens issued by releases without persistent version claims.
ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE student_reports ADD COLUMN evidence_json TEXT NULL;

CREATE INDEX idx_task_attempt ON answer_records(user_id,event_id,question_id,attempt_order);
CREATE INDEX idx_history_scope ON conversation_history(user_id,session_id,conversation_type,question_id,event_id,is_active,sequence_number);

-- Cover task aggregates without reading large answer snapshot rows.
CREATE INDEX idx_event_summary ON answer_records(event_id,practice,user_id,question_id,correct,attempt_order);
