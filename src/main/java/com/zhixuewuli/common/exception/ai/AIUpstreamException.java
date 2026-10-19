package com.zhixuewuli.common.exception.ai;

/** Safe upstream status without exposing provider response bodies or credentials. */
public class AIUpstreamException extends AIServiceUnavailableException {
    private final int status;

    public AIUpstreamException(int status) {
        super("AI服务返回错误状态: " + status);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
