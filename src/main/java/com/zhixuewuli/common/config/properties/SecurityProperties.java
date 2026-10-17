package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 安全配置属性类
 * 
 * 管理应用程序安全相关的配置参数，包括JWT令牌配置、CORS跨域配置等。
 * 提供安全相关的默认值，确保系统的安全性和稳定性。
 * 
 * <p>
 * 配置项说明：
 * </p>
 * <ul>
 * <li><strong>jwtExpiration</strong>: JWT令牌过期时间，默认24小时</li>
 * <li><strong>corsMaxAge</strong>: CORS预检请求缓存时间，默认1小时</li>
 * </ul>
 * 
 * <p>
 * 配置示例：
 * </p>
 * 
 * <pre>
 * app:
 *   security:
 *     jwt-expiration: 86400000
 *     cors-max-age: 3600
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see ConfigurationProperties Spring Boot配置属性注解
 */
@Data
@Component("securityProperties")
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

        private long jwtExpiration = 86400000L;
        private int corsMaxAge = 3600;

        // 安全开关
        private boolean csrfEnabled = false; // 默认关闭（开发环境）
        private boolean securityEnabled = true; // 是否启用Spring Security

        // CORS 详细配置（当前为全开放，可调整）
        private List<String> corsAllowedOrigins = Collections.singletonList("*");
        private List<String> corsAllowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        private List<String> corsAllowedHeaders = Collections.singletonList("*");
        private boolean corsAllowCredentials = true;

        // 拦截器排除路径
        private List<String> authExcludePaths = Arrays.asList(
                        "/auth/login",
                        "/auth/register",
                        "/swagger-ui/**",
                        "/v1/api-docs/**",
                        "/webjars/**",
                        "/actuator/**"); // Actuator 健康检查和指标监控

        private List<String> sessionExcludePaths = Arrays.asList(
                        "/swagger-ui/**",
                        "/v1/api-docs/**",
                        "/webjars/**",
                        "/actuator/**"); // Actuator 健康检查和指标监控

        // JWT 配置
        private String jwtSecret = "zhixuewuli-secret-key-for-development-only-change-in-production-please";
        private long jwtExpirationMs = 86400000L;
}