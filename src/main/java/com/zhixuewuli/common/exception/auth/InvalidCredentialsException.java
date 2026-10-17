package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 密码错误异常
 * 
 * 当用户提供错误密码时抛出此异常。
 * 用于区分认证失败的具体原因，提供精确的错误反馈。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 */
public class InvalidCredentialsException extends AuthenticationException {

    /** 默认错误消息 */
    private static final String DEFAULT_MESSAGE = "密码错误";

    /**
     * 构造函数 - 使用默认错误消息
     */
    public InvalidCredentialsException() {
        super(ResultCode.USER_PASSWORD_INCORRECT, DEFAULT_MESSAGE);
    }

    /**
     * 构造函数 - 使用自定义错误消息
     * 
     * @param message 自定义错误消息
     */
    public InvalidCredentialsException(String message) {
        super(ResultCode.USER_PASSWORD_INCORRECT, message);
    }
}