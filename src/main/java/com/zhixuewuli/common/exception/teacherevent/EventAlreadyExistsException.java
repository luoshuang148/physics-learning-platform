package com.zhixuewuli.common.exception.teacherevent;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 事件已存在异常
 * 
 * 当教师尝试发布新事件但已存在事件时抛出此异常。
 * 属于业务规则违反类异常。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEventBusinessException 教师事件业务异常基类
 */
public class EventAlreadyExistsException extends TeacherEventBusinessException {

    /**
     * 事件已存在错误码
     * 使用ResultCode中定义的标准错误码
     */
    public static final ResultCode ERROR_CODE = ResultCode.TEACHER_EVENT_ALREADY_EXISTS;

    /**
     * 构造函数
     */
    public EventAlreadyExistsException() {
        super(ERROR_CODE, "该教师已存在事件，请先删除现有事件");
    }

    /**
     * 构造函数 - 带教师ID参数
     * 
     * @param teacherId 教师ID
     */
    public EventAlreadyExistsException(Long teacherId) {
        super(ERROR_CODE, String.format("教师 ID %d 已存在事件，请先删除现有事件", teacherId));
    }

    /**
     * 构造函数 - 带自定义消息
     * 
     * @param message 错误消息
     */
    public EventAlreadyExistsException(String message) {
        super(ERROR_CODE, message);
    }
}