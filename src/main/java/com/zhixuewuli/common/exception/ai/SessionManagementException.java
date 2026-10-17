package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 会话管理异常基类
 * 继承自AITechnicalException，为所有会话相关异常提供统一基类
 */
public abstract class SessionManagementException extends AITechnicalException {

    protected SessionManagementException(ResultCode code, String message) {
        super(code, message);
    }
}