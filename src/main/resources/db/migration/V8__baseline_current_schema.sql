-- Flyway baseline: current schema after the manual V2..V7 upgrades.
-- Existing V7 databases are baselined at version 8 and skip this script;
-- empty databases execute it to create the complete current structure.

CREATE TABLE users (
    token_version BIGINT NOT NULL DEFAULT 0,
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（学号/工号）',
    password_hash VARCHAR(255) NOT NULL COMMENT '加密密码',
    user_type VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '用户类型',
    real_name VARCHAR(100) NOT NULL COMMENT '真实姓名',
    class_name VARCHAR(50) COMMENT '班级名称',
    active BOOLEAN DEFAULT TRUE COMMENT '账户状态',
    last_login DATETIME NULL COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_real_name (real_name),
    INDEX idx_class_user_active (class_name, user_type, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- ====================================================================
-- 3. 题目表 (questions) - 存储练习题目
-- ====================================================================
CREATE TABLE questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '题目 ID',
    content LONGTEXT NOT NULL COMMENT '题目内容（统一 LaTeX 格式）',
    content_hash CHAR(64) NOT NULL COMMENT 'content 的 SHA-256 哈希值，用于唯一性约束',
    options LONGTEXT NOT NULL COMMENT '选项内容（LaTeX格式JSON数组）',
    correct_option VARCHAR(50) NOT NULL COMMENT '正确答案',
    explanation LONGTEXT COMMENT '解析说明（LaTeX格式）',
    difficulty INT DEFAULT 1 COMMENT '难度等级（1-5）',
    image_base64 LONGTEXT COMMENT '题目图片Base64编码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_content_hash (content_hash),
    INDEX idx_difficulty (difficulty),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目信息表（统一 LaTeX 格式）';

-- ====================================================================
-- 4. 答题记录表 (answer_records) - 存储用户答题记录
-- ====================================================================
CREATE TABLE answer_records (
    timestamp_utc BOOLEAN NOT NULL DEFAULT TRUE,
    request_id VARCHAR(64) NULL,
    practice BOOLEAN NOT NULL DEFAULT FALSE,
    question_snapshot LONGTEXT NULL,
    image_ref VARCHAR(64) NULL COMMENT '内容寻址题目图片哈希；旧快照可为空',
    content_snapshot LONGTEXT NULL, options_snapshot LONGTEXT NULL, correct_snapshot VARCHAR(1) NULL, analysis_snapshot LONGTEXT NULL,difficulty_snapshot INT NULL,
    attempt_order BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    UNIQUE KEY uk_user_request(user_id,request_id),
    event_id BIGINT NULL COMMENT '教学任务ID，旧数据和自由练习可为空',
    INDEX idx_answer_records_event (event_id),
    INDEX idx_task_attempt (user_id,event_id,question_id,attempt_order),
    INDEX idx_event_summary (event_id,practice,user_id,question_id,correct,attempt_order),
    id VARCHAR(36) PRIMARY KEY COMMENT '记录ID（UUID）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    session_id VARCHAR(255) NOT NULL COMMENT '会话ID',
    selected_option VARCHAR(255) NOT NULL COMMENT '用户选择的答案',
    correct BOOLEAN COMMENT '是否正确',
    answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- 实体类定义的索引
    INDEX idx_question_answered (question_id, answered_at),
    INDEX idx_user_answered (user_id, answered_at),
    INDEX idx_session_answered (session_id, answered_at),
    INDEX idx_user_session_answered (user_id, session_id, answered_at),
    INDEX idx_user_question_answered (user_id, question_id, answered_at),
    INDEX idx_user_correct_answered (user_id, correct, answered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录表';

-- 4.1 题目图片内容寻址表 - 同一图片只保存一次
CREATE TABLE question_images (
    content_hash CHAR(64) PRIMARY KEY COMMENT '图片内容的 SHA-256',
    content LONGTEXT NOT NULL COMMENT '图片 Base64 内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变题目图片内容';

-- ====================================================================
-- 5. AI对话历史表 (conversation_history) - 存储AI对话记录
-- ====================================================================
CREATE TABLE conversation_history (
    event_id BIGINT NULL COMMENT '教学任务ID，旧数据和自由练习可为空',
    INDEX idx_conversation_history_event (event_id),
    INDEX idx_history_scope (user_id,session_id,conversation_type,question_id,event_id,is_active,sequence_number),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(255) NOT NULL COMMENT '会话ID',
    question TEXT COMMENT '用户问题',
    answer TEXT COMMENT 'AI回答',
    sequence_number INT COMMENT '对话序号',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否活跃',
    token_count INT DEFAULT 0 COMMENT 'token数量',
    conversation_type VARCHAR(255) NOT NULL COMMENT '会话类型',
    question_id BIGINT COMMENT '关联题目ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    
    -- 实体类定义的索引
    INDEX idx_session_active_created (session_id, is_active, created_at),
    INDEX idx_session_active_seq (session_id, is_active, sequence_number),
    INDEX idx_user_session_active_seq (user_id, session_id, is_active, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话历史记录表';

-- ====================================================================
-- 6. 教师事件表 (teacher_events) - 存储教师发布的学习事件
-- ====================================================================
CREATE TABLE teacher_events (
    timestamp_utc BOOLEAN NOT NULL DEFAULT TRUE,
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件 ID',
    teacher_id BIGINT NOT NULL COMMENT '关联教师 ID',
    teacher_name VARCHAR(100) NOT NULL COMMENT '教师姓名',
    event_title VARCHAR(20) NOT NULL COMMENT '事件标题',
    event_description VARCHAR(100) COMMENT '事件描述',
    event_timestamp DATETIME NOT NULL COMMENT '事件时间戳',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED/CLOSED/ARCHIVED',
    start_at DATETIME NULL COMMENT '允许开始作答时间',
    due_at DATETIME NULL COMMENT '截止时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_teacher_events_status (status),
    INDEX idx_teacher_events_status_start (status, start_at),
    INDEX idx_teacher_events_status_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师事件表';

-- ====================================================================
-- 7. 学生报告表 (student_reports) - 存储AI生成的学生个人学习报告
-- ====================================================================
CREATE TABLE student_reports (
    evidence_json TEXT NULL,
    event_id BIGINT NULL COMMENT '教学任务ID，旧数据和自由练习可为空',
    INDEX idx_student_reports_event (event_id),
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报告ID',
    user_id BIGINT NOT NULL COMMENT '学生用户ID',
    real_name VARCHAR(100) NOT NULL COMMENT '学生真实姓名',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    report_content TEXT NOT NULL COMMENT '报告内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    
    -- 实体类定义的索引
    INDEX idx_student_report_session_id (session_id),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生报告表';

-- ====================================================================
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

-- 与增量升级一致：学习数据引用的任务不能被物理删除。
ALTER TABLE answer_records ADD CONSTRAINT fk_answer_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);
ALTER TABLE conversation_history ADD CONSTRAINT fk_history_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);
ALTER TABLE student_reports ADD CONSTRAINT fk_report_event FOREIGN KEY (event_id) REFERENCES teacher_events(id);

CREATE TABLE task_recipients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    CONSTRAINT uk_task_recipient UNIQUE(event_id,user_id),
    CONSTRAINT fk_recipient_event FOREIGN KEY(event_id) REFERENCES teacher_events(id) ON DELETE RESTRICT,
    CONSTRAINT fk_recipient_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
