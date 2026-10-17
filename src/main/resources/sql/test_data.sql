-- 智学物理系统测试数据脚本 v4.0（适配抛体运动与斜面约束新知识点）
-- 整合版本：包含精简测试数据用于开发和测试
-- 创建时间：2026-03-02
-- 适用环境：开发环境
-- 更新说明：聚焦抛体运动与斜面约束，题目精简为5道，答题记录30条，其他数据量不变

-- 设置字符集
SET NAMES utf8mb4;
USE zhixuewuli;

-- 完全清空所有测试数据（按依赖顺序）
SET FOREIGN_KEY_CHECKS = 0;

-- 清理顺序：子表 → 父表
DELETE FROM answer_records;
DELETE FROM conversation_history;
DELETE FROM teacher_events;
DELETE FROM users WHERE username != 'admin'; -- 保留系统管理员账户
DELETE FROM questions;
DELETE FROM videos;
DELETE FROM experiments;

-- 重置自增ID（根据各表当前最大ID合理设置起始值）
ALTER TABLE videos AUTO_INCREMENT = 1;
ALTER TABLE questions AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 100; -- 避免与现有用户ID冲突
ALTER TABLE answer_records AUTO_INCREMENT = 1;
ALTER TABLE conversation_history AUTO_INCREMENT = 1;
ALTER TABLE teacher_events AUTO_INCREMENT = 1;
ALTER TABLE experiments AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 1. 视频测试数据 (15条) - 抛体运动与斜面约束专题
--    question_id 仅作记录，无外键约束，可设为 NULL 或关联题目ID
-- ====================================================================
INSERT INTO videos (id, title, url, knowledge_point, duration, description, question_id, created_at, updated_at) VALUES
(1,  '抛体运动基础',                'https://example.com/video1.mp4',  '抛体运动', 320, '抛体运动的定义、分类及基本分析方法', 1,  NOW(), NOW()),
(2,  '平抛运动的分解',              'https://example.com/video2.mp4',  '抛体运动', 300, '将平抛运动分解为水平匀速和竖直自由落体', 2, NOW(), NOW()),
(3,  '斜面约束下的抛体运动',        'https://example.com/video3.mp4',  '斜面约束', 340, '物体在斜面上方抛出，受到斜面几何约束的运动特点', 3, NOW(), NOW()),
(4,  '沿斜面方向的运动分解',        'https://example.com/video4.mp4',  '分解方法', 280, '将重力加速度沿斜面方向和垂直斜面方向分解', 4, NOW(), NOW()),
(5,  '垂直斜面方向的运动分解',      'https://example.com/video5.mp4',  '分解方法', 290, '垂直斜面方向的运动独立分析，用于求最大距离', 5, NOW(), NOW()),
(6,  '位移偏角与速度偏角的关系',    'https://example.com/video6.mp4',  '抛体运动', 310, '平抛运动中位移与速度方向角的关系推导', NULL, NOW(), NOW()),
(7,  '斜面上平抛运动的时间计算',    'https://example.com/video7.mp4',  '斜面约束', 330, '利用位移分解求物体落在斜面上的时间', NULL, NOW(), NOW()),
(8,  '斜面上平抛运动的最大距离',    'https://example.com/video8.mp4',  '斜面约束', 350, '垂直斜面方向运动分析求离开斜面的最远距离', NULL, NOW(), NOW()),
(9,  '斜面抛体运动的综合应用',      'https://example.com/video9.mp4',  '综合问题', 360, '结合斜面约束的复杂抛体问题解题策略', NULL, NOW(), NOW()),
(10, '类平抛运动与斜面',            'https://example.com/video10.mp4', '抛体运动', 300, '在恒定合力下类似平抛的运动处理方法', NULL, NOW(), NOW()),
(11, '利用分解法解决斜面问题',      'https://example.com/video11.mp4', '分解方法', 320, '通过合理分解将曲线运动转化为直线运动', NULL, NOW(), NOW()),
(12, '抛体运动实验演示',            'https://example.com/video12.mp4', '实验', 280, '实际抛体运动实验录像与数据分析', NULL, NOW(), NOW()),
(13, '斜面约束的数学表达',          'https://example.com/video13.mp4', '数学工具', 260, '用几何和代数描述斜面约束条件', NULL, NOW(), NOW()),
(14, '抛体运动在生活中的应用',      'https://example.com/video14.mp4', '应用', 290, '体育、工程中的抛体运动实例', NULL, NOW(), NOW()),
(15, '抛体运动复习专题',            'https://example.com/video15.mp4', '复习', 400, '抛体运动知识点梳理与典型例题', NULL, NOW(), NOW());

