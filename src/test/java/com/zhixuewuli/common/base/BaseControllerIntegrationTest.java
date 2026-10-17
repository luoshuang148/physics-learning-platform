package com.zhixuewuli.common.base;

import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 基础控制器集成测试类
 * 
 * <p>
 * 提供 Spring MVC 控制器集成测试的基础配置和常用工具方法。
 * 支持完整的 HTTP 请求/响应测试、JWT 认证、事务管理等核心功能。
 * 所有控制器集成测试应继承此类以获得统一的测试环境和便捷的工具方法。
 * </p>
 * 
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>Spring Boot Web 测试上下文支持（随机端口）</li>
 * <li>H2 数据库自动配置（MySQL 兼容模式）</li>
 * <li>事务自动回滚（保证测试隔离性）</li>
 * <li>MockMvc 访问支持（用于 HTTP 请求测试）</li>
 * <li>JWT 令牌生成与认证头构建</li>
 * <li>用户数据快速访问</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     public class AuthControllerIntegrationTest extends BaseControllerIntegrationTest {
 * 
 *         &#64;Autowired
 *         private AuthController authController;
 * 
 *         @Test
 *         void testLogin() throws Exception {
 *             // 准备测试数据
 *             User user = createTestUser();
 * 
 *             // 构建登录请求
 *             LoginRequest request = new LoginRequest();
 *             request.setUsername(user.getUsername());
 *             request.setPassword("password123");
 * 
 *             // 发送 POST 请求
 *             String responseContent = mockMvc.perform(post("/auth/login")
 *                     .contentType(MediaType.APPLICATION_JSON)
 *                     .content(objectMapper.writeValueAsString(request)))
 *                     .andExpect(status().isOk())
 *                     .andReturn()
 *                     .getResponse()
 *                     .getContentAsString();
 * 
 *             // 验证响应
 *             assertThat(responseContent).contains("token");
 *         }
 *     }
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseIntegrationTest 基础集成测试类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.yml")
@Transactional
@Tag("integration-controller")
public abstract class BaseControllerIntegrationTest extends BaseIntegrationTest {

    /**
     * Spring MVC 测试上下文
     */
    @Autowired
    protected WebApplicationContext webApplicationContext;

    /**
     * MockMvc 对象，用于模拟 HTTP 请求
     */
    @Autowired
    protected MockMvc mockMvc;

    /**
     * JWT 工具类，用于生成和验证令牌
     */
    @Autowired
    protected JwtUtil jwtUtil;

    /**
     * 用户仓库，用于测试数据的准备和验证
     */
    @Autowired
    protected UserRepository userRepository;

    /**
     * 生成认证的 JWT 令牌
     * 
     * <p>
     * 根据用户 ID 和用户名生成有效的 JWT 令牌，
     * 用于后续请求的认证头构建。
     * </p>
     * 
     * @param userId   用户唯一标识
     * @param username 用户名
     * @return String JWT 令牌字符串
     */
    protected String createAuthHeader(Long userId, String username) {
        String token = jwtUtil.generateToken(userId, username);
        return "Bearer " + token;
    }

    /**
     * 创建带认证的 HTTP 请求实体（包含请求体）
     * 
     * <p>
     * 构建包含 JWT 认证头和指定内容类型的 HTTP 请求对象，
     * 适用于 POST、PUT 等需要请求体的场景。
     * </p>
     * 
     * @param body     请求体对象
     * @param userId   用户 ID
     * @param username 用户名
     * @param <T>      请求体类型
     * @return HttpEntity<T> 包含认证信息的请求实体
     */
    protected <T> HttpEntity<T> createAuthenticatedRequest(T body, Long userId, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", createAuthHeader(userId, username));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 创建带认证的 HTTP 请求实体（无请求体）
     * 
     * <p>
     * 构建包含 JWT 认证头的 HTTP 请求对象，
     * 适用于 GET、DELETE 等不需要请求体的场景。
     * </p>
     * 
     * @param userId   用户 ID
     * @param username 用户名
     * @return HttpEntity<Void> 包含认证信息的空请求实体
     */
    protected HttpEntity<Void> createAuthenticatedRequest(Long userId, String username) {
        return createAuthenticatedRequest(null, userId, username);
    }
}
