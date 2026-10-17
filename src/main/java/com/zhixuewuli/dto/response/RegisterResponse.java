package com.zhixuewuli.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户注册响应DTO
 * 
 * 封装用户注册成功后的响应信息，用于向前端返回注册结果。
 * 包含基本的成功提示和用户标识信息。
 * 
 * <p>
 * 设计理念：
 * </p>
 * <ul>
 * <li><strong>简约原则</strong>: 注册成功后只需返回必要信息</li>
 * <li><strong>安全考虑</strong>: 不返回敏感信息如密码等</li>
 * <li><strong>用户体验</strong>: 提供清晰的成功反馈</li>
 * </ul>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>用户完成注册后的成功提示</li>
 * <li>前端跳转到登录页面的依据</li>
 * <li>注册流程完成状态的确认</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see com.zhixuewuli.controller.AuthController 认证控制器
 * @see com.zhixuewuli.service.auth.AuthService 认证服务
 */
@Schema(description = "注册响应数据")
@Data
public class RegisterResponse {

    /**
     * 注册结果消息
     * 
     * 描述注册操作的结果状态，通常为成功提示信息。
     * 例如："注册成功，请登录" 或 "账户创建完成"
     */
    @Schema(description = "注册结果消息", example = "注册成功，请登录系统开始学习")
    private String message;

    /**
     * 用户名
     * 
     * 刚刚注册成功的用户名，用于确认注册的账户信息。
     * 可用于前端显示或自动填充登录表单。
     */
    @Schema(description = "注册用户名", example = "student001")
    private String username;

    /**
     * 用户类型
     * 
     * 注册用户的类型标识，用于前端界面个性化展示。
     * 可选值：STUDENT（学生）、TEACHER（教师）
     */
    @Schema(description = "用户类型", example = "STUDENT")
    private String userType;

    /**
     * 构造函数 - 创建注册成功响应
     * 
     * @param username 注册成功的用户名
     * @param userType 用户类型
     */
    public RegisterResponse(String username, String userType) {
        this.message = "注册成功，请登录系统开始学习";
        this.username = username;
        this.userType = userType;
    }

    /**
     * 默认构造函数
     */
    public RegisterResponse() {
    }
}