-- 实验管理测试场景基础数据
-- 用于 ExperimentControllerIntegrationTest 和 ExperimentRepositoryIntegrationTest

-- 清理旧数据（可选，根据测试框架自动回滚）
-- DELETE FROM experiments;
-- DELETE FROM questions;
-- DELETE FROM users;

-- ==================== 基础用户数据 ====================
-- 使用 NOT EXISTS 避免重复插入
-- 注意：User 实体没有 role 字段，只有 user_type
INSERT INTO users (id, username, password_hash, user_type, real_name, class_name, active, created_at, updated_at)
SELECT 2001, 'teacher_experiment', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'TEACHER', '测试教师', null, true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2001);

INSERT INTO users (id, username, password_hash, user_type, real_name, class_name, active, created_at, updated_at)
SELECT 2002, 'student_experiment', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'STUDENT', '测试学生', '高三 (1) 班', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 2002);

-- ==================== 基础题目数据 ====================
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, created_at, updated_at)
SELECT 3001, '牛顿第一定律题目 - 下列关于惯性的说法正确的是（ ）', 'hash_3001', '{"A":"速度越大惯性越大","B":"质量越大惯性越大","C":"受力越大惯性越大","D":"加速度越大惯性越大"}', 'B', '惯性只与质量有关，质量越大惯性越大', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = 3001);

INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, created_at, updated_at)
SELECT 3002, '牛顿第二定律题目 - 关于 F=ma，下列说法正确的是（ ）', 'hash_3002', '{"A":"F 与 a 成正比","B":"m 与 a 成反比","C":"F 是合外力","D":"以上都正确"}', 'D', '根据牛顿第二定律 F=ma，F 是合外力，F 与 a 成正比，m 与 a 成反比', 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = 3002);

INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, created_at, updated_at)
SELECT 3003, '自由落体题目 - 关于自由落体运动，下列说法正确的是（ ）', 'hash_3003', '{"A":"初速度为零","B":"加速度为 g","C":"是匀加速直线运动","D":"以上都正确"}', 'D', '自由落体运动是初速度为零、加速度为 g 的匀加速直线运动', 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM questions WHERE id = 3003);

-- ==================== 基础实验数据 ====================
-- 只插入最基本的测试数据，边界值测试在测试类中动态创建
INSERT INTO experiments (id, name, question_ids, description, order_number, created_at, updated_at)
SELECT 9001, '正常实验', '[3001,3002]', '这是一个正常的实验配置描述', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM experiments WHERE id = 9001);

-- 注意：最大长度名称测试（255 字符）在测试类中动态创建，避免 SQL 脚本问题

-- 注意：
-- 1. 密码哈希示例：BCrypt('$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy') = "password"
-- 2. 测试时会自动回滚，无需手动清理
-- 3. 边界值测试数据在测试类中动态创建
