-- ============================================================
-- 教师事件管理测试场景数据
-- ============================================================
-- 用途：TeacherEventControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含教师和学生用户（用于权限验证）
--   2. 一条全局事件数据
--   3. 所有密码均为 BCrypt 加密后的哈希值
--   4. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- ============================================================
-- 用户数据
-- ============================================================
-- 密码都是 "password123" 的 BCrypt 哈希值
-- BCrypt 哈希值：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- 学生用户 1（正常状态）- 用于查询权限测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1001, 'student_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '学生用户', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 1（正常状态）- 用于发布/删除事件测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2001, 'teacher_user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '教师用户', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 2（无事件）- 用于发布事件测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2002, 'teacher_no_event', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '无事件教师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 教师事件数据
-- ============================================================

-- 创建一条全局事件（用于查询测试）
INSERT INTO teacher_events (id, teacher_id, teacher_name, event_title, event_description, event_timestamp, status, created_at, updated_at)
VALUES (1, 2001, '教师用户', '新学期开始', '2026 年春季学期正式开始', '2026-03-01 08:00:00', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 测试数据说明
-- ============================================================
-- 学生用户：
--   - 1001: student_user / 学生用户 / 三年一班 / 激活
--
-- 教师用户：
--   - 2001: teacher_user / 教师用户 / null / 激活
--   - 2002: teacher_no_event / 无事件教师 / null / 激活
--
-- 教师事件：
--   - 事件 ID 1: 已发布任务 / 教师 2001 / 新学期开始 / 2026-03-01 08:00:00 / status=PUBLISHED
--
-- 所有用户密码：password123
-- ============================================================
