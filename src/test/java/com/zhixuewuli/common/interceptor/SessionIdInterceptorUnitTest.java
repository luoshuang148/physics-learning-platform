package com.zhixuewuli.common.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.exception.auth.AccountDisabledException;
import com.zhixuewuli.common.exception.auth.InvalidTokenException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.common.util.SessionUtil;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;

/**
 * SessionIdInterceptor 单元测试
 * 
 * 测试会话 ID 拦截器在各种场景下的行为
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionIdInterceptor 单元测试")
class SessionIdInterceptorUnitTest extends BaseUnitTest {

    private SessionIdInterceptor interceptor;

    @Mock
    private MockHttpServletRequest request;

    @Mock
    private MockHttpServletResponse response;

    @Mock
    private Object handler;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<String> headerCaptor;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        interceptor = new SessionIdInterceptor();
        objectMapper = new ObjectMapper();
    }

    /**
     * TC-SI-01: 有效的会话 ID 请求头
     * 验证当请求头包含有效的 X-Session-Id 时，使用现有会话 ID
     */
    @Test
    @DisplayName("TC-SI-01: 有效的会话 ID 请求头 - 使用现有会话 ID")
    void testPreHandle_WithValidSessionId_ShouldUseExisting() throws Exception {
        // Given
        String existingSessionId = "20261222143025_a1b2c3d4e5f67890";
        when(request.getHeader("X-Session-Id")).thenReturn(existingSessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(() -> SessionUtil.isValidSessionId(existingSessionId)).thenReturn(true);
            mockedStatic.when(() -> SessionUtil.sanitizeSessionId(existingSessionId)).thenReturn(existingSessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(existingSessionId);
            verify(response).setHeader(eq("X-Session-Id"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).isEqualTo(existingSessionId);
        }
    }

    /**
     * TC-SI-02: 无会话 ID 请求头
     * 验证当请求头没有 X-Session-Id 时，生成新会话 ID
     */
    @Test
    @DisplayName("TC-SI-02: 无会话 ID 请求头 - 生成新会话 ID")
    void testPreHandle_WithoutSessionId_ShouldGenerateNew() throws Exception {
        // Given
        String newSessionId = "20261222150000_b2c3d4e5f6g78901";
        when(request.getHeader("X-Session-Id")).thenReturn(null);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(SessionUtil::generateSecureSessionId).thenReturn(newSessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(newSessionId);
            verify(response).setHeader(eq("X-Session-Id"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).isEqualTo(newSessionId);
        }
    }

    /**
     * TC-SI-03: 无效格式会话 ID
     * 验证当请求头包含无效格式的 X-Session-Id 时，生成新会话 ID 替换
     */
    @Test
    @DisplayName("TC-SI-03: 无效格式会话 ID - 生成新会话 ID 替换")
    void testPreHandle_WithInvalidSessionId_ShouldGenerateNew() throws Exception {
        // Given
        String invalidSessionId = "invalid-session-id";
        String newSessionId = "20261222150000_c3d4e5f6g7h89012";
        when(request.getHeader("X-Session-Id")).thenReturn(invalidSessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(() -> SessionUtil.isValidSessionId(invalidSessionId)).thenReturn(false);
            mockedStatic.when(SessionUtil::generateSecureSessionId).thenReturn(newSessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(newSessionId);
            verify(response).setHeader(eq("X-Session-Id"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).isEqualTo(newSessionId);
        }
    }

    /**
     * TC-SI-04: 会话 ID 清理测试
     * 验证会话 ID 包含危险字符时会被清理
     */
    @Test
    @DisplayName("TC-SI-04: 会话 ID 清理 - 移除危险字符")
    void testPreHandle_WithDangerousCharacters_ShouldSanitize() throws Exception {
        // Given
        String dangerousSessionId = "20261222143025_a1b2c3d4e5f67890<script>";
        String sanitizedSessionId = "20261222143025_a1b2c3d4e5f67890script";
        when(request.getHeader("X-Session-Id")).thenReturn(dangerousSessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(() -> SessionUtil.isValidSessionId(dangerousSessionId)).thenReturn(true);
            mockedStatic.when(() -> SessionUtil.sanitizeSessionId(dangerousSessionId)).thenReturn(sanitizedSessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(sanitizedSessionId);
            verify(response).setHeader(eq("X-Session-Id"), headerCaptor.capture());
            assertThat(headerCaptor.getValue()).isEqualTo(sanitizedSessionId);
        }
    }

    /**
     * TC-SI-05: 属性与响应头设置
     * 验证请求属性和响应头中的会话 ID 一致
     */
    @Test
    @DisplayName("TC-SI-05: 属性与响应头设置 - 保持一致")
    void testPreHandle_AttributesAndHeadersShouldMatch() throws Exception {
        // Given
        String sessionId = "20261222160000_d4e5f6g7h8i90123";
        when(request.getHeader("X-Session-Id")).thenReturn(sessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(() -> SessionUtil.isValidSessionId(sessionId)).thenReturn(true);
            mockedStatic.when(() -> SessionUtil.sanitizeSessionId(sessionId)).thenReturn(sessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            ArgumentCaptor<String> attributeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);

            verify(request).setAttribute(eq("sessionId"), attributeCaptor.capture());
            verify(response).setHeader(eq("X-Session-Id"), headerCaptor.capture());

            assertThat(attributeCaptor.getValue()).isEqualTo(headerCaptor.getValue())
                    .as("请求属性和响应头中的会话 ID 应该一致");
        }
    }

    /**
     * TC-SI-06: 完成日志记录
     * 验证请求处理完成后正确记录会话使用信息
     */
    @Test
    @DisplayName("TC-SI-06: 完成日志记录 - 记录会话使用信息")
    void testAfterCompletion_ShouldLogUsageInfo() throws Exception {
        // Given
        String sessionId = "20261222170000_e5f6g7h8i9j01234";
        String uri = "/api/test";
        int status = 200;

        when(request.getAttribute("sessionId")).thenReturn(sessionId);
        when(request.getRequestURI()).thenReturn(uri);
        when(response.getStatus()).thenReturn(status);

        // When
        interceptor.afterCompletion(request, response, handler, null);

        // Then
        // 验证方法被调用（日志内容通过控制台输出验证）
        verify(request).getAttribute("sessionId");
        verify(response).getStatus();
    }

    /**
     * 测试 afterCompletion 处理异常情况
     */
    @Test
    @DisplayName("TC-SI-07: 完成日志记录 - 记录异常信息")
    void testAfterCompletion_WithException_ShouldLogError() throws Exception {
        // Given
        String sessionId = "20261222170000_f6g7h8i9j0k12345";
        String uri = "/api/test";
        Exception ex = new RuntimeException("Test exception");

        when(request.getAttribute("sessionId")).thenReturn(sessionId);
        when(request.getRequestURI()).thenReturn(uri);

        // When
        interceptor.afterCompletion(request, response, handler, ex);

        // Then
        verify(request).getAttribute("sessionId");
    }

    /**
     * 测试空会话 ID 的情况
     */
    @Test
    @DisplayName("TC-SI-08: 空会话 ID - 生成新会话 ID")
    void testPreHandle_WithEmptySessionId_ShouldGenerateNew() throws Exception {
        // Given
        String emptySessionId = "";
        String newSessionId = "20261222180000_g7h8i9j0k1l23456";
        when(request.getHeader("X-Session-Id")).thenReturn(emptySessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(SessionUtil::generateSecureSessionId).thenReturn(newSessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(newSessionId);
        }
    }

    /**
     * 测试旧格式 UUID 会话 ID（向后兼容）
     */
    @Test
    @DisplayName("TC-SI-09: 旧格式 UUID 会话 ID - 向后兼容")
    void testPreHandle_WithLegacyUuidSessionId_ShouldAccept() throws Exception {
        // Given
        String legacySessionId = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"; // 32 位纯 UUID
        when(request.getHeader("X-Session-Id")).thenReturn(legacySessionId);

        try (MockedStatic<SessionUtil> mockedStatic = Mockito.mockStatic(SessionUtil.class)) {
            mockedStatic.when(() -> SessionUtil.isValidSessionId(legacySessionId)).thenReturn(true);
            mockedStatic.when(() -> SessionUtil.sanitizeSessionId(legacySessionId)).thenReturn(legacySessionId);

            // When
            boolean result = interceptor.preHandle(request, response, handler);

            // Then
            assertThat(result).isTrue();
            verify(request).setAttribute(eq("sessionId"), stringCaptor.capture());
            assertThat(stringCaptor.getValue()).isEqualTo(legacySessionId);
        }
    }
}
