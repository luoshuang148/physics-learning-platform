package com.zhixuewuli.common.rabbitmq;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * RabbitMQ 测试基座：固定镜像的真实 broker（Testcontainers），并覆盖测试环境默认关闭的
 * 监听器/调度器开关。使用短间隔让自动重试、租约恢复与发件箱投递在测试中可观察。
 */
public abstract class RabbitMqTestSupport {

    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> true);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> true);
        registry.add("app.report-batch.enabled", () -> true);
        registry.add("app.report-batch.publish-interval", () -> "1s");
        registry.add("app.report-batch.recover-interval", () -> "2s");
        registry.add("app.report-batch.retry-base-delay", () -> "1s");
        registry.add("app.report-batch.retry-max-delay", () -> "2s");
        registry.add("app.report-batch.publish-confirm-timeout", () -> "5s");
        registry.add("management.health.rabbit.enabled", () -> false);
    }
}
