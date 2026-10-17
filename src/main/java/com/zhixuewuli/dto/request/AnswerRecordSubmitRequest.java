package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答题记录提交请求DTO
 * 
 * 用于提交用户答题记录的基本信息，包括题目ID、用户答案和答题时间等核心字段。
 * 该DTO主要用于答题记录的创建和存储操作。
 * 
 * <p>
 * 核心字段说明：
 * </p>
 * <ul>
 * <li><strong>questionId</strong>: 关联的题目ID，必须存在</li>
 * <li><strong>userAnswer</strong>: 用户的实际答案内容</li>
 * <li><strong>errorReason</strong>: 用户对错误原因的主观描述（可选）</li>
 * <li><strong>answeredAt</strong>: 答题时间戳，默认为当前时间</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Schema(description = "答题记录提交请求参数")
@Data
public class AnswerRecordSubmitRequest {
    private Long eventId;

    /**
     * 题目ID
     * 
     * 必须填写，不能为空。
     * 关联系统中已存在的题目记录。
     */
    @NotNull(message = "题目ID不能为空")
    @Schema(description = "题目ID", example = "2001")
    private Long questionId;

    /**
     * 用户答案
     * 
     * 必须填写，不能为空白字符串。
     * 用户对该题目的实际作答内容。
     */
    @NotBlank(message = "用户答案不能为空")
    @Schema(description = "用户答案", example = "A")
    private String userAnswer;

    /**
     * 错误原因
     * 
     * 可选填写，用户对自己错误原因的主观描述。
     * 用于辅助错误分析和学习改进建议。
     * 长度限制不超过200个字符。
     */
    @Size(max = 200, message = "错误原因长度不能超过200个字符")
    @Schema(description = "错误原因", example = "对电磁感应定律的理解不够深入")
    private String errorReason;

    /**
     * 答题时间
     * 
     * 可选填写，用户实际答题的具体时间点。
     * 用于学习行为时间序列分析和答题效率评估。
     * 如果未提供，系统将使用当前时间作为默认值。
     */
    @Schema(description = "答题时间", example = "2026-01-15T14:30:00")
    private LocalDateTime answeredAt;
}