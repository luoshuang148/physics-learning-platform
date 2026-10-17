package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * AI响应解析异常
 * 
 * 当AI服务返回的响应数据格式不正确或无法解析时抛出此异常。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class AIResponseParseException extends AITechnicalException {

    /**
     * 构造函数 - 包含消息和原因
     * 
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    public AIResponseParseException(String message, Throwable cause) {
        super(ResultCode.AI_PARSE_ERROR, message, cause);
    }

    /**
     * 构造函数 - 仅包含消息
     * 
     * @param message 异常描述信息
     */
    public AIResponseParseException(String message) {
        super(ResultCode.AI_PARSE_ERROR, message);
    }
}