package com.zhixuewuli.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求 DTO
 * 
 * 封装用户登录时提交的凭证信息，包括用户名和密码。
 * 用于接收前端登录表单数据并进行服务端验证。
 * 
 * <p>
 * 字段说明：
 * </p>
 * <ul>
 * <li><strong>username</strong>: 登录标识，只接受用户名</li>
 * <li><strong>password</strong>: 登录密码，需符合安全要求</li>
 * </ul>
 * 
 * <p>
 * 验证规则：
 * </p>
 * <ul>
 * <li>登录标识：非空，最大 50 字符</li>
 * <li>密码：非空</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.controller.AuthController 认证控制器
 */
@Schema(description = "登录请求参数")
@Data
public class LoginRequest {

    /**
     * 登录标识
     * 
     * 只接受用户名；真实姓名不能作为登录凭证。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50位")
    private String username;

    /**
     * 登录密码
     * 
     * 用户登录系统的凭证，将在服务端进行安全加密存储。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
