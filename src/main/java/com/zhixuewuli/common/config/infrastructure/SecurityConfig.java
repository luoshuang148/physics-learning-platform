package com.zhixuewuli.common.config.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security配置类
 * 
 * 配置Spring Security的安全策略，暂时禁用安全检查以便测试流式API功能。
 * 在生产环境中应该启用适当的安全措施。
 * 
 * <p>
 * 当前配置：
 * </p>
 * <ul>
 * <li>禁用CSRF保护（开发阶段）</li>
 * <li>允许所有请求匿名访问</li>
 * <li>禁用表单登录</li>
 * <li>禁用HTTP基本认证</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see EnableWebSecurity 启用Web安全配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置密码编码器
     * 
     * 使用BCrypt算法进行密码加密，提供安全的密码存储。
     * BCrypt具有自动加盐和可调节计算复杂度的特点。
     * 
     * @return PasswordEncoder 密码编码器实例
     * 
     * @implSpec 使用默认强度（10）的BCrypt算法
     * @implSpec 在生产环境中可根据安全要求调整强度参数
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置安全过滤链
     * 
     * 定义哪些请求需要保护，哪些可以匿名访问。
     * 当前允许所有请求通过。
     * 
     * @param http HttpSecurity配置对象
     * @return SecurityFilterChain 安全过滤链
     * @throws Exception 配置过程中可能抛出的异常
     * 
     * @implSpec 开发阶段禁用所有安全限制
     * @implSpec 生产环境应启用JWT认证和适当的权限控制
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF保护（开发阶段）
                .csrf(AbstractHttpConfigurer::disable)
                // 配置请求授权规则
                .authorizeHttpRequests(authz -> authz
                        // 允许所有请求匿名访问
                        .anyRequest().permitAll())
                // 允许同源 iframe，供学生实验页嵌入实验组件
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                // 禁用表单登录
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用HTTP基本认证
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
