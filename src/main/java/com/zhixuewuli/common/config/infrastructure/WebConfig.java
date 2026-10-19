package com.zhixuewuli.common.config.infrastructure;

import com.zhixuewuli.common.config.properties.SecurityProperties;
import com.zhixuewuli.common.interceptor.SessionIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类
 * 
 * 配置Spring MVC的核心组件，包括单页应用路由转发和拦截器。
 * 为整个应用程序提供统一的Web层配置。
 * 
 * <p>
 * 配置内容：
 * </p>
 * <ul>
 * <li><strong>SPA路由</strong>: 前端 history 路由刷新时转发到 index.html</li>
 * <li><strong>拦截器配置</strong>: 注册会话ID拦截器；认证与授权由 SecurityConfig 统一处理</li>
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
    private final SessionIdInterceptor sessionIdInterceptor;
    private final SecurityProperties securityProperties;

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
        // 会话ID拦截器（所有接口，除文档外）
        registry.addInterceptor(sessionIdInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(securityProperties.getSessionExcludePaths());
    }
}