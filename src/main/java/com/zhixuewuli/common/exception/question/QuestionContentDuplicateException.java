package com.zhixuewuli.common.exception.question;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 题目内容重复异常
 * 
 * 当尝试创建或更新题目时，发现题目内容已存在时抛出此异常。
 * 错误码：4002
 * 
 * <p>
 * 触发场景：
 * </p>
 * <ul>
 * <li>创建新题目时题目内容与现有题目重复</li>
 * <li>更新题目时新内容与其它题目重复</li>
 * <li>批量导入题目时检测到重复内容</li>
 * </ul>
 * 
 * <p>
 * 设计考量：
 * </p>
 * <ul>
 * <li>题目内容全局唯一性是业务规则要求</li>
 * <li>避免重复题目影响学习体验</li>
 * <li>便于题目去重和质量控制</li>
 * </ul>
 * 
 * <p>
 * 处理建议：
 * </p>
 * <ul>
 * <li>提示用户题目已存在</li>
 * <li>提供相似题目的查看功能</li>
 * <li>建议修改题目内容或选项</li>
 * <li>记录重复检测日志</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class QuestionContentDuplicateException extends QuestionBusinessException {

    private static final ResultCode ERROR_CODE = ResultCode.QUESTION_CONTENT_DUPLICATE;

    /**
     * 构造函数 - 根据重复的题目内容创建异常
     * 
     * @param content 重复的题目内容
     */
    public QuestionContentDuplicateException(String content) {
        super(ERROR_CODE, String.format("题目内容已存在：%s", content));
    }

    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param customMessage 自定义异常消息
     */
    public QuestionContentDuplicateException(String customMessage, boolean unused) {
        super(ERROR_CODE, customMessage);
    }
}