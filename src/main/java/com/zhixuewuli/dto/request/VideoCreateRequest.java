package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 视频创建请求 DTO
 * 
 * 用于接收创建新视频的请求参数。
 * 包含视频的基本信息和验证规则。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>title</strong>: 视频标题，必填</li>
 * <li><strong>url</strong>: 视频播放链接，必填</li>
 * <li><strong>knowledgePoint</strong>: 关联的知识点名称（可选）</li>
 * <li><strong>coverUrl</strong>: 视频封面图片链接（可选）</li>
 * <li><strong>duration</strong>: 视频时长（秒）</li>
 * <li><strong>description</strong>: 视频描述信息（可选）</li>
 * <li><strong>questionId</strong>: 关联的题目 ID（可选）</li>
 * </ul>
 * 
 * <p>
 * 验证规则：
 * </p>
 * <ul>
 * <li>标题不能为空且长度不超过 200 字符</li>
 * <li>视频链接支持 HTTP/HTTPS 绝对地址，或 /videos/ 开头的相对地址</li>
 * <li>知识点名称长度不超过 100 字符</li>
 * <li>封面链接必须是有效的 HTTP 或 HTTPS 地址</li>
 * <li>视频时长必须在合理范围内（1-86400 秒）</li>
 * <li>描述信息长度不超过 1000 字符</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoUpdateRequest 视频更新请求 DTO
 */
@Data
@Schema(description = "视频创建请求")
public class VideoCreateRequest {

    /**
     * 视频标题
     * 
     * 视频的名称，用于展示和识别。
     * 长度不能超过 200 个字符。
     */
    @NotBlank(message = "视频标题不能为空")
    @Size(max = 200, message = "视频标题长度不能超过200个字符")
    @Schema(description = "视频标题", example = "牛顿第二定律详解")
    private String title;

    /**
     * 视频播放链接
     * 
     * 视频文件的在线播放地址。
     * 支持 http/https 绝对地址，或 /videos/、/api/v1/videos/ 开头的相对地址。
     */
    @NotBlank(message = "视频播放链接不能为空")
    @Pattern(
            regexp = "^(https?://.+|/videos/.+|/api/v1/videos/.+)$",
            message = "视频链接必须是有效的地址"
    )
    @Schema(description = "视频播放链接", example = "https://example.com/video1.mp4")
    private String url;

    /**
     * 关联的知识点名称
     * 
     * 视频所讲解的知识点名称，用于分类和检索。
     * 长度不能超过 100 个字符。
     */
    @Size(max = 100, message = "知识点名称长度不能超过100个字符")
    @Schema(description = "关联的知识点名称", example = "牛顿第二定律")
    private String knowledgePoint;

    /**
     * 视频封面图片链接
     * 
     * 视频封面的图片地址，用于列表和详情页面展示。
        * 支持 HTTP 或 HTTPS 协议。
     */
    @Pattern(regexp = "^https?://.+$", message = "封面链接必须是有效的HTTP或HTTPS地址")
    @Schema(description = "视频封面图片链接", example = "https://example.com/cover1.jpg")
    private String coverUrl;

    /**
     * 视频时长（秒）
     * 
     * 视频的总播放时长，以秒为单位。
     * 取值范围：1-86400 秒（24 小时）。
     */
    @Min(value = 1, message = "视频时长必须大于0秒")
    @Max(value = 86400, message = "视频时长不能超过24小时")
    @Schema(description = "视频时长（秒）", example = "300")
    private Integer duration;

    /**
     * 视频描述信息
     * 
     * 视频的详细介绍文本，用于帮助用户了解视频内容。
     * 长度不能超过 1000 个字符。
     */
    @Size(max = 1000, message = "视频描述长度不能超过1000个字符")
    @Schema(description = "视频描述信息", example = "详细讲解牛顿第二定律的应用")
    private String description;

    /**
     * 关联的题目 ID
     * 
     * 与该视频相关联的题目 ID，用于建立视频与题目的映射关系。
     */
    @Schema(description = "关联的题目 ID", example = "1001")
    private Long questionId;
}
