package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI业务配置属性类
 * 
 * 统一管理AI服务相关的业务配置参数，包括上下文管理、
 * 模型选择、供应商配置等核心业务设置。
 * 通过@ConfigurationProperties注解实现配置文件属性的自动绑定。
 * 
 * <p>
 * 配置层次结构：
 * </p>
 * <ul>
 * <li><strong>上下文管理配置</strong>: 控制对话历史和token使用</li>
 * <li><strong>模型配置</strong>: 指定不同场景下的AI模型</li>
 * <li><strong>供应商通用参数</strong>: 温度、最大token数等通用设置</li>
 * <li><strong>阿里云专属配置</strong>: 阿里云服务商的特定参数</li>
 * <li><strong>智谱AI专属配置</strong>: 智谱AI服务商的特定参数</li>
 * </ul>
 * 
 * <p>
 * 配置示例：
 * </p>
 * 
 * <pre>
 * ai:
 *   context:
 *     # 上下文管理配置
 *     max-history-count: 15
 *     max-token-count: 1000000
 *     session-expiration: 24h
 *     context-background: "你是一名经验丰富的高中物理教师..."
 *     
 *     # 模型配置
 *     real-time-qa-model: glm-4.1v-thinking-flash
 *     wrong-question-analysis-model: glm-4.5v
 *     teacher-report-generation-model: qwen-long
 *     student-report-generation-model: qwen3.5-plus
 *     
 *     # 通用参数
 *     temperature: 0.6
 *     max-tokens: 2048
 *     
 *     # 阿里云配置
 *     aliyun-api-key: your-aliyun-key
 *     aliyun-base-url: https://dashscope.aliyuncs.com/api/v1
 *     aliyun-default-model: qwen3.5-plus
 *     aliyun-incremental-output: true
 *     aliyun-temperature: 0.7
 *     aliyun-max-tokens: 3072
 *     
 *     # 智谱AI配置
 *     zhipu-api-key: your-zhipu-key
 *     zhipu-base-url: https://open.bigmodel.cn/api/paas/v4
 *     zhipu-default-model: glm-4.1v-thinking-flash
 *     zhipu-temperature: 0.3
 *     zhipu-max-tokens: 2048
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Data
@Component("aiBusinessProperties")
@ConfigurationProperties(prefix = "ai.context")
public class AIBusinessProperties {
        private boolean enabled = true;

        // ========== 上下文管理配置 ==========
        /**
         * 最大历史对话记录数
         * 控制保留的历史对话轮次，影响上下文理解和内存使用
         * 默认15条，可通过配置调整
         */
        private int maxHistoryCount = 15;

        /** Maximum cached conversations in this application instance. */
        private int maxCachedSessions = 1000;

        /**
         * 最大token总数限制
         * 防止上下文过长导致的性能问题和成本增加
         * 默认1000000 tokens，约相当于75万个中文字符
         */
        private int maxTokenCount = 1000000;

        /**
         * 会话过期时间
         * 控制用户会话的有效期，超时后需要重新建立上下文
         * 默认24小时，支持多种时间单位配置（如1d, 12h, 30m）
         */
        private Duration sessionExpiration = Duration.ofHours(24);

        /**
         * 会话清理定时任务执行间隔
         * 控制定时任务多久扫描一次过期会话
         * 默认10分钟，支持多种时间单位配置（如 10m, 30s, 1h）
         */
        private Duration cleanupInterval = Duration.ofMinutes(10);

        /**
         * AI上下文背景信息
         * 为AI设定角色和行为准则，指导其回答风格和内容方向
         * 默认为高中物理教师角色，强调通俗易懂和耐心专业的教学风格
         */
        private String contextBackground = "你是一名经验丰富的高中物理教师，擅长用通俗易懂的方式讲解复杂的物理概念。" +
                        "本次课程的高中学生正在通过3D模拟实验平台学习实验相关知识，然后练习巩固" +
                        "你的职责是帮助学生理解物理知识与方法，解决学生困惑，并提供针对性的学习建议。" +
                        "请用中文回答，语气要亲切、专业、有耐心。";

        /**
         * 教师题目分析默认提示词（首次加载时使用）
         */
        private String teacherQuestionAnalysisDefaultPrompt = "请总结归纳学生本题的学习情况，从以下几个角度关注：\r\n" +
                        "1、整体掌握情况（包括正确率、理解程度或学生成绩与掌握情况在班级里的分布等）\r\n" +
                        "2、结合选项分布和答题情况分析典型错误思考模式，不推测学生未提供的主观想法\r\n" +
                        "3、教学应对建议（包括针对性纠错策略和核心强化知识点与教学方法提示）\r\n" +
                        "4、鼓励在回答中体现具体数据支撑";

