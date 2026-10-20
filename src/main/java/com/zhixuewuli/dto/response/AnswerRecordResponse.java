package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 答题记录响应DTO
 * 
 * 用于返回单个答题记录的完整信息，包含基础答题信息、题目详细信息和会话信息。
 * 专门针对单个记录设计，不包含批量操作相关字段。
 * 
 * <p>
 * 数据结构组成：
 * </p>
 * <ul>
 * <li><strong>基础答题信息</strong>: 用户答案、正确性、答题时间、错误原因等</li>
 * <li><strong>题目详细信息</strong>: 内容、选项、答案、解析、难度、图片等</li>
 * <li><strong>会话信息</strong>: 会话ID、会话时间等</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>专为单个记录设计，职责单一</li>
 * <li>支持构建器模式便于灵活创建</li>
 * <li>包含答题记录ID便于前端操作</li>
 * <li>与AnswerRecordBatchResponse配合使用处理批量场景</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 3.0.0
 * @since 2026
 * @see AnswerRecordBatchResponse 批量答题记录响应DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "答题记录响应")
public class AnswerRecordResponse {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Builder.Default
    private boolean timestampUtc=true;
    public String getTimeBasis() { return timestampUtc ? "UTC" : "LEGACY_LOCAL"; }
    @com.fasterxml.jackson.annotation.JsonGetter("answeredAt")
    public String learningTimestampForJson() {
        return com.zhixuewuli.common.util.LearningTimeWire.format(answeredAt,timestampUtc);
    }

    private Long eventId;
    private String requestId;
    private boolean practice;
    private Long attemptOrder;
    private String studentName;

    // ==================== 基础答题信息 ====================

    /**
     * 答题记录ID
     * 
     * 答题记录的唯一标识符，用于前端操作和定位。
     */
    @Schema(description = "答题记录ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String recordId;

    /**
     * 用户ID
     * 
     * 答题用户的唯一标识符，用于关联用户信息。
     */
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    /**
     * 题目ID
     * 
     * 对应真实题目主键，用于前端继续发起错题分析、追问等与题目相关的操作。
     */
    @Schema(description = "题目ID", example = "1001")
    private Long questionId;

    /**
     * 用户提交的答案
     * 
     * 用户实际选择的答案选项。
     */
    @Schema(description = "用户答案", example = "A")
    private String userAnswer;

    /**
     * 答题正确性标志
     * 
     * 标识用户对该题的回答是否正确。
     * true表示回答正确，false表示回答错误。
     */
    @Schema(description = "答题是否正确", example = "true")
    private Boolean correct;

    /**
     * 答题时间
     * 
     * 用户实际答题的时间戳。
     */
    @Schema(description = "答题时间", example = "2026-01-15T14:30:00")
    private LocalDateTime answeredAt;

    // ==================== 题目详细信息 ====================

    /**
     * 题目内容
     * 
     * 题目的完整文本内容。
     */
    @Schema(description = "题目内容", example = "一个质量为2kg的物体...求加速度是多少？")
    private String questionContent;

    /**
     * 题目选项
     * 
     * 题目的可选项键值对映射，key为选项标识(A/B/C/D)，value为选项内容。
     */
    @Schema(description = "题目选项键值对映射")
    private Map<String, String> questionOptions;

    /**
     * 正确答案
     * 
     * 题目的标准正确答案。
     */
    @Schema(description = "正确答案", example = "C")
    private String correctAnswer;

    /**
     * 题目解析
     * 
     * 题目的详细解析说明。
     */
    @Schema(description = "题目解析", example = "根据牛顿第二定律F=ma...")
    private String questionAnalysis;

    /**
     * 题目难度
     * 
     * 题目的难度等级。
     */
    @Schema(description = "题目难度", example = "中等")
    private String difficulty;

    /**
     * 题目图片编码
     * 
     * 题目相关的图片Base64编码。
     */
    @Schema(description = "题目图片编码", example = "/9j/4AAQSkZJRgABAQAAAQABAAD...")
    private String imageBase64;

    // ==================== 会话信息 ====================

    /**
     * 会话ID
     * 
     * 关联的学习会话标识符。
     */
    @Schema(description = "会话ID", example = "sess_20260101_123456")
    private String sessionId;
}