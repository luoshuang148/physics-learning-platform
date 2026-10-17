package com.zhixuewuli.common.base;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * WireMock 测试配置基类
 * 
 * 提供 WireMock 服务器的启动、停止和配置功能，用于模拟外部API 服务。
 * 所有继承此类的集成测试将自动获得 WireMock 支持。
 * 
 * <p>
 * 主要特性：
 * </p>
 * <ul>
 * <li>自动启动 WireMock 服务器（端口 8089）</li>
 * <li>测试结束后自动清理所有 stubs</li>
 * <li>支持静态文件存根（src/test/resources/wiremock）</li>
 * <li>可编程方式动态添加 stubbing</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>{@code
 * public class AIServiceIntegrationTest extends WireMockTestBase {
 * 
 *     @Test
 *     void testAIResponse() {
 *         // 方式 1：使用静态存根文件
 *         // 文件位于：src/test/resources/wiremock/__files/aliyun-ai-response.json
 * 
 *         // 方式 2：编程方式添加 stubbing
 *         stubFor(post("/v1/chat/completions")
 *                 .willReturn(aResponse()
 *                         .withStatus(200)
 *                         .withHeader("Content-Type", "application/json")
 *                         .withBody("{\"choices\":[{\"message\":{\"content\":\"AI 回答\"}}]}")));
 * 
 *         // 执行测试逻辑
 *     }
 * }
 * }</pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see WireMockServer
 * @see BaseIntegrationTest
 */
@ActiveProfiles("test")
public abstract class WireMockTestBase extends BaseIntegrationTest {

    /**
     * WireMock 服务器端口，从 application-test.yml 读取
     */
    @Value("${test.wiremock.port:8089}")
    protected static int wiremockPort;

    /**
     * WireMock 服务器实例
     */
    protected static WireMockServer wireMockServer;

    /**
     * 在所有测试方法之前启动 WireMock 服务器
     * 
     * 配置说明：
     * - 使用指定端口（默认 8089）
     * - 从 classpath:wiremock 目录加载存根文件
     * - 禁用浏览器通知（避免测试日志污染）
     */
    @BeforeAll
    static void startWireMockServer() {
        if (wireMockServer == null) {
            WireMockConfiguration config = options()
                    .port(wiremockPort)
                    .usingFilesUnderClasspath("wiremock");

            wireMockServer = new WireMockServer(config);
            wireMockServer.start();
            configureFor("localhost", wiremockPort);

            System.out.println("✓ WireMock server started on port " + wiremockPort);
        }
    }

    /**
     * 在所有测试方法之后停止 WireMock 服务器
     */
    @AfterAll
    static void stopWireMockServer() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            System.out.println("✓ WireMock server stopped");
        }
    }

    /**
     * 在每个测试方法之前重置所有 stubs
     * 
     * 确保测试间的隔离性，避免 stubbing 相互影响
     */
    @org.junit.jupiter.api.BeforeEach
    void resetWireMockStubs() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }
}
