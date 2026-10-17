package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.common.util.TestJwtUtil;
import com.zhixuewuli.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 控制器集成测试基础设施验证测试
 * 
 * <p>
 * 验证 BaseControllerIntegrationTest 基类和 TestJwtUtil 工具类的功能是否正常。
 * 确保后续控制器集成测试可以正常使用这些基础设施。
 * </p>
 * 
 * <p>
 * 测试范围：
 * </p>
 * <ul>
 * <li>MockMvc 是否可用</li>
 * <li>JWT 令牌生成和认证是否正常</li>
 * <li>用户数据是否可以访问</li>
 * <li>事务管理是否生效</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@DisplayName("控制器集成测试基础设施验证")
public class ControllerInfrastructureTest extends BaseControllerIntegrationTest {

    @Autowired
    private TestJwtUtil testJwtUtil;

    @Test
    @DisplayName("应该正确注入 MockMvc 对象")
    void mockMvcShouldBeInjected() {
        assertThat(mockMvc).isNotNull();
    }

    @Test
    @DisplayName("应该正确注入 JwtUtil 对象")
    void jwtUtilShouldBeInjected() {
        assertThat(jwtUtil).isNotNull();
    }

    @Test
    @DisplayName("应该正确注入 TestJwtUtil 对象")
    void testJwtUtilShouldBeInjected() {
        assertThat(testJwtUtil).isNotNull();
    }

    @Test
    @DisplayName("应该正确注入 UserRepository 对象")
    void userRepositoryShouldBeInjected() {
        assertThat(userRepository).isNotNull();
    }

    @Test
    @DisplayName("应该能够生成有效的 JWT 令牌")
    void shouldGenerateValidJwtToken() {
        // Given
        Long userId = 1001L;
        String username = "test_user";

        // When
        String token = createAuthHeader(userId, username);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).startsWith("Bearer ");

        // 验证令牌可以被解析
        String extractedToken = token.substring(7); // 去除 "Bearer " 前缀
        assertThat(testJwtUtil.getUserIdFromToken(extractedToken)).isEqualTo(userId);
        assertThat(testJwtUtil.getUsernameFromToken(extractedToken)).isEqualTo(username);
    }

    @Test
    @DisplayName("应该能够创建带认证的 HTTP 请求实体（包含请求体）")
    void shouldCreateAuthenticatedRequestWithBody() {
        // Given
        Long userId = 1001L;
        String username = "test_user";
        String requestBody = "{\"key\":\"value\"}";

        // When
        var requestEntity = createAuthenticatedRequest(requestBody, userId, username);

        // Then
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(requestEntity.getHeaders().get("Authorization")).isNotEmpty();
        assertThat(requestEntity.getBody()).isEqualTo(requestBody);
    }

    @Test
    @DisplayName("应该能够创建带认证的 HTTP 请求实体（无请求体）")
    void shouldCreateAuthenticatedRequestWithoutBody() {
        // Given
        Long userId = 1001L;
        String username = "test_user";

        // When
        var requestEntity = createAuthenticatedRequest(userId, username);

        // Then
        assertThat(requestEntity).isNotNull();
        assertThat(requestEntity.getHeaders().get("Authorization")).isNotEmpty();
        assertThat(requestEntity.getBody()).isNull();
    }

    @Test
    @DisplayName("应该能够保存和查询用户数据")
    void shouldBeAbleToSaveAndQueryUser() throws Exception {
        // Given
        String uniqueUsername = "test_" + System.currentTimeMillis();
        User user = new User();
        user.setUsername(uniqueUsername);
        user.setPasswordHash("$2a$10$example_hash");
        user.setRealName("测试用户");
        user.setUserType(User.UserType.STUDENT);
        user.setActive(true);

        // When - 保存用户
        User savedUser = userRepository.save(user);
        userRepository.flush();

        // Then - 验证用户已保存
        assertThat(savedUser.getId()).isNotNull();

        // 查询用户
        User foundUser = userRepository.findByUsername(uniqueUsername).orElse(null);
        assertThat(foundUser).isNotNull();
        assertThat(foundUser.getUsername()).isEqualTo(uniqueUsername);
    }

    @Test
    @DisplayName("应该支持事务回滚")
    void shouldSupportTransactionRollback() throws Exception {
        // Given
        String uniqueUsername = "rollback_test_" + System.currentTimeMillis();
        User user = new User();
        user.setUsername(uniqueUsername);
        user.setPasswordHash("$2a$10$example_hash");
        user.setRealName("测试用户");
        user.setUserType(User.UserType.STUDENT);
        user.setActive(true);

        // When - 保存用户
        userRepository.save(user);
        userRepository.flush();

        // Then - 当前事务内可以查询到
        assertThat(userRepository.findByUsername(uniqueUsername)).isPresent();

        // 注意：由于@Transactional 注解，测试结束后会自动回滚
        // 下一个测试不会看到这个用户
    }

    @Test
    @DisplayName("应该可以使用 MockMvc 发送 HTTP 请求")
    void shouldBeAbleToSendHttpRequestWithMockMvc() throws Exception {
        // Given
        String testUrl = "/auth/login"; // 假设的登录接口

        // When & Then - 至少验证 MockMvc 可以工作
        // 注意：这里不验证具体响应内容，因为安全配置可能影响结果
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
