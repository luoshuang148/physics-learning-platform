package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.common.util.PasswordUtil;
import com.zhixuewuli.dto.request.LoginRequest;
import com.zhixuewuli.dto.request.RegisterRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;

/**
 * 认证控制器集成测试
 * 
 * <p>
 * 测试用户认证相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>用户登录 - 验证用户名密码并获取 JWT 令牌</li>
 * <li>用户注册 - 创建新的用户账户</li>
 * <li>用户登出 - 退出登录会话</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-auth-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景和边界条件</li>
 * <li>验证 HTTP 状态码、响应结构和业务逻辑</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("认证管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-auth-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class AuthControllerIntegrationTest extends BaseControllerIntegrationTest {

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private PasswordUtil passwordUtil;

        @Autowired
        private UserRepository userRepository;

        @BeforeEach
        void setUp() {
                // 确保测试前数据库处于干净状态
                // cleanTable("users"); // 已由 @Sql 脚本处理，无需重复清理
                // 调试：打印密码哈希
                String hash = passwordUtil.encode("password123");
                System.out.println("Password hash for 'password123': " + hash);
                // 验证测试数据中的哈希是否匹配
                boolean matches = passwordUtil.matches("password123",
                                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
                System.out.println("Matches test data hash: " + matches);
                // 更新所有测试用户的密码哈希，确保匹配
                jdbcTemplate.update("UPDATE users SET password_hash = ?", hash);
        }

        // ============================================================
        // 用户登录测试
        // ============================================================

        @DisplayName("登录成功 - 正确的用户名和密码")
        @Test
        void testLogin_Success() throws Exception {
                // Given: 已存在的测试用户（来自 SQL 脚本）
                String username = "student_test01";
                String password = "password123";

                // 调试：检查用户是否存在
                Long userCount = queryForObject("SELECT COUNT(*) FROM users", Long.class);
                System.out.println("User count: " + userCount);
                User user = userRepository.findByUsername(username).orElse(null);
                System.out.println("User found: " + user);
                if (user != null) {
                        System.out.println("User password hash: " + user.getPasswordHash());
                }

                LoginRequest request = new LoginRequest();
                request.setUsername(username);
                request.setPassword(password);

                // When: 发送登录请求
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证登录成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.token").exists())
                                .andExpect(jsonPath("$.data.userId").value(1001))
                                .andExpect(jsonPath("$.data.username").value(username))
                                .andExpect(jsonPath("$.data.userType").value("STUDENT"))
                                .andExpect(jsonPath("$.data.realName").value("Test Student 01"));
        }

        @DisplayName("登录成功 - 使用真实姓名和密码")
        @Test
        void testLogin_SuccessByRealName() throws Exception {
                // Given: 使用真实姓名作为登录标识
                String realName = "Test Student 01";
                String password = "password123";

                LoginRequest request = new LoginRequest();
                request.setUsername(realName);
                request.setPassword(password);

                // When: 发送登录请求
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证登录成功并返回实际用户名
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.token").exists())
                                .andExpect(jsonPath("$.data.userId").value(1001))
                                .andExpect(jsonPath("$.data.username").value("student_test01"))
                                .andExpect(jsonPath("$.data.userType").value("STUDENT"))
                                .andExpect(jsonPath("$.data.realName").value(realName));
        }

        @DisplayName("登录失败 - 错误的密码")
        @Test
        void testLogin_WrongPassword() throws Exception {
                // Given: 已存在的用户但密码错误
                String username = "student_test01";
                String wrongPassword = "wrong_password";

                LoginRequest request = new LoginRequest();
                request.setUsername(username);
                request.setPassword(wrongPassword);

                // When: 发送登录请求
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证登录失败
                result.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("登录失败 - 不存在的用户名")
        @Test
        void testLogin_NonExistentUser() throws Exception {
                // Given: 不存在的用户名
                String username = "non_existent_user";
                String password = "password123";

                LoginRequest request = new LoginRequest();
                request.setUsername(username);
                request.setPassword(password);

                // When: 发送登录请求
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证登录失败
                result.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("登录失败 - 禁用的账户")
        @Test
        void testLogin_LockedAccount() throws Exception {
                // Given: 被锁定的用户账户
                String username = "locked_user";
                String password = "password123";

                LoginRequest request = new LoginRequest();
                request.setUsername(username);
                request.setPassword(password);

                // When: 发送登录请求
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证登录失败（账户被锁定）
                result.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("登录失败 - 用户名为空")
        @Test
        void testLogin_EmptyUsername() throws Exception {
                // Given: 空用户名
                LoginRequest request = new LoginRequest();
                request.setUsername("");
                request.setPassword("password123");

                // When: 发送登录请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("登录失败 - 密码为空")
        @Test
        void testLogin_EmptyPassword() throws Exception {
                // Given: 空密码
                LoginRequest request = new LoginRequest();
                request.setUsername("student_test01");
                request.setPassword("");

                // When: 发送登录请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        // ============================================================
        // 用户注册测试
        // ============================================================

        @DisplayName("注册成功 - 新用户")
        @Test
        void testRegister_Success() throws Exception {
                // Given: 新的用户注册信息
                String username = "new_user_" + System.currentTimeMillis();
                String password = "password123";
                String realName = "新注册用户";
                User.UserType userType = User.UserType.STUDENT;
                String className = "三年四班";

                RegisterRequest request = new RegisterRequest();
                request.setUsername(username);
                request.setPassword(password);
                request.setRealName(realName);
                request.setUserType(userType);
                request.setClassName(className);

                // When: 发送注册请求
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证注册成功
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.data.username").value(username))
                                .andExpect(jsonPath("$.data.userType").value("STUDENT"));

                // 验证用户已保存到数据库（SQL 脚本 5 个 + 新注册 1 个）
                Long count = queryForObject("SELECT COUNT(*) FROM users", Long.class);
                assertThat(count).isEqualTo(6L);
        }

        @DisplayName("注册失败 - 用户名重复")
        @Test
        void testRegister_DuplicateUsername() throws Exception {
                // Given: 已存在的用户名
                String duplicateUsername = "student_test01";
                String password = "password123";
                String realName = "重复用户";
                User.UserType userType = User.UserType.STUDENT;

                // 调试：检查用户是否存在
                boolean exists = userRepository.existsByUsername(duplicateUsername);
                System.out.println("User exists: " + exists);
                Long userCount = userRepository.count();
                System.out.println("Total users: " + userCount);

                RegisterRequest request = new RegisterRequest();
                request.setUsername(duplicateUsername);
                request.setPassword(password);
                request.setRealName(realName);
                request.setUserType(userType);

                // When: 发送注册请求
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证注册失败（用户名已存在）
                result.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").exists())
                                .andExpect(jsonPath("$.message").exists());
        }

        @DisplayName("注册失败 - 用户名为空")
        @Test
        void testRegister_EmptyUsername() throws Exception {
                // Given: 空用户名
                RegisterRequest request = new RegisterRequest();
                request.setUsername("");
                request.setPassword("password123");
                request.setRealName("测试用户");
                request.setUserType(User.UserType.STUDENT);

                // When: 发送注册请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("注册失败 - 密码为空")
        @Test
        void testRegister_EmptyPassword() throws Exception {
                // Given: 空密码
                RegisterRequest request = new RegisterRequest();
                request.setUsername("new_user");
                request.setPassword("");
                request.setRealName("测试用户");
                request.setUserType(User.UserType.STUDENT);

                // When: 发送注册请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("注册失败 - 真实姓名为空")
        @Test
        void testRegister_EmptyRealName() throws Exception {
                // Given: 空真实姓名
                RegisterRequest request = new RegisterRequest();
                request.setUsername("new_user");
                request.setPassword("password123");
                request.setRealName("");
                request.setUserType(User.UserType.STUDENT);

                // When: 发送注册请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("注册失败 - 用户类型为空")
        @Test
        void testRegister_NullUserType() throws Exception {
                // Given: 空用户类型
                RegisterRequest request = new RegisterRequest();
                request.setUsername("new_user");
                request.setPassword("password123");
                request.setRealName("测试用户");
                request.setUserType(null);

                // When: 发送注册请求（应该被 @Valid 拦截）
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("公开注册拒绝伪造教师角色")
        @Test
        void testRegister_TeacherUser() throws Exception {
                // Given: 教师用户注册信息
                String username = "new_teacher_" + System.currentTimeMillis();
                String password = "password123";
                String realName = "新注册教师";
                User.UserType userType = User.UserType.TEACHER;

                RegisterRequest request = new RegisterRequest();
                request.setUsername(username);
                request.setPassword(password);
                request.setRealName(realName);
                request.setUserType(userType);

                // When: 发送注册请求
                ResultActions result = mockMvc.perform(post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Direct HTTP requests cannot grant a privileged role.
                result.andExpect(status().isForbidden());
                org.assertj.core.api.Assertions.assertThat(userRepository.findByUsername(username)).isEmpty();
        }

        // ============================================================
        // 用户登出测试
        // ============================================================

        @DisplayName("登出成功")
        @Test
        void testLogout_Success() throws Exception {
                // Given: 已登录用户（实际 JWT 无状态模式下服务端不做特殊处理）
                Long userId = 1001L;
                String username = "student_test01";

                // When: 发送登出请求
                ResultActions result = mockMvc.perform(post("/auth/logout")
                                .header("Authorization", createAuthHeader(userId, username)))
                                .andDo(print());

                // Then: 验证登出成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data").value("登出成功"));
        }

}
