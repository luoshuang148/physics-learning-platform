-- ============================================================
-- 题目错误分析测试场景数据
-- ============================================================
-- 用途：QuestionErrorAnalysisControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含教师、学生用户数据
--   2. 包含视频和题目数据
--   3. 包含正确和错误的答题记录（用于错误统计）
--   4. 包含会话数据（用于测试多轮对话）
--   5. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- ============================================================
-- 1. 用户数据
-- ============================================================

-- 教师用户（用于访问错题分析功能）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (5001, 'teacher_error_analysis', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '错题分析测试教师', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户1（有错误答题记录）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (5002, 'student_error_01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '错题学生01', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户2（有错误答题记录）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (5003, 'student_error_02', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '错题学生02', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户3（只有正确答题记录）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (5004, 'student_correct_01', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '正确学生01', 'STUDENT', '三年二班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户（用于权限测试 - 学生不能访问教师功能）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (5005, 'student_no_access', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '无权限学生', 'STUDENT', '三年三班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 2. 视频数据（题目关联依赖）
-- ============================================================

-- 视频 1 - 力学基础
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (7001, 
        '力学基础概念', 
        '力学基础概念讲解', 
        'https://test.example.com/video/mechanics_basic.mp4', 
        'https://test.example.com/covers/mechanics_basic.jpg', 
        300, 
        '力学,牛顿定律,运动学', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 2 - 能量守恒
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (7002, 
        '能量守恒定律', 
        '能量守恒定律详细讲解', 
        'https://test.example.com/video/energy_conservation.mp4', 
        'https://test.example.com/covers/energy_conservation.jpg', 
        420, 
        '能量守恒,机械能,动能,势能', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 3 - 电路基础
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (7003, 
        '电路基础', 
        '电路基础知识讲解', 
        'https://test.example.com/video/circuit_basic.mp4', 
        'https://test.example.com/covers/circuit_basic.jpg', 
        380, 
        '电路,欧姆定律,电阻,电流', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- ============================================================
-- 3. 题目数据
-- ============================================================

-- 题目 1 - 高错误率题目（错误人次：3，错误率：75%）
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (6001, 
        '关于牛顿第一定律，下列说法正确的是（ ）',
        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        '{"A": "力是维持物体运动的原因", "B": "力是改变物体运动状态的原因", "C": "物体不受力时一定静止", "D": "物体运动必须有力作用"}',
        'B',
        '根据牛顿第一定律，力不是维持物体运动的原因，而是改变物体运动状态的原因。',
        2,
        '{"A": 7001, "B": 7001, "C": 7001, "D": 7001}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 2 - 中等错误率题目（错误人次：2，错误率：50%）
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (6002, 
        '质量为 2kg 的物体受到 10N 的水平力作用，求加速度 a。（ ）',
        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
        '{"A": "2 m/s²", "B": "5 m/s²", "C": "10 m/s²", "D": "20 m/s²"}',
        'B',
        '根据牛顿第二定律 F=ma，得 a=F/m=10/2=5 m/s²',
        3,
        '{"A": 7002, "B": 7002, "C": 7002, "D": 7002}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 3 - 无错误题目（错误人次：0，错误率：0%）
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (6003, 
        '关于欧姆定律，下列说法正确的是（ ）',
        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
        '{"A": "电流与电压成反比", "B": "电流与电阻成正比", "C": "电阻与电压成正比", "D": "电流与电压成正比，与电阻成反比"}',
        'D',
        '根据欧姆定律 I=U/R，电流与电压成正比，与电阻成反比。',
        1,
        '{"A": 7003, "B": 7003, "C": 7003, "D": 7003}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 4 - 低错误率题目（错误人次：1，错误率：25%）
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (6004, 
        '下列关于惯性的说法正确的是（ ）',
        'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
        '{"A": "速度越大，惯性越大", "B": "质量越大，惯性越大", "C": "受力越大，惯性越大", "D": "加速度越大，惯性越大"}',
        'B',
        '惯性是物体的固有属性，只与质量有关，质量越大惯性越大。',
        2,
        '{"A": 7001, "B": 7001, "C": 7001, "D": 7001}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- ============================================================
-- 4. 答题记录数据
-- ============================================================

-- 题目 1 的答题记录（3 个错误，1 个正确）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8001, 5002, 6001, '20260315000001_a1b2c3d4e5f67890', 'A', false, '认为不受力物体一定静止', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8002, 5003, 6001, '20260315000002_b2c3d4e5f6g78901', 'C', false, '认为不受力物体一定静止', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8003, 5002, 6001, '20260315000003_c3d4e5f6g7h89012', 'D', false, '认为运动必须有力作用', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8004, 5004, 6001, '20260315000004_d4e5f6g7h8i90123', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 题目 2 的答题记录（2 个错误，2 个正确）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8005, 5002, 6002, '20260315000005_e5f6g7h8i9j01234', 'A', false, '计算错误，用了 m=2, F=10, 但计算成 a=F/m=10/2=2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8006, 5003, 6002, '20260315000006_f6g7h8i9j0k12345', 'C', false, '忘记除以质量，直接用了力的大小', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8007, 5004, 6002, '20260315000007_g7h8i9j0k1l23456', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8008, 5005, 6002, '20260315000008_h8i9j0k1l2m34567', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 题目 3 的答题记录（全部正确，4 个正确）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8009, 5002, 6003, '20260315000009_i9j0k1l2m3n45678', 'D', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8010, 5003, 6003, '20260315000010_j0k1l2m3n4o56789', 'D', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8011, 5004, 6003, '20260315000011_k1l2m3n4o5p67890', 'D', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8012, 5005, 6003, '20260315000012_l2m3n4o5p6q78901', 'D', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 题目 4 的答题记录（1 个错误，3 个正确）
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8013, 5002, 6004, '20260315000013_m3n4o5p6q7r89012', 'A', false, '错误认为速度影响惯性', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8014, 5003, 6004, '20260315000014_n4o5p6q7r8s90123', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8015, 5004, 6004, '20260315000015_o5p6q7r8s9t01234', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at)
VALUES (8016, 5005, 6004, '20260315000016_p6q7r8s9t0u12345', 'B', true, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 5. 会话数据 (用于测试多轮对话)
-- ============================================================

-- 教师题目分析会话 (用于测试 SSE 流式响应) - 阿里 AI 模块
INSERT INTO conversation_history (id, user_id, session_id, question_id, conversation_type, question, answer, token_count, created_at, updated_at) VALUES (9001, 5001, 'teacher-question-analysis-session', 6001, 'TEACHER_QUESTION_ANALYSIS', '请分析这道题学生的错误原因', '学生主要错误原因是...', 150, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生实时问答会话 (用于测试智谱 AI 模块的会话管理)
INSERT INTO conversation_history (id, user_id, session_id, question_id, conversation_type, question, answer, token_count, is_active, created_at, updated_at) VALUES (9002, 5002, '20260315000002_b2c3d4e5f6a78901', 6001, 'REAL_TIME_QA', '牛顿第一定律是什么？', '牛顿第一定律指出：一切物体在没有受到外力作用的时候...', 120, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生错题分析会话 (用于测试会话隔离)
INSERT INTO conversation_history (id, user_id, session_id, question_id, conversation_type, question, answer, token_count, is_active, created_at, updated_at) VALUES (9003, 5003, '20260315000003_c3d4e5f6a7b89012', 6002, 'WRONG_QUESTION_ANALYSIS', '这道题为什么错了？', '你的错误在于...', 100, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 数据统计说明：
-- 
-- 题目1 (6001): 错误3次，正确1次，错误率75%
-- 题目2 (6002): 错误2次，正确2次，错误率50%  
-- 题目3 (6003): 错误0次，正确4次，错误率0%（不显示在统计中）
-- 题目4 (6004): 错误1次，正确3次，错误率25%
-- 
-- 预期统计排序：题目1(75%) -> 题目2(50%) -> 题目4(25%)
-- ============================================================