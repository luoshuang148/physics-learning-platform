package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 无效题目ID异常
 * 
 * <p>
 * 当AI服务接收到无效、空或格式不正确的题目ID时抛出此异常。
 * 属于AI业务异常类别，用于处理题目标识相关的业务问题。
 * </p>
 * 
 * <h3>触发条件：</h3>
 * <ul>
 * <li><strong>ID为空</strong>：传入的题目ID为null或空字符串</li>
 * <li><strong>格式错误</strong>：题目ID不符合预期格式要求</li>
 * <li><strong>ID不存在</strong>：指定的题目ID在系统中找不到对应记录</li>
 * <li><strong>类型不匹配</strong>：题目ID类型与期望类型不符</li>
 * </ul>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 * <li>提供具体的题目ID信息以便调试</li>
 * <li>区分不同类型的ID错误（空值、格式、不存在等）</li>
 * <li>记录相关上下文信息（如用户ID、操作类型等）</li>
 * <li>支持国际化错误消息</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AIBusinessException AI业务异常基类
 * @see ResultCode.INVALID_QUESTION_ID 对应的业务状态码(4509)
 */
public class InvalidQuestionIdException extends AIBusinessException {

    private static final ResultCode ERROR_CODE = ResultCode.INVALID_QUESTION_ID;

    private final String questionId;

    /**
     * 构造函数 - 题目ID为空的情况
     */
    public InvalidQuestionIdException() {
        super(ERROR_CODE, "题目ID不能为空");
        this.questionId = null;
    }

    /**
     * 构造函数 - 根据题目ID创建异常
     * 
     * @param questionId 题目ID
     */
    public InvalidQuestionIdException(String questionId) {
        super(ERROR_CODE, String.format("无效的题目ID: %s", questionId));
        this.questionId = questionId;
    }

    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param message 自定义异常消息
     */
    public InvalidQuestionIdException(String message, String questionId) {
        super(ERROR_CODE, message);
        this.questionId = questionId;
    }

    /**
     * 构造函数 - 使用自定义消息和原因
     * 
     * @param message 自定义异常消息
     * @param cause   异常原因
     */
    public InvalidQuestionIdException(String message, String questionId, Throwable cause) {
        super(ERROR_CODE, message);
        initCause(cause);
        this.questionId = questionId;
    }

    /**
     * 获取题目ID
     * 
     * @return String 题目ID，如果未设置则返回null
     */
    public String getQuestionId() {
        return questionId;
    }
}