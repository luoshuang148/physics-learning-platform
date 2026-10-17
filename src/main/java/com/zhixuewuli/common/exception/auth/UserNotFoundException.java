package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 用户不存在异常
 * 
 * <p>
 * 当根据用户名、用户ID或其他唯一标识无法找到对应用户记录时抛出此异常。
 * 属于认证模块的基础异常类型，用于处理用户身份验证过程中的资源缺失问题。
 * </p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 * <li><strong>登录验证</strong>：输入的用户名在系统中不存在</li>
 * <li><strong>用户查询</strong>：根据ID查询用户信息但记录不存在</li>
 * <li><strong>权限验证</strong>：基于用户身份的权限检查失败</li>
 * <li><strong>数据同步</strong>：外部系统用户与本地用户映射失败</li>
 * </ul>
 * 
 * <h3>安全考虑：</h3>
 * <ul>
 * <li>避免暴露用户存在状态信息</li>
 * <li>统一错误响应格式防止信息探测</li>
 * <li>记录安全审计日志</li>
 * <li>实施频率限制防止暴力破解</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 * @see ResultCode.USER_NOT_FOUND 对应的业务状态码
 */
public class UserNotFoundException extends AuthenticationException {

    /** 默认错误消息 */
    private static final String DEFAULT_MESSAGE = "用户不存在";

    /**
     * 构造函数 - 使用默认错误消息
     */
    public UserNotFoundException() {
        super(ResultCode.USER_NOT_FOUND, DEFAULT_MESSAGE);
    }

    /**
     * 构造函数 - 使用自定义错误消息
     * 
     * @param message 自定义错误消息
     */
    public UserNotFoundException(String message) {
        super(ResultCode.USER_NOT_FOUND, message);
    }
}