package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 测试工具类
 * 提供常用的测试辅助方法
 */
public class TestUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将对象转换为JSON字符串
     */
    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to object", e);
        }
    }

    /**
     * 创建POST请求构建器
     */
    public static MockHttpServletRequestBuilder postJson(String url, Object body) {
        return MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body));
    }

    /**
     * 创建PUT请求构建器
     */
    public static MockHttpServletRequestBuilder putJson(String url, Object body) {
        return MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body));
    }

    /**
     * 创建GET请求构建器
     */
    public static MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    /**
     * 创建DELETE请求构建器
     */
    public static MockHttpServletRequestBuilder delete(String url) {
        return MockMvcRequestBuilders.delete(url);
    }

    /**
     * 生成随机用户名
     */
    public static String randomUsername() {
        return "testuser_" + System.currentTimeMillis();
    }

    /**
     * 生成随机邮箱（模拟格式）
     */
    public static String randomEmail() {
        return "test_" + System.currentTimeMillis() + "@example.com";
    }

    /**
     * 生成随机手机号（模拟格式）
     */
    public static String randomPhone() {
        return "138" + String.format("%08d", (int) (Math.random() * 100000000));
    }

    /**
     * 生成测试JWT Token
     */
    public static String generateTestToken(Long userId) {
        // 简化的JWT生成，实际测试中可能需要更复杂的实现
        return "test_token_for_user_" + userId + "_" + System.currentTimeMillis();
    }

    /**
     * 创建模拟HTTP请求
     */
    public static MockHttpServletRequest createMockRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        return request;
    }

    /**
     * 创建模拟HTTP响应
     */
    public static MockHttpServletResponse createMockResponse() {
        return new MockHttpServletResponse();
    }

    /**
     * 验证两个时间是否在指定时间内接近
     */
    public static boolean isTimeClose(LocalDateTime time1, LocalDateTime time2, long toleranceSeconds) {
        if (time1 == null || time2 == null) {
            return time1 == time2;
        }
        long diff = Math.abs(time1.toEpochSecond(java.time.ZoneOffset.UTC) -
                time2.toEpochSecond(java.time.ZoneOffset.UTC));
        return diff <= toleranceSeconds;
    }

    /**
     * 创建测试用的Map数据
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOf(Map.Entry<K, V>... entries) {
        Map<K, V> map = new java.util.HashMap<>();
        for (Map.Entry<K, V> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /**
     * 创建键值对
     */
    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new java.util.AbstractMap.SimpleEntry<>(key, value);
    }
}