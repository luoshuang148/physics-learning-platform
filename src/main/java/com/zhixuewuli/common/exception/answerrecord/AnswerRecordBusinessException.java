package com.zhixuewuli.common.exception.answerrecord;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 答题记录业务异常基类
 * 
 * 继承自 BusinessException，专门为答题记录相关业务提供统一的异常处理基础。
 * 定义答题记录领域的错误码范围和通用异常处理逻辑。
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用特定的错误码范围，避免与其他模块冲突</li>
 * <li>提供答题记录领域专用的构造方法</li>
 * <li>保持与业务异常体系的一致性</li>
 * <li>支持链式异常处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class AnswerRecordBusinessException extends BusinessException {

    /**
     * 构造函数 - 使用默认答题记录状态不正确错误码
     * 
     * @param message 异常描述信息
     */
    public AnswerRecordBusinessException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.ANSWER_RECORD_STATUS_INVALID, message);
    }

    /**
     * 构造函数 - 指定错误码和消息
     * 
     * @param code    自定义答题记录错误码
     * @param message 异常描述信息
     */
    public AnswerRecordBusinessException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public AnswerRecordBusinessException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.ANSWER_RECORD_STATUS_INVALID, message);
        initCause(cause);
    }

    /**
     * 构造函数 - 指定错误码、消息和原因异常
     * 
     * @param code    自定义答题记录错误码
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public AnswerRecordBusinessException(ResultCode code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }

}