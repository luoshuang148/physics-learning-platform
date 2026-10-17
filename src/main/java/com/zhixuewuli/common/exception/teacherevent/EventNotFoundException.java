package com.zhixuewuli.common.exception.teacherevent;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 事件不存在异常
 * 
 * 当尝试操作不存在的教师事件时抛出此异常。
 * 属于资源不存在类异常。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TeacherEventBusinessException 教师事件业务异常基类
 */
public class EventNotFoundException extends TeacherEventBusinessException {

    /**
     * 事件不存在错误码
     * 使用ResultCode中定义的标准错误码
     */
    public static final ResultCode ERROR_CODE = ResultCode.TEACHER_EVENT_NOT_FOUND;

    /**
     * 构造函数
     */
    public EventNotFoundException() {
        super(ERROR_CODE, "事件不存在");
    }

    /**
     * 构造函数 - 带教师ID参数
     * 
     * @param teacherId 教师ID
     */
    public EventNotFoundException(Long teacherId) {
        super(ERROR_CODE, String.format("教师ID %d 的事件不存在", teacherId));
    }
}