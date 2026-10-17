package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单题学生错误详情DTO
 * 
 * 用于返回指定题目下所有答错学生的基本信息和答题详情。
 * 支持教师查看具体哪些学生在某道题目上出错以及错误详情。
 * 
 * <p>
 * 数据结构：
 * </p>
 * <ul>
 * <li>学生基本信息（ID、姓名、班级）</li>
 * <li>答题信息（答案、时间、错误原因）</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>教师点击查看具体题目错误详情</li>
 * <li>个性化辅导和针对性教学</li>
 * <li>学生错题档案管理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.entity.AnswerRecord 答题记录实体
 * @see com.zhixuewuli.entity.User 用户实体
 */
@Schema(description = "题目学生错误详情响应")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionStudentErrorDetailResponse {

    @Schema(description = "学生ID")
    private Long userId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "班级名称")
    private String className;

    @Schema(description = "学生答案")
    private String userAnswer;

    @Schema(description = "答题时间")
    private LocalDateTime answerTime;

    @Schema(description = "错误原因")
    private String errorReason;
}