-- ====================================================================
-- 2. 题目测试数据 (5 条) - 抛体运动与斜面约束
--    选项统一为 JSON 对象格式，内容采用 LaTeX 语法
-- ====================================================================
INSERT INTO questions (id, content, content_hash, options, correct_option, explanation, difficulty, explanation_video_mapping, image_base64, created_at, updated_at) VALUES
(1, '在倾角为$\theta$的斜面上，将一小球以水平初速度$v_0$抛出，小球最终落在斜面上。忽略空气阻力，则小球从抛出到落在斜面上的时间$t$为？',
    SHA2('在倾角为$\\theta$的斜面上，将一小球以水平初速度$v_0$抛出，小球最终落在斜面上。忽略空气阻力，则小球从抛出到落在斜面上的时间$t$为？', 256),
    '{"A": "$t=\\\\frac{2v_0\\\\tan\\\\theta}{g}$", "B": "$t=\\\\frac{v_0\\\\tan\\\\theta}{g}$", "C": "$t=\\\\frac{2v_0}{g\\\\tan\\\\theta}$", "D": "$t=\\\\frac{v_0}{g\\\\tan\\\\theta}$"}',
    'A', '设水平位移$x=v_0t$，竖直位移$y=\\frac{1}{2}gt^2$，落在斜面时有$\\frac{y}{x}=\\tan\\theta$，即$\\frac{gt}{2v_0}=\\tan\\theta$，解得$t=\\frac{2v_0\\tan\\theta}{g}$',
    3, '{"A": 1, "B": 2, "C": 3, "D": 4}', NULL, NOW(), NOW()),

(2, '在倾角为$\theta$的光滑斜面上，将一小球以初速度$v_0$沿斜面向上抛出（小球仅受重力，斜面支持力），则小球沿斜面向上运动的最大距离$s$为？',
    SHA2('在倾角为$\theta$的光滑斜面上，将一小球以初速度$v_0$沿斜面向上抛出（小球仅受重力，斜面支持力），则小球沿斜面向上运动的最大距离$s$为？', 256),
    '{"A": "$s=\\\\frac{v_0^2}{2g\\\\sin\\\\theta}$", "B": "$s=\\\\frac{v_0^2}{g\\\\sin\\\\theta}$", "C": "$s=\\\\frac{v_0^2\\\\sin\\\\theta}{2g}$", "D": "$s=\\\\frac{v_0^2}{2g\\\\cos\\\\theta}$"}',
    'A', '沿斜面方向加速度$a=g\\sin\\theta$，由运动学公式$v_0^2=2as$得$s=\\frac{v_0^2}{2g\\sin\\theta}$',
    2, '{"A": 1, "B": 2, "C": 3, "D": 4}', NULL, NOW(), NOW()),

(3, '从倾角为$\theta$的斜面上某点以水平初速度$v_0$抛出一个小球，小球落在斜面上时速度方向与水平方向的夹角为$\alpha$，则$\tan\alpha$与$\tan\theta$的关系是？',
    SHA2('从倾角为$\\theta$的斜面上某点以水平初速度$v_0$抛出一个小球，小球落在斜面上时速度方向与水平方向的夹角为$\\alpha$，则$\\tan\\alpha$与$\\tan\\theta$的关系是？', 256),
    '{"A": "$\\\\tan\\\\alpha = 2\\\\tan\\\\theta$", "B": "$\\\\tan\\\\alpha = \\\\tan\\\\theta$", "C": "$\\\\tan\\\\alpha = \\\\frac{1}{2}\\\\tan\\\\theta$", "D": "$\\\\tan\\\\alpha = \\\\frac{2}{\\\\tan\\\\theta}$"}',
    'A', '平抛落在斜面上时，位移偏角$\\theta$满足$\\tan\\theta = \\frac{y}{x} = \\frac{gt}{2v_0}$，速度偏角$\\alpha$满足$\\tan\\alpha = \\frac{v_y}{v_0} = \\frac{gt}{v_0}$，故$\\tan\\alpha = 2\\tan\\theta$',
    3, '{"A": 5, "B": 6, "C": 7, "D": 8}', NULL, NOW(), NOW()),

