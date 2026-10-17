package com.zhixuewuli.common.util;

import com.zhixuewuli.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE 发射器构建器
 * 
 * 提供统一的 SseEmitter 创建和配置逻辑，确保所有 SSE 连接都具有一致的
 * 错误处理、超时回调和连接测试机制。
 */
@Slf4j
public class SseEmitterBuilder {

    /**
     * 创建配置好的 SseEmitter 实例
     * 
     * @param timeout       超时时间（毫秒）
     * @param reconnectTime 重连时间（毫秒）
     * @return 配置好的 SseEmitter 实例
     * @throws BusinessException 如果连接初始化失败
     */
    public static SseEmitter createEmitter(long timeout, long reconnectTime) {
        log.debug("创建 SseEmitter，超时时间：{}ms, 重连时间：{}ms", timeout, reconnectTime);

        SseEmitter emitter = new SseEmitter(timeout);

        try {
            // 发送初始连接消息，验证客户端连接状态
            emitter.send(SseEmitter.event()
                    .reconnectTime(reconnectTime)
                    .data("连接成功"));
            log.debug("SSE 初始连接消息发送成功");
        } catch (IOException e) {
            log.error("SSE 连接初始化失败：客户端可能已断开或网络异常", e);
            // 立即关闭 emitter，避免后续使用无效连接
            emitter.complete();
            throw new BusinessException("SSE 连接初始化失败，请刷新页面重试");
        }

        // 注册超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，等待客户端重连");
            emitter.complete();
        });

        // 注册错误回调
        emitter.onError(throwable -> {
            log.error("SSE 连接错误", throwable);
            emitter.complete();
        });

        return emitter;
    }
}
