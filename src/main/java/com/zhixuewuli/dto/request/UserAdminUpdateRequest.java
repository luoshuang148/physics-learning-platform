package com.zhixuewuli.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.zhixuewuli.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新请求 DTO
 * 
 * 用于管理员（教师）更新用户信息的请求数据传输对象。
 * 仅包含管理员可以修改的字段：真实姓名、班级、账户状态、用户类型。
 * 不包含用户名 (username) 和密码 (password) 字段。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>userId</strong>: 目标用户 ID，必填</li>
 * <li><strong>realName</strong>: 用户真实姓名</li>
 * <li><strong>className</strong>: 所属班级名称</li>
 * <li><strong>active</strong>: 账户激活状态</li>
 * <li><strong>userType</strong>: 用户类型（学生/教师）</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User.UserType 用户类型枚举
 */
@Data
@Schema(description = "管理员更新用户请求")
public class UserAdminUpdateRequest {

    /**
     * 目标用户 ID
     * 
     * 指定要更新的目标用户的唯一标识符。
     */
    @Schema(description = "目标用户 ID", example = "123")
    @JsonAlias("id")
    @NotNull(message = "目标用户ID不能为空")
    private Long userId;

    /**
     * 真实姓名
     * 
     * 用户的真实姓名，用于界面显示和身份识别。
     */
    @Schema(description = "真实姓名", example = "李四")
    private String realName;

    /**
     * 班级名称
     * 
     * 学生用户的班级归属信息，用于分组管理和统计分析。
     */
    @Schema(description = "班级名称", example = "高三 (2) 班")
    private String className;

    /**
     * 账户状态
     * 
     * 标识用户账户是否处于激活状态。
     * true-激活，false-禁用。
     */
    @Schema(description = "账户状态", example = "true")
    private Boolean active;

    /**
     * 用户类型
     * 
     * 标识用户的角色类型，决定可访问的功能范围。
     * 可选值：STUDENT（学生）、TEACHER（教师）
     */
    @Schema(description = "用户类型", example = "STUDENT")
    private User.UserType userType;

    // 注意：不包含 username 和 password 字段
}
