package com.zhixuewuli.common.util;

import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.redis.RedisConnectionLeases;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 流式处理工具类
 * 
 * 集中处理 SSE 流式通信中的通用操作，包括错误消息发送和发射器安全关闭等。
 * 消除各服务实现类中的重复代码，提高代码复用性和可维护性。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Component
@Slf4j
public class StreamUtils {

    private final RedisConnectionLeases leases;
    private final Map<SseEmitter, String> emitterLeases = new ConcurrentHashMap<>();

    /** 活跃连接映射表：emitter -> 创建时间戳 */
    private final Map<SseEmitter, Long> activeEmitters = new ConcurrentHashMap<>();

    /** 关联上下文映射表：emitter -> 关联的 SessionContext */
    private final Map<SseEmitter, SessionContextInternalDTO> emitterContexts = new ConcurrentHashMap<>();

    private final Map<String, SseEmitter> conversationOwners = new ConcurrentHashMap<>();
    private final Map<SseEmitter, Long> emitterUsers = new ConcurrentHashMap<>();

    /** Request state lives on the emitter, so late callbacks cannot cancel a later request. */
    private static final class RequestEmitter extends SseEmitter {
        private boolean cancelled;
        private final java.util.List<Runnable> actions = new java.util.ArrayList<>();
        RequestEmitter(long timeout) { super(timeout); }
        synchronized boolean isCancelled() { return cancelled; }
        void onCancel(Runnable action) {
            synchronized (this) {
                if (!cancelled) { actions.add(action); return; }
            }
            action.run();
        }
        void cancelRequest() {
            java.util.List<Runnable> pending;
            synchronized (this) {
                if (cancelled) return;
                cancelled = true;
                pending = java.util.List.copyOf(actions);
                actions.clear();
            }
            for (Runnable action : pending) {
                try { action.run(); } catch (RuntimeException ex) { log.debug("取消上游调用失败", ex); }
            }
        }
    }

    public boolean isCancelled(SseEmitter emitter) {
        return emitter instanceof RequestEmitter request && request.isCancelled();
    }

    public void onCancel(SseEmitter emitter, Runnable action) {
        if (emitter instanceof RequestEmitter request) request.onCancel(action);
        else {
            emitter.onCompletion(action);
            emitter.onTimeout(action);
            emitter.onError(error -> action.run());
        }
    }

    public void claimUser(SseEmitter emitter, Long userId) {
        synchronized (emitterLeases) {
            if (userId == null) throw new BusinessException("未认证请求");
            String lease = emitterLeases.get(emitter);
            if (isCancelled(emitter) || lease == null) throw new BusinessException("请求已取消");
            if (emitterUsers.containsKey(emitter)) return;
            Integer configured = aiStreamingProperties.getMaxSseConnectionsPerUser();
            int limit = configured == null || configured < 1 ? 2 : configured;
            if (!leases.claimUser(lease, userId, limit))
                throw new BusinessException("同时生成的请求过多，请等待当前请求完成");
            emitterUsers.put(emitter, userId);
        }
    }

    private String conversationKey(SessionContextInternalDTO context) {
        return context.getUserId() + ":" + context.getSessionId() + ":" + context.getConversationType() + ":" + context.getQuestionId();
    }

    /** 最大连接数限制（从配置类读取） */
    private volatile Integer maxConnections = null;

    /** 连接数监控定时任务 - 由 Spring 容器管理生命周期 */
    private final ScheduledExecutorService connectionMonitor;

    /** AI 流式配置属性 */
    private final AIStreamingProperties aiStreamingProperties;

