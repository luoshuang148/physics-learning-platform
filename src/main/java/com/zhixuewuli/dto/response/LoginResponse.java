package com.zhixuewuli.dto.response;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应DTO
 * 
 * 封装用户登录成功后的响应信息，包括认证令牌和用户基本信息。
 * 用于向前端返回登录结果和用户身份信息。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>token</strong>: JWT认证令牌，用于后续API调用的身份验证</li>
 * <li><strong>userId</strong>: 用户唯一标识符</li>
 * <li><strong>username</strong>: 用户登录名</li>
 * <li><strong>userType</strong>: 用户类型（STUDENT/TEACHER）</li>
 * <li><strong>realName</strong>: 用户真实姓名</li>
 * </ul>
 * 
 * <p>
 * 业务价值：
 * </p>
 * <ul>
 * <li>提供安全的身份认证凭证</li>
 * <li>传递用户基础信息给前端</li>
 * <li>支持用户界面个性化展示</li>
 * <li>为权限控制提供用户类型信息</li>
 * </ul>
 * 
 * <p>
 * 安全考虑：
 * </p>
 * <ul>
 * <li>令牌包含过期时间，防止长期有效</li>
 * <li>敏感信息最小化暴露原则</li>
 * <li>支持HTTPS传输保护</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.controller.AuthController 认证控制器
 * @see com.zhixuewuli.service.auth.AuthService 认证服务
 */
@Schema(description = "登录响应数据")
@Data
public class LoginResponse {

    /**
     * JWT认证令牌
     * 
     * 用于后续API请求的身份验证凭证，包含用户身份信息和过期时间。
     * 前端需要在Authorization头部中携带此令牌。
     */
    @Schema(description = "认证令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    /**
     * 用户唯一标识符
     * 
     * 数据库中的用户主键ID，用于唯一标识用户身份。
     */
    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    /**
     * 用户登录名
     * 
     * 用户用于登录系统的用户名，具有唯一性约束。
     */
    @Schema(description = "用户名", example = "student001")
    private String username;

    /**
     * 用户类型
     * 
     * 标识用户角色类型，决定可访问的功能范围和权限级别。
     * 可选值：STUDENT（学生）、TEACHER（教师）
     */
    @Schema(description = "用户类型", example = "STUDENT")
    private String userType;

    /**
     * 用户真实姓名
     * 
     * 用户的真实姓名，用于界面显示和个人化体验。
     */
    @Schema(description = "真实姓名", example = "张三")
    private String realName;
}
