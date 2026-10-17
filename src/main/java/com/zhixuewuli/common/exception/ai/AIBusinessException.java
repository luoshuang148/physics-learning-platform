package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * AI业务异常基类
 * 
 * 所有AI相关业务异常的父类，继承自BusinessException。
 * 用于表示AI服务层面的业务问题。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public abstract class AIBusinessException extends BusinessException {

    /**
     * 构造函数 - 使用默认AI服务不可用错误码
     * 
     * @param message 异常描述信息
     */
    protected AIBusinessException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.AI_SERVICE_UNAVAILABLE, message);
    }

    /**
     * 构造函数 - 指定具体错误码
     * 
     * @param code    具体业务错误码
     * @param message 异常描述信息
     */
    protected AIBusinessException(ResultCode code, String message) {
        super(code, message);
    }
}