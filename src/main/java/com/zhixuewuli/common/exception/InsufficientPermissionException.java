package com.zhixuewuli.common.exception;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 权限不足异常
 * 
 * 当用户尝试执行超出其权限范围的操作时抛出此异常。
 * 主要用于教师权限验证失败的场景。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class InsufficientPermissionException extends BusinessException {

    /**
     * 用户ID
     */
    private final Long userId;

    /**
     * 构造函数 - 只包含消息
     * 
     * @param message 权限不足的具体描述
     */
    public InsufficientPermissionException(String message) {
        super(ResultCode.PERMISSION_DENIED, message);
        this.userId = null;
    }

    /**
     * 构造函数 - 包含用户ID和消息
     * 
     * @param userId  用户ID
     * @param message 权限不足的具体描述
     */
    public InsufficientPermissionException(Long userId, String message) {
        super(ResultCode.PERMISSION_DENIED, message);
        this.userId = userId;
    }

    /**
     * 获取用户ID
     * 
     * @return 用户ID，可能为null
     */
    public Long getUserId() {
        return userId;
    }
}