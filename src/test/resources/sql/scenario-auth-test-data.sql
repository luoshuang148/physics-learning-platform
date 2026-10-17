-- ============================================================
-- 认证测试场景数据
-- ============================================================
-- 用途：AuthControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含各种状态的测试用户（正常、禁用、不同角色）
--   2. 所有密码均为 BCrypt 加密后的哈希值
--   3. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- 插入测试用户数据
-- 密码都是 "password123" 的 BCrypt 哈希值

-- 学生用户 1（正常状态）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1001, 'student_test01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test Student 01', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户 2（正常状态）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1002, 'student_test02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test Student 02', 'STUDENT', '三年二班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 1（正常状态）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2001, 'teacher_test01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test Teacher 01', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 2（正常状态）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2002, 'teacher_test02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Test Teacher 02', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 禁用账户用户（用于测试登录失败场景）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (9999, 'locked_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Locked User', 'STUDENT', '三年一班', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 注释：密码说明
-- 所有测试用户的密码都是 "password123"
-- BCrypt 哈希值：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- 这样可以确保密码验证逻辑正确工作
