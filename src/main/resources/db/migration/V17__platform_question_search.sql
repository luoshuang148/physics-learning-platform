-- Task 2: 题库 AI 补练发布字段与检索索引一致性
-- MySQL 是当前有效版本的依据；向量库只做异步镜像，可重建。

-- 1) questions 新增 AI 补练发布字段
ALTER TABLE questions
    ADD COLUMN ai_practice_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '教师审核后才允许用于 AI 补练',
    ADD COLUMN ai_content_revision BIGINT NOT NULL DEFAULT 1 COMMENT '内容修订号；题干/选项/答案/解析/难度/标签/图片/AI开关变化时递增';

-- 2) 可检索单元（当前有效文本/版本；MySQL 为有效版本依据）
CREATE TABLE question_search_units (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    part VARCHAR(20) NOT NULL COMMENT 'QUESTION | EXPLANATION',
    ordinal_no INT NOT NULL,
    unit_text LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    knowledge_point_codes JSON NULL,
    difficulty INT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_question_unit (question_id, revision, part, ordinal_no),
    KEY idx_question_unit_rev (question_id, revision),
    -- 不加 questions 外键：题目删除后保留删除墓碑/任务历史
    CONSTRAINT fk_question_unit_self CHECK (question_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) 持久化索引任务（租约 + 尝试次数）
-- status: PENDING | INDEXING | READY | FAILED | OBSOLETE | DELETED
CREATE TABLE question_index_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    job_type VARCHAR(20) NOT NULL COMMENT 'INDEX | DELETE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64) NULL,
    lease_owner VARCHAR(64) NULL,
    lease_expires_at DATETIME(6) NULL,
    index_version VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_question_job_runnable (status, lease_expires_at, question_id),
    KEY idx_question_job_question (question_id, revision),
    -- 一题一修订一种任务只保留一行，重试/重入直接更新该行（幂等）
    UNIQUE KEY uk_question_job (question_id, revision, job_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) 发布元数据（写入向量后才发布 READY；旧任务晚完成不得覆盖）
CREATE TABLE question_index_publications (
    question_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    index_version VARCHAR(64) NOT NULL,
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (question_id, revision),
    KEY idx_question_pub_version (index_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
