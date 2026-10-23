package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

import com.zhixuewuli.dto.internal.ConversationType;

/**
 * 对话历史实体类
 * 
 * <p>
 * 对应数据库表：conversation_history，存储用户与 AI 的对话历史记录。
 * 支持实时问答和错题分析两种会话类型，实现不同功能的上下文管理。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>记录用户与 AI 的完整对话过程</li>
 * <li>支持会话级别的上下文连续性</li>
 * <li>区分实时问答和错题分析场景</li>
 * <li>统计 token 消耗便于成本核算</li>
 * <li>支持会话激活状态管理</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>序列号机制保证对话顺序</li>
 * <li>激活标识支持会话归档和恢复</li>
 * <li>题目 ID 可选字段支持错题分析场景</li>
 * <li>多索引优化查询性能</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "conversation_history", indexes = {
        @Index(name = "idx_conversation_history_event", columnList = "event_id"),
        @Index(name = "idx_session_active_created", columnList = "session_id, is_active, created_at"),
        @Index(name = "idx_session_active_seq", columnList = "session_id, is_active, sequence_number"),
        @Index(name = "idx_user_session_active_seq", columnList = "user_id, session_id, is_active, sequence_number")
})
@Schema(description = "对话历史实体")
public class ConversationHistory extends BaseTimeEntity {

    /** Teaching event captured when this learning record is created; null denotes legacy/free practice. */
    @Column(name = "event_id")
    private Long eventId;

    /**
     * 对话记录唯一标识符 - 主键自增长
     * 
     * <p>
     * 数据库自动生成的唯一 ID，用于标识每条对话记录。
     * </p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "对话记录 ID", example = "1")
    private Long id;

    /**
     * 用户 ID - 外键引用
     * 
     * <p>
     * 指向 users 表的外键，标识参与对话的用户。
     * 非空约束，确保每条对话都有明确的用户归属。
     * </p>
     */
    @Column(name = "user_id", nullable = false)
    @Schema(description = "用户 ID", example = "1001")
    private Long userId;

    /**
     * 会话 ID - 对话场景标识
     * 
     * <p>
     * 标识所属的对话会话，同一会话中的多条对话记录具有连续性。
     * 用于聚合和管理一次完整的对话过程。
     * </p>
     * 
     * <p>
     * 示例值："sess_20261115_001"
     * </p>
     * 
     * <p>
     * 业务用途：
     * </p>
     * <ul>
     * <li>关联同一次学习过程的多个对话记录</li>
     * <li>支持连续对话场景下的上下文理解</li>
     * <li>便于会话级别的统计和分析</li>
     * </ul>
     */
    @Column(name = "session_id", nullable = false)
    @Schema(description = "会话 ID", example = "sess_20261115_001")
    private String sessionId;

    /**
     * 用户问题内容
     * 
     * <p>
     * 用户向 AI 提出的问题或请求。
     * 支持长文本，使用 TEXT 类型存储。
     * </p>
     * 
     * <p>
     * 示例："请解释一下牛顿第三定律的内容"
     * </p>
     */
    @Column(name = "question", columnDefinition = "TEXT")
    @Schema(description = "用户问题", example = "请解释一下牛顿第三定律")
    private String question;

    /**
     * AI 回答内容
     * 
     * <p>
     * AI 针对用户问题的回复和解答。
     * 支持长文本，使用 TEXT 类型存储。
     * </p>
     * 
     * <p>
     * 示例："牛顿第三定律指出：两个物体之间的作用力和反作用力总是大小相等、方向相反..."
     * </p>
     */
    @Column(name = "answer", columnDefinition = "TEXT")
    @Schema(description = "AI 回答", example = "牛顿第三定律指出：作用力和反作用力大小相等、方向相反")
    private String answer;

    /**
     * 对话序列号 - 会话内排序
     * 
     * <p>
     * 在同一会话中的对话顺序编号，从 1 开始递增。
     * 用于维护对话的先后顺序和上下文关系。
     * </p>
     * 
     * <p>
     * 示例：1, 2, 3... 表示第 1 轮、第 2 轮、第 3 轮对话
     * </p>
     */
    @Column(name = "sequence_number")
    @Schema(description = "对话序列号", example = "1")
    private Integer sequenceNumber;

    /**
     * 激活状态标识 - 会话有效性
     * 
     * <p>
     * 标识该对话记录是否处于激活状态。
     * </p>
     * 
     * <p>
     * 取值说明：
     * </p>
     * <ul>
     * <li>true - 激活状态：当前正在使用的会话</li>
     * <li>false - 归档状态：已结束或已废弃的会话</li>
     * </ul>
     * 
     * <p>
     * 默认值：true（新建对话默认激活）
     * </p>
     */
    @Column(name = "is_active")
    @Schema(description = "是否激活", example = "true")
    private Boolean isActive = true;

    @Transient private Long aiRunId;
    @Transient private java.util.List<com.zhixuewuli.dto.knowledge.AiMessageSourceView> sources = java.util.List.of();

    /**
     * Token 消耗数量
     * 
     * <p>
     * 本次对话消耗的 API token 数量。
     * 用于统计 AI 服务的使用成本和资源消耗。
     * </p>
     * 
     * <p>
     * 默认值：0
     * </p>
     */
    @Column(name = "token_count")
    @Schema(description = "Token 消耗数", example = "150")
    private Integer tokenCount = 0;

    /**
     * 会话类型 - 区分不同对话场景
     * 
     * <p>
     * 必填字段，用于区分不同类型的 AI 对话会话。
     * 实现不同功能的上下文隔离和独立统计。
     * </p>
     * 
     * <p>
     * 取值范围：
     * </p>
     * <ul>
     * <li>REAL_TIME_QA - 实时问答：通用物理学习问题咨询</li>
     * <li>WRONG_QUESTION_ANALYSIS - 错题分析：针对特定题目的深度分析</li>
     * <li>TEACHER_QUESTION_ANALYSIS - 教师题目分析</li>
     * </ul>
     * 
     * <p>
     * 核心设计理念：
     * </p>
     * <ul>
     * <li>实时问答和错题分析功能独立维护上下文</li>
     * <li>相同题目 ID 的错题分析共享会话历史</li>
     * <li>不同类型会话的 token 消耗独立统计</li>
     * <li>支持会话类型的扩展性</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false)
    @Schema(description = "会话类型", example = "REAL_TIME_QA")
    private ConversationType conversationType;

    /**
     * 题目 ID - 错题分析专用字段
     * 
     * <p>
     * 可选字段，仅在错题分析类型 (WRONG_QUESTION_ANALYSIS) 时使用。
     * 标识该对话所分析的具体题目。
     * </p>
     * 
     * <p>
     * 业务逻辑：
     * </p>
     * <ul>
     * <li>实时问答类型：该字段为 null</li>
     * <li>错题分析类型：该字段必填，关联 questions 表</li>
     * </ul>
     * 
     * <p>
     * 示例值：2001（表示分析题号为 2001 的题目）
     * </p>
     */
    @Column(name = "question_id")
    @Schema(description = "题目 ID(错题分析专用)", example = "2001")
    private Long questionId;
}
