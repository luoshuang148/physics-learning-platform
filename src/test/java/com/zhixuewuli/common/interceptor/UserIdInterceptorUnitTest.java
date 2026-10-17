package com.zhixuewuli.common.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.exception.auth.AccountDisabledException;
import com.zhixuewuli.common.exception.auth.InvalidTokenException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;

/**
 * UserIdInterceptor 单元测试
 * 
 * 测试用户身份拦截器在各种场景下的行为
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserIdInterceptor 单元测试")
@SuppressWarnings("unchecked")
class UserIdInterceptorUnitTest extends BaseUnitTest {

    private UserIdInterceptor interceptor;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MockHttpServletRequest request;

    @Mock
    private MockHttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @Mock
    private Object nonHandlerMethod;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<Long> longCaptor;

    private ObjectMapper objectMapper;
    private User activeUser;
    private User disabledUser;
    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new UserIdInterceptor(jwtUtil, userRepository);
        objectMapper = new ObjectMapper();

        // 准备测试用户数据
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setUsername("testuser");
        activeUser.setUserType(User.UserType.STUDENT);
        activeUser.setActive(true);

        disabledUser = new User();
        disabledUser.setId(2L);
        disabledUser.setUsername("disabled");
        disabledUser.setUserType(User.UserType.TEACHER);
        disabledUser.setActive(false);

        validToken = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.valid.token";
        expiredToken = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.expired.token";
    }

    // ==================== 正常流程测试 ====================

    /**
     * TC-UI-01: 有效令牌和活跃用户
     * 验证当提供有效的 Bearer Token 且用户活跃时，认证通过
     */
    @Test
    @DisplayName("TC-UI-01: 有效令牌和活跃用户 - 认证通过")
    void testPreHandle_ValidTokenAndActiveUser_ShouldPass() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(validToken)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();

        // 验证请求属性设置
        verify(request).setAttribute(eq("userId"), eq(1L));
        verify(request).setAttribute(eq("currentUser"), any(User.class));
        verify(request).setAttribute(eq("username"), eq("testuser"));
        verify(request).setAttribute(eq("userType"), eq(User.UserType.STUDENT));
    }

    /**
     * TC-UI-02: 非 HandlerMethod 处理
     * 验证当 handler 不是 HandlerMethod 类型时，跳过认证
     */
    @Test
    @DisplayName("TC-UI-02: 非 HandlerMethod 处理 - 跳过认证")
    void testPreHandle_NonHandlerMethod_ShouldSkip() throws Exception {
        // Given - 使用真实的 Mock 对象而不是模拟 getClass()
        Object realNonHandlerMethod = new Object();

        // When
        boolean result = interceptor.preHandle(request, response, realNonHandlerMethod);

        // Then
        assertThat(result).isTrue();
        // 验证没有调用 JWT 相关方法
        verify(jwtUtil, never()).validateToken(anyString());
        verify(userRepository, never()).findById(anyLong());
    }

    // ==================== 白名单路径测试 ====================

    /**
     * TC-UI-03: 白名单路径 /auth/login
     * 验证访问登录接口时跳过认证
     */
    @Test
    @DisplayName("TC-UI-03: 白名单路径 /auth/login - 跳过认证")
    void testPreHandle_WhitelistAuthLogin_ShouldSkip() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/auth/login");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil, never()).validateToken(anyString());
    }

    /**
     * TC-UI-04: 白名单路径 /swagger-ui.html
     * 验证访问 Swagger 文档时跳过认证
     */
    @Test
    @DisplayName("TC-UI-04: 白名单路径 /swagger-ui.html - 跳过认证")
    void testPreHandle_WhitelistSwagger_ShouldSkip() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/swagger-ui.html");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil, never()).validateToken(anyString());
    }

    /**
     * TC-UI-05: 白名单路径 /questions/teacher/get/123
     * 验证访问题目详情接口时跳过认证
     */
    @Test
    @DisplayName("TC-UI-05: 白名单路径 /questions/teacher/get/123 - 跳过认证")
    void testPreHandle_WhitelistQuestionDetail_ShouldSkip() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/questions/teacher/get/123");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil, never()).validateToken(anyString());
    }

    /**
     * TC-UI-06: 路径 /questions/teacher/get/all
     * 验证访问获取所有题目接口时需要认证
     */
    @Test
    @DisplayName("TC-UI-06: 路径 /questions/teacher/get/all - 需要认证")
    void testPreHandle_GetAllQuestions_ShouldRequireAuth() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/questions/teacher/get/all");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(validToken)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil).validateToken(validToken);
    }

    // ==================== 异常流程测试 ====================

    /**
     * TC-UI-07: 缺失 Authorization 头
     * 验证当缺少 Authorization 头时，返回 401 错误
     */
    @Test
    @DisplayName("TC-UI-07: 缺失 Authorization 头 - 返回 401")
    void testPreHandle_MissingAuthorizationHeader_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseBody = stringWriter.toString();
        assertThat(responseBody).contains("\"code\":401");
        assertThat(responseBody).contains("认证失败");
    }

    /**
     * TC-UI-08: 无效 JWT 令牌格式
     * 验证当 JWT 令牌格式无效时，返回 401 错误
     */
    @Test
    @DisplayName("TC-UI-08: 无效 JWT 令牌格式 - 返回 401")
    void testPreHandle_InvalidJwtFormat_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtUtil.validateToken("invalid-token")).thenReturn(false);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseBody = stringWriter.toString();
        assertThat(responseBody).contains("\"code\":401");
    }

    /**
     * TC-UI-09: 过期 JWT 令牌
     * 验证当 JWT 令牌过期时，返回 401 错误
     */
    @Test
    @DisplayName("TC-UI-09: 过期 JWT 令牌 - 返回 401")
    void testPreHandle_ExpiredJwtToken_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);
        when(jwtUtil.validateToken(expiredToken)).thenReturn(false);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseBody = stringWriter.toString();
        assertThat(responseBody).contains("\"code\":401");
    }

    /**
     * TC-UI-10: 用户不存在
     * 验证当用户 ID 对应的用户不存在时，返回 401 错误
     */
    @Test
    @DisplayName("TC-UI-10: 用户不存在 - 返回 401")
    void testPreHandle_UserNotFound_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(validToken)).thenReturn(999L);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseBody = stringWriter.toString();
        assertThat(responseBody).contains("\"code\":401");
    }

    /**
     * TC-UI-11: 用户账户被禁用
     * 验证当用户账户被禁用时，返回 401 错误
     */
    @Test
    @DisplayName("TC-UI-11: 用户账户被禁用 - 返回 401")
    void testPreHandle_UserDisabled_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(validToken)).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(disabledUser));
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        verify(response).setContentType("application/json;charset=UTF-8");

        String responseBody = stringWriter.toString();
        assertThat(responseBody).contains("\"code\":401");
    }

    // ==================== 功能方法测试 ====================

    /**
     * TC-UI-12: extractToken() 方法测试
     * 验证从 Authorization 头中提取 token 的逻辑
     */
    @Test
    @DisplayName("TC-UI-12: extractToken 方法 - Bearer 前缀处理")
    void testExtractToken_BearerPrefixHandling() throws Exception {
        // Given - 大写 Bearer
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);

        // When & Then - 使用反射调用私有方法
        Method extractTokenMethod = UserIdInterceptor.class.getDeclaredMethod("extractToken", HttpServletRequest.class);
        extractTokenMethod.setAccessible(true);
        String token = (String) extractTokenMethod.invoke(interceptor, request);
        assertThat(token).isEqualTo(validToken);

        // Given - 小写 bearer
        when(request.getHeader("Authorization")).thenReturn("bearer " + validToken);

        // When & Then
        token = (String) extractTokenMethod.invoke(interceptor, request);
        assertThat(token).isEqualTo(validToken);

        // Given - 无 Bearer 前缀
        when(request.getHeader("Authorization")).thenReturn(validToken);

        // When & Then
        token = (String) extractTokenMethod.invoke(interceptor, request);
        assertThat(token).isNull();

        // Given - null
        when(request.getHeader("Authorization")).thenReturn(null);

        // When & Then
        token = (String) extractTokenMethod.invoke(interceptor, request);
        assertThat(token).isNull();
    }

    /**
     * TC-UI-13: shouldSkipAuthentication() 方法测试
     * 验证白名单路径判断逻辑
     */
    @Test
    @DisplayName("TC-UI-13: shouldSkipAuthentication 方法 - 白名单判断")
    void testShouldSkipAuthentication_WhitelistPaths() throws Exception {
        // Given
        Method skipMethod = UserIdInterceptor.class.getDeclaredMethod("shouldSkipAuthentication",
                HttpServletRequest.class);
        skipMethod.setAccessible(true);

        String[] whitelistPaths = {
                "/auth/login",
                "/auth/register",
                "/questions/teacher/get/123",
                "/questions/teacher/get/abc",
                "/swagger-ui.html",
                "/swagger-resources",
                "/v3/api-docs/swagger-config",
                "/webjars/springfox-swagger-ui/swagger-ui.css",
                "/answers/health",
                "/answers/next-video",
                "/answers/submit-with-next-video"
        };

        String[] requireAuthPaths = {
                "/questions/teacher/get/all",
                "/api/users",
                "/auth/login/extra",
                "/questions/teacher/get",
                "/"
        };

        // When & Then - 白名单路径应该跳过认证
        for (String path : whitelistPaths) {
            when(request.getRequestURI()).thenReturn(path);
            Boolean result = (Boolean) skipMethod.invoke(interceptor, request);
            assertThat(result).isTrue()
                    .as("路径 %s 应该在白名单中", path);
        }

        // When & Then - 非白名单路径需要认证
        for (String path : requireAuthPaths) {
            when(request.getRequestURI()).thenReturn(path);
            Boolean result = (Boolean) skipMethod.invoke(interceptor, request);
            assertThat(result).isFalse()
                    .as("路径 %s 应该需要认证", path);
        }
    }

    /**
     * 测试 afterCompletion 方法记录用户 ID
     */
    @Test
    @DisplayName("TC-UI-14: afterCompletion - 记录用户 ID")
    void testAfterCompletion_ShouldLogUserId() throws Exception {
        // Given
        Long userId = 1L;
        String uri = "/api/test";
        int status = 200;

        when(request.getAttribute("userId")).thenReturn(userId);
        when(request.getRequestURI()).thenReturn(uri);
        when(response.getStatus()).thenReturn(status);

        // When
        interceptor.afterCompletion(request, response, handlerMethod, null);

        // Then
        verify(request).getAttribute("userId");
        verify(response).getStatus();
    }

    /**
     * 测试 afterCompletion 方法记录异常
     */
    @Test
    @DisplayName("TC-UI-15: afterCompletion - 记录异常")
    void testAfterCompletion_WithException_ShouldLogError() throws Exception {
        // Given
        Long userId = 1L;
        String uri = "/api/test";
        Exception ex = new RuntimeException("Test exception");

        when(request.getAttribute("userId")).thenReturn(userId);
        when(request.getRequestURI()).thenReturn(uri);

        // When
        interceptor.afterCompletion(request, response, handlerMethod, ex);

        // Then
        verify(request).getAttribute("userId");
    }

    /**
     * 测试小写 bearer 前缀的处理
     */
    @Test
    @DisplayName("TC-UI-16: 小写 bearer 前缀 - 正确提取 token")
    void testPreHandle_LowercaseBearerPrefix_ShouldExtractToken() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(validToken)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil).validateToken(validToken);
    }

    /**
     * 测试 TechnicalException 抛出场景
     */
    @Test
    @DisplayName("TC-UI-17: 非认证异常 - 抛出 TechnicalException")
    void testPreHandle_NonAuthenticationException_ShouldThrowTechnicalException() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.validateToken(validToken)).thenThrow(new RuntimeException("Unexpected error"));
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(TechnicalException.class)
                .hasMessageContaining("认证服务暂时不可用");
    }

    /**
     * 测试多个额外的白名单路径
     */
    @Test
    @DisplayName("TC-UI-18: 额外白名单路径 - v3/api-docs")
    void testPreHandle_WhitelistV3ApiDocs_ShouldSkip() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/v3/api-docs");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
        verify(jwtUtil, never()).validateToken(anyString());
    }

    /**
     * 测试边界情况：空 token
     */
    @Test
    @DisplayName("TC-UI-19: 空 token - 返回 401")
    void testPreHandle_EmptyToken_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
    }

    /**
     * 测试边界情况：token 为 null 但 Authorization 头存在
     */
    @Test
    @DisplayName("TC-UI-20: Authorization 头存在但无法提取 token - 返回 401")
    void testPreHandle_AuthorizationExistsButNoToken_ShouldReturn401() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getHeader("Authorization")).thenReturn("Basic something");
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);
        when(handlerMethod.getMethod()).thenReturn(Object.class.getMethod("toString"));

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isFalse();
        verify(response).setStatus(401);
    }
}
