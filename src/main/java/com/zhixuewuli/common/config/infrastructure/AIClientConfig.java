package com.zhixuewuli.common.config.infrastructure;

import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.util.StreamUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@Slf4j
public class AIClientConfig {

    private final AIStreamingProperties aiStreamingProperties;

    /** AI 线程池监控定时任务 - 由 Spring 容器管理生命周期 */
    private final ScheduledExecutorService aiMonitorScheduler;

    /** StreamUtils 实例，用于设置连接数限制 */
    private final StreamUtils streamUtils;

    public AIClientConfig(AIStreamingProperties aiStreamingProperties, StreamUtils streamUtils) {
        this.aiStreamingProperties = aiStreamingProperties;
        this.streamUtils = streamUtils;

        // 初始化 StreamUtils 的连接数限制
        this.streamUtils.setMaxConnections(aiStreamingProperties.getMaxSseConnections());

        // 创建带守护线程的调度器，确保 JVM 可以正常退出
        this.aiMonitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-monitor-thread");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * AI 任务专用线程池
     * 
     * 用于处理 AI 相关的异步任务，包括 SSE 请求处理和对话历史保存
     * 配置参数从 AIStreamingProperties 中获取，支持动态调整
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(aiStreamingProperties.getAiCorePoolSize());
        executor.setMaxPoolSize(aiStreamingProperties.getAiMaxPoolSize());
        executor.setQueueCapacity(aiStreamingProperties.getAiQueueCapacity());
        executor.setThreadNamePrefix(aiStreamingProperties.getAiThreadNamePrefix());

        // Saturation must not run blocking model I/O on the servlet caller.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

        executor.initialize();

        // 定期记录线程池状态（每 5 分钟）- 使用实例变量 aiMonitorScheduler
        aiMonitorScheduler.scheduleAtFixedRate(() -> {
            log.info("AI 线程池状态 - 活跃线程：{}/{}, 队列大小：{}, 等待任务：{}, 完成任务：{}",
                    executor.getActiveCount(),
                    executor.getMaxPoolSize(),
                    executor.getThreadPoolExecutor().getQueue().size(),
                    executor.getThreadPoolExecutor().getQueue().size(),
                    executor.getThreadPoolExecutor().getCompletedTaskCount());
        }, 5, 5, TimeUnit.MINUTES);

        return executor;
    }

    /**
     * Spring 容器销毁时关闭监控线程池
     */
    @PreDestroy
    public void destroy() {
        if (aiMonitorScheduler != null && !aiMonitorScheduler.isShutdown()) {
            log.info("正在关闭 AI 线程池监控任务...");
            aiMonitorScheduler.shutdown();
            try {
                if (!aiMonitorScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("监控任务未在指定时间内关闭，强制终止");
                    aiMonitorScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭监控任务中断", e);
                aiMonitorScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
