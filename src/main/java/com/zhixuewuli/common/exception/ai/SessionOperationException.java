package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 会话操作异常
 * 当会话相关操作（查询历史、清除等）失败时抛出
 */
public class SessionOperationException extends SessionManagementException {
    public SessionOperationException(String operation, String sessionId, String reason) {
        super(ResultCode.SESSION_OPERATION_FAILED,
                String.format("会话%s操作失败 - 会话ID: %s, 原因: %s", operation, sessionId, reason));
    }
}