(4, '如图所示，在倾角为$\theta$的斜面上，将一小球以水平初速度$v_0$抛出，空气阻力不计。则小球在运动过程中距离斜面的最大距离$d$为？',
    SHA2('如图所示，在倾角为$\theta$的斜面上，将一小球以水平初速度$v_0$抛出，空气阻力不计。则小球在运动过程中距离斜面的最大距离$d$为？', 256),
    '{"A": "$d=\\\\frac{v_0^2\\\\sin^2\\\\theta}{2g\\\\cos\\\\theta}$", "B": "$d=\\\\frac{v_0^2\\\\sin\\\\theta}{2g}$", "C": "$d=\\\\frac{v_0^2\\\\cos^2\\\\theta}{2g\\\\sin\\\\theta}$", "D": "$d=\\\\frac{v_0^2}{2g}\\\\sin\\\\theta$"}',
    'A', '将运动分解为垂直斜面方向：初速度$v_{0\\perp}=v_0\\sin\\theta$（垂直斜面向上），加速度$a_\\perp=g\\cos\\theta$（垂直斜面向下）。当垂直斜面方向速度减为零时距离最大，$d=\\frac{v_{0\\perp}^2}{2a_\\perp}=\\frac{v_0^2\\sin^2\\theta}{2g\\cos\\theta}$',
    4, '{"A": 9, "B": 10, "C": 11, "D": 12}', NULL, NOW(), NOW()),

(5, '在倾角为$\theta$的斜面上，以初速度$v_0$沿与斜面成$\beta$角的方向抛出一个小球（$\beta$为初速度与斜面的夹角），忽略空气阻力。小球在斜面上方运动的过程中，沿斜面方向的运动是？',
    SHA2('在倾角为$\theta$的斜面上，以初速度$v_0$沿与斜面成$\beta$角的方向抛出一个小球（$\beta$为初速度与斜面的夹角），忽略空气阻力。小球在斜面上方运动的过程中，沿斜面方向的运动是？', 256),
    '{"A": "匀速直线运动", "B": "匀加速直线运动", "C": "匀减速直线运动", "D": "变加速直线运动"}',
    'B', '将重力加速度分解到沿斜面方向，大小为$g\\sin\\theta$，方向沿斜面向下。沿斜面方向初速度为$v_0\\cos\\beta$，加速度恒定，故为匀加速直线运动（若$v_0\\cos\\beta$方向沿斜面向下则为匀加速，若向上则为匀减速，但加速度大小方向均不变，本质上是匀变速直线运动）',
    2, '{"A": 1, "B": 2, "C": 3, "D": 4}', NULL, NOW(), NOW());

