package com.zhixuewuli.common.exception;
import com.zhixuewuli.common.result.ResultCode;
public class KnowledgeNotFoundException extends BusinessException {
    public KnowledgeNotFoundException(){super(ResultCode.BUSINESS_ERROR,"资料不可用或不存在");}
}
