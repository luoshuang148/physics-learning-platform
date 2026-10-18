-- ============================================================
-- 答题记录管理测试场景数据
-- ============================================================
-- 用途：AnswerRecordControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含用户数据（学生、教师）
--   2. 包含题目数据（答题记录关联依赖）
--   3. 包含答题记录数据（覆盖不同场景）
--   4. 包含会话数据（用于会话维度查询）
--   5. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- ============================================================
-- 1. 基础用户数据
-- ============================================================

-- 学生用户 1 - 主要测试用户
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1003, 'student_answer_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '答题测试学生', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户 2 - 用于权限测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1004, 'student_other_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '其他测试学生', 'STUDENT', '三年二班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 1 - 主要测试教师
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2003, 'teacher_answer_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '答题测试教师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 教师用户 2 - 用于权限对比测试
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2004, 'teacher_another_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '其他测试教师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 2. 视频数据（题目关联依赖）
-- ============================================================

-- 视频 1 - 牛顿第一定律
-- 视频 2 - 牛顿第二定律
-- ============================================================
-- 3. 题目数据
-- ============================================================

-- 题目 1 - 简单难度
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, image_base64, created_at, updated_at)
VALUES (4001, 
        '关于牛顿第一定律，下列说法正确的是（ ）',
        '0000000000000000000000000000000000000000000000000000000000000000',
        '{"A": "力是维持物体运动的原因", "B": "力是改变物体运动状态的原因", "C": "物体不受力时一定静止", "D": "物体运动必须有力作用"}',
        'B',
        '根据牛顿第一定律，力不是维持物体运动的原因，而是改变物体运动状态的原因。',
        1,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 2 - 中等难度
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, image_base64, created_at, updated_at)
VALUES (4002, 
        '下列关于惯性的说法正确的是（ ）',
        '0000000000000000000000000000000000000000000000000000000000000001',
        '{"A": "速度越大，惯性越大", "B": "质量越大，惯性越大", "C": "受力越大，惯性越大", "D": "加速度越大，惯性越大"}',
        'B',
        '惯性是物体保持原有运动状态的性质，质量是惯性大小的唯一量度。',
        2,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 3 - 困难难度
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, image_base64, created_at, updated_at)
VALUES (4003, 
        '一个质量为 2kg 的物体，在水平方向受到 10N 的恒力作用，其加速度为（ ）',
        '0000000000000000000000000000000000000000000000000000000000000002',
        '{"A": "2 m/s²", "B": "5 m/s²", "C": "10 m/s²", "D": "20 m/s²"}',
        'B',
        '根据牛顿第二定律 F=ma，得 a=F/m=10/2=5 m/s²。',
        3,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 4 - 极难难度
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, image_base64, created_at, updated_at)
VALUES (4004, 
        '关于牛顿第三定律，下列说法错误的是（ ）',
        '0000000000000000000000000000000000000000000000000000000000000003',
        '{"A": "作用力与反作用力大小相等", "B": "作用力与反作用力方向相反", "C": "作用力与反作用力作用在同一物体上", "D": "作用力与反作用力同时产生同时消失"}',
        'C',
        '作用力与反作用力作用在两个不同的物体上，这是牛顿第三定律的关键点。',
        4,
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- ============================================================
-- 4. 答题记录数据
-- ============================================================

-- 学生 1003 的答题记录 - 会话 session_001（2026-01-10）
-- 正确答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_001', 1003, 4001, 'session_001', 'B', TRUE, '2026-01-10 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 错误答题（概念理解错误）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_002', 1003, 4002, 'session_001', 'A', FALSE, '2026-01-10 10:05:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 正确答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_003', 1003, 4003, 'session_001', 'B', TRUE, '2026-01-10 10:10:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生 1003 的答题记录 - 会话 session_002（2026-01-11）
-- 正确答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_004', 1003, 4001, 'session_002', 'B', TRUE, '2026-01-11 14:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 错误答题（计算错误）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_005', 1003, 4003, 'session_002', 'A', FALSE, '2026-01-11 14:05:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 错误答题（公式记忆错误）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1003_006', 1003, 4004, 'session_002', 'D', FALSE, '2026-01-11 14:10:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生 1004 的答题记录 - 会话 session_003（2026-01-09）
-- 正确答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1004_001', 1004, 4001, 'session_003', 'B', TRUE, '2026-01-09 09:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 正确答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1004_002', 1004, 4002, 'session_003', 'B', TRUE, '2026-01-09 09:05:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 错误答题
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, answered_at, created_at, updated_at)
VALUES ('rec_stu1004_003', 1004, 4004, 'session_003', 'A', FALSE, '2026-01-09 09:10:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 数据说明：
-- 学生 1003：6 条记录（session_001: 3 条，session_002: 3 条），正确率 50%
-- 学生 1004：3 条记录（session_003: 3 条），正确率 67%
-- 总计：9 条答题记录
-- 时间跨度：2026-01-09 至 2026-01-11
-- ============================================================
