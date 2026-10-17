package com.zhixuewuli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智学物理后端应用启动类
 * 
 * Spring Boot应用程序的主入口点，负责初始化和启动整个应用。
 * 配置包扫描、自动配置和应用上下文管理。
 * 
 * <p>
 * 核心功能集成：
 * </p>
 * <ul>
 * <li>智能AI问答服务</li>
 * <li>视频学习管理</li>
 * <li>在线答题系统</li>
 * <li>学习数据分析与报告</li>
 * <li>用户认证与授权</li>
 * </ul>
 * 
 * <p>
 * 启动配置：
 * </p>
 * <ul>
 * <li>启用Spring Boot自动配置</li>
 * <li>配置组件扫描路径</li>
 * <li>启用配置属性绑定</li>
 * <li>启用重试机制支持</li>
 * </ul>
 * 
 * <p>
 * 部署说明：
 * </p>
 * <ul>
 * <li>支持多环境配置(dev/prod)</li>
 * <li>集成健康检查和监控端点</li>
 * <li>支持优雅停机和热部署</li>
 * <li>配置合理的JVM参数优化性能</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see SpringBootApplication Spring Boot核心注解
 * @see EnableConfigurationProperties 启用配置属性绑定
 * @see EnableRetry 启用重试机制
 */
@SpringBootApplication(scanBasePackages = "com.zhixuewuli")
@EnableConfigurationProperties
@EnableJpaRepositories(basePackages = "com.zhixuewuli.repository")
@EntityScan(basePackages = "com.zhixuewuli.entity")
@EnableRetry
@EnableScheduling
public class Application {

    /**
     * 应用程序主入口方法
     * 
     * 初始化并启动Spring Boot应用程序容器，加载所有必要的组件和服务。
     * 
     * @param args 命令行参数
     * @throws IllegalStateException    当应用程序启动失败时抛出
     * @throws IllegalArgumentException 当参数格式不正确时抛出
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