    /**
     * 构造器注入 AIStreamingProperties
     */
    public StreamUtils(AIStreamingProperties aiStreamingProperties, RedisConnectionLeases leases) {
        this.leases = leases;
        this.aiStreamingProperties = aiStreamingProperties;

        // 创建带守护线程的调度器，确保 JVM 可以正常退出
        this.connectionMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-connection-monitor");
            t.setDaemon(true);
            return t;
        });

        // 定期执行清理和状态记录（每 5 分钟）
        this.connectionMonitor.scheduleAtFixedRate(() -> {
            try {
                cleanupStaleConnections();
                logConnectionStatus();
            } catch (RuntimeException error) { log.warn("SSE 连接监控失败", error); }
        }, 5, 5, TimeUnit.MINUTES);

        this.connectionMonitor.scheduleAtFixedRate(this::renewLeases, 20, 20, TimeUnit.SECONDS);
        log.info("SSE 连接监控任务已启动");
    }

    /**
     * 获取最大连接数限制
     * 
     * @return 最大连接数，如果未初始化则返回默认值 32
     */
    private int getMaxConnections() {
        if (maxConnections == null) {
            synchronized (StreamUtils.class) {
                if (maxConnections == null) {
                    maxConnections = 32; // 默认值
                }
            }
        }
        return maxConnections;
    }

    /**
     * 设置最大连接数限制（由 Spring 容器初始化时调用）
     * 
     * @param maxConnections 最大连接数
     */
    public void setMaxConnections(int maxConnections) {
        synchronized (this) {
            this.maxConnections = maxConnections;
            log.info("SSE 最大连接数限制已设置为：{}", maxConnections);
        }
    }

    /**
     * 检查是否允许新连接
     * 
     * @return true 表示允许新连接，false 表示已达上限
     */
    public boolean canAcceptNewConnection() {
        return leases.count() < getMaxConnections();
    }

    /**
     * 获取当前活跃连接数
     * 
     * @return 当前活跃连接数
     */
    public int getActiveConnectionCount() {
        return leases.count();
    }

    /**
     * 为 emitter 绑定上下文。当连接中断时，关联上下文的已取消标记将被设置为 true。
     *
     * @param emitter SSE 发射器
     * @param context 关联的上下文对象（需包含 setCancelled(boolean) 方法，如 SessionContextInternalDTO）
     */
    public void bindContext(SseEmitter emitter, SessionContextInternalDTO context) {
        if (emitter != null && context != null) {
            synchronized (conversationOwners) {
                if (isCancelled(emitter)) throw new BusinessException("请求已取消");
                SseEmitter owner = conversationOwners.putIfAbsent(conversationKey(context), emitter);
                if (owner != null && owner != emitter) throw new BusinessException("该会话正在生成，请等待或停止当前请求");
                context.setCancelled(false);
                emitterContexts.put(emitter, context);
            }
            log.debug("SSE 绑定上下文成功");
        }
    }

    /**
     * 安全移除 emitter，并递减活跃连接数（幂等操作）
     * 
     * @param emitter SSE 发射器实例
     */
    private void safeRemoveEmitter(SseEmitter emitter) {
        if (emitter == null)
            return;

        if (emitter instanceof RequestEmitter request) request.cancelRequest();
        activeEmitters.remove(emitter);
        SessionContextInternalDTO context;
        synchronized (conversationOwners) {
            context = emitterContexts.remove(emitter);
            if (context != null) {
                context.setCancelled(true);
                conversationOwners.remove(conversationKey(context), emitter);
            }
        }
        synchronized (emitterLeases) {
            String lease = emitterLeases.remove(emitter);
            Long userId = emitterUsers.remove(emitter);
            if (lease != null) {
                try { leases.release(lease, userId); }
                catch (RuntimeException error) { log.warn("释放 SSE 租约失败，将由 TTL 回收", error); }
            }
        }

        if (context != null) {
            log.info("SSE 连接已移除，关联上下文已标记为取消");
        }

    }

    private void renewLeases() {
        for (SseEmitter emitter : emitterLeases.keySet()) {
            boolean renewed;
            synchronized (emitterLeases) {
                String lease = emitterLeases.get(emitter);
                if (lease == null) continue;
                try { renewed = leases.renew(lease, emitterUsers.get(emitter)); }
                catch (RuntimeException error) {
                    log.warn("续期 SSE 租约失败，关闭连接", error);
                    renewed = false;
                }
            }
            if (!renewed) safeCompleteEmitter(emitter);
        }
    }

    /**
     * 清理超时连接（超过最大存活时间）
     * 最大存活时间 = sseEmitterTimeout * 2，若配置无效则默认 10 分钟
     */
    private void cleanupStaleConnections() {
        long now = System.currentTimeMillis();
        long maxAge = aiStreamingProperties.getSseEmitterTimeout() * 2;

        // 如果配置无效，使用默认值 10 分钟
        if (maxAge <= 0) {
            maxAge = 10 * 60 * 1000L; // 10 分钟
        }

        for (Map.Entry<SseEmitter, Long> entry : activeEmitters.entrySet()) {
            SseEmitter emitter = entry.getKey();
            long createTime = entry.getValue();

            if (now - createTime > maxAge) {
                log.warn("检测到超时连接，强制关闭 - 创建时间：{}, 当前：{}", createTime, now);
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("强制关闭连接时发生异常（可能已关闭）", e);
                } finally {
                    // 无论 complete 是否成功，都尝试移除并递减计数
                    safeRemoveEmitter(emitter);
                }
            }
        }
    }

    /**
     * 记录当前连接状态
     */
    private void logConnectionStatus() {
        log.info("SSE 连接状态 - 当前活跃连接数：{}/{}, 使用率：{}%",
                getActiveConnectionCount(),
                getMaxConnections(),
                (getActiveConnectionCount() * 100.0 / getMaxConnections()));
    }

    /**
     * 发送错误消息
     * 
     * 向客户端发送错误事件，并在发送完成后安全关闭 SSE 发射器。
     * 统一处理发送异常，确保资源得到正确释放。
     * 
     * @param emitter      SSE 发射器实例
     * @param errorMessage 错误消息内容
     */

    /**
     * 发送包含错误码的错误消息
     * 
     * 向客户端发送包含错误码和消息的标准错误响应，并在发送完成后安全关闭 SSE 发射器。
     * 统一处理发送异常，确保资源得到正确释放。
     * 具备防御性编程能力，对已完成的 emitter 不再尝试发送。
     * 
     * @param emitter SSE 发射器实例
     * @param code    错误码枚举
     * @param message 错误消息内容
     */
    public void sendError(SseEmitter emitter, ResultCode code, String message) {
        try {
            if (emitter == null) {
                log.warn("sendError: emitter 为 null，跳过错误发送");
                return;
            }
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("code", code.getCode());
            errorData.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(errorData));
        } catch (IllegalStateException e) {
            // emitter 已完成或处于错误状态，这是正常情况（客户端可能已断开）
            log.warn("错误消息发送失败，emitter可能已关闭或处于错误状态: {}", e.getMessage());
        } catch (Exception e) {
            log.error("发送错误消息失败", e);
        } finally {
            safeCompleteEmitter(emitter);
        }
    }

    /**
     * 创建并配置 SseEmitter 实例（带连接数限制检查）
     * 
     * @param timeout       超时时间（毫秒）
     * @param reconnectTime 重连时间（毫秒）
     * @return 配置好的 SseEmitter 实例
     * @throws BusinessException 如果连接初始化失败或连接数已达上限
     */
    public SseEmitter createConfiguredEmitterWithLimit(long timeout, long reconnectTime) {
        // 原子预占连接名额，确保并发下不突破上限
        String lease = leases.acquire(getMaxConnections());

        SseEmitter emitter = new RequestEmitter(timeout);
        emitterLeases.put(emitter, lease);

        log.debug("创建 SseEmitter，超时时间：{}ms, 重连时间：{}ms, 租约：{}",
                timeout, reconnectTime, lease);

        try {
            // 发送初始连接消息，验证客户端连接状态
            emitter.send(SseEmitter.event()
                    .reconnectTime(reconnectTime)
                    .data("连接成功"));
            log.debug("SSE 初始连接消息发送成功");

            // ★★★ 关键：连接成功后加入映射表 ★★★
            activeEmitters.put(emitter, System.currentTimeMillis());

        } catch (IOException | RuntimeException e) {
            log.error("SSE 连接初始化失败：客户端可能已断开或网络异常", e);
            // 立即关闭 emitter 并减少计数
            safeRemoveEmitter(emitter);
            emitter.complete();
            throw new BusinessException("SSE 连接初始化失败，请刷新页面重试");
        }

        // 注册超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，等待客户端重连");
            safeRemoveEmitter(emitter); // 先移除，避免泄漏
            emitter.complete();
        });

        // 注册错误回调
        emitter.onError(throwable -> {
            log.error("SSE 连接错误", throwable);
            safeRemoveEmitter(emitter);
            emitter.complete();
        });

        // 设置完成回调以减少计数
        emitter.onCompletion(() -> {
            safeRemoveEmitter(emitter); // 安全移除
        });

        return emitter;
    }

    /**
     * 安全关闭 SSE 发射器
     * 
     * 安全地关闭 SSE 发射器，处理各种可能的异常情况。
     * 避免重复关闭导致的 IllegalStateException 异常。
     * 
     * @param emitter SSE 发射器实例
     */
    public void safeCompleteEmitter(SseEmitter emitter) {
        if (emitter == null)
            return;
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("Emitter already completed");
        } catch (Exception e) {
            log.warn("关闭 emitter 时发生异常", e);
        } finally {
            // 避免依赖回调触发，确保连接计数和映射一定会被清理
            safeRemoveEmitter(emitter);
        }
    }

    /**
     * Spring 容器销毁时关闭监控线程池
     */
    @PreDestroy
    public void destroy() {
        for (SseEmitter emitter : emitterLeases.keySet()) safeCompleteEmitter(emitter);
        if (connectionMonitor != null && !connectionMonitor.isShutdown()) {
            log.info("正在关闭 SSE 连接监控任务...");
            connectionMonitor.shutdown();
            try {
                if (!connectionMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("监控任务未在指定时间内关闭，强制终止");
                    connectionMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭监控任务中断", e);
                connectionMonitor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}