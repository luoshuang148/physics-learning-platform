package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 视频响应数据DTO
 * 
 * 用于向前端返回视频的完整信息
 * 包含视频的基本属性和时间戳信息
 * 
 * <p>
 * 数据结构：
 * </p>
 * <ul>
 * <li>视频基础信息（ID、标题、链接等）</li>
 * <li>关联信息（知识点、封面等）</li>
 * <li>时间信息（创建时间、更新时间）</li>
 * <li>辅助信息（时长、描述等）</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>视频详情查询响应</li>
 * <li>视频列表查询响应</li>
 * <li>视频创建/更新成功响应</li>
 * <li>视频删除前的信息确认</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.entity.Video 视频实体类
 */
@Schema(description = "视频响应数据")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoResponse {

    @Schema(description = "视频ID")
    private Long id;

    @Schema(description = "视频标题")
    private String title;

    @Schema(description = "视频播放链接")
    private String url;

    @Schema(description = "关联的知识点名称")
    private String knowledgePoint;

    @Schema(description = "视频封面图片链接")
    private String coverUrl;

    @Schema(description = "关联的题目 ID")
    private Long questionId;

    @Schema(description = "视频时长（秒）")
    private Integer duration;

    @Schema(description = "视频描述信息")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}