-- ====================================================================
-- 3. 用户测试数据 (15条) - 完全保留原脚本内容
--    注意：real_name 必须唯一，密码哈希值保持不变
-- ====================================================================
INSERT INTO users (username, password_hash, user_type, real_name, class_name, active, last_login, created_at, updated_at) VALUES
('student001', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '张三', '高三(1)班', TRUE, NULL, NOW(), NOW()),
('student002', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '李四', '高三(1)班', TRUE, NULL, NOW(), NOW()),
('student003', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '王五', '高三(2)班', TRUE, NULL, NOW(), NOW()),
('student004', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '赵六', '高三(2)班', TRUE, NULL, NOW(), NOW()),
('student005', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '钱七', '高三(3)班', TRUE, NULL, NOW(), NOW()),
('student006', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '孙八', '高三(3)班', TRUE, NULL, NOW(), NOW()),
('student007', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '周九', '高二(1)班', TRUE, NULL, NOW(), NOW()),
('student008', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '吴十', '高二(1)班', TRUE, NULL, NOW(), NOW()),
('student009', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '郑一', '高二(2)班', TRUE, NULL, NOW(), NOW()),
('student010', '$2a$10$mHG9.I9UFZSnKXvDPGrMheC4OAwHuAsrX3A9tIL.jWIrS1WFetTv6', 'STUDENT', '王二', '高二(2)班', TRUE, NULL, NOW(), NOW()),
('teacher001', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '李老师', NULL, TRUE, NULL, NOW(), NOW()),
('teacher002', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '陈老师', NULL, TRUE, NULL, NOW(), NOW()),
('teacher003', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '刘老师', NULL, TRUE, NULL, NOW(), NOW()),
('teacher004', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '杨老师', NULL, TRUE, NULL, NOW(), NOW()),
('researcher001', '$2a$10$ibH4m19t8jF/i2o9NRKLye6.HyN5GzZMlYqDDzxE/EaYN02yh3RKm', 'TEACHER', '科研人员', '研究组', TRUE, NULL, NOW(), NOW());

