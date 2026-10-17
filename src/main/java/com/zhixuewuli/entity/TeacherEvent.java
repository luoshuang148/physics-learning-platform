package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 教师事件实体类
 * 
 * <p>
 * 对应数据库表：teacher_events，用于存储教师发布的全局学习事件信息。
 * 作为学生学习进度统计和报告生成的时间基准点。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>标记全局教学节点（如期中考试、实验课、章节测试等）</li>
 * <li>为学习报告提供时间参考基准</li>
 * <li>支持按事件维度统计学生学习数据</li>
 * <li>记录教师对学生的学习期望和目标</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>全局唯一事件：整个系统只存在一个当前有效事件</li>
 * <li>事件一旦发布不可修改，只能删除后重新发布</li>
 * <li>所有操作基于教师身份认证，无需传递事件 ID</li>
 * <li>事件时间戳由前端指定，支持历史事件补录</li>
 * </ul>
 * 
 * <p>
 * 业务规则：
 * </p>
 * <ul>
 * <li><b>全局唯一性约束</b>：整个系统只能存在一条 is_global=true 的记录</li>
 * <li><b>发布流程</b>：发布新事件前必须先删除已有事件</li>
 * <li><b>权限控制</b>：只有教师可以创建/删除事件，所有认证用户可查询事件</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Entity
@Table(name = "teacher_events", indexes = {
        @Index(name = "idx_is_global", columnList = "is_global")
})
@Schema(description = "教师事件实体")
@Data
@EqualsAndHashCode(callSuper = false)
public class TeacherEvent extends BaseTimeEntity {

    @Column(name = "active_slot", unique = true, insertable = false, updatable = false,
            columnDefinition = "integer GENERATED ALWAYS AS (CASE WHEN is_global = TRUE THEN 1 ELSE NULL END)")
    private Integer activeSlot;

    /**
     * 事件唯一标识符 - 主键自增长
     * 
     * <p>
     * 数据库自动生成的唯一 ID，用于内部管理和持久化。
     * </p>
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "事件 ID", example = "1")
    private Long id;

    /**
     * 关联教师 ID - 外键引用
     * 
     * <p>
     * 指向 users 表的外键，标识发布该事件的教师用户。
     * 该字段来源于 JWT 令牌中的教师身份信息。
     * </p>
     * 
     * <p>
     * 约束条件：非空
     * </p>
     */
    @Column(name = "teacher_id", nullable = false)
    @NotNull(message = "教师 ID 不能为空")
    @Schema(description = "教师 ID", example = "1001")
    private Long teacherId;

    /**
     * 关联的教师用户对象 - 一对一懒加载
     * 
     * <p>
     * 用于 ORM 关联查询，可以直接获取教师的完整信息。
     * insertable 和 updatable 设为 false，避免与 teacherId 字段冲突。
     * </p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    private User teacher;

    /**
     * 教师真实姓名 - 冗余存储
     * 
     * <p>
     * 从 User 表同步的教师姓名，避免频繁关联查询。
     * 便于快速展示和统计，提高查询性能。
     * </p>
     * 
     * <p>
     * 约束条件：
     * </p>
     * <ul>
     * <li>非空校验：必须填写教师姓名</li>
     * <li>长度限制：最多 100 个字符</li>
     * </ul>
     */
    @Column(name = "teacher_name", nullable = false, length = 100)
    @NotBlank(message = "教师姓名不能为空")
    @Size(max = 100, message = "教师姓名长度不能超过100字符")
    @Schema(description = "教师姓名", example = "王老师")
    private String teacherName;

    /**
     * 事件标题 - 简短标识
     * 
     * <p>
     * 事件的简短标题，用于快速识别事件类型。
     * 示例："期中考试"、"力学实验课"、"第三章测试"
     * </p>
     * 
     * <p>
     * 约束条件：
     * </p>
     * <ul>
     * <li>非空校验：必须填写事件标题</li>
     * <li>长度限制：最多 20 个字符（保持简洁）</li>
     * </ul>
     */
    @Column(name = "event_title", nullable = false, length = 20)
    @NotBlank(message = "事件标题不能为空")
    @Size(max = 20, message = "事件标题长度不能超过20字符")
    @Schema(description = "事件标题", example = "期中考试")
    private String eventTitle;

    /**
     * 事件描述 - 详细说明
     * 
     * <p>
     * 对事件的具体说明，包括考试范围、实验内容、测试重点等。
     * 可选字段，支持更详细的信息补充。
     * </p>
     * 
     * <p>
     * 示例："本次期中考试范围为必修一第一章至第三章，包括运动的描述、匀变速直线运动、相互作用"
     * </p>
     * 
     * <p>
     * 约束条件：最多 100 个字符
     * </p>
     */
    @Column(name = "event_description", length = 100)
    @Size(max = 100, message = "事件描述长度不能超过100字符")
    @Schema(description = "事件描述", example = "范围：必修一第 1-3 章")
    private String eventDescription;

    /**
     * 事件时间戳 - 学习时间基准点
     * 
     * <p>
     * 事件发生的时间点，作为学生学习进度统计的基准。
     * 由前端根据实际教学安排指定，支持历史事件补录。
     * </p>
     * 
     * <p>
     * 业务用途：
     * </p>
     * <ul>
     * <li>统计学生在事件前的学习完成情况</li>
     * <li>生成基于时间节点的学习报告</li>
     * <li>分析教学进度与学生学习效果的关系</li>
     * </ul>
     */
    @Column(name = "event_timestamp", nullable = false)
    @NotNull(message = "事件时间戳不能为空")
    @Schema(description = "事件时间戳", example = "2026-11-15T10:00:00")
    private LocalDateTime eventTimestamp;

    /**
     * 是否为全局事件 - 标识字段
     * 
     * <p>
     * 标识该事件是否为全局事件。
     * 全局事件在整个系统中唯一，所有用户共享同一个教师事件。
     * </p>
     * 
     * <p>
     * 默认值：true（新建事件默认为全局事件）
     * </p>
     */
    @Column(name = "is_global", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Schema(description = "是否为全局事件", example = "true")
    private Boolean isGlobal = true;
}
