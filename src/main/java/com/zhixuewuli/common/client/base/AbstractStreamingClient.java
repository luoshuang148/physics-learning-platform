package com.zhixuewuli.common.client.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 抽象流式AI客户端基类
 * 
 * 提供AI客户端的通用基础功能，包括HTTP客户端配置、
 * 连接池管理、超时设置等基础设施。作为所有具体AI客户端的父类，
 * 封装了网络通信的核心逻辑和配置管理。
 * 
 * <p>
 * 核心特性：
 * </p>
 * <ul>
 * <li><strong>统一的HTTP客户端配置</strong>: 基于OkHttp构建，支持连接池复用</li>
 * <li><strong>精细化超时控制</strong>: 支持连接、读取、写入三个维度的超时配置</li>
 * <li><strong>并发请求管理</strong>: 通过Dispatcher控制最大并发请求数</li>
 * <li><strong>连接池优化</strong>: 可配置最大空闲连接数和保活时间</li>
 * <li><strong>自动重连机制</strong>: 网络异常时自动尝试重新连接</li>
 * <li><strong>完整的日志跟踪</strong>: 记录请求开始、完成、错误等关键节点</li>
 * <li><strong>JSON序列化支持</strong>: 内置ObjectMapper用于数据转换</li>
 * <li><strong>配置验证机制</strong>: 启动时验证必要配置项的完整性</li>
 * </ul>
 * 
 * <p>
 * 配置来源说明：
 * </p>
 * <ul>
 * <li>通过@Autowired注入AIStreamingProperties配置属性</li>
 * <li>子类通过抽象方法提供API密钥和基础URL</li>
 * <li>配置文件中的值优先于代码中的默认值</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see OkHttpClient HTTP客户端实现
 * @see AIStreamCallback 流式回调接口
 * @see AIStreamingProperties 流式服务配置属性
 */
@Slf4j
public abstract class AbstractStreamingClient {

    /**
     * JSON对象映射器
     * 用于处理HTTP请求/响应的JSON序列化和反序列化操作
     */
    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * HTTP客户端实例
     * 基于OkHttp构建，负责所有的网络通信操作
     */
    protected OkHttpClient httpClient;

    /**
     * AI流式服务配置属性
     * 注入配置文件中的流式服务相关参数
     */
    @Autowired
    protected AIStreamingProperties aiStreamingProperties;

    /**
     * 初始化HTTP客户端
     * 
     * 在Spring容器初始化完成后自动调用，配置完整的HTTP客户端。
     * 包括超时设置、连接池参数、并发控制和自动重连等核心配置。
     * 
     * <p>
     * 超时配置说明：
     * </p>
     * <ul>
     * <li><strong>独立配置</strong>: 各超时项均为独立配置，互不影响</li>
     * <li><strong>调用超时</strong>: callTimeout控制整个请求的总时间（0表示无限制）</li>
     * <li><strong>阶段超时</strong>: connect/read/write timeout分别控制各阶段</li>
     * <li><strong>默认值</strong>: 当配置未设置时使用合理的默认值</li>
     * </ul>
     * 
     * <p>
     * 连接池配置说明：
     * </p>
     * <ul>
     * <li>最大空闲连接数：默认5个，可通过maxIdleConnections配置</li>
     * <li>连接保活时间：默认5分钟，可通过keepAliveDuration配置</li>
     * <li>超过保活时间的空闲连接会被自动清理</li>
     * </ul>
     * 
     * @implNote 此方法使用{@code @PostConstruct}注解确保在依赖注入完成后执行
     */
    @PostConstruct
    protected void initHttpClient() {

        validateConfiguration();

        // 获取各维度超时配置值，使用独立配置项
        long connectTimeout = aiStreamingProperties.getConnectTimeout() != null
                ? aiStreamingProperties.getConnectTimeout()
                : 10000L;
        long readTimeout = aiStreamingProperties.getReadTimeout() != null
                ? aiStreamingProperties.getReadTimeout()
                : 60000L;
        long writeTimeout = aiStreamingProperties.getWriteTimeout() != null
                ? aiStreamingProperties.getWriteTimeout()
                : 40000L;
        // 获取整体调用超时配置，0表示无限制
        long callTimeout = aiStreamingProperties.getCallTimeout() != null
                ? aiStreamingProperties.getCallTimeout()
                : 0;

        // 创建请求分发器并配置并发限制
        // Dispatcher用于控制HTTP请求的并发执行，防止系统过载
        okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
        if (aiStreamingProperties.getMaxConcurrentRequests() != null) {
            // 设置全局最大并发请求数，防止系统资源耗尽
            dispatcher.setMaxRequests(aiStreamingProperties.getMaxConcurrentRequests());
        }
        if (aiStreamingProperties.getMaxRequestsPerHost() != null) {
            // 设置每主机最大并发请求数，避免对单一服务造成过大压力
            dispatcher.setMaxRequestsPerHost(aiStreamingProperties.getMaxRequestsPerHost());
        }

        // 构建HTTP客户端，集成所有配置参数
        this.httpClient = new OkHttpClient.Builder()
                // 网络连接超时设置
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                // 数据读取超时设置
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                // 数据写入超时设置
                .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
                // 整个请求的总超时设置
                .callTimeout(callTimeout, TimeUnit.MILLISECONDS)
                // 连接池配置：管理空闲连接的复用和清理
                .connectionPool(new okhttp3.ConnectionPool(
                        aiStreamingProperties.getMaxIdleConnections() != null
                                ? aiStreamingProperties.getMaxIdleConnections()
                                : 5,
                        aiStreamingProperties.getKeepAliveDuration() != null
                                ? aiStreamingProperties.getKeepAliveDuration()
                                : 5,
                        TimeUnit.MINUTES))
                // 网络连接失败时是否自动重试
                .retryOnConnectionFailure(
                        aiStreamingProperties.getAutoReconnect() != null ? aiStreamingProperties.getAutoReconnect()
                                : true)
                // 请求分发器配置
                .dispatcher(dispatcher)
                .build();

        // 记录客户端初始化完成的日志信息
        log.info(
                "初始化{}HTTP客户端完成，连接超时:{}ms，读取超时:{}ms，写入超时:{}ms，调用超时:{}ms，最大空闲连接数:{}, 空闲时间:{}分钟, 自动重连:{}, 最大并发请求数:{}, 每主机最大并发数:{}",
                this.getClass().getSimpleName(),
                connectTimeout,
                readTimeout,
                writeTimeout,
                callTimeout,
                aiStreamingProperties.getMaxIdleConnections(),
                aiStreamingProperties.getKeepAliveDuration(),
                aiStreamingProperties.getAutoReconnect(),
                aiStreamingProperties.getMaxConcurrentRequests(),
                aiStreamingProperties.getMaxRequestsPerHost());
    }

