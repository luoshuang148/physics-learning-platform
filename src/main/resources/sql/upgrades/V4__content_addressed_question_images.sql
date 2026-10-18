-- Run after V3 with writes stopped. Existing answer snapshots remain readable without backfill.
CREATE TABLE IF NOT EXISTS question_images (
    content_hash CHAR(64) PRIMARY KEY,
    content LONGTEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- New attempts point at an immutable image row instead of repeating Base64 in every JSON snapshot.
ALTER TABLE answer_records ADD COLUMN image_ref VARCHAR(64) NULL;
