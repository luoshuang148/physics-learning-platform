package com.zhixuewuli.common.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI流式服务配置属性类
 * 
 * 用于管理AI流式问答相关的配置参数，包括网络超时、连接池、
 * 并发控制、重试策略等关键基础设施配置。
 * 通过@ConfigurationProperties注解实现配置文件属性的自动绑定。
 * 
 * <p>
 * 配置设计理念：
 * </p>
 * <ul>
 * <li><strong>层级化配置</strong>: 支持通用配置和细粒度配置的组合使用</li>
 * <li><strong>合理默认值</strong>: 所有配置都有经过实践验证的默认值</li>
 * <li><strong>性能优化</strong>: 通过连接池和并发控制提升系统性能</li>
 * <li><strong>稳定性保障</strong>: 超时控制和重试机制确保服务稳定运行</li>
 * </ul>
 * 
 * <p>
 * 超时配置说明：
 * </p>
 * <ul>
 * <li><strong>独立配置</strong>: 各超时项均为独立配置，互不影响</li>
 * <li><strong>调用超时</strong>: call-timeout控制整个请求的总时间</li>
 * <li><strong>阶段超时</strong>: connect/read/write timeout分别控制各阶段</li>
 * <li><strong>无限制设置</strong>: 调用超时设为0表示无时间限制</li>
 * </ul>
 * 
 * <p>
 * 配置示例：
 * </p>
 * 
 * <pre>
 * ai:
 *   streaming:
 *     # 整体调用超时配置（毫秒）
 *     call-timeout: 300000      # 整个请求的总超时时间
 *     
 *     # 各阶段独立超时配置
 *     connect-timeout: 10000    # 连接建立超时
 *     read-timeout: 60000       # 数据读取超时
 *     write-timeout: 40000      # 数据写入超时
 *     
 *     # 连接池配置
 *     max-idle-connections: 10  # 最大空闲连接数
 *     keep-alive-duration: 5    # 连接保活时间（分钟）
 *     
 *     # 并发控制配置
 *     max-concurrent-requests: 32   # 全局最大并发请求数
 *     max-requests-per-host: 16     # 每主机最大并发数
 *     
 *     # 连接可靠性配置
 *     auto-reconnect: true      # 网络异常时自动重连
 *     
 *     # 数据处理配置
 *     buffer-size: 8192         # 读取缓冲区大小（字节）
 *     
 *     # 线程池配置
 *     ai-core-pool-size: 10     # AI任务核心线程数
 *     ai-max-pool-size: 50      # AI任务最大线程数
 *     ai-queue-capacity: 200    # AI任务队列容量
 *     ai-thread-name-prefix: "ai-task-"  # 线程名称前缀
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see com.zhixuewuli.common.client.base.AbstractStreamingClient HTTP客户端基类
 */
@Data
@ConfigurationProperties(prefix = "ai.streaming")
@Component
public class AIStreamingProperties {

    // ========== 超时配置 ==========
    /**
     * 整体调用超时时间（毫秒）
     * 控制整个HTTP请求调用的总时间限制，包括连接、写入、读取等所有阶段
     * 设置为0表示无超时限制
     * 默认300秒，适用于大多数AI流式服务场景
     */
    private Long callTimeout = 300000L;

    /**
     * 连接建立超时时间（毫秒）
     * 控制与AI服务建立TCP连接的时间限制
     * 独立配置项，不依赖其他超时设置，默认10秒
     */
    private Long connectTimeout = 10000L;

    /**
     * 数据读取超时时间（毫秒）
     * 控制从AI服务读取响应数据的时间限制
     * 独立配置项，不依赖其他超时设置，默认60秒
     */
    private Long readTimeout = 60000L;

    /**
     * 数据写入超时时间（毫秒）
     * 控制向AI服务发送请求数据的时间限制
     * 独立配置项，不依赖其他超时设置，默认40秒
     */
    private Long writeTimeout = 40000L;

    // ========== 连接池配置 ==========
    /**
     * 最大空闲连接数
     * 控制连接池中保持的空闲连接数量上限
     * 合理设置可以减少连接建立开销，提高响应速度
     * 默认10个连接，根据实际并发量可适当调整
     */
    private Integer maxIdleConnections = 10;

    /**
     * 连接保活时间（分钟）
     * 空闲连接在连接池中的最长保持时间
     * 超过此时间的空闲连接会被自动清理
     * 默认5分钟，平衡了连接复用和资源回收
     */
    private Long keepAliveDuration = 5L;

    // ========== 可靠性配置 ==========
    /**
     * 连接失败自动重试开关
     * 对应OkHttp的retryOnConnectionFailure配置
     * 开启后在网络异常时会自动尝试重新连接
     * 默认开启，提高服务的容错能力
     */
    private Boolean autoReconnect = true;

    // ========== 数据处理配置 ==========
    /**
     * 读取缓冲区大小（字节）
     * 用于BufferedReader的数据读取缓冲区大小
     * 影响数据读取效率和内存使用
     * 默认8192字节（8KB），适合大多数流式传输场景
     */
    private Integer bufferSize = 8192;

    // ========== 并发控制配置 ==========
    /**
     * 最大并发请求数
     * 控制整个应用同时向AI服务发起的最大请求数
     * 防止因并发过高导致系统资源耗尽
     * 默认32个并发请求，可根据服务器性能调整
     */
    private Integer maxConcurrentRequests = 32;

    /**
     * 每主机最大并发请求数
     * 控制对单个AI服务主机的并发请求数量上限
     * 避免对单一服务造成过大压力
     * 默认16个并发，保护AI服务提供商的稳定性
     */
    private Integer maxRequestsPerHost = 16;

    // ========== SSE配置 ==========
    /**
     * SSE连接超时时间（毫秒）
     * 建议配合前端reconnectTime使用，一般设为300000（5分钟）
     */
    private Long sseEmitterTimeout = 300000L;

    /**
     * SSE客户端自动重连时间（毫秒）
     * 控制客户端在连接断开后的重试间隔时间
     * 默认3000毫秒（3秒），建议与前端重连策略保持一致
     */
    private Long sseReconnectTime = 3000L;

    // ========== 线程池配置 ==========
    /**
     * AI任务线程池核心线程数
     * 处理AI相关的异步任务，包括SSE请求处理和对话历史保存
     * 默认10个核心线程
     */
    private Integer aiCorePoolSize = 10;

    /**
     * AI任务线程池最大线程数
     * 当核心线程都在忙碌且队列满时创建的额外线程数上限
     * 默认50个最大线程
     */
    private Integer aiMaxPoolSize = 50;

    /**
     * AI任务线程池队列容量
     * 等待执行的任务队列最大容量
     * 默认200个任务队列容量
     */
    private Integer aiQueueCapacity = 200;

    /**
     * AI 任务线程池线程名称前缀
     * 用于区分不同线程池创建的线程
     * 默认"ai-task-"
     */
    private String aiThreadNamePrefix = "ai-task-";

    // ========== SSE 连接数限制配置 ==========
    /**
     * 最大 SSE 连接数限制
     * 控制同时允许的 SSE 连接数量上限，防止高并发时系统资源耗尽
     * 默认 32 个连接，可根据服务器性能调整
     */
    private Integer maxSseConnections = 32;

}