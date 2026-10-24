package com.zhixuewuli.common.config;

import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AgentProperties;
import com.zhixuewuli.common.config.properties.KnowledgeProperties;
import com.zhixuewuli.common.config.properties.LearningAssistantProperties;
import com.zhixuewuli.common.metrics.AIMetrics;
import com.zhixuewuli.controller.AICapabilityController;
import com.zhixuewuli.common.result.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 环境配置绑定测试。
 *
 * <p>验证 .env 形式的环境变量（APP_KNOWLEDGE_ENABLED、AI_AGENT_* 等）能正确绑定到
 * {@link KnowledgeProperties} / {@link AgentProperties} 等配置对象；
 * 且当开关关闭时，能力接口不得声称可用（已配置 ≠ 实际调用成功）。</p>
 */
@Tag("unit")
@DisplayName("AI 环境配置绑定测试")
class AiEnvironmentBindingTest {

    /**
     * 加载真实 application.yml / application-dev.yml，再叠加 .env 形式的环境变量。
     * 走 {@code SystemEnvironmentPropertySource} 才能模拟真实 env-var 松弛绑定；
     * YAML 中的 {@code ${ENV_KEY:default}} 占位符会从该环境解析。
     */
    private static ApplicationContextRunner envRunner(Map<String, Object> envStyleValues) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    StandardEnvironment env = new StandardEnvironment();
                    var loader = new YamlPropertySourceLoader();
                    for (String name : List.of("application.yml", "application-dev.yml")) {
                        try {
                            for (PropertySource<?> ps : loader.load(name, new ClassPathResource(name))) {
                                env.getPropertySources().addLast(ps);
                            }
                        } catch (IOException e) {
                            throw new IllegalStateException("无法加载 " + name, e);
                        }
                    }
                    env.getPropertySources().addFirst(
                            new org.springframework.core.env.SystemEnvironmentPropertySource(
                                    "envStyle", envStyleValues));
                    context.setEnvironment(env);
                })
                .withUserConfiguration(BindingConfig.class);
    }

    /**
     * 仅通过 {@code withUserConfiguration} 显式注册。
     * <strong>不要</strong>加 {@code @Configuration}：该类位于 {@code com.zhixuewuli} 下，
     * 会被主应用组件扫描收进 @SpringBootTest 上下文，造成重复 Bean。
     */
    @EnableConfigurationProperties({
            KnowledgeProperties.class,
            AgentProperties.class,
            AIBusinessProperties.class,
            LearningAssistantProperties.class
    })
    static class BindingConfig {
        @Bean
        AIMetrics aiMetrics() {
            return new AIMetrics(new SimpleMeterRegistry());
        }

        @Bean
        AICapabilityController aiCapabilityController(
                AIBusinessProperties business,
                AIMetrics metrics,
                AgentProperties agent) {
            return new AICapabilityController(business, metrics, agent);
        }
    }

    @Nested
    @DisplayName("环境变量绑定")
    class EnvBinding {

        @Test
        @DisplayName("APP_KNOWLEDGE_ENABLED=true 绑定到 KnowledgeProperties.enabled")
        void knowledgeEnabledFromEnv() {
            envRunner(Map.of("APP_KNOWLEDGE_ENABLED", "true"))
                    .run(context -> {
                        KnowledgeProperties knowledge = context.getBean(KnowledgeProperties.class);
                        assertTrue(knowledge.isEnabled(), "APP_KNOWLEDGE_ENABLED=true 应绑定为 enabled=true");
                    });
        }

        @Test
        @DisplayName("APP_KNOWLEDGE_QDRANT_URL 等知识库键绑定到 KnowledgeProperties")
        void knowledgeUrlAndModelFromEnv() {
            envRunner(Map.of(
                    "APP_KNOWLEDGE_ENABLED", "true",
                    "APP_KNOWLEDGE_QDRANT_URL", "http://qdrant.internal:6333",
                    "APP_KNOWLEDGE_EMBEDDING_MODEL", "bge-small-zh-v1.5-quantized",
                    "APP_KNOWLEDGE_EMBEDDING_DIMENSIONS", "512"
            )).run(context -> {
                KnowledgeProperties knowledge = context.getBean(KnowledgeProperties.class);
                assertTrue(knowledge.isEnabled());
                assertEquals("http://qdrant.internal:6333", knowledge.getQdrantUrl());
                assertEquals("bge-small-zh-v1.5-quantized", knowledge.getEmbeddingModel());
                assertEquals(512, knowledge.getEmbeddingDimensions());
            });
        }

        @Test
        @DisplayName("AI_AGENT_* 键绑定到 AgentProperties")
        void agentKeysFromEnv() {
            envRunner(Map.of(
                    "AI_AGENT_TOOL_CALLING_ENABLED", "true",
                    "AI_AGENT_BASE_URL", "https://llm.example.com/v1",
                    "AI_AGENT_API_KEY", "sk-test-not-real",
                    "AI_AGENT_MODEL", "test-model",
                    "AI_AGENT_TIMEOUT_SECONDS", "30"
            )).run(context -> {
                AgentProperties agent = context.getBean(AgentProperties.class);
                assertTrue(agent.isToolCallingEnabled());
                assertEquals("https://llm.example.com/v1", agent.getBaseUrl());
                assertEquals("sk-test-not-real", agent.getApiKey());
                assertEquals("test-model", agent.getModel());
                assertEquals(30L, agent.getTimeoutSeconds());
            });
        }

        @Test
        @DisplayName("AI_ENABLED 与 AI_LEARNING_ASSISTANT_* 键绑定")
        void businessAndAssistantKeysFromEnv() {
            envRunner(Map.of(
                    "AI_ENABLED", "true",
                    "AI_LEARNING_ASSISTANT_MODE", "AGENT",
                    "AI_LEARNING_ASSISTANT_MAX_MODEL_ROUNDS", "6",
                    "AI_LEARNING_ASSISTANT_MAX_TOOL_CALLS", "8",
                    "AI_LEARNING_ASSISTANT_DEADLINE_SECONDS", "60",
                    "AI_LEARNING_ASSISTANT_MODEL_TIMEOUT_SECONDS", "25",
                    "AI_LEARNING_ASSISTANT_TOOL_TIMEOUT_SECONDS", "5"
            )).run(context -> {
                AIBusinessProperties business = context.getBean(AIBusinessProperties.class);
                assertTrue(business.isEnabled(), "AI_ENABLED=true 应绑定为 ai.context.enabled=true");

                LearningAssistantProperties assistant = context.getBean(LearningAssistantProperties.class);
                assertEquals(LearningAssistantProperties.Mode.AGENT, assistant.getMode());
                assertEquals(6, assistant.getMaxModelRounds());
                assertEquals(8, assistant.getMaxToolCalls());
                assertEquals(60L, assistant.getTotalDeadlineSeconds());
                assertEquals(25L, assistant.getModelTimeoutSeconds());
                assertEquals(5L, assistant.getToolTimeoutSeconds());
            });
        }

        @Test
        @DisplayName("未设置环境变量时保持安全默认值（关闭/空）")
        void safeDefaultsWhenEnvAbsent() {
            envRunner(Map.of()).run(context -> {
                KnowledgeProperties knowledge = context.getBean(KnowledgeProperties.class);
                assertFalse(knowledge.isEnabled(), "知识库默认应关闭");

                AgentProperties agent = context.getBean(AgentProperties.class);
                // 默认回退到智谱 OpenAI 兼容端点；无密钥时 agentReady 仍为 false
                assertEquals("https://open.bigmodel.cn/api/paas/v4", agent.getBaseUrl());
                assertEquals("glm-4-flash", agent.getModel());
                assertTrue(agent.getApiKey() == null || agent.getApiKey().isBlank());

                LearningAssistantProperties assistant = context.getBean(LearningAssistantProperties.class);
                assertEquals(LearningAssistantProperties.Mode.WORKFLOW, assistant.getMode());
            });
        }
    }

    @Nested
    @DisplayName("能力接口不虚报可用")
    class CapabilityHonesty {

        @SuppressWarnings("unchecked")
        private Map<String, Object> capabilities(ApplicationContextRunner runner) {
            Map<String, Object>[] holder = new Map[1];
            runner.run(context -> {
                AICapabilityController controller = context.getBean(AICapabilityController.class);
                Result<Map<String, Object>> result = controller.capabilities();
                holder[0] = result.getData();
            });
            return holder[0];
        }

        @Test
        @DisplayName("全部开关关闭时 agentReady 为 false")
        void allSwitchesOff() {
            Map<String, Object> caps = capabilities(envRunner(Map.of(
                    "AI_ENABLED", "false",
                    "AI_AGENT_TOOL_CALLING_ENABLED", "false"
            )));
            assertEquals(Boolean.FALSE, caps.get("agentReady"));
            assertEquals(Boolean.FALSE, caps.get("reports"));
            assertEquals(Boolean.FALSE, caps.get("tutoring"));
        }

        @Test
        @DisplayName("Tool Calling 开关打开但缺 api-key 时 agentReady 仍为 false")
        void agentSwitchOnButIncomplete() {
            Map<String, Object> caps = capabilities(envRunner(Map.of(
                    "AI_ENABLED", "true",
                    "AI_AGENT_TOOL_CALLING_ENABLED", "true",
                    "AI_AGENT_API_KEY", ""
            )));
            assertEquals(Boolean.FALSE, caps.get("agentReady"),
                    "已配置开关不等于可运行，缺少 api-key 时不得声称可用");
        }

        @Test
        @DisplayName("开关与连接信息齐全时 agentReady 为 true（仍只是已配置）")
        void agentConfigured() {
            Map<String, Object> caps = capabilities(envRunner(Map.of(
                    "AI_ENABLED", "true",
                    "AI_AGENT_TOOL_CALLING_ENABLED", "true",
                    "AI_AGENT_BASE_URL", "https://llm.example.com/v1",
                    "AI_AGENT_MODEL", "test-model",
                    "AI_AGENT_API_KEY", "sk-test"
            )));
            assertEquals(Boolean.TRUE, caps.get("agentReady"));
            assertTrue(String.valueOf(caps.get("notice")).contains("已配置"),
                    "notice 应区分「已配置」与「实际调用成功」");
        }
    }
}
