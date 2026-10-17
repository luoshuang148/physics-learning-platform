-- ============================================================
-- 视频管理测试场景数据
-- ============================================================
-- 用途：VideoControllerIntegrationTest 专用测试数据
-- 说明：
--   1. 包含不同角色的用户（教师、学生）
--   2. 包含初始视频数据（用于CRUD操作测试）
--   3. 数据格式与Video 实体结构一致
--   4. 标题和 URL 全局唯一，符合数据库约束
--   5. 测试执行前会自动加载，测试后通过事务回滚清理
-- ============================================================

-- ============================================================
-- 1. 基础用户数据
-- ============================================================

-- 教师用户（用于创建、更新、删除视频）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (2001, 'video_test_teacher', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Video Test Teacher', 'TEACHER', NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 学生用户（用于查询视频和权限测试）
INSERT INTO users (id, username, password_hash, real_name, user_type, class_name, active, last_login, created_at, updated_at)
VALUES (1001, 'video_test_student', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Video Test Student', 'STUDENT', '三年一班', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============================================================
-- 2. 初始视频数据（用于 CRUD 操作测试）
-- ============================================================

-- 视频 1 - 牛顿第一定律详解（关联题目 6001）
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, question_id, created_at, updated_at)
VALUES (5001, 
        'Newton First Law Detailed Explanation', 
        'Comprehensive introduction to Newton first law concepts, inertia principle and real-world applications', 
        'https://test.example.com/video/newton_first_detailed.mp4', 
        'https://test.example.com/covers/newton_first_detailed.jpg', 
        420, 
        'Newton First Law,Inertia Law,Force and Motion', 
        6001,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 2 - 牛顿第二定律公式与应用（关联题目 6002）
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, question_id, created_at, updated_at)
VALUES (5002, 
        'Newton Second Law Formula and Applications', 
        'In-depth analysis of F=ma formula, problem-solving strategies and practical examples', 
        'https://test.example.com/video/newton_second_formula.mp4', 
        'https://test.example.com/covers/newton_second_formula.jpg', 
        540, 
        'Newton Second Law,Acceleration,Force,Mass,F=ma', 
        6002,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 3 - 能量守恒定律基础（无关联题目）
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, question_id, created_at, updated_at)
VALUES (5003, 
        'Energy Conservation Law Fundamentals', 
        'Complete explanation of mechanical energy conservation law with demonstrations', 
        'https://test.example.com/video/energy_conservation_basic.mp4', 
        'https://test.example.com/covers/energy_conservation_basic.jpg', 
        380, 
        'Energy Conservation,Mechanical Energy,Kinetic Energy,Potential Energy', 
        NULL,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 4 - 动量定理讲解（无关联题目）
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, question_id, created_at, updated_at)
VALUES (5004, 
        'Momentum Theorem Explanation', 
        'Detailed explanation of momentum theorem and collision problems', 
        'https://test.example.com/video/momentum_theorem.mp4', 
        'https://test.example.com/covers/momentum_theorem.jpg', 
        460, 
        'Momentum Theorem,Impulse,Collision,Conservation of Momentum', 
        NULL,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 视频 5 - 圆周运动入门（无关联题目）
INSERT INTO videos (id, title, description, url, cover_url, duration, knowledge_point, question_id, created_at, updated_at)
VALUES (5005, 
        'Introduction to Circular Motion', 
        'Basic concepts of circular motion, centripetal force and acceleration', 
        'https://test.example.com/video/circular_motion_intro.mp4', 
        'https://test.example.com/covers/circular_motion_intro.jpg', 
        400, 
        'Circular Motion,Centripetal Force,Angular Velocity,Acceleration', 
        NULL,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- ============================================================
-- 3. 题目数据（用于视频关联）
-- ============================================================

-- 题目 1 - 被视频 5001 关联
INSERT INTO questions (id, content, content_hash, options, correct_option, difficulty, created_at, updated_at)
VALUES (6001, 
        '{"questionText": "关于牛顿第一定律，以下说法正确的是？", "options": {"A": "力是维持物体运动的原因", "B": "力是改变物体运动状态的原因", "C": "物体不受力时会慢慢停下来", "D": "静止的物体一定不受力"}}',
        'hash_newton_first_law_q1',
        '{"A": "力是维持物体运动的原因", "B": "力是改变物体运动状态的原因", "C": "物体不受力时会慢慢停下来", "D": "静止的物体一定不受力"}',
        'B',
        2,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);

-- 题目 2 - 被视频 5002 关联
INSERT INTO questions (id, content, content_hash, options, correct_option, difficulty, created_at, updated_at)
VALUES (6002, 
        '{"questionText": "根据牛顿第二定律 F=ma，当力不变时，质量增大，加速度会？", "options": {"A": "增大", "B": "减小", "C": "不变", "D": "无法确定"}}',
        'hash_newton_second_law_q1',
        '{"A": "增大", "B": "减小", "C": "不变", "D": "无法确定"}',
        'B',
        2,
        CURRENT_TIMESTAMP, 
        CURRENT_TIMESTAMP);
