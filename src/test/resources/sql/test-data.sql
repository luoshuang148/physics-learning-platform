-- src/test/resources/sql/test-data.sql
-- H2兼容的测试数据脚本

-- 清理现有数据
DELETE FROM answer_records;
DELETE FROM conversation_history;
DELETE FROM teacher_events;
DELETE FROM users;
DELETE FROM questions;
DELETE FROM videos;

-- 重置自增序列（H2语法）
ALTER TABLE videos ALTER COLUMN id RESTART WITH 1;
ALTER TABLE questions ALTER COLUMN id RESTART WITH 1;
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;
ALTER TABLE conversation_history ALTER COLUMN id RESTART WITH 1;
ALTER TABLE teacher_events ALTER COLUMN id RESTART WITH 1;
ALTER TABLE student_reports ALTER COLUMN id RESTART WITH 1;

-- ====================================================================
-- 插入测试数据（使用现有脚本的核心数据部分，移除MySQL特定语法）
-- ====================================================================

-- 1. 视频测试数据 (15条)
INSERT INTO videos (id, title, url, knowledge_point, duration, description, question_id, created_at, updated_at) VALUES
(1,  '抛体运动基础',                'https://example.com/video1.mp4',  '抛体运动', 320, '抛体运动的定义、分类及基本分析方法', 1,  CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(2,  '平抛运动的分解',              'https://example.com/video2.mp4',  '抛体运动', 300, '将平抛运动分解为水平匀速和竖直自由落体', 2, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(3,  '斜面约束下的抛体运动',        'https://example.com/video3.mp4',  '斜面约束', 340, '物体在斜面上方抛出，受到斜面几何约束的运动特点', 3, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(4,  '沿斜面方向的运动分解',        'https://example.com/video4.mp4',  '分解方法', 280, '将重力加速度沿斜面方向和垂直斜面方向分解', 4, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(5,  '垂直斜面方向的运动分解',      'https://example.com/video5.mp4',  '分解方法', 290, '垂直斜面方向的运动独立分析，用于求最大距离', 5, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(6,  '位移偏角与速度偏角的关系',    'https://example.com/video6.mp4',  '抛体运动', 310, '平抛运动中位移与速度方向角的关系推导', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(7,  '斜面上平抛运动的时间计算',    'https://example.com/video7.mp4',  '斜面约束', 330, '利用位移分解求物体落在斜面上的时间', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(8,  '斜面上平抛运动的最大距离',    'https://example.com/video8.mp4',  '斜面约束', 350, '垂直斜面方向运动分析求离开斜面的最远距离', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(9,  '斜面抛体运动的综合应用',      'https://example.com/video9.mp4',  '综合问题', 360, '结合斜面约束的复杂抛体问题解题策略', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(10, '类平抛运动与斜面',            'https://example.com/video10.mp4', '抛体运动', 300, '在恒定合力下类似平抛的运动处理方法', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(11, '利用分解法解决斜面问题',      'https://example.com/video11.mp4', '分解方法', 320, '通过合理分解将曲线运动转化为直线运动', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(12, '抛体运动实验演示',            'https://example.com/video12.mp4', '实验', 280, '实际抛体运动实验录像与数据分析', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(13, '斜面约束的数学表达',          'https://example.com/video13.mp4', '数学工具', 260, '用几何和代数描述斜面约束条件', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(14, '抛体运动在生活中的应用',      'https://example.com/video14.mp4', '应用', 290, '体育、工程中的抛体运动实例', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(15, '抛体运动复习专题',            'https://example.com/video15.mp4', '复习', 400, '抛体运动知识点梳理与典型例题', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 2. 题目测试数据 (5 条)
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

-- 3. 用户测试数据 (15条)
INSERT INTO users (id, username, password_hash, user_type, real_name, class_name, active, last_login, created_at, updated_at) VALUES
(100, 'student001', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '张三', '高三(1)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(101, 'student002', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '李四', '高三(1)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(102, 'student003', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '王五', '高三(2)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(103, 'student004', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '赵六', '高三(2)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(104, 'student005', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '钱七', '高三(3)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(105, 'student006', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '孙八', '高三(3)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(106, 'student007', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '周九', '高二(1)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(107, 'student008', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '吴十', '高二(1)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(108, 'student009', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '郑一', '高二(2)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(109, 'student010', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '王二', '高二(2)班', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(110, 'teacher001', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '李老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(111, 'teacher002', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '陈老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(112, 'teacher003', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '刘老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(113, 'teacher004', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '杨老师', NULL, TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(114, 'researcher001', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '科研人员', '研究组', TRUE, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 4. 答题记录测试数据 (30条)
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at) VALUES
('rec_100_1', 100, 1, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_100_2', 100, 2, 'session_test', 'B', FALSE, '将加速度误认为g，忽略了sinθ因子', DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_100_3', 100, 3, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_101_1', 101, 1, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_101_2', 101, 2, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_101_3', 101, 4, 'session_test', 'C', FALSE, '混淆了垂直斜面和平行斜面的分解', DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_102_1', 102, 1, 'session_test', 'C', FALSE, '时间公式记反，应为2v₀tanθ/g', DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_102_2', 102, 3, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_102_3', 102, 5, 'session_test', 'B', TRUE, NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_103_1', 103, 2, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_103_2', 103, 4, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_103_3', 103, 5, 'session_test', 'D', FALSE, '未正确分解速度到垂直斜面方向', DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_104_1', 104, 1, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_104_2', 104, 3, 'session_test', 'B', FALSE, '误认为tanα=tanθ', DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_104_3', 104, 4, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_105_1', 105, 2, 'session_test', 'C', FALSE, '错用cosθ代替sinθ', DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_105_2', 105, 3, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_105_3', 105, 5, 'session_test', 'B', TRUE, NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_106_1', 106, 1, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -7, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_106_2', 106, 4, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -7, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_106_3', 106, 5, 'session_test', 'A', FALSE, '误将最大距离公式记错', DATEADD('DAY', -7, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_107_1', 107, 2, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -8, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_107_2', 107, 3, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -8, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_107_3', 107, 4, 'session_test', 'D', FALSE, '垂直斜面方向加速度分析错误', DATEADD('DAY', -8, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_108_1', 108, 1, 'session_test', 'B', FALSE, '忘记乘以2', DATEADD('DAY', -9, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_108_2', 108, 2, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -9, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_108_3', 108, 5, 'session_test', 'B', TRUE, NULL, DATEADD('DAY', -9, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

('rec_109_1', 109, 3, 'session_test', 'A', TRUE, NULL, DATEADD('DAY', -10, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_109_2', 109, 4, 'session_test', 'C', FALSE, '将sinθ与cosθ位置颠倒', DATEADD('DAY', -10, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
('rec_109_3', 109, 5, 'session_test', 'B', TRUE, NULL, DATEADD('DAY', -10, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 5. AI对话历史测试数据 (50条) - 使用静态数据替代存储过程
INSERT INTO conversation_history (user_id, session_id, question, answer, sequence_number, is_active, token_count, conversation_type, question_id, created_at, updated_at) VALUES
-- 基础对话记录
(100, 'chat_session_001', '什么是牛顿第一定律？', '牛顿第一定律也称为惯性定律，表述为：物体在不受外力作用时，保持静止状态或匀速直线运动状态。这一定律揭示了物体具有惯性的特性。', 1, TRUE, 45, 'REAL_TIME_QA', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(100, 'chat_session_001', '能举个生活中的例子吗？', '当然可以。比如汽车突然急刹车时，车内的乘客会向前倾倒，这就是因为乘客的身体由于惯性要保持原来的运动状态。还有抖掉衣服上的灰尘，也是利用了灰尘的惯性。', 2, TRUE, 67, 'REAL_TIME_QA', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(100, 'chat_session_002', '动量守恒定律的适用条件是什么？', '动量守恒定律的适用条件包括：1) 系统不受外力作用；2) 系统所受外力矢量和为零；3) 系统内力远大于外力。在这些条件下，系统的总动量保持不变。', 1, TRUE, 52, 'REAL_TIME_QA', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(100, 'chat_session_002', '火箭推进为什么能用动量守恒解释？', '火箭推进正是动量守恒的典型应用。火箭向后高速喷射燃气，获得向后的动量，根据动量守恒定律，火箭本身就会获得向前的动量，从而实现推进。', 2, TRUE, 58, 'REAL_TIME_QA', NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),

-- 自动生成的对话记录 (46条)
(100, 'chat_session_001', '这个公式的物理意义是什么？', '这个公式描述了物理量之间的定量关系，反映了自然界的基本规律。', 1, TRUE, 35, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(101, 'chat_session_002', '如何解决这类计算题？', '解答此类题目需要先分析物理过程，选择合适的公式，注意单位换算。', 2, TRUE, 42, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(102, 'chat_session_003', '实验现象怎么解释？', '这个现象可以通过相关的物理原理解释，关键是要找到现象背后的物理机制。', 1, TRUE, 38, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(103, 'chat_session_004', '这两个概念有什么区别？', '虽然这两个概念看起来相似，但在定义、适用范围和物理意义上都有重要区别。', 3, TRUE, 45, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(104, 'chat_session_005', '日常生活中的应用有哪些？', '这一物理原理在现代科技和日常生活中有很多重要应用，比如...', 2, TRUE, 50, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(105, 'chat_session_006', '学习这个知识点要注意什么？', '学习时要重点理解概念的本质，多做练习巩固，注意与其他知识点的联系。', 1, TRUE, 41, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(106, 'chat_session_007', '考试中容易出现哪些错误？', '常见错误包括概念混淆、公式记错、单位换算失误等，需要特别注意。', 2, TRUE, 39, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(107, 'chat_session_008', '能推荐一些参考资料吗？', '建议参考教材相关章节，配合习题册练习，还可以查阅相关的科普读物。', 1, TRUE, 43, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(108, 'chat_session_009', '这个公式的物理意义是什么？', '这个公式描述了物理量之间的定量关系，反映了客观世界的运行规律。', 2, TRUE, 37, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(109, 'chat_session_010', '如何解决这类计算题？', '解决计算题的关键在于正确理解题意，选用适当的物理公式，仔细进行数值计算。', 1, TRUE, 44, 'REAL_TIME_QA', NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(100, 'chat_session_011', '实验现象怎么解释？', '物理实验现象往往体现了某种物理规律的作用，需要结合理论知识进行深入分析。', 3, TRUE, 40, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(101, 'chat_session_012', '这两个概念有什么区别？', '它们的主要区别体现在概念内涵、适用条件和物理实质等方面。', 2, TRUE, 36, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(102, 'chat_session_013', '日常生活中的应用有哪些？', '该原理广泛应用于工程技术、科学研究和日常生活的各个方面。', 1, TRUE, 48, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(103, 'chat_session_014', '学习这个知识点要注意什么？', '掌握基础知识，注重理解而非死记硬背，加强实践应用能力培养。', 2, TRUE, 42, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(104, 'chat_session_015', '考试中容易出现哪些错误？', '审题不清、公式套用不当、计算粗心是最常见的失分原因。', 1, TRUE, 38, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(105, 'chat_session_001', '能推荐一些参考资料吗？', '除了教科书，还可以阅读相关的学术论文和技术资料来加深理解。', 3, TRUE, 46, 'REAL_TIME_QA', NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(106, 'chat_session_002', '这个公式的物理意义是什么？', '它揭示了物理量间的内在联系和变化规律，是认识自然的重要工具。', 1, TRUE, 39, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(107, 'chat_session_003', '如何解决这类计算题？', '首先要明确物理模型，然后选择正确的解题方法，最后验证结果的合理性。', 2, TRUE, 43, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(108, 'chat_session_004', '实验现象怎么解释？', '通过控制变量法可以有效地分析各种因素对实验结果的影响。', 1, TRUE, 41, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(109, 'chat_session_005', '这两个概念有什么区别？', '从本质上看，它们反映了不同层次的物理现象和规律特征。', 2, TRUE, 37, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(100, 'chat_session_006', '日常生活中的应用有哪些？', '现代科技产品中大量运用了这些基本的物理原理和方法。', 3, TRUE, 49, 'REAL_TIME_QA', NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(101, 'chat_session_007', '学习这个知识点要注意什么？', '建立完整的知识体系，培养科学思维方法，提高解决问题的能力。', 1, TRUE, 45, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(102, 'chat_session_008', '考试中容易出现哪些错误？', '概念理解偏差、公式记忆错误、解题思路混乱是主要问题所在。', 2, TRUE, 40, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(103, 'chat_session_009', '能推荐一些参考资料吗？', '可以查阅权威的物理学辞典和专业期刊获取更深入的信息。', 1, TRUE, 44, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(104, 'chat_session_010', '这个公式的物理意义是什么？', '它是连接理论与实践的桥梁，帮助我们更好地理解和改造世界。', 2, TRUE, 38, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(105, 'chat_session_011', '如何解决这类计算题？', '熟练掌握基本公式，灵活运用数学工具，养成良好的解题习惯。', 3, TRUE, 42, 'REAL_TIME_QA', NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(106, 'chat_session_012', '实验现象怎么解释？', '科学的实验方法和严谨的数据分析是得出正确认识的基础。', 1, TRUE, 41, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(107, 'chat_session_013', '这两个概念有什么区别？', '差异主要体现在定义方式、适用范围和物理内涵的不同方面。', 2, TRUE, 36, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(108, 'chat_session_014', '日常生活中的应用有哪些？', '从家用电器到交通工具，无不体现着物理学的巨大价值。', 1, TRUE, 47, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(109, 'chat_session_015', '学习这个知识点要注意什么？', '重视基础训练，加强思维锻炼，做到学以致用融会贯通。', 3, TRUE, 43, 'REAL_TIME_QA', NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(100, 'chat_session_001', '考试中容易出现哪些错误？', '基础知识掌握不牢、解题技巧不够熟练是影响成绩的主要因素。', 2, TRUE, 39, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(101, 'chat_session_002', '能推荐一些参考资料吗？', '经典教材配合最新的研究成果，能够获得更加全面深刻的认识。', 1, TRUE, 45, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(102, 'chat_session_003', '这个公式的物理意义是什么？', '它表达了物质运动变化的基本规律，是物理学大厦的重要基石。', 3, TRUE, 40, 'REAL_TIME_QA', NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(103, 'chat_session_004', '如何解决这类计算题？', '深入理解物理过程，准确选择适用公式，规范书写解题步骤。', 1, TRUE, 44, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(104, 'chat_session_005', '实验现象怎么解释？', '严密的逻辑推理和充分的事实依据是科学解释的基本要求。', 2, TRUE, 38, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(105, 'chat_session_006', '这两个概念有什么区别？', '它们分别从不同的角度描述了同类物理现象的本质特征。', 1, TRUE, 37, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(106, 'chat_session_007', '日常生活中的应用有哪些？', '现代文明的发展离不开这些基础科学理论的指导和支持。', 2, TRUE, 48, 'REAL_TIME_QA', NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(107, 'chat_session_008', '学习这个知识点要注意什么？', '循序渐进地构建知识网络，理论联系实际地深化理解认识。', 3, TRUE, 42, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(108, 'chat_session_009', '考试中容易出现哪些错误？', '心理紧张、时间分配不合理也会严重影响发挥水平。', 1, TRUE, 41, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(109, 'chat_session_010', '能推荐一些参考资料吗？', '网络资源丰富多样，但要注意甄别信息的真实性和权威性。', 2, TRUE, 46, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(100, 'chat_session_011', '这个公式的物理意义是什么？', '它体现了自然界和谐统一的内在美，展现了科学理论的力量。', 3, TRUE, 39, 'REAL_TIME_QA', NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(101, 'chat_session_012', '如何解决这类计算题？', '培养扎实的数学功底，掌握有效的解题策略，积累丰富的实战经验。', 1, TRUE, 43, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(102, 'chat_session_013', '实验现象怎么解释？', '实事求是的科学态度和精益求精的研究精神至关重要。', 2, TRUE, 40, 'REAL_TIME_QA', NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(103, 'chat_session_014', '这两个概念有什么区别？', '细微差别往往决定了它们在实际应用中的不同表现。', 1, TRUE, 36, 'REAL_TIME_QA', NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(104, 'chat_session_015', '日常生活中的应用有哪些？', '科学技术的进步正在不断改善人类的生活质量和认知水平。', 3, TRUE, 49, 'REAL_TIME_QA', NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(105, 'chat_session_001', '学习这个知识点要注意什么？', '既要知其然更要知其所以然，形成系统完整的知识结构。', 2, TRUE, 45, 'REAL_TIME_QA', NULL, DATEADD('DAY', -4, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(106, 'chat_session_002', '考试中容易出现哪些错误？', '缺乏足够的练习和总结反思是制约提升的主要瓶颈。', 1, TRUE, 38, 'REAL_TIME_QA', NULL, DATEADD('DAY', -3, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP()),
(107, 'chat_session_003', '能推荐一些参考资料吗？', '优秀的科普作品同样能够启发思考，拓展视野增长见识。', 2, TRUE, 44, 'REAL_TIME_QA', NULL, DATEADD('DAY', -6, CURRENT_TIMESTAMP()), CURRENT_TIMESTAMP());

-- 6. 教师事件测试数据 (5条)
INSERT INTO teacher_events (teacher_id, teacher_name, event_title, event_description, event_timestamp, is_global, created_at, updated_at) VALUES
(110, '李老师', '期中考试', '高三物理期中考试安排', DATEADD('DAY', -30, CURRENT_TIMESTAMP()), false, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(111, '陈老师', '实验课程', '力学实验课程通知', DATEADD('DAY', -25, CURRENT_TIMESTAMP()), false, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(112, '刘老师', '知识点复习', '电磁学重点知识梳理', DATEADD('DAY', -20, CURRENT_TIMESTAMP()), false, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(113, '杨老师', '期末准备', '期末复习计划制定', DATEADD('DAY', -15, CURRENT_TIMESTAMP()), false, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()),
(114, '科研人员', '研究成果分享', '最新物理研究成果交流', DATEADD('DAY', -10, CURRENT_TIMESTAMP()), false, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP());

-- 显示测试数据统计信息
SELECT 'H2测试数据初始化完成' as message;
SELECT 
    (SELECT COUNT(*) FROM videos) as video_count,
    (SELECT COUNT(*) FROM questions) as question_count,
    (SELECT COUNT(*) FROM users) as user_count,
    (SELECT COUNT(*) FROM answer_records) as answer_record_count,
    (SELECT COUNT(*) FROM conversation_history) as conversation_count,
    (SELECT COUNT(*) FROM teacher_events) as teacher_event_count;

SELECT user_type, COUNT(*) as count FROM users GROUP BY user_type;

-- 显示各视频关联的题目分布
SELECT v.title, v.knowledge_point, 
       CASE WHEN v.question_id IS NULL THEN '无关联题目' ELSE '有关联题目' END as question_status
FROM videos v 
GROUP BY v.title, v.knowledge_point, v.question_id
ORDER BY v.title;

-- 显示答题正确率统计
SELECT 
    u.real_name,
    COUNT(ar.id) as total_answers,
    SUM(CASE WHEN ar.correct = TRUE THEN 1 ELSE 0 END) as correct_answers,
    ROUND(SUM(CASE WHEN ar.correct = TRUE THEN 1 ELSE 0 END) * 100.0 / COUNT(ar.id), 2) as accuracy_rate
FROM users u 
JOIN answer_records ar ON u.id = ar.user_id 
WHERE u.user_type = 'STUDENT'
GROUP BY u.id, u.real_name 
ORDER BY accuracy_rate DESC;