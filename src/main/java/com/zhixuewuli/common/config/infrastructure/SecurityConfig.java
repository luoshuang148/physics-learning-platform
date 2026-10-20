package com.zhixuewuli.common.config.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.SecurityProperties;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.security.JwtAuthenticationFilter;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.common.security.AuthUserCache;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * 统一的认证与授权配置。
 *
 * <ul>
 * <li>认证：{@link JwtAuthenticationFilter} 解析 Bearer 令牌，写入 SecurityContext；</li>
 * <li>URL 授权：默认需要登录，仅放行登录/注册、健康检查、接口文档和前端静态资源；</li>
 * <li>角色授权：控制器方法上的 {@code @PreAuthorize("hasRole('TEACHER')")}；</li>
 * <li>数据归属（例如学生只能看自己的记录）仍由 Service 层按业务规则判断。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 由后端托管的前端单页应用资源与路由，均为 GET。 */
    private static final String[] SPA_RESOURCES = {
            "/", "/index.html", "/favicon.svg", "/favicon.png", "/icons.svg", "/assets/**", "/three-physical-project/**",
            "/auth/login", "/auth/register", "/student/**", "/teacher/**", "/preview/**"
    };

    private static final String[] API_DOCS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.getCorsAllowedOrigins());
        config.setAllowedMethods(properties.getCorsAllowedMethods());
        config.setAllowedHeaders(properties.getCorsAllowedHeaders());
        config.setExposedHeaders(List.of("X-Session-Id"));
        config.setAllowCredentials(properties.isCorsAllowCredentials());
        config.setMaxAge((long) properties.getCorsMaxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, AuthUserCache users,
            ObjectMapper objectMapper, @Value("${springdoc.api-docs.enabled:true}") boolean docsEnabled)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // 使用下方 CorsConfigurationSource，预检请求在认证之前处理
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    // SSE 的异步分派、错误页和 SPA 转发都源自一个已经通过授权的请求
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD)
                            .permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll();
                    auth.requestMatchers("/actuator/health").permitAll();
                    auth.requestMatchers("/actuator", "/actuator/**").hasRole("TEACHER");
                    if (docsEnabled) {
                        auth.requestMatchers(API_DOCS).permitAll();
                    }
                    // /teacher/events 是 API，必须排在 SPA 的 /teacher/** 之前
                    auth.requestMatchers("/teacher/events", "/teacher/events/**").authenticated();
                    auth.requestMatchers("/teacher/report-batches", "/teacher/report-batches/**").authenticated();
                    auth.requestMatchers(HttpMethod.GET, SPA_RESOURCES).permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil, users),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> {
                            Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
                            String message = reason == null ? "请先登录" : "认证失败: " + reason;
                            writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, message);
                        })
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "权限不足")))
                // 允许同源 iframe，供学生实验页嵌入实验组件
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                new Result<>(status.value(), message, null, System.currentTimeMillis(), null));
    }
}
