package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 批量学生报告（RabbitMQ）配置。
 *
 * <p>所有投递、认领、重试参数集中在这里，便于在不同环境调整数据量与并发上限。
 * 消息拓扑名称也通过配置暴露，测试环境可指向独立队列。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.report-batch")
public class ReportBatchProperties {

    /** 总开关：关闭后调度任务与消费者不启动（测试环境默认关闭）。 */
    private boolean enabled = true;

    /** 单个批次允许的最大名单数量。 */
    private int maxStudentsPerBatch = 100;

    /** 教师同时处于待处理/处理中的批次数上限。 */
    private int maxActiveBatchesPerTeacher = 5;

    /** 单个子任务的模型生成最大执行次数（含首次）。 */
    private int itemMaxAttempts = 3;

    /** 子任务执行租约；超过后恢复调度器可重新投递。 */
    private Duration itemLease = Duration.ofMinutes(6);

    /** 自动重试基础延迟（指数退避，带上限）。 */
    private Duration retryBaseDelay = Duration.ofSeconds(30);

    private Duration retryMaxDelay = Duration.ofMinutes(10);

    /** 发件箱发布扫描间隔/批量/认领租约。 */
    private Duration publishInterval = Duration.ofSeconds(5);
    private int publishBatchSize = 20;
    private Duration publishLease = Duration.ofSeconds(30);
    private int outboxMaxAttempts = 20;
    private Duration publishConfirmTimeout = Duration.ofSeconds(10);

    /** 过期执行恢复扫描。 */
    /** Published work left unclaimed beyond this delay may be delivered again (at-least-once). */
    private Duration pendingDeliveryTimeout = Duration.ofMinutes(6);

    private Duration recoverInterval = Duration.ofSeconds(30);
    private int recoverBatchSize = 50;

    /** 模型调用超时（后台生成，不做长事务等待）。 */
    private Duration modelTimeout = Duration.ofMinutes(5);

    /** RabbitMQ 拓扑。 */
    private String mainExchange = "report.batch.exchange";
    private String mainQueue = "report.batch.item.queue";
    private String mainRoutingKey = "report.batch.item";
    private String deadLetterExchange = "report.batch.dlx";
    private String deadLetterQueue = "report.batch.item.dlq";
    private String deadLetterRoutingKey = "report.batch.item.dead";
}
