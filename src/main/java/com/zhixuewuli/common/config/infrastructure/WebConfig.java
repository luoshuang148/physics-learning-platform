package com.zhixuewuli.common.config.infrastructure;

import com.zhixuewuli.common.config.properties.SecurityProperties;
import com.zhixuewuli.common.interceptor.SessionIdInterceptor;
import com.zhixuewuli.common.interceptor.UserIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 
 * 配置Spring MVC的核心组件，包括跨域资源共享(CORS)和拦截器。
 * 为整个应用程序提供统一的Web层配置。
 * 
 * <p>
 * 配置内容：
 * </p>
 * <ul>
 * <li><strong>CORS配置</strong>: 允许跨域请求，支持前后端分离架构</li>
 * <li><strong>拦截器配置</strong>: 注册认证拦截器，保护API安全</li>
 * <li><strong>路径排除</strong>: 为公开接口和文档接口设置白名单</li>
 * </ul>
 * 
 * <p>
 * 安全策略：
 * </p>
 * <ul>
 * <li>除特定公开接口外，所有API都需要身份认证</li>
 * <li>支持凭证传输，维持会话状态</li>
 * <li>合理的缓存时间配置，平衡性能与安全性</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see WebMvcConfigurer Spring MVC配置接口
 * @see SecurityProperties 安全配置属性类
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final UserIdInterceptor userIdInterceptor;
    private final SessionIdInterceptor sessionIdInterceptor;
    private final SecurityProperties securityProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(securityProperties.getCorsAllowedOrigins().toArray(new String[0]))
                .allowedMethods(securityProperties.getCorsAllowedMethods().toArray(new String[0]))
                .allowedHeaders(securityProperties.getCorsAllowedHeaders().toArray(new String[0]))
                .exposedHeaders("X-Session-Id")
                .allowCredentials(securityProperties.isCorsAllowCredentials())
                .maxAge(securityProperties.getCorsMaxAge());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 支持 Vue Router history 模式下的刷新与直达访问
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/auth/**").setViewName("forward:/index.html");
        registry.addViewController("/student/**").setViewName("forward:/index.html");
        registry.addViewController("/teacher/**").setViewName("forward:/index.html");
        registry.addViewController("/preview/**").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户ID拦截器（需要认证的接口）
        registry.addInterceptor(userIdInterceptor)
                .addPathPatterns("/**");

        // 会话ID拦截器（所有接口，除文档外）
        registry.addInterceptor(sessionIdInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(securityProperties.getSessionExcludePaths());
    }
}