-- ====================================================================
-- 4. 答题记录测试数据 (30条)
--    10名学生（ID 100~109）每人回答3道题，覆盖题目1-5
--    使用统一会话 ID '20260315000000_sessionTestABC01'（符合 SessionUtil 规范）
-- ====================================================================
INSERT INTO answer_records (id, user_id, question_id, session_id, selected_option, correct, error_reason, answered_at, created_at, updated_at) VALUES
-- 学生 100 (张三)
('rec_100_1', 100, 1, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
('rec_100_2', 100, 2, '20260315000000_sessionTestABC01', 'B', FALSE, '将加速度误认为 g，忽略了 sinθ因子', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
('rec_100_3', 100, 3, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),

-- 学生 101 (李四)
('rec_101_1', 101, 1, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
('rec_101_2', 101, 2, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
('rec_101_3', 101, 4, '20260315000000_sessionTestABC01', 'C', FALSE, '混淆了垂直斜面和平行斜面的分解', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),

-- 学生 102 (王五)
('rec_102_1', 102, 1, '20260315000000_sessionTestABC01', 'C', FALSE, '时间公式记反，应为 2v₀tanθ/g', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),
('rec_102_2', 102, 3, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),
('rec_102_3', 102, 5, '20260315000000_sessionTestABC01', 'B', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),

-- 学生 103 (赵六)
('rec_103_1', 103, 2, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW()),
('rec_103_2', 103, 4, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW()),
('rec_103_3', 103, 5, '20260315000000_sessionTestABC01', 'D', FALSE, '未正确分解速度到垂直斜面方向', DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), NOW()),

-- 学生 104 (钱七)
('rec_104_1', 104, 1, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW()),
('rec_104_2', 104, 3, '20260315000000_sessionTestABC01', 'B', FALSE, '误认为 tanα=tanθ', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW()),
('rec_104_3', 104, 4, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW()),

-- 学生 105 (孙八)
('rec_105_1', 105, 2, '20260315000000_sessionTestABC01', 'C', FALSE, '错用 cosθ代替 sinθ', DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW()),
('rec_105_2', 105, 3, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW()),
('rec_105_3', 105, 5, '20260315000000_sessionTestABC01', 'B', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), NOW()),

-- 学生 106 (周九)
('rec_106_1', 106, 1, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
('rec_106_2', 106, 4, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
('rec_106_3', 106, 5, '20260315000000_sessionTestABC01', 'A', FALSE, '误将最大距离公式记错', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), NOW()),

-- 学生 107 (吴十)
('rec_107_1', 107, 2, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
('rec_107_2', 107, 3, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
('rec_107_3', 107, 4, '20260315000000_sessionTestABC01', 'D', FALSE, '垂直斜面方向加速度分析错误', DATE_SUB(NOW(), INTERVAL 8 DAY), NOW(), NOW()),

-- 学生 108 (郑一)
('rec_108_1', 108, 1, '20260315000000_sessionTestABC01', 'B', FALSE, '忘记乘以 2', DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW()),
('rec_108_2', 108, 2, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW()),
('rec_108_3', 108, 5, '20260315000000_sessionTestABC01', 'B', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW()),

-- 学生 109 (王二)
('rec_109_1', 109, 3, '20260315000000_sessionTestABC01', 'A', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), NOW()),
('rec_109_2', 109, 4, '20260315000000_sessionTestABC01', 'C', FALSE, '将 sinθ与 cosθ位置颠倒', DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), NOW()),
('rec_109_3', 109, 5, '20260315000000_sessionTestABC01', 'B', TRUE, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), NOW());

-- ====================================================================
-- 5. AI对话历史测试数据 (50条) - 与原脚本一致，仅调整question_id为NULL
--    保留原脚本的存储过程和记录（已移除context字段）
-- ====================================================================
DELIMITER //
CREATE PROCEDURE GenerateConversationHistory()
BEGIN
    DECLARE i INT DEFAULT 5;
    DECLARE user_id_val INT;
    DECLARE session_id_val VARCHAR(64);
    DECLARE question_val TEXT;
    DECLARE answer_val TEXT;
    DECLARE token_count_val INT;
    
    WHILE i <= 50 DO
        SET user_id_val = 100 + FLOOR(RAND() * 10);  -- 对应 student001~student010 的 ID
        SET session_id_val = CONCAT('chat_session_', LPAD(FLOOR(RAND() * 15) + 1, 3, '0'));
        SET token_count_val = 30 + FLOOR(RAND() * 70);
        
        CASE FLOOR(RAND() * 8)
            WHEN 0 THEN 
                SET question_val = '这个公式的物理意义是什么？';
                SET answer_val = '这个公式描述了物理量之间的定量关系，反映了自然界的基本规律。';
            WHEN 1 THEN 
                SET question_val = '如何解决这类计算题？';
                SET answer_val = '解答此类题目需要先分析物理过程，选择合适的公式，注意单位换算。';
            WHEN 2 THEN 
                SET question_val = '实验现象怎么解释？';
                SET answer_val = '这个现象可以通过相关的物理原理解释，关键是要找到现象背后的物理机制。';
            WHEN 3 THEN 
                SET question_val = '这两个概念有什么区别？';
                SET answer_val = '虽然这两个概念看起来相似，但在定义、适用范围和物理意义上都有重要区别。';
            WHEN 4 THEN 
                SET question_val = '日常生活中的应用有哪些？';
                SET answer_val = '这一物理原理在现代科技和日常生活中有很多重要应用，比如...';
            WHEN 5 THEN 
                SET question_val = '学习这个知识点要注意什么？';
                SET answer_val = '学习时要重点理解概念的本质，多做练习巩固，注意与其他知识点的联系。';
            WHEN 6 THEN 
                SET question_val = '考试中容易出现哪些错误？';
                SET answer_val = '常见错误包括概念混淆、公式记错、单位换算失误等，需要特别注意。';
            ELSE 
                SET question_val = '能推荐一些参考资料吗？';
                SET answer_val = '建议参考教材相关章节，配合习题册练习，还可以查阅相关的科普读物。';
        END CASE;
        
        INSERT INTO conversation_history 
            (user_id, session_id, question, answer, sequence_number, is_active, token_count, conversation_type, question_id, created_at, updated_at) 
        VALUES 
            (user_id_val, session_id_val, question_val, answer_val, FLOOR(RAND() * 5) + 1, TRUE, token_count_val, 'REAL_TIME_QA', NULL, NOW(), NOW());
        
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

-- 先插入一些基础对话记录（原脚本中的前4条）
INSERT INTO conversation_history (user_id, session_id, question, answer, sequence_number, is_active, token_count, conversation_type, question_id, created_at, updated_at) VALUES
(100, 'chat_session_001', '什么是牛顿第一定律？', '牛顿第一定律也称为惯性定律，表述为：物体在不受外力作用时，保持静止状态或匀速直线运动状态。这一定律揭示了物体具有惯性的特性。', 1, TRUE, 45, 'REAL_TIME_QA', NULL, NOW(), NOW()),
(100, 'chat_session_001', '能举个生活中的例子吗？', '当然可以。比如汽车突然急刹车时，车内的乘客会向前倾倒，这就是因为乘客的身体由于惯性要保持原来的运动状态。还有抖掉衣服上的灰尘，也是利用了灰尘的惯性。', 2, TRUE, 67, 'REAL_TIME_QA', NULL, NOW(), NOW()),
(100, 'chat_session_002', '动量守恒定律的适用条件是什么？', '动量守恒定律的适用条件包括：1) 系统不受外力作用；2) 系统所受外力矢量和为零；3) 系统内力远大于外力。在这些条件下，系统的总动量保持不变。', 1, TRUE, 52, 'REAL_TIME_QA', NULL, NOW(), NOW()),
(100, 'chat_session_002', '火箭推进为什么能用动量守恒解释？', '火箭推进正是动量守恒的典型应用。火箭向后高速喷射燃气，获得向后的动量，根据动量守恒定律，火箭本身就会获得向前的动量，从而实现推进。', 2, TRUE, 58, 'REAL_TIME_QA', NULL, NOW(), NOW());

-- 调用存储过程生成剩余对话记录
CALL GenerateConversationHistory();

-- 删除临时存储过程
DROP PROCEDURE GenerateConversationHistory;

-- ====================================================================
-- 6. 教师事件测试数据 (2条) - 匹配前端硬编码的系列实验
--    event_title 必须是: '原子物理系列实验' 或 '万有引力与宇宙航天系列实验'
--    event_description 格式: 'QIDS:题目ID1,题目ID2,...'
-- ====================================================================
INSERT INTO teacher_events (teacher_id, teacher_name, event_title, event_description, event_timestamp, created_at, updated_at) VALUES
(110, '李老师', '原子物理系列实验', 'QIDS:1,2,3,4,5', DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), NOW());

-- ====================================================================
-- 显示测试数据统计信息
-- ====================================================================
SELECT '测试数据初始化完成' as message;
SELECT 
    (SELECT COUNT(*) FROM videos) as video_count,
    (SELECT COUNT(*) FROM questions) as question_count,
    (SELECT COUNT(*) FROM users) as user_count,
    (SELECT COUNT(*) FROM answer_records) as answer_record_count,
    (SELECT COUNT(*) FROM conversation_history) as conversation_count,
    (SELECT COUNT(*) FROM teacher_events) as teacher_event_count;

SELECT user_type, COUNT(*) as count FROM users GROUP BY user_type;

-- 显示各知识点题目分布（基于题目关联的视频）
SELECT v.knowledge_point, COUNT(q.id) as question_count 
FROM videos v 
LEFT JOIN questions q ON v.question_id = q.id
GROUP BY v.knowledge_point 
ORDER BY question_count DESC;

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

-- ====================================================================
-- 8. 实验配置测试数据
-- ====================================================================

-- 插入实验配置测试数据（5 个实验）
INSERT INTO experiments (id, name, question_ids, description, order_number, created_at, updated_at) VALUES
(1, '牛顿第一定律实验', '[1,2,3]', '探究牛顿第一定律的基础实验配置', 1, NOW(), NOW()),
(2, '抛体运动基础实验', '[1,2,4,5]', '平抛运动与斜抛运动的入门实验', 2, NOW(), NOW()),
(3, '斜面约束实验', '[3,4,5]', '物体在斜面上运动的实验配置', 3, NOW(), NOW()),
(4, '运动分解综合实验', '[2,3,4,5]', '运动合成与分解的综合应用实验', 4, NOW(), NOW()),
(5, '力学综合实验', '[1,2,3,4,5]', '力学部分知识的综合实验配置', 5, NOW(), NOW());

-- 显示实验配置信息
SELECT '实验配置数据插入完成' AS message;
SELECT 
    e.name AS experiment_name,
    JSON_LENGTH(e.question_ids) AS question_count,
    e.order_number AS order_number,
    e.description AS description
FROM experiments e
ORDER BY e.order_number;