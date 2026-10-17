package com.zhixuewuli.dto.request;

import com.zhixuewuli.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户自我更新请求 DTO
 * 
 * 用于用户更新自己基本信息的请求数据传输对象。
 * 仅包含用户可以自我修改的字段：用户名、密码、真实姓名、班级。角色仅兼容回传原值，不能通过此接口修改。
 * 不包含账户状态 (active) 字段。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>username</strong>: 用户登录名（可选）</li>
 * <li><strong>password</strong>: 登录密码（可选）</li>
 * <li><strong>realName</strong>: 用户真实姓名（可选）</li>
 * <li><strong>className</strong>: 所属班级名称（可选）</li>
 * <li><strong>userType</strong>: 用户类型（兼容字段，不可修改）</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Data
@Schema(description = "用户自我更新请求")
public class UserSelfUpdateRequest {

    /**
     * 用户名
     * 
     * 用户用于登录系统的身份标识，具有唯一性约束。
     * 需满足格式要求：3-50 位字母、数字和下划线。
     */
    @Schema(description = "用户名", example = "new_username")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名只能包含字母、数字和下划线，长度3-50位")
    private String username;

    /**
     * 密码
     * 
     * 用户登录系统的凭证，将在服务端进行安全加密存储。
     */
    @Schema(description = "密码", example = "new_password123")
    private String password;

    /**
     * 真实姓名
     * 
     * 用户的真实姓名，用于界面显示和身份识别。
     */
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    /**
     * 班级名称
     * 
     * 学生用户的班级归属信息，用于分组管理和统计分析。
     */
    @Schema(description = "班级名称", example = "高三 (1) 班")
    private String className;

    /**
     * 用户类型
     * 
     * 标识用户的角色类型，决定可访问的功能范围。
     * 可选值：STUDENT（学生）、TEACHER（教师）
     */
    @Schema(description = "兼容字段，仅可回传当前角色；修改角色必须使用教师管理接口", example = "STUDENT", deprecated = true)
    private User.UserType userType;
}
