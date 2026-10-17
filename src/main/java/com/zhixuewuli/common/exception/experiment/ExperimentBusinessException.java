package com.zhixuewuli.common.exception.experiment;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 实验业务异常基类
 * 
 * <p>
 * 继承自 BusinessException，专门为实验管理相关业务提供统一的异常处理基础。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class ExperimentBusinessException extends BusinessException {
    
    /**
     * 构造函数 - 使用默认错误码
     * 
     * @param message 异常描述信息
     */
    public ExperimentBusinessException(String message) {
        super(ResultCode.EXPERIMENT_STATUS_INVALID, message);
    }
    
    /**
     * 构造函数 - 指定错误码
     * 
     * @param code    自定义错误码
     * @param message 异常描述信息
     */
    public ExperimentBusinessException(ResultCode code, String message) {
        super(code, message);
    }
    
    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public ExperimentBusinessException(String message, Throwable cause) {
        super(ResultCode.EXPERIMENT_STATUS_INVALID, message);
        initCause(cause);
    }
}
