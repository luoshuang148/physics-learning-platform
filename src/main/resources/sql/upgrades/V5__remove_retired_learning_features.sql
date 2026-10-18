-- Run once after V4 with writes stopped and a backup available.
-- This removes the retired self-reported error-reason, video and experiment structures.
ALTER TABLE answer_records DROP COLUMN error_reason;
ALTER TABLE questions DROP COLUMN explanation_video_mapping;
DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS experiments;
