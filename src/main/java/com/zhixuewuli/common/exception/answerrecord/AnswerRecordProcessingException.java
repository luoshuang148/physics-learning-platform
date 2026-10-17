package com.zhixuewuli.common.exception.answerrecord;

import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 答题记录处理异常
 * 
 * 当答题记录处理过程中发生系统级错误时抛出此异常。
 * 用于处理业务逻辑执行失败的情况。
 * 
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>数据库操作失败</li>
 * <li>事务处理异常</li>
 * <li>并发操作冲突</li>
 * <li>系统资源不足</li>
 * <li>外部服务调用失败</li>
 * <li>业务逻辑执行异常</li>
 * </ul>
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>提供操作上下文信息</li>
 * <li>支持操作类型分类</li>
 * <li>直接继承通用技术异常基类</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see TechnicalException 技术异常基类
 */
public class AnswerRecordProcessingException extends TechnicalException {

    /** 操作类型 */
    private final String operationType;

    /** 相关记录ID */
    private final String recordId;

    /**
     * 构造函数 - 基础处理异常
     * 
     * @param operationType 操作类型
     * @param message       错误信息
     */
    public AnswerRecordProcessingException(String operationType, String message) {
        super(ResultCode.ANSWER_RECORD_PROCESSING_FAILED,
                String.format("答题记录处理失败 [%s]: %s", operationType, message));
        this.operationType = operationType;
        this.recordId = null;
    }

    /**
     * 构造函数 - 带记录ID的处理异常
     * 
     * @param operationType 操作类型
     * @param recordId      记录ID
     * @param message       错误信息
     */
    public AnswerRecordProcessingException(String operationType, String recordId, String message) {
        super(ResultCode.ANSWER_RECORD_PROCESSING_FAILED, String.format("答题记录处理失败 [%s] - RecordId: %s: %s",
                operationType, recordId, message));
        this.operationType = operationType;
        this.recordId = recordId;
    }

    /**
     * 构造函数 - 包含原因异常
     * 
     * @param operationType 操作类型
     * @param message       错误信息
     * @param cause         原因异常
     */
    public AnswerRecordProcessingException(String operationType, String message, Throwable cause) {
        super(ResultCode.ANSWER_RECORD_PROCESSING_FAILED,
                String.format("答题记录处理失败 [%s]: %s", operationType, message), cause);
        this.operationType = operationType;
        this.recordId = null;
    }

    /**
     * 构造函数 - 带记录ID和原因异常
     * 
     * @param operationType 操作类型
     * @param recordId      记录ID
     * @param message       错误信息
     * @param cause         原因异常
     */
    public AnswerRecordProcessingException(String operationType, String recordId, String message, Throwable cause) {
        super(ResultCode.ANSWER_RECORD_PROCESSING_FAILED, String.format("答题记录处理失败 [%s] - RecordId: %s: %s",
                operationType, recordId, message), cause);
        this.operationType = operationType;
        this.recordId = recordId;
    }

    /**
     * 获取操作类型
     * 
     * @return String 操作类型
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * 获取记录ID
     * 
     * @return String 记录ID
     */
    public String getRecordId() {
        return recordId;
    }

    /**
     * 创建创建记录失败异常的便捷方法
     * 
     * @param cause 原因异常
     * @return AnswerRecordProcessingException 创建记录失败异常
     */
    public static AnswerRecordProcessingException createFailed(Throwable cause) {
        return new AnswerRecordProcessingException("CREATE", "创建答题记录失败", cause);
    }

    /**
     * 创建更新记录失败异常的便捷方法
     * 
     * @param recordId 记录ID
     * @param cause    原因异常
     * @return AnswerRecordProcessingException 更新记录失败异常
     */
    public static AnswerRecordProcessingException updateFailed(String recordId, Throwable cause) {
        return new AnswerRecordProcessingException("UPDATE", recordId, "更新答题记录失败", cause);
    }

    /**
     * 创建删除记录失败异常的便捷方法
     * 
     * @param recordId 记录ID
     * @param cause    原因异常
     * @return AnswerRecordProcessingException 删除记录失败异常
     */
    public static AnswerRecordProcessingException deleteFailed(String recordId, Throwable cause) {
        return new AnswerRecordProcessingException("DELETE", recordId, "删除答题记录失败", cause);
    }

    /**
     * 创建批量操作失败异常的便捷方法
     * 
     * @param operationType 操作类型
     * @param count         操作记录数
     * @param cause         原因异常
     * @return AnswerRecordProcessingException 批量操作失败异常
     */
    public static AnswerRecordProcessingException batchOperationFailed(String operationType, int count,
            Throwable cause) {
        return new AnswerRecordProcessingException(operationType,
                String.format("批量%s操作失败，涉及%d条记录", operationType.toLowerCase(), count), cause);
    }

    /**
     * 创建事务处理失败异常的便捷方法
     * 
     * @param operationType 操作类型
     * @param cause         原因异常
     * @return AnswerRecordProcessingException 事务处理失败异常
     */
    public static AnswerRecordProcessingException transactionFailed(String operationType, Throwable cause) {
        return new AnswerRecordProcessingException(operationType, "事务处理失败", cause);
    }

    /**
     * 创建并发冲突异常的便捷方法
     * 
     * @param recordId 记录ID
     * @param cause    原因异常
     * @return AnswerRecordProcessingException 并发冲突异常
     */
    public static AnswerRecordProcessingException concurrencyConflict(String recordId, Throwable cause) {
        return new AnswerRecordProcessingException("CONCURRENT_UPDATE", recordId, "并发更新冲突", cause);
    }
}