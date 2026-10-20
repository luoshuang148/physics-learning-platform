package com.zhixuewuli.service.reportbatch;

import java.net.InetAddress;
import java.util.UUID;

/** 实例标识：用于发布/消费的租约归属与日志定位（不含任何用户数据）。 */
final class ReportBatchNode {
    static final String ID = compute();

    private ReportBatchNode() {}

    private static String compute() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception unavailable) {
            return "node-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
