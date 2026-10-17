-- src/test/resources/sql/cleanup-all.sql
-- 完全清理脚本（清理所有表数据并重置序列）
-- 用于测试间的数据隔离和环境重置

SET MODE MySQL;

-- 禁用外键检查
SET REFERENTIAL_INTEGRITY FALSE;

-- 清理所有表的数据（按依赖关系倒序）
DELETE FROM teacher_event_questions;
DELETE FROM student_reports;
DELETE FROM teacher_events;
DELETE FROM conversation_history;
DELETE FROM answer_records;
DELETE FROM questions;
DELETE FROM videos;
DELETE FROM users;

-- 重置所有表的自增序列
ALTER TABLE users ALTER COLUMN id RESTART WITH 1;
ALTER TABLE videos ALTER COLUMN id RESTART WITH 1;
ALTER TABLE questions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE conversation_history ALTER COLUMN id RESTART WITH 1;
ALTER TABLE teacher_events ALTER COLUMN id RESTART WITH 1;
ALTER TABLE student_reports ALTER COLUMN id RESTART WITH 1;

-- 启用外键检查
SET REFERENTIAL_INTEGRITY TRUE;

SELECT '数据库已完全清理' as message;
