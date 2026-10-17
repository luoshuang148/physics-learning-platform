package com.zhixuewuli.common.exception.question;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 题目不存在异常
 * 
 * <p>
 * 当尝试访问、操作或引用一个不存在的题目记录时抛出此异常。
 * 属于题目模块的核心业务异常，用于处理题目资源管理相关的问题。
 * </p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 * <li><strong>数据查询</strong>：根据ID查询题目但记录不存在</li>
 * <li><strong>数据更新</strong>：尝试修改已删除或不存在的题目</li>
 * <li><strong>数据删除</strong>：删除操作针对不存在的题目ID</li>
 * <li><strong>关联验证</strong>：题目与其他资源的关联关系失效</li>
 * <li><strong>并发操作</strong>：多用户同时操作导致数据不一致</li>
 * </ul>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 * <li><strong>前置验证</strong>：操作前先验证题目存在性</li>
 * <li><strong>事务管理</strong>：确保相关操作的原子性</li>
 * <li><strong>缓存同步</strong>：及时更新缓存中的题目状态</li>
 * <li><strong>用户友好</strong>：提供清晰的错误提示和恢复建议</li>
 * </ul>
 * 
 * <h3>安全考虑：</h3>
 * <ul>
 * <li>避免暴露内部ID生成规律</li>
 * <li>统一错误响应防止信息探测</li>
 * <li>记录操作审计日志</li>
 * <li>实施合理的频率限制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionBusinessException 题目业务异常基类
 * @see ResultCode.QUESTION_NOT_FOUND 对应的业务状态码(4201)
 */
public class QuestionNotFoundException extends QuestionBusinessException {

    private static final ResultCode ERROR_CODE = ResultCode.QUESTION_NOT_FOUND;

    /** 题目ID */
    private final Long questionId;

    /**
     * 构造函数 - 根据题目ID创建异常
     * 
     * @param questionId 题目ID
     */
    public QuestionNotFoundException(Long questionId) {
        super(ERROR_CODE, String.format("题目不存在，ID: %d", questionId));
        this.questionId = questionId;
    }

    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param message 自定义异常消息
     */
    public QuestionNotFoundException(String message) {
        super(ERROR_CODE, message);
        this.questionId = null;
    }

    /**
     * 获取题目ID
     * 
     * @return Long 题目ID，如果未设置则返回null
     */
    public Long getQuestionId() {
        return questionId;
    }
}