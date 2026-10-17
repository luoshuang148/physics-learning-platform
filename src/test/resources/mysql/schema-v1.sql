-- TEST FIXTURE ONLY: destroys the zhixuewuli database; never use against business data.
-- 智学物理系统数据库初始化脚本 v3.2（与实体类严格对应）
-- 整合版本：包含完整表结构、索引和基础数据
-- 创建时间：2026-03-02
-- 适用环境：开发环境

-- 设置客户端字符集和排序规则
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- 删除数据库（如果存在）
DROP DATABASE IF EXISTS zhixuewuli;

-- 创建数据库并指定字符集
CREATE DATABASE IF NOT EXISTS zhixuewuli
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE zhixuewuli;

-- 设置表的默认字符集
ALTER DATABASE zhixuewuli
CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

-- ====================================================================
-- 1. 用户表 (users) - 存储用户基本信息
-- ====================================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名（学号/工号）',
    password_hash VARCHAR(255) NOT NULL COMMENT '加密密码',
    user_type VARCHAR(20) NOT NULL DEFAULT 'STUDENT' COMMENT '用户类型',
    real_name VARCHAR(100) NOT NULL COMMENT '真实姓名',
    class_name VARCHAR(50) COMMENT '班级名称',
    active BOOLEAN DEFAULT TRUE COMMENT '账户状态',
    last_login DATETIME NULL COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_real_name (real_name),
    INDEX idx_class_user_active (class_name, user_type, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- ====================================================================
-- 2. 视频表 (videos) - 存储教学视频信息
-- ====================================================================
CREATE TABLE IF NOT EXISTS videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '视频ID',
    title VARCHAR(255) NOT NULL UNIQUE COMMENT '视频标题',
    url VARCHAR(255) NOT NULL UNIQUE COMMENT '视频地址',
    knowledge_point VARCHAR(255) COMMENT '知识点',
    cover_url VARCHAR(255) COMMENT '封面图片URL',
    duration INT COMMENT '视频时长（秒）',
    description TEXT COMMENT '视频描述',
    question_id BIGINT COMMENT '关联的题目ID，一对一关系',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频信息表';

-- ====================================================================
-- 3. 题目表 (questions) - 存储练习题目
-- ====================================================================
CREATE TABLE IF NOT EXISTS questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '题目 ID',
    content LONGTEXT NOT NULL COMMENT '题目内容（统一 LaTeX 格式）',
    content_hash CHAR(64) NOT NULL COMMENT 'content 的 SHA-256 哈希值，用于唯一性约束',
    options LONGTEXT NOT NULL COMMENT '选项内容（LaTeX格式JSON数组）',
    correct_option VARCHAR(50) NOT NULL COMMENT '正确答案',
    explanation LONGTEXT COMMENT '解析说明（LaTeX格式）',
    difficulty INT DEFAULT 1 COMMENT '难度等级（1-5）',
    explanation_video_mapping LONGTEXT COMMENT '讲解视频映射（JSON格式）',
    image_base64 LONGTEXT COMMENT '题目图片Base64编码',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_content_hash (content_hash),
    INDEX idx_difficulty (difficulty),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目信息表（统一 LaTeX 格式）';

-- ====================================================================
-- 4. 答题记录表 (answer_records) - 存储用户答题记录
-- ====================================================================
CREATE TABLE IF NOT EXISTS answer_records (
    id VARCHAR(36) PRIMARY KEY COMMENT '记录ID（UUID）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    session_id VARCHAR(255) NOT NULL COMMENT '会话ID',
    selected_option VARCHAR(255) NOT NULL COMMENT '用户选择的答案',
    correct BOOLEAN COMMENT '是否正确',
    error_reason VARCHAR(255) COMMENT '错误原因',
    answered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- 实体类定义的索引
    INDEX idx_question_answered (question_id, answered_at),
    INDEX idx_user_answered (user_id, answered_at),
    INDEX idx_session_answered (session_id, answered_at),
    INDEX idx_user_session_answered (user_id, session_id, answered_at),
    INDEX idx_user_question_answered (user_id, question_id, answered_at),
    INDEX idx_user_correct_answered (user_id, correct, answered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录表';

-- ====================================================================
-- 5. AI对话历史表 (conversation_history) - 存储AI对话记录
-- ====================================================================
CREATE TABLE IF NOT EXISTS conversation_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id VARCHAR(255) NOT NULL COMMENT '会话ID',
    question TEXT COMMENT '用户问题',
    answer TEXT COMMENT 'AI回答',
    sequence_number INT COMMENT '对话序号',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否活跃',
    token_count INT DEFAULT 0 COMMENT 'token数量',
    conversation_type VARCHAR(255) NOT NULL COMMENT '会话类型',
    question_id BIGINT COMMENT '关联题目ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,

    -- 实体类定义的索引
    INDEX idx_session_active_created (session_id, is_active, created_at),
    INDEX idx_session_active_seq (session_id, is_active, sequence_number),
    INDEX idx_user_session_active_seq (user_id, session_id, is_active, sequence_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话历史记录表';

-- ====================================================================
-- 6. 教师事件表 (teacher_events) - 存储教师发布的学习事件
-- ====================================================================
CREATE TABLE IF NOT EXISTS teacher_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件 ID',
    teacher_id BIGINT NOT NULL COMMENT '关联教师 ID',
    teacher_name VARCHAR(100) NOT NULL COMMENT '教师姓名',
    event_title VARCHAR(20) NOT NULL COMMENT '事件标题',
    event_description VARCHAR(100) COMMENT '事件描述',
    event_timestamp DATETIME NOT NULL COMMENT '事件时间戳',
    is_global BOOLEAN DEFAULT TRUE COMMENT '是否为全局事件',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_is_global (is_global)  -- 全局事件查询索引
    -- 实体类没有定义其他索引，故不添加额外索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师事件表';

-- ====================================================================
-- 7. 学生报告表 (student_reports) - 存储AI生成的学生个人学习报告
-- ====================================================================
CREATE TABLE IF NOT EXISTS student_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '报告ID',
    user_id BIGINT NOT NULL COMMENT '学生用户ID',
    real_name VARCHAR(100) NOT NULL COMMENT '学生真实姓名',
    session_id VARCHAR(100) NOT NULL COMMENT '会话ID',
    report_content TEXT NOT NULL COMMENT '报告内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- 实体类定义的索引
    INDEX idx_student_report_session_id (session_id),
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生报告表';

-- ====================================================================
-- 8. 实验表 (experiments) - 存储实验配置信息
-- ====================================================================
CREATE TABLE IF NOT EXISTS experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '实验ID',
    name VARCHAR(255) NOT NULL UNIQUE COMMENT '实验名称（全局唯一）',
    question_ids LONGTEXT COMMENT '有序题目ID列表（JSON 数组格式，如 [1,2,3]）',
    description TEXT COMMENT '实验描述',
    order_number INT DEFAULT 0 NOT NULL COMMENT '排序序号（正整数，全局唯一）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_experiment_name (name),
    UNIQUE KEY uk_order_number (order_number),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验配置表';

-- ====================================================================
-- 插入初始必需数据
-- ====================================================================

INSERT INTO users (username, password_hash, user_type, real_name, class_name) VALUES
('admin', '$2a$10$5nxV.VGD3yCGNdr3mgzyR.QIGne12zdXlFL158jiTII9H30nLyCKC', 'TEACHER', '系统管理员', '管理员');

-- 显示创建完成信息
SELECT '数据库初始化完成' as message;
SELECT CONCAT('创建了 ', COUNT(*), ' 个表') as table_count
FROM information_schema.tables
WHERE table_schema = 'zhixuewuli';

-- 重新启用外键检查
SET FOREIGN_KEY_CHECKS = 1;
