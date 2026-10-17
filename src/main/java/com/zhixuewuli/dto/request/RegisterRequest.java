package com.zhixuewuli.dto.request;

import com.zhixuewuli.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 用户注册请求DTO
 * 
 * 封装用户注册时提交的信息，包括基本身份信息和账户设置。
 * 用于接收前端注册表单数据并进行服务端验证。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>username</strong>: 用户登录名，需满足格式要求且唯一</li>
 * <li><strong>password</strong>: 登录密码，需符合安全要求</li>
 * <li><strong>realName</strong>: 用户真实姓名，用于显示和识别</li>
 * <li><strong>userType</strong>: 用户类型（公开注册仅允许学生）</li>
 * <li><strong>className</strong>: 所属班级名称（可选）</li>
 * </ul>
 * 
 * <p>
 * 验证规则：
 * </p>
 * <ul>
 * <li>用户名：3-50位字母、数字、下划线组成</li>
 * <li>密码：非空，建议复杂度验证</li>
 * <li>真实姓名：非空</li>
 * <li>用户类型：必须选择</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User 用户实体类
 * @see com.zhixuewuli.controller.AuthController 认证控制器
 */
@Data
public class RegisterRequest {

    /**
     * 用户登录名
     * 
     * 用于系统登录的身份标识，需满足以下要求：
     * - 长度3-50个字符
     * - 只能包含字母、数字和下划线
     * - 在系统中必须唯一
     */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名只能包含字母、数字和下划线，长度3-50位")
    private String username;

    /**
     * 登录密码
     * 
     * 用户登录系统的凭证，将在服务端进行安全加密存储。
     * 建议包含大小写字母、数字和特殊字符以提高安全性。
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 用户真实姓名
     * 
     * 用户的真实姓名，用于界面显示和身份识别。
     * 教师用户可用于课堂管理和学生互动。
     */
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /**
     * 用户类型
     * 
     * 标识用户的角色类型，决定可访问的功能范围：
     * - STUDENT：学生用户，主要使用学习和答题功能
     * - TEACHER：教师用户，可访问管理功能和学生数据分析
     */
    @NotNull(message = "用户类型不能为空")
    private User.UserType userType;

    /**
     * 所属班级名称
     * 
     * 学生用户的班级归属信息，用于分组管理和统计分析。
     * 教师用户可留空或填写所属部门信息。
     */
    private String className;
}