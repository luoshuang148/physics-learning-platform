-- src/test/resources/sql/scenario-teaching.sql
-- 教学场景数据脚本（完整的教学相关测试数据）
-- 用于需要完整教学场景的集成测试

-- 在 core-data 基础上追加数据（假设已执行 core-data.sql）

-- ====================================================================
-- 教学场景数据：视频、题目、学生用户、答题记录
-- ====================================================================

-- 1. 视频测试数据 (5 个典型视频)
INSERT INTO videos (id, title, url, knowledge_point, duration, description, question_id, created_at, updated_at) VALUES
(1, '抛体运动基础', 'https://example.com/video1.mp4', '抛体运动', 320, '抛体运动的定义、分类及基本分析方法', 1, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(2, '平抛运动的分解', 'https://example.com/video2.mp4', '抛体运动', 300, '将平抛运动分解为水平匀速和竖直自由落体', 2, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(3, '斜面约束下的抛体运动', 'https://example.com/video3.mp4', '斜面约束', 340, '物体在斜面上方抛出，受到斜面几何约束的运动特点', 3, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(4, '沿斜面方向的运动分解', 'https://example.com/video4.mp4', '分解方法', 280, '将重力加速度沿斜面方向和垂直斜面方向分解', 4, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(5, '垂直斜面方向的运动分解', 'https://example.com/video5.mp4', '分解方法', 290, '垂直斜面方向的运动独立分析，用于求最大距离', 5, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 2. 题目测试数据 (5 道典型题目)
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at) VALUES
(1, '在倾角为$\\theta$的斜面上，将一小球以水平初速度$v_0$抛出，小球最终落在斜面上。忽略空气阻力，则小球从抛出到落在斜面上的时间$t$为？',
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    '{"A": "$t=\\frac{2v_0\\tan\\theta}{g}$", "B": "$t=\\frac{v_0\\tan\\theta}{g}$", "C": "$t=\\frac{2v_0}{g\\tan\\theta}$", "D": "$t=\\frac{v_0}{g\\tan\\theta}$"}',
    'A', '设水平位移$x=v_0t$，竖直位移$y=\\frac{1}{2}gt^2$，落在斜面时有$\\frac{y}{x}=\\tan\\theta$，即$\\frac{gt}{2v_0}=\\tan\\theta$，解得$t=\\frac{2v_0\\tan\\theta}{g}$',
    3, '{"A": "平抛位移分解", "B": "斜面约束", "C": "时间求解", "D": "代数运算"}', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

(2, '在倾角为$\\theta$的光滑斜面上，将一小球以初速度$v_0$沿斜面向上抛出（小球仅受重力，斜面支持力），则小球沿斜面向上运动的最大距离$s$为？',
    'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
    '{"A": "$s=\\frac{v_0^2}{2g\\sin\\theta}$", "B": "$s=\\frac{v_0^2}{g\\sin\\theta}$", "C": "$s=\\frac{v_0^2\\sin\\theta}{2g}$", "D": "$s=\\frac{v_0^2}{2g\\cos\\theta}$"}',
    'A', '沿斜面方向加速度$a=g\\sin\\theta$，由运动学公式$v_0^2=2as$得$s=\\frac{v_0^2}{2g\\sin\\theta}$',
    2, '{"A": "斜面分解", "B": "匀变速公式", "C": "加速度分析", "D": "最大值问题"}', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

(3, '从倾角为$\\theta$的斜面上某点以水平初速度$v_0$抛出一个小球，小球落在斜面上时速度方向与水平方向的夹角为$\\alpha$，则$\\tan\\alpha$与$\\tan\\theta$的关系是？',
    'b3a8e0e1f9ab1bfe3a36f231f676f78bb30a519d2b21e6c530c0eee8ebb4a5d0',
    '{"A": "$\\tan\\alpha = 2\\tan\\theta$", "B": "$\\tan\\alpha = \\tan\\theta$", "C": "$\\tan\\alpha = \\frac{1}{2}\\tan\\theta$", "D": "$\\tan\\alpha = \\frac{2}{\\tan\\theta}$"}',
    'A', '平抛落在斜面上时，位移偏角$\\theta$满足$\\tan\\theta = \\frac{y}{x} = \\frac{gt}{2v_0}$，速度偏角$\\alpha$满足$\\tan\\alpha = \\frac{v_y}{v_0} = \\frac{gt}{v_0}$，故$\\tan\\alpha = 2\\tan\\theta$',
    3, '{"A": "速度偏角", "B": "位移偏角", "C": "关系推导", "D": "角度比较"}', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

(4, '如图所示，在倾角为$\\theta$的斜面上，将一小球以水平初速度$v_0$抛出，空气阻力不计。则小球在运动过程中距离斜面的最大距离$d$为？',
    '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
    '{"A": "$d=\\frac{v_0^2\\sin^2\\theta}{2g\\cos\\theta}$", "B": "$d=\\frac{v_0^2\\sin\\theta}{2g}$", "C": "$d=\\frac{v_0^2\\cos^2\\theta}{2g\\sin\\theta}$", "D": "$d=\\frac{v_0^2}{2g}\\sin\\theta$"}',
    'A', '将运动分解为垂直斜面方向：初速度$v_{0\\perp}=v_0\\sin\\theta$（垂直斜面向上），加速度$a_\\perp=g\\cos\\theta$（垂直斜面向下）。当垂直斜面方向速度减为零时距离最大，$d=\\frac{v_{0\\perp}^2}{2a_\\perp}=\\frac{v_0^2\\sin^2\\theta}{2g\\cos\\theta}$',
    4, '{"A": "垂直斜面分解", "B": "最大距离条件", "C": "运动合成", "D": "代数化简"}', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

(5, '在倾角为$\\theta$的斜面上，以初速度$v_0$沿与斜面成$\\beta$角的方向抛出一个小球（$\\beta$为初速度与斜面的夹角），忽略空气阻力。小球在斜面上方运动的过程中，沿斜面方向的运动是？',
    '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8',
    '{"A": "匀速直线运动", "B": "匀加速直线运动", "C": "匀减速直线运动", "D": "变加速直线运动"}',
    'B', '将重力加速度分解到沿斜面方向，大小为$g\\sin\\theta$，方向沿斜面向下。沿斜面方向初速度为$v_0\\cos\\beta$，加速度恒定，故为匀加速直线运动（若$v_0\\cos\\beta$方向沿斜面向下则为匀加速，若向上则为匀减速，但加速度大小方向均不变，本质上是匀变速直线运动）',
    2, '{"A": "力的分解", "B": "运动分解", "C": "加速度分析", "D": "运动类型判断"}', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 3. 学生用户测试数据 (5 个学生)
INSERT INTO users (id, username, password_hash, user_type, real_name, class_name, active, last_login, created_at, updated_at) VALUES
(110, 'student001', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '张三', '高三 (1) 班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(111, 'student002', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '李四', '高三 (1) 班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(112, 'student003', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '王五', '高三 (2) 班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(113, 'student004', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '赵六', '高三 (2) 班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(114, 'student005', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '钱七', '高三 (3) 班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 4. 答题记录测试数据 (每个学生 3 条记录，共 15 条)
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at) VALUES
('rec_110_1', 110, 1, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_110_2', 110, 2, 'session_teaching_test', 'B', FALSE, '将加速度误认为 g，忽略了 sinθ因子', DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_110_3', 110, 3, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_111_1', 111, 1, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_111_2', 111, 2, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_111_3', 111, 4, 'session_teaching_test', 'C', FALSE, '混淆了垂直斜面和平行斜面的分解', DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_112_1', 112, 1, 'session_teaching_test', 'C', FALSE, '时间公式记反，应为 2v₀tanθ/g', DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_112_2', 112, 3, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_112_3', 112, 5, 'session_teaching_test', 'B', TRUE, NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_113_1', 113, 2, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_113_2', 113, 4, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_113_3', 113, 5, 'session_teaching_test', 'D', FALSE, '未正确分解速度到垂直斜面方向', DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_114_1', 114, 1, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_114_2', 114, 3, 'session_teaching_test', 'B', FALSE, '误认为 tanα=tanθ', DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_114_3', 114, 4, 'session_teaching_test', 'A', TRUE, NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 显示教学场景数据统计信息
SELECT '教学场景数据初始化完成' as message;
SELECT 
    (SELECT COUNT(*) FROM videos) as video_count,
    (SELECT COUNT(*) FROM questions) as question_count,
    (SELECT COUNT(*) FROM users WHERE user_type = 'STUDENT') as student_count,
    (SELECT COUNT(*) FROM answer_records) as answer_record_count;
