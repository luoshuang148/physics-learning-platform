package com.zhixuewuli.common.exception;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 会话不存在异常
 * 
 * 当尝试访问不存在的会话时抛出此异常。
 * 用于区分权限问题和资源不存在问题。
 * 
 * <p>
 * 错误码：4003
 * </p>
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>根据会话ID查询会话记录但会话不存在</li>
 * <li>尝试操作已删除或从未存在的会话</li>
 * <li>会话ID格式正确但数据库中无对应记录</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用明确的错误码4003（区别于权限错误）</li>
 * <li>提供会话ID信息便于调试</li>
 * <li>直接继承通用业务异常基类</li>
 * <li>明确区分资源不存在和权限不足两种情况</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class SessionNotFoundException extends BusinessException {

    /** 会话ID */
    private final String sessionId;

    /**
     * 构造函数 - 使用默认错误信息
     * 
     * @param sessionId 会话ID
     */
    public SessionNotFoundException(String sessionId) {
        super(ResultCode.SESSION_NOT_FOUND, String.format("会话不存在: %s", sessionId));
        this.sessionId = sessionId;
    }

    /**
     * 构造函数 - 自定义错误信息
     * 
     * @param sessionId 会话ID
     * @param message   自定义错误信息
     */
    public SessionNotFoundException(String sessionId, String message) {
        super(ResultCode.SESSION_NOT_FOUND, String.format("会话不存在: %s ,", sessionId) + message);
        this.sessionId = sessionId;
    }

    /**
     * 构造函数 - 包含原因异常
     * 
     * @param sessionId 会话ID
     * @param cause     原因异常
     */
    public SessionNotFoundException(String sessionId, Throwable cause) {
        super(ResultCode.SESSION_NOT_FOUND, String.format("会话不存在: %s", sessionId));
        this.sessionId = sessionId;
        initCause(cause);
    }

    /**
     * 构造函数 - 自定义信息和原因异常
     * 
     * @param sessionId 会话ID
     * @param message   自定义错误信息
     * @param cause     原因异常
     */
    public SessionNotFoundException(String sessionId, String message, Throwable cause) {
        super(ResultCode.SESSION_NOT_FOUND, String.format("会话不存在: %s ,", sessionId) + message);
        this.sessionId = sessionId;
        initCause(cause);
    }

    /**
     * 获取会话ID
     * 
     * @return String 会话ID
     */
    public String getSessionId() {
        return sessionId;
    }
}