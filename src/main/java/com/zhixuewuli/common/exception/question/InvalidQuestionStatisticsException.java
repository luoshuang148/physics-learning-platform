package com.zhixuewuli.common.exception.question;

import com.zhixuewuli.common.exception.TechnicalException;

/**
 * 无效题目统计异常
 * 
 * 当统计查询返回异常数据或数据转换失败时抛出。
 * 属于技术异常范畴，表示系统内部处理错误。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TechnicalException 技术异常基类
 */
public class InvalidQuestionStatisticsException extends TechnicalException {

    /**
     * 构造函数 - 使用默认错误信息
     * 
     * @param message 异常描述信息
     */
    public InvalidQuestionStatisticsException(String message) {
        super("题目统计查询异常：" + message);
    }

    /**
     * 构造函数 - 包含原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public InvalidQuestionStatisticsException(String message, Throwable cause) {
        super("题目统计查询异常：" + message, cause);
    }
}