package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频实体类
 * 
 * <p>
 * 对应数据库表：videos，存储教学视频的基本信息。
 * 每个视频记录包含标题、链接、知识点关联等核心属性。
 * </p>
 * 
 * <p>
 * 业务用途：
 * </p>
 * <ul>
 * <li>存储和管理教学视频资源</li>
 * <li>关联视频与知识点的关系</li>
 * <li>支持视频播放统计和分析</li>
 * <li>提供视频搜索和筛选功能</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>视频标题和链接全局唯一，避免重复上传</li>
 * <li>支持封面图片和时长等元数据</li>
 * <li>可关联题目实现视频 - 练习联动</li>
 * <li>TEXT 类型描述支持详细说明</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Entity
@Table(name = "videos", indexes = {
                @Index(name = "idx_created_at", columnList = "created_at")
})
@Schema(description = "视频实体")
@Data
@EqualsAndHashCode(callSuper = false)
public class Video extends BaseTimeEntity {

        /**
         * 视频唯一标识符 - 主键自增长
         * 
         * <p>
         * 数据库自动生成的唯一 ID，用于标识每个视频资源。
         * </p>
         */
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Schema(description = "视频 ID", example = "1")
        private Long id;

        /**
         * 视频标题 - 全局唯一标识
         * 
         * <p>
         * 视频的名称标识，用于展示和检索。
         * 示例："牛顿第一定律讲解"、"自由落体运动分析"
         * </p>
         * 
         * <p>
         * 约束条件：非空且全局唯一，建立索引便于快速查找。
         * </p>
         */
        @Column(nullable = false, unique = true)
        @Schema(description = "视频标题", example = "牛顿第一定律讲解")
        private String title;

        /**
         * 视频播放链接 - 全局唯一
         * 
         * <p>
         * 视频文件的在线播放地址或 CDN 链接。
         * 示例："https://cdn.example.com/videos/newton-law.mp4"
         * </p>
         * 
         * <p>
         * 约束条件：非空且全局唯一，确保每个视频链接独立。
         * </p>
         */
        @Column(nullable = false, unique = true)
        @Schema(description = "视频播放链接", example = "https://cdn.example.com/videos/newton-law.mp4")
        private String url;

        /**
         * 关联知识点名称
         * 
         * <p>
         * 视频所讲解的物理知识点或章节名称。
         * 示例："牛顿运动定律"、"机械能守恒"
         * </p>
         * 
         * <p>
         * 业务用途：用于知识图谱构建和个性化推荐。
         * </p>
         */
        @Schema(description = "知识点名称", example = "牛顿运动定律")
        private String knowledgePoint;

        /**
         * 视频封面图片链接
         * 
         * <p>
         * 视频预览封面的 URL 地址。
         * 用于前端列表和卡片展示。
         * </p>
         */
        @Schema(description = "封面图片链接", example = "https://cdn.example.com/covers/newton-law.jpg")
        private String coverUrl;

        /**
         * 视频时长 - 单位秒
         * 
         * <p>
         * 视频的总播放时长，以秒为单位。
         * 示例：300（表示 5 分钟）
         * </p>
         * 
         * <p>
         * 业务用途：用于学习时长统计和进度管理。
         * </p>
         */
        @Schema(description = "视频时长 (秒)", example = "300")
        private Integer duration;

        /**
         * 视频描述信息 - 详细说明
         * 
         * <p>
         * 对视频内容、教学目标、适用对象等的详细描述。
         * 支持长文本，使用 TEXT 类型存储。
         * </p>
         * 
         * <p>
         * 示例："本节视频详细讲解牛顿第一定律的概念、历史背景和应用场景..."
         * </p>
         */
        @Column(columnDefinition = "TEXT")
        @Schema(description = "视频描述", example = "详细讲解牛顿第一定律的概念和应用")
        private String description;

        /**
         * 关联题目 ID - 一对一映射
         * 
         * <p>
         * 与该视频相关联的题目 ID。
         * 用于实现视频学习后的即时练习功能。
         * </p>
         * 
         * <p>
         * 业务逻辑：学生观看视频后自动跳转到对应的练习题进行巩固。
         * </p>
         */
        @Column(name = "question_id")
        @Schema(description = "关联题目 ID", example = "2001")
        private Long questionId;

        // 时间字段由 BaseTimeEntity 基类提供，自动管理创建和更新时间
}
