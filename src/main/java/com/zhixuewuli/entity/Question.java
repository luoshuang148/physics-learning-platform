package com.zhixuewuli.entity;

import org.apache.commons.codec.digest.DigestUtils;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题目实体类
 * 
 * 对应数据库中的 questions 表，存储练习题目的完整信息。
 * 每道题目独立存在，可被视频通过 questionId 字段关联。
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>题目实体完全独立，不直接关联视频</li>
 * <li>支持多种题型和选项格式</li>
 * <li>包含难度分级和时间定位</li>
 * <li>提供详细的答案解析</li>
 * </ul>
 * 
 * <p>
 * 业务价值：
 * </p>
 * <ul>
 * <li>支撑在线答题系统的核心数据</li>
 * <li>实现视频学习效果的即时检测</li>
 * <li>为学习路径推荐提供数据基础</li>
 * <li>支持个性化学习路径跳转</li>
 * <li>支持学习数据分析和个性化推荐</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "uk_content_hash", columnList = "content_hash"),
        @Index(name = "idx_difficulty", columnList = "difficulty"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class Question extends BaseTimeEntity {

    /** 题目唯一标识符，自动生成 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 题目内容 - 统一使用LaTeX格式存储，全局唯一
     * 所有物理量、单位、数学表达式必须使用LaTeX语法
     * 示例：质量为$2\,\mathrm{kg}$的物体，速度$v=3\,\mathrm{m/s}$
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String contentHash; // 用于唯一性约束

    /**
     * 题目选项 - 使用Map结构存储，key为选项标识(A/B/C/D)，value为LaTeX格式内容
     * 存储格式示例：{"A": "受到$10\,\mathrm{N}$的力", "B": "产生$5\,\mathrm{m/s^2}$的加速度"}
     */
    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String options;

    /** 正确答案选项标识 (A/B/C/D) */
    @Column(name = "correct_option", nullable = false, length = 50)
    private String correctOption;

    /**
     * 题目解析说明 - 统一使用LaTeX格式
     * 支持复杂的数学推导和公式展示
     */
    @Column(columnDefinition = "LONGTEXT")
    private String explanation;

    /** 题目难度等级，取值范围1-5 */
    private Integer difficulty = 1;

    /**
     * 讲解视频映射 - key为选项标识，value为视频标题
     * 存储格式示例：{"A": "牛顿第一定律讲解", "B": "力学基础概念", "C": "运动学分析", "D": "动力学原理"}
     * 其中正确答案的视频标题也包含在此映射中
     */
    @Column(name = "explanation_video_mapping", columnDefinition = "LONGTEXT")
    private String explanationVideoMapping;

    /**
     * 题目图片Base64编码数据（仅支持PNG格式）
     * 
     * 可选字段，用于存储与题目相关的图片信息。
     * 格式要求：data:image/png;base64,[编码数据]
     */
    @Column(name = "image_base64", columnDefinition = "LONGTEXT")
    private String imageBase64;

    /**
     * 在持久化前自动计算 contentHash
     */
    @PrePersist
    @PreUpdate
    private void computeHash() {
        if (this.content != null) {
            // 使用Apache Commons Codec或手动实现SHA-256
            this.contentHash = DigestUtils.sha256Hex(this.content);
        } else {
            this.contentHash = null; // 但content不能为null，所以不会执行到这里
        }
    }
}
