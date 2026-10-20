package com.zhixuewuli.common.config.infrastructure;

import com.zhixuewuli.common.config.properties.ReportBatchProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 批量报告消息拓扑：主交换机/队列 + 死信交换机/队列，全部持久化。
 *
 * <p>主队列绑定死信交换机，被消费者 reject（不重新入队）的消息进入 DLQ 供排查；
 * 业务终态失败的任务标识则通过发件箱投递到同一 DLQ 作为检查记录。</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RabbitMqConfig {

    private final ReportBatchProperties properties;
    private final MeterRegistry meterRegistry;

    @Bean
    public DirectExchange reportBatchExchange() {
        return new DirectExchange(properties.getMainExchange(), true, false);
    }

    @Bean
    public Queue reportBatchQueue() {
        return QueueBuilder.durable(properties.getMainQueue())
                .deadLetterExchange(properties.getDeadLetterExchange())
                .deadLetterRoutingKey(properties.getDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public Binding reportBatchBinding(Queue reportBatchQueue, DirectExchange reportBatchExchange) {
        return BindingBuilder.bind(reportBatchQueue).to(reportBatchExchange).with(properties.getMainRoutingKey());
    }

    @Bean
    public DirectExchange reportBatchDeadLetterExchange() {
        return new DirectExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue reportBatchDeadLetterQueue() {
        return QueueBuilder.durable(properties.getDeadLetterQueue()).build();
    }

    @Bean
    public Binding reportBatchDeadLetterBinding(Queue reportBatchDeadLetterQueue,
                                                DirectExchange reportBatchDeadLetterExchange) {
        return BindingBuilder.bind(reportBatchDeadLetterQueue).to(reportBatchDeadLetterExchange)
                .with(properties.getDeadLetterRoutingKey());
    }

    /** 持久化投递 + mandatory 返回处理（不可路由消息属于配置错误，必须被看见）。 */
    @Bean
    public RabbitTemplateCustomizer reportBatchRabbitTemplateCustomizer() {
        Counter returned = Counter.builder("report.batch.outbox.publish.failures")
                .tag("reason", "returned").register(meterRegistry);
        return template -> {
            template.setMandatory(true);
            template.setReturnsCallback(returnedMessage -> {
                returned.increment();
                log.error("报告消息不可路由 - exchange: {}, routingKey: {}, reply: {}",
                        returnedMessage.getExchange(), returnedMessage.getRoutingKey(),
                        returnedMessage.getReplyText());
            });
        };
    }
}
