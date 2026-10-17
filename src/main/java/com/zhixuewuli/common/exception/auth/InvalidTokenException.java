package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 令牌无效异常
 * 
 * 当JWT令牌无效、过期或格式不正确时抛出此异常。
 * 用于处理令牌验证相关的认证问题。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 */
public class InvalidTokenException extends AuthenticationException {

    /** 默认错误消息 */
    private static final String DEFAULT_MESSAGE = "令牌无效或已过期";

    /**
     * 构造函数 - 使用默认错误消息
     */
    public InvalidTokenException() {
        super(ResultCode.INVALID_TOKEN, DEFAULT_MESSAGE);
    }

    /**
     * 构造函数 - 使用自定义错误消息
     * 
     * @param message 自定义错误消息
     */
    public InvalidTokenException(String message) {
        super(ResultCode.INVALID_TOKEN, message);
    }
}