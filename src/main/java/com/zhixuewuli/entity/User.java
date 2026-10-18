package com.zhixuewuli.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户实体类 - 最小化设计，仅为答题系统服务
 * 
 * <p>
 * 对应数据库表：users，存储系统用户的基本信息。
 * 支持学生和教师两种用户类型，提供身份认证和权限管理基础。
 * </p>
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>用户身份认证和登录管理</li>
 * <li>区分学生和教师角色权限</li>
 * <li>关联学生答题记录和教师事件</li>
 * <li>支持班级维度的用户分组管理</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>用户名全局唯一，支持字母、数字和下划线组合</li>
 * <li>真实姓名唯一标识，便于线下身份对应</li>
 * <li>支持用户激活状态控制</li>
 * <li>记录最后登录时间便于安全审计</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "uk_real_name", columnList = "real_name"),
        @Index(name = "idx_class_user_active", columnList = "class_name, user_type, active")
})
@Schema(description = "用户实体")
@Data
@EqualsAndHashCode(callSuper = false)
public class User extends BaseTimeEntity {
    @Column(name="token_version",nullable=false,columnDefinition="BIGINT DEFAULT 0")
    private Long tokenVersion=0L;

    /**
     * 用户唯一标识符，主键自增长
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "用户 ID", example = "1001")
    private Long id;
    
    /**
     * 用户名 - 登录凭证
     * 
     * <p>
     * 格式要求：只能包含字母 (a-z,A-Z)、数字 (0-9) 和下划线 (_)，长度 3-50 位。
     * 示例：zhangsan123, teacher_wang, student_2026
     * </p>
     * 
     * <p>
     * 约束条件：全局唯一且非空，用于用户登录认证。
     * </p>
     */
    @Column(nullable = false, unique = true, length = 50)
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名只能包含字母、数字和下划线，长度3-50位")
    @Schema(description = "用户名", example = "zhangsan123")
    private String username;
    
    /**
     * 密码哈希值
     * 
     * <p>
     * 存储经过哈希加密的密码，采用 BCrypt 或其他安全算法。
     * 最大长度 255 字符，确保存储安全性。
     * </p>
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    @Schema(description = "密码哈希值", hidden = true)
    private String passwordHash;
    
    /**
     * 用户类型 - 区分学生和教师角色
     * 
     * <p>
     * 取值范围：
     * </p>
     * <ul>
     * <li>STUDENT - 学生用户：可以查看视频、参与答题、生成学习报告</li>
     * <li>TEACHER - 教师用户：可以发布学习事件、查看学生数据、管理题目</li>
     * </ul>
     * 
     * <p>
     * 默认值：STUDENT（新建用户默认为学生身份）
     * </p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    @Schema(description = "用户类型", example = "STUDENT")
    private UserType userType = UserType.STUDENT;
    
    /**
     * 真实姓名
     * 
     * <p>
     * 用户的实名认证姓名，用于线下身份对应和班级管理。
     * 全局唯一且非空，便于教师识别学生身份。
     * </p>
     */
    @Column(name = "real_name", unique = true, length = 100)
    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;
    
    /**
     * 班级名称
     * 
     * <p>
     * 用户所属的班级标识，用于按班级维度组织和管理学生。
     * 示例：高二 (1) 班，初三 (2) 班
     * </p>
     */
    @Column(name = "class_name", length = 50)
    @Schema(description = "班级名称", example = "高二 (1) 班")
    private String className;
    
    /**
     * 激活状态标识
     * 
     * <p>
     * 标识用户账号是否处于激活可用状态。
     * true - 账号正常可用；false - 账号被禁用
     * </p>
     * 
     * <p>
     * 默认值：true（新建用户默认激活）
     * </p>
     */
    @Column(name = "active")
    @Schema(description = "是否激活", example = "true")
    private Boolean active = true;
    
    /**
     * 最后登录时间
     * 
     * <p>
     * 记录用户最近一次成功登录系统的时间点。
     * 用于安全审计和用户活跃度分析。
     * </p>
     */
    @Column(name = "last_login")
    @Schema(description = "最后登录时间", example = "2026-01-15T14:30:00")
    private LocalDateTime lastLogin;

    // 时间字段由BaseTimeEntity基类提供，自动管理

    /**
     * 用户的答题记录集合，一对多关系
     * 
     * 用于教师查看学生答题情况、统计分析等场景。
     * mappedBy = "user" 表示由AnswerRecord实体中的user字段维护关系
     * fetch = FetchType.LAZY 延迟加载，提高查询性能
     */
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AnswerRecord> answerRecords;

    /**
     * 用户类型枚举 - 只区分学生和教师
     * 
     * <p>
     * 使用场景说明：
     * </p>
     * <ul>
     * <li><b>STUDENT（学生）</b>：
     *   <ul>
     *     <li>观看教学视频并完成配套练习</li>
     *     <li>参与实时问答和错题分析</li>
     *     <li>接收 AI 生成的个性化学习报告</li>
     *     <li>查看个人答题记录和学习进度</li>
     *   </ul>
     * </li>
     * <li><b>TEACHER（教师）</b>：
     *   <ul>
     *     <li>发布学习事件并设置时间节点</li>
     *     <li>查看所带班级学生的答题情况</li>
     *     <li>分析学生学习数据和薄弱知识点</li>
     *     <li>管理题目库和实验配置</li>
     *   </ul>
     * </li>
     * </ul>
     * 
     * <p>
     * 权限隔离：两种用户类型在前端界面和后端 API 访问上有明确的权限区分。
     * </p>
     */
    public enum UserType {
        /** 学生用户 - 学习功能主体 */
        STUDENT,
        /** 教师用户 - 教学管理主体 */
        TEACHER
    }
}
