package com.zhixuewuli.common.exception.teacherevent;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 教师事件业务异常基类
 * 
 * 继承自 BusinessException，专门处理教师事件相关的业务异常。
 * 为教师事件领域提供统一的异常处理基础，便于异常分类和管理。
 * 
 * <p>
 * 错误码范围：4700-4799
 * </p>
 * <ul>
 * <li>4700-4709: 基础异常</li>
 * <li>4710-4719: 权限相关异常</li>
 * <li>4720-4729: 数据验证异常</li>
 * <li>4730-4739: 业务流程异常</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用特定的错误码范围（4700+系列）</li>
 * <li>提供教师事件领域专用的构造方法</li>
 * <li>保持与业务异常体系的一致性</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class TeacherEventBusinessException extends BusinessException {

    /**
     * 构造函数 - 使用默认教师事件不正确错误码
     * 
     * @param message 异常描述信息
     */
    public TeacherEventBusinessException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.TEACHER_EVENT_STATUS_INVALID, message);
    }

    /**
     * 构造函数 - 指定错误码
     * 
     * @param code    自定义错误码
     * @param message 异常描述信息
     */
    public TeacherEventBusinessException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public TeacherEventBusinessException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.TEACHER_EVENT_STATUS_INVALID, message);
        initCause(cause);
    }
}