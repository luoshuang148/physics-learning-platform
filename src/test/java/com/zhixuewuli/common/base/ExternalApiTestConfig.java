package com.zhixuewuli.common.base;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.Tag;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * 外部API验证测试配置注解
 * 
 * 用于标记需要真实调用外部API的测试类，使用dev环境配置和真实API密钥
 * 这些测试仅在需要验证外部服务连通性时运行，不参与常规单元测试
 * 
 * 特点：
 * - 使用@SpringBootTest启动完整Spring上下文
 * - 激活dev环境配置，获取真实的API密钥
 * - 标记为"external-api"标签，便于选择性运行
 * - 支持事务回滚，避免测试数据污染
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see SpringBootTest
 * @see ActiveProfiles
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(locations = "classpath:application-dev.yml")
@Tag("external-api")
@Transactional
public @interface ExternalApiTestConfig {
}