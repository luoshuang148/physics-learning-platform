-- ============================================================
-- 用户管理测试场景数据
-- ============================================================
-- 用途：UserControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含各种状态的测试用户（正常、禁用、不同角色）
--   2. 所有密码均为 BCrypt 加密后的哈希值
--   3. 测试执行前会自动加载，测试后通过事务回滚清理
--   4. 覆盖用户自我管理、教师管理、权限控制等测试场景
-- ============================================================

-- 插入测试用户数据
-- 密码都是 "password123" 的 BCrypt 哈希值
-- BCrypt 哈希值：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

-- ============================================================
-- 学生用户数据
-- ============================================================

-- 学生用户 1（正常状态）- 用于自我操作测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1001, 'student_self_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户 2（正常状态）- 用于普通用户测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1002, 'student_normal', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 'STUDENT', '三年二班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户 3（禁用状态）- 用于禁用账户测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1003, 'student_inactive', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '王五', 'STUDENT', '三年三班', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 教师用户数据
-- ============================================================

-- 教师用户 1（正常状态）- 用于教师管理员测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2001, 'teacher_admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '赵老师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 2（正常状态）- 用于其他教师测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2002, 'teacher_another', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '钱老师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 3（禁用状态）- 用于禁用教师账户测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2003, 'teacher_inactive', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '孙老师', 'TEACHER', NULL, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 测试数据说明
-- ============================================================
-- 学生用户：
--   - 1001: student_self_test / 张三 / 三年一班 / 激活
--   - 1002: student_normal / 李四 / 三年二班 / 激活
--   - 1003: student_inactive / 王五 / 三年三班 / 禁用
--
-- 教师用户：
--   - 2001: teacher_admin / 赵老师 / null / 激活
--   - 2002: teacher_another / 钱老师 / null / 激活
--   - 2003: teacher_inactive / 孙老师 / null / 禁用
--
-- 所有用户密码：password123
-- ============================================================
