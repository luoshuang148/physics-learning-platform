package com.zhixuewuli.common.exception.questionErrorAnalysis;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 词云分析异常
 * 
 * 当词云分析过程中发生错误时抛出，如分词失败、数据处理异常等。
 * 使用错题分析模块专用错误码 5602。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionErrorAnalysisException 错题分析异常基类
 */
public class WordCloudAnalysisException extends QuestionErrorAnalysisException {

    private static final ResultCode ERROR_CODE = ResultCode.WORD_CLOUD_ANALYSIS_FAILED;

    /**
     * 构造函数 - 使用默认消息
     * 
     * @param cause 异常原因
     */
    public WordCloudAnalysisException(Throwable cause) {
        super(ERROR_CODE, "词云分析失败", cause);
    }

    /**
     * 构造函数 - 指定错误消息
     * 
     * @param message 错误消息
     */
    public WordCloudAnalysisException(String message) {
        super(ERROR_CODE, "词云分析失败: " + message);
    }

    /**
     * 构造函数 - 指定错误消息和原因
     * 
     * @param message 错误消息
     * @param cause   异常原因
     */
    public WordCloudAnalysisException(String message, Throwable cause) {
        super(ERROR_CODE, "词云分析失败: " + message, cause);
    }
}