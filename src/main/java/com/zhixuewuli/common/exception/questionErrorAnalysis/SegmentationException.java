package com.zhixuewuli.common.exception.questionErrorAnalysis;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 分词处理异常
 * 
 * 当使用HanLP进行文本分词处理时发生错误时抛出。
 * 使用错题分析模块专用错误码 5603。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see QuestionErrorAnalysisException 错题分析异常基类
 */
public class SegmentationException extends QuestionErrorAnalysisException {

    private static final ResultCode ERROR_CODE = ResultCode.SEGMENTATION_FAILED;

    /** 待分词的文本 */
    private final String text;

    /**
     * 构造函数 - 根据待分词文本创建异常
     * 
     * @param text  待分词文本
     * @param cause 异常原因
     */
    public SegmentationException(String text, Throwable cause) {
        super(ERROR_CODE, String.format("文本分词处理失败：%s", text), cause);
        this.text = text;
    }

    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param text    待分词文本
     * @param message 自定义异常消息
     * @param cause   异常原因
     */
    public SegmentationException(String text, String message, Throwable cause) {
        super(ERROR_CODE, String.format("文本分词处理失败：%s ，", text) + message, cause);
        this.text = text;
    }

    /**
     * 获取待分词文本
     * 
     * @return String 待分词文本
     */
    public String getText() {
        return text;
    }
}