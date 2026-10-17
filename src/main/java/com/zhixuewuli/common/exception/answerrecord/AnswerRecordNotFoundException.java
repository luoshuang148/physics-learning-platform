package com.zhixuewuli.common.exception.answerrecord;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 答题记录不存在异常
 * 
 * 当尝试访问或操作不存在的答题记录时抛出此异常。
 * 通常发生在根据ID查询答题记录但记录不存在的情况下。
 * 
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>根据ID查询答题记录时记录不存在</li>
 * <li>尝试更新不存在的答题记录</li>
 * <li>删除不存在的答题记录</li>
 * <li>基于特定答题记录的业务操作</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用明确的错误码3001</li>
 * <li>提供记录ID信息便于调试</li>
 * <li>继承答题记录业务异常基类</li>
 * <li>支持链式异常处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AnswerRecordBusinessException 答题记录业务异常基类
 */
public class AnswerRecordNotFoundException extends AnswerRecordBusinessException {

    /** 记录ID */
    private final String recordId;

    /**
     * 构造函数 - 使用记录ID构造（推荐使用）
     * 
     * @param recordId 答题记录ID
     */
    public AnswerRecordNotFoundException(String recordId) {
        super(ResultCode.ANSWER_RECORD_NOT_FOUND, String.format("答题记录不存在: %s", recordId));
        this.recordId = recordId;
    }

    /**
     * 构造函数 - 只传入消息（ID设为空）
     * 通过第二个参数true来区分重载版本
     * 
     * @param message 异常消息
     * @param unused  仅用于区分重载的占位参数，传入true即可
     */
    public AnswerRecordNotFoundException(String message, boolean unused) {
        super(ResultCode.ANSWER_RECORD_NOT_FOUND, message);
        this.recordId = null;
    }

    /**
     * 构造函数 - 自定义错误信息
     * 
     * @param recordId 答题记录ID
     * @param message  自定义错误信息
     */
    public AnswerRecordNotFoundException(String recordId, String message) {
        super(ResultCode.ANSWER_RECORD_NOT_FOUND, String.format("答题记录不存在: %s", recordId) + message);
        this.recordId = recordId;
    }

    /**
     * 构造函数 - 包含原因异常
     * 
     * @param recordId 答题记录ID
     * @param cause    原因异常
     */
    public AnswerRecordNotFoundException(String recordId, Throwable cause) {
        super(ResultCode.ANSWER_RECORD_NOT_FOUND, String.format("答题记录不存在: %s", recordId), cause);
        this.recordId = recordId;
    }

    /**
     * 构造函数 - 自定义信息和原因异常
     * 
     * @param recordId 答题记录ID
     * @param message  自定义错误信息
     * @param cause    原因异常
     */
    public AnswerRecordNotFoundException(String recordId, String message, Throwable cause) {
        super(ResultCode.ANSWER_RECORD_NOT_FOUND, String.format("答题记录不存在: %s", recordId) + message, cause);
        this.recordId = recordId;
    }

    /**
     * 获取答题记录ID
     * 
     * @return String 答题记录ID
     */
    public String getRecordId() {
        return recordId;
    }
}