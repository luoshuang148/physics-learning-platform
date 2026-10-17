package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * Token计算异常
 * 当token数量计算出现错误时抛出
 */
public class TokenCalculationException extends TokenQuotaException {
    public TokenCalculationException(String message) {
        super(ResultCode.TOKEN_CALCULATION_ERROR, "Token计算失败: " + message);
    }

    public TokenCalculationException(String message, Throwable cause) {
        super(ResultCode.TOKEN_CALCULATION_ERROR, "Token计算失败: " + message);
        this.initCause(cause);
    }
}