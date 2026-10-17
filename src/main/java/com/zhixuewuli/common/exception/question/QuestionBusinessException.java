package com.zhixuewuli.common.exception.question;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 题目业务异常基类
 * 
 * 继承自 BusinessException，专门为题目管理相关业务提供统一的异常处理基础。
 * 定义题目管理领域的错误码范围和通用异常处理逻辑。
 * 
 * <p>
 * 错误码范围：4200-4299
 * </p>
 * <ul>
 * <li>4200-4209: 基础异常</li>
 * <li>4210-4219: 权限相关异常</li>
 * <li>4220-4229: 数据验证异常</li>
 * <li>4230-4239: 业务流程异常</li>
 * <li>4240-4249: 系统处理异常</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用特定的错误码范围，避免与其他模块冲突</li>
 * <li>提供题目管理领域专用的构造方法</li>
 * <li>保持与业务异常体系的一致性</li>
 * <li>支持链式异常处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class QuestionBusinessException extends BusinessException {

    /**
     * 构造函数 - 使用默认题目状态不正确错误码
     * 
     * @param message 异常描述信息
     */
    public QuestionBusinessException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.QUESTION_STATUS_INVALID, message);
    }

    /**
     * 构造函数 - 指定错误码
     * 
     * @param code    自定义错误码
     * @param message 异常描述信息
     */
    public QuestionBusinessException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public QuestionBusinessException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.QUESTION_STATUS_INVALID, message);
        initCause(cause);
    }
}