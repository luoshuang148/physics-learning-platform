-- ============================================================
-- 题目管理测试场景数据
-- ============================================================
-- 用途：QuestionControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含视频数据（题目关联依赖）
--   2. 包含初始题目数据
--   3. 包含不同角色的用户（教师、学生）
--   4. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- ============================================================
-- 1. 基础用户数据
-- ============================================================

-- 教师用户（用于创建和管理题目）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2003, 'teacher_question_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Question Test Teacher', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户（用于查询题目和权限测试）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1003, 'student_question_test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Question Test Student', 'STUDENT', '三年三班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 2. 视频数据（题目关联依赖）
-- ============================================================

-- 视频 1 - 牛顿第一定律
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (3001, 
        'Newton First Law Explanation', 
        'Detailed introduction to Newton first law concepts and applications', 
        'https://test.example.com/video/newton_first.mp4', 
        'https://test.example.com/covers/newton_first.jpg', 
        300, 
        'Newton First Law,Inertia Law', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 2 - 牛顿第二定律
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (3002, 
        'Newton Second Law Explanation', 
        'In-depth analysis of F=ma formula and its applications', 
        'https://test.example.com/video/newton_second.mp4', 
        'https://test.example.com/covers/newton_second.jpg', 
        420, 
        'Newton Second Law,Acceleration,Force', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 3 - 能量守恒定律
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, created_at, updated_at)
VALUES (3003, 
        'Energy Conservation Law', 
        'Complete explanation of mechanical energy conservation law', 
        'https://test.example.com/video/energy_conservation.mp4', 
        'https://test.example.com/covers/energy_conservation.jpg', 
        380, 
        'Energy Conservation,Mechanical Energy,Kinetic Energy,Potential Energy', 
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- ============================================================
-- 3. 题目数据
-- ============================================================

-- 题目 1 - 关联视频 1（牛顿第一定律相关）
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (4001, 
        'Regarding Newton''s first law, which of the following statements is correct? ( )',
        '0000000000000000000000000000000000000000000000000000000000000000',
        '{"A": "Force is the cause of maintaining object motion", "B": "Force is the cause of changing object motion state", "C": "Object without force must be stationary", "D": "Object motion must have force acting"}',
        'B',
        'According to Newton''s first law, force is not the cause of maintaining object motion, but the cause of changing object motion state. When no force acts, an object can maintain uniform linear motion.',
        2,
        '{"A": 3001, "B": 3001, "C": 3001, "D": 3001}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 2 - 关联视频 2(牛顿第二定律计算题)
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (4002, 
        'An object with mass 2 kg is acted upon by a horizontal force of 10 N, find acceleration a. ( )',
        '0000000000000000000000000000000000000000000000000000000000000001',
        '{"A": "2 m/s^2", "B": "5 m/s^2", "C": "10 m/s^2", "D": "20 m/s^2"}',
        'B',
        'According to Newton''s second law F=ma, we get a=F/m=10/2=5 m/s^2',
        3,
        '{"A": 3002, "B": 3002, "C": 3002, "D": 3002}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

-- 题目 3 - 关联视频 3(能量守恒，带图片)
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at)
VALUES (4003, 
        'As shown in the figure, a ball with mass m falls freely from height h, ignoring air resistance, then the speed of the ball when it lands is ( )',
        '0000000000000000000000000000000000000000000000000000000000000002',
        '{"A": "sqrt(gh)", "B": "sqrt(2gh)", "C": "2*sqrt(gh)", "D": "2gh"}',
        'B',
        'According to mechanical energy conservation law: mgh=1/2*mv^2, solve for v=sqrt(2gh)',
        4,
        '{"A": 3003, "B": 3003, "C": 3003, "D": 3003}',
        NULL,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);