        // ========== 模型配置 ==========
        /**
         * 实时问答模型名称
         * 用于处理用户的即时问答请求
         * 默认使用glm-4.1v-thinking-flash模型，具有较快的响应速度
         */
        private String realTimeQaModel = "glm-4.1v-thinking-flash"; // "glm-4.6v-flash-web";

        /**
         * 错题分析模型名称
         * 专门用于分析学生错题，提供深度解析和学习建议
         * 默认使用glm-4.5v模型，具备更强的推理能力
         */
        private String wrongQuestionAnalysisModel = "glm-4.5v"; // "glm-4.1v-thinking-flash";

        /**
         * 教师报告生成模型名称
         * 专门用于生成教师教学报告和分析文档
         * 默认使用qwen3-30b-a3b-instruct-2507模型，适合处理长文本和归纳总结任务
         */
        private String teacherReportGenerationModel = "qwen3-30b-a3b-instruct-2507";

        /**
         * 学生报告生成模型名称
         * 专门用于生成学生学习报告和个人化分析
         * 默认使用qwen3-235b-a22b-instruct-2507模型，非思考模式
         */
        private String studentReportGenerationModel = "qwen3-235b-a22b-instruct-2507";

        // ========== 供应商通用业务参数 ==========
        /**
         * 默认生成温度（全局默认，各供应商可单独覆盖）
         * 控制AI生成内容的随机性和创造性
         * 取值范围：0.0-1.0，数值越高越随机
         * <ul>
         * <li><strong>0.0-0.3</strong>: 保守模式，回答更加确定和一致</li>
         * <li><strong>0.4-0.7</strong>: 平衡模式，兼顾创造性和准确性</li>
         * <li><strong>0.8-1.0</strong>: 创造模式，回答更加多样但可能不够准确</li>
         * </ul>
         */
        private Double temperature = 0.6;

        /**
         * 默认最大输出token数（全局默认，各供应商可单独覆盖）
         * 限制单次生成内容的最大长度，控制成本和响应时间
         * 默认2048 tokens，约相当于1500个中文字符
         */
        private Integer maxTokens = 2048;

        // ========== 阿里云配置 ==========
        /**
         * 阿里云API密钥
         * 用于访问阿里云DashScope服务的身份凭证
         * 必须在配置文件中设置，不能为空
         */
        private String aliyunApiKey;

        /**
         * 阿里云基础URL
         * 阿里云DashScope API的服务端点地址
         * 一般不需要修改，除非有特殊需求
         */
        private String aliyunBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        /**
         * 阿里云默认模型
         * 当未指定具体模型时使用的默认AI模型
         * qwen3-235b-a22b-instruct-2507是非思考模式模型
         */
        private String aliyunDefaultModel = "qwen3-235b-a22b-instruct-2507";

        /**
         * 阿里云生成温度
         * 覆盖全局温度设置，专门为阿里云服务定制
         * 默认0.7，适合平衡的回答效果
         */
        private Double aliyunTemperature = 0.7;

        /**
         * 阿里云最大输出token数
         * 覆盖全局最大token设置，控制阿里云服务的输出长度
         * 默认4096 tokens，与全局设置保持一致
         */
        private Integer aliyunMaxTokens = 4096;

        // ========== 智谱AI配置 ==========

        /**
         * 智谱AI API密钥
         * 用于访问智谱AI开放平台服务的身份凭证
         * 必须在配置文件中设置，不能为空
         */
        private String zhipuApiKey;

        /**
         * 智谱AI基础URL
         * 智谱AI开放平台API的服务端点地址
         * 一般不需要修改，除非有特殊需求
         */
        private String zhipuBaseUrl = "https://open.bigmodel.cn/api/paas/v4";

        /**
         * 智谱AI默认模型
         * 当未指定具体模型时使用的默认AI模型
         * glm-4.1v-thinking-flash是GLM系列的思维链推理版本
         */
        private String zhipuDefaultModel = "glm-4.1v-thinking-flash";

        /**
         * 智谱AI生成温度
         * 覆盖全局温度设置，专门为智谱AI服务定制
         * 默认0.3，较低温度确保回答的准确性和一致性
         */
        private Double zhipuTemperature = 0.3;

        /**
         * 智谱AI最大输出token数
         * 覆盖全局最大token设置，控制智谱AI服务的输出长度
         * 默认2048 tokens，与全局设置保持一致
         */
        private Integer zhipuMaxTokens = 2048;

        /**
         * 是否启用深度思考（reasoning）输出
         * - true: 启用，模型返回reasoning_content字段
         * - false: 禁用，仅返回content字段
         */
        private Boolean zhipuEnableReasoning = true;
}