-- Back up first; run once after V2 with writes stopped. MySQL 8.0+.
CREATE TABLE teacher_event_questions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 event_id BIGINT NOT NULL,
 question_id BIGINT NOT NULL,
 position_number INT NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 snapshot_origin VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
 UNIQUE KEY uk_event_question(event_id,question_id),
 UNIQUE KEY uk_event_position(event_id,position_number),
 CONSTRAINT fk_event_question_event FOREIGN KEY(event_id) REFERENCES teacher_events(id) ON DELETE CASCADE
);
-- Existing QIDS can only be reconstructed from the bank at upgrade time, never original history.
INSERT INTO teacher_event_questions(event_id,question_id,position_number,snapshot_json,snapshot_origin)
SELECT e.id,t.question_id,t.ord-1,
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
