package com.zhixuewuli.service.reportbatch;

/** AI 能力未启用或未配置：属于永久失败，重试无意义，向教师明确暴露原因。 */
public class ReportBatchAiDisabledException extends RuntimeException {
    public ReportBatchAiDisabledException(String message) {
        super(message);
    }
}
