-- src/test/resources/sql/test-schema.sql
-- H2兼容的数据库schema脚本
SET MODE MySQL;

-- 禁用外键检查
SET REFERENTIAL_INTEGRITY FALSE;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    real_name VARCHAR(100) NOT NULL,
    class_name VARCHAR(50),
    active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_real_name UNIQUE (real_name)
);

-- 2. 视频表
-- 3. 题目表
CREATE TABLE IF NOT EXISTS questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    options LONGTEXT NOT NULL,
    correct_option VARCHAR(50) NOT NULL,
    explanation LONGTEXT,
    difficulty INT DEFAULT 1,
    image_base64 LONGTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_content_hash UNIQUE (content_hash)
);

-- 4. 答题记录表
CREATE TABLE IF NOT EXISTS answer_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    selected_option VARCHAR(255) NOT NULL,
    correct BOOLEAN,
    answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_answer_records_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_records_questions FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 5. AI对话历史表
CREATE TABLE IF NOT EXISTS conversation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    question TEXT,
    answer TEXT,
    sequence_number INT,
    is_active BOOLEAN DEFAULT TRUE,
    token_count INT DEFAULT 0,
    conversation_type VARCHAR(255) NOT NULL,
    question_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_conversation_history_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_history_questions FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
);

-- 6. 教师事件表
CREATE TABLE IF NOT EXISTS teacher_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    teacher_name VARCHAR(100) NOT NULL,
    event_title VARCHAR(20) NOT NULL,
    event_description VARCHAR(100),
    event_timestamp TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    start_at TIMESTAMP NULL,
    due_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_teacher_events_users FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 教学任务状态索引
CREATE INDEX idx_teacher_events_status ON teacher_events(status);
CREATE INDEX idx_teacher_events_status_start ON teacher_events(status, start_at);
CREATE INDEX idx_teacher_events_status_due ON teacher_events(status, due_at);

-- 7. 学生报告表
CREATE TABLE IF NOT EXISTS student_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    session_id VARCHAR(100) NOT NULL,
    report_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_student_reports_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_users_class_user_active ON users(class_name, user_type, active);
CREATE INDEX idx_questions_created_at ON questions(created_at);
CREATE INDEX idx_answer_records_question_answered ON answer_records(question_id, answered_at);
CREATE INDEX idx_answer_records_user_answered ON answer_records(user_id, answered_at);
CREATE INDEX idx_answer_records_session_answered ON answer_records(session_id, answered_at);
CREATE INDEX idx_answer_records_user_session_answered ON answer_records(user_id, session_id, answered_at);
CREATE INDEX idx_answer_records_user_question_answered ON answer_records(user_id, question_id, answered_at);
CREATE INDEX idx_answer_records_user_correct_answered ON answer_records(user_id, correct, answered_at);
CREATE INDEX idx_conversation_history_session_active_created ON conversation_history(session_id, is_active, created_at);
CREATE INDEX idx_conversation_history_session_active_seq ON conversation_history(session_id, is_active, sequence_number);
CREATE INDEX idx_conversation_history_user_session_active_seq ON conversation_history(user_id, session_id, is_active, sequence_number);
CREATE INDEX idx_student_reports_session_id ON student_reports(session_id);
CREATE INDEX idx_student_reports_user_created ON student_reports(user_id, created_at);
CREATE INDEX idx_student_reports_user_session ON student_reports(user_id, session_id);

-- 启用外键检查
SET REFERENTIAL_INTEGRITY TRUE;