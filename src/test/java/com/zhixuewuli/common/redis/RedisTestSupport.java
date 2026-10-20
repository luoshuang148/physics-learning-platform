package com.zhixuewuli.common.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** One Redis per test JVM, with a separate namespace for each Spring context. */
public abstract class RedisTestSupport {
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    static { REDIS.start(); }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        String namespace = "test:" + java.util.UUID.randomUUID() + ":";
        registry.add("app.redis.namespace", () -> namespace);
    }
}
