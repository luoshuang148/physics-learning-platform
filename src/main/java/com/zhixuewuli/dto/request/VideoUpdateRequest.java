package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 视频更新请求DTO
 * 
 * 用于接收更新视频信息的请求参数
 * 实现全量覆盖更新策略：即使字段为空也会覆盖原值
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>所有字段均为可选，支持部分更新</li>
 * <li>采用全量覆盖策略，null值会清空原有字段</li>
 * <li>保持与创建请求相同的验证规则</li>
 * <li>包含视频ID用于定位要更新的记录</li>
 * </ul>
 * 
 * <p>
 * 更新策略说明：
 * </p>
 * <ul>
 * <li>当字段值为null时，会将数据库中原有值设置为null</li>
 * <li>当字段值为空字符串时，会将数据库中原有值设置为空字符串</li>
 * <li>只有显式传递的字段才会被更新</li>
 * <li>未传递的字段保持原值不变</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoCreateRequest 视频创建请求DTO
 */
@Data
@Schema(description = "视频更新请求")
public class VideoUpdateRequest {

    @Schema(description = "视频ID", example = "1")
    private Long id;

    // 其他字段与VideoCreateRequest相同，但都为可选字段
    // 实现全量覆盖更新：即使字段为空也会覆盖原值

    @NotBlank(message = "视频标题不能为空")
    @Size(max = 200, message = "视频标题长度不能超过200个字符")
    @Schema(description = "视频标题", example = "更新后的牛顿第二定律详解")
    private String title;

    @NotBlank(message = "视频播放链接不能为空")
    @Pattern(
            regexp = "^(https?://.+|/videos/.+|/api/v1/videos/.+)$",
            message = "视频链接必须是有效的地址"
    )
    @Schema(description = "视频播放链接", example = "https://example.com/updated-video1.mp4")
    private String url;

    @Size(max = 100, message = "知识点名称长度不能超过100个字符")
    @Schema(description = "关联的知识点名称", example = "更新的牛顿第二定律")
    private String knowledgePoint;

    @Pattern(regexp = "^https?://.+$", message = "封面链接必须是有效的HTTP或HTTPS地址")
    @Schema(description = "视频封面图片链接", example = "https://example.com/updated-cover1.jpg")
    private String coverUrl;

    @Min(value = 1, message = "视频时长必须大于0秒")
    @Max(value = 86400, message = "视频时长不能超过24小时")
    @Schema(description = "视频时长（秒）", example = "350")
    private Integer duration;

    @Size(max = 1000, message = "视频描述长度不能超过1000个字符")
    @Schema(description = "视频描述信息", example = "更新后的详细讲解牛顿第二定律的应用")
    private String description;

    @Schema(description = "关联的题目ID", example = "1001")
    private Long questionId;
}