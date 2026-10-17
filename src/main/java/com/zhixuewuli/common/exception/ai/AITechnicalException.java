package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * AI技术异常基类
 * 
 * 所有AI相关技术异常的父类，继承自TechnicalException。
 * 用于表示AI服务层面的技术问题。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public abstract class AITechnicalException extends TechnicalException {

    /**
     * 构造函数 - 包含业务码、消息和原因
     * 
     * @param code    业务错误码
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    protected AITechnicalException(ResultCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /**
     * 构造函数 - 包含业务码和消息
     * 
     * @param code    业务错误码
     * @param message 异常描述信息
     */
    protected AITechnicalException(ResultCode code, String message) {
        super(code, message);
    }
}