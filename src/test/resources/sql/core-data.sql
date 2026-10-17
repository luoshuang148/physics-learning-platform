-- src/test/resources/sql/core-data.sql
-- 核心基础数据脚本（仅包含管理员用户等必需数据）
-- 用于需要最少初始数据的集成测试场景

-- 清理现有数据
DELETE FROM answer_records;
DELETE FROM conversation_history;
DELETE FROM teacher_events;
DELETE FROM users;
DELETE FROM questions;
DELETE FROM videos;

-- 重置自增序列
ALTER TABLE videos ALTER COLUMN id RESTART WITH 1;
ALTER TABLE questions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;
ALTER TABLE conversation_history ALTER COLUMN id RESTART WITH 1;
ALTER TABLE teacher_events ALTER COLUMN id RESTART WITH 1;
ALTER TABLE student_reports ALTER COLUMN id RESTART WITH 1;

-- ====================================================================
-- 核心基础数据（仅必需的管理员和教师账户）
-- ====================================================================

-- 1. 核心用户数据 (仅管理员和教师)
INSERT INTO users (id, username, password_hash, user_type, real_name, class_name, active, last_login, created_at, updated_at) VALUES
(100, 'admin', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '系统管理员', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(101, 'teacher001', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '李老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(102, 'teacher002', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '王老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 显示核心数据统计信息
SELECT '核心基础数据初始化完成' as message;
SELECT 
    (SELECT COUNT(*) FROM users) as user_count,
    (SELECT COUNT(*) FROM videos) as video_count,
    (SELECT COUNT(*) FROM questions) as question_count;
