package com.zhixuewuli.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI问答请求参数DTO
 * 
 * 封装用户向AI提问时所需的各种参数信息。
 * 用于AI问答接口的请求数据传输对象。
 * 
 * <p>
 * 参数说明：
 * </p>
 * <ul>
 * <li><strong>question</strong>: 必填项，用户提出的具体问题</li>
 * <li><strong>context</strong>: 可选项，问题相关的上下文信息</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>学生在学习过程中提出疑问</li>
 * <li>请求AI对特定知识点进行解释</li>
 * <li>获取学习建议和指导</li>
 * <li>寻求解题思路和方法</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.controller.AIController AI控制器
 */
@Schema(description = "AI问答请求参数")
@Data
public class AIAskRequest {

    /**
     * 用户提出的问题内容
     * 
     * 必须填写，不能为空白字符串，且长度不能超过800个字符。
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 800, message = "问题内容长度不能超过800个字符")
    @Schema(description = "问题内容", example = "什么是牛顿第二定律？")
    private String question;

    /**
     * 题目ID
     * 
     * 如果是实时问答请求，则该字段必须为空，如果是错题分析请求，则该字段必填。
     * 关联系统中已存在的题目记录。
     */
    @Schema(description = "题目ID", example = "2001")
    private Long questionId;

    /**
     * 图片Base64编码（仅支持PNG格式）
     * 
     * 可选字段，用户上传的图片内容经过Base64编码后的字符串。
     * 用于支持图片问答功能，如物理实验图、电路图等。
     * 格式要求：data:image/png;base64,[编码数据]
     */
    @Schema(description = "图片Base64编码（仅支持PNG格式）", example = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwADhgGAWjR9awAAAABJRU5ErkJggg==")
    private String imageBase64;
}