    /**
     * 获取API密钥
     * 
     * 抽象方法，由具体的AI客户端子类实现。
     * 返回用于API认证的密钥字符串。
     * 
     * @return API密钥字符串，不能为空
     * @throws IllegalStateException 当密钥未配置时抛出
     */
    protected abstract String getApiKey();

    /**
     * 获取基础URL
     * 
     * 抽象方法，由具体的AI客户端子类实现。
     * 返回AI服务的基础API端点地址。
     * 
     * @return 基础URL字符串，不能为空
     * @throws IllegalStateException 当URL未配置时抛出
     */
    protected abstract String getBaseUrl();

    /**
     * 验证客户端配置
     * 
     * 检查必要的配置项是否正确设置，在发起请求前确保环境准备就绪。
     * 主要验证API密钥和基础URL两个核心配置项。
     * 
     * @throws IllegalStateException 当配置不完整时抛出
     * @implNote 建议在具体客户端的初始化方法中调用此方法
     */
    protected void validateConfiguration() {
        if (getApiKey() == null || getApiKey().isEmpty()) {
            throw new IllegalStateException("API密钥未配置");
        }
        if (getBaseUrl() == null || getBaseUrl().isEmpty()) {
            throw new IllegalStateException("基础URL未配置");
        }
    }

    /**
     * 记录流式传输开始
     * 
     * 记录流式请求的开始信息，用于监控和调试。
     * 包含使用的AI模型和提示词长度等关键信息。
     * 
     * @param model        使用的AI模型名称
     * @param promptLength 提示词字符长度
     * @implNote 日志级别为INFO，便于生产环境监控
     */
    protected void logStreamStart(String model, int promptLength) {
        log.info("开始流式传输 - 模型: {}, 提示词长度: {}", model, promptLength);
    }

    /**
     * 记录流式传输完成
     * 
     * 记录流式请求的成功完成信息，包含性能指标。
     * 用于性能监控和用户体验分析。
     * 
     * @param model    使用的AI模型名称
     * @param duration 请求总耗时(毫秒)
     * @implNote 日志级别为INFO，便于统计分析
     */
    protected void logStreamComplete(String model, long duration) {
        log.info("流式传输完成 - 模型: {}, 耗时: {}ms", model, duration);
    }

    /**
     * 记录流式传输错误
     * 
     * 记录流式请求过程中的异常信息，用于问题排查。
     * 包含完整的异常堆栈信息以便调试。
     * 
     * @param model 使用的AI模型名称
     * @param error 发生的具体异常对象
     * @implNote 日志级别为ERROR，包含异常堆栈
     */
    protected void logStreamError(String model, Exception error) {
        log.error("流式传输错误 - 模型: {}, 错误: {}", model, error.getMessage(), error);
    }
}