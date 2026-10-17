package com.zhixuewuli.common.exception.questionErrorAnalysis;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 错题分析模块自定义异常基类
 * 
 * 所有错题分析相关的业务异常都应该继承此类，
 * 便于统一异常处理和日志记录。
 * 
 * <p>
 * 错误码范围：4100-4199
 * </p>
 * <ul>
 * <li>4100-4109: 基础异常</li>
 * <li>4110-4119: 数据查询异常</li>
 * <li>4120-4129: 分析处理异常</li>
 * <li>4130-4139: 业务逻辑异常</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public abstract class QuestionErrorAnalysisException extends BusinessException {

    /**
     * 构造函数 - 使用默认错题分析服务不可用错误码
     * 
     * @param message 异常描述信息
     */
    protected QuestionErrorAnalysisException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.QUESTION_ERROR_ANALYSIS_SERVICE_UNAVAILABLE, message);
    }

    /**
     * 构造函数 - 必须指定错误码和消息（基类不能直接使用）
     * 
     * @param code    自定义错误码（必须显式传入）
     * @param message 异常描述信息
     */
    protected QuestionErrorAnalysisException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    protected QuestionErrorAnalysisException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.QUESTION_ERROR_ANALYSIS_SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }

    /**
     * 构造函数 - 必须指定错误码和原因（基类不能直接使用）
     * 
     * @param code  自定义错误码（必须显式传入）
     * @param cause 异常原因
     */
    protected QuestionErrorAnalysisException(ResultCode code, Throwable cause) {
        super(code, cause.getMessage());
        initCause(cause);
    }

    /**
     * 构造函数 - 必须指定错误码、消息和原因（基类不能直接使用）
     * 
     * @param code    自定义错误码（必须显式传入）
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    protected QuestionErrorAnalysisException(ResultCode code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }
}