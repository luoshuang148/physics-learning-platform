package com.zhixuewuli.service.reportbatch;

/**
 * 过期执行者写入被拒绝：子任务的执行标识已被更晚的认领取代（租约恢复或人工重试）。
 * 该结果必须整体回滚，不得覆盖新执行者的最终结果。
 */
public class StaleReportBatchExecutionException extends RuntimeException {
    public StaleReportBatchExecutionException(String message) {
        super(message);
    }
}
