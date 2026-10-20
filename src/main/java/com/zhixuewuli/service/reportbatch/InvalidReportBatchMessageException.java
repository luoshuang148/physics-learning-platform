package com.zhixuewuli.service.reportbatch;

/** 消息体格式非法：无法解析或协议版本/标识不合法，直接拒绝（不重新入队）。 */
public class InvalidReportBatchMessageException extends RuntimeException {
    public InvalidReportBatchMessageException(String message) {
        super(message);
    }
}
