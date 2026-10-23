package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 助手模型网关：复用现有 ZhipuAIClient，不另造供应商 SDK。
 * 单次请求最多 25 秒（设计 §7）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantModelGateway {

    public static final long MODEL_TIMEOUT_SECONDS = 25;

    private final ZhipuAIClient client;
    private final AIBusinessProperties properties;

    /** 同步完成一次问答生成；取消时返回已生成的部分文本。 */
    public String complete(String systemAndContext, String userQuestion, BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) return "";
        String prompt = systemAndContext + "\n\n用户问题：" + userQuestion;
        String model = properties.getRealTimeQaModel();
        AtomicReference<StringBuilder> buffer = new AtomicReference<>(new StringBuilder());
        CountDownLatch latch = new CountDownLatch(1);
        try {
            client.streamChat(prompt, model, new AIStreamCallback() {
                @Override
                public void onContent(String content, boolean isLast) {
                    if (content != null) buffer.get().append(content);
                    if (isLast) latch.countDown();
                }

                @Override
                public void onReasoningContent(String reasoning) {
                    // 不把内部推理作为产品必需数据
                }

                @Override
                public void onComplete(int p, int c, int t) {
                    latch.countDown();
                }

                @Override
                public void onError(Exception error) {
                    latch.countDown();
                }

                @Override
                public void onProgress(int progress) {
                }

                @Override
                public boolean isCancelled() {
                    boolean isCancelled = cancelled != null && cancelled.getAsBoolean();
                    if (isCancelled) latch.countDown();
                    return isCancelled;
                }

                @Override
                public void onCancel(Runnable action) {
                    if (cancelled != null) {
                        // 立即触发取消
                        action.run();
                    }
                }
            });
            if (!latch.await(MODEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new com.zhixuewuli.common.exception.ai.AITimeoutException("模型请求超时");
            }
        } catch (com.zhixuewuli.common.exception.ai.AITimeoutException timeout) {
            throw timeout;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new com.zhixuewuli.common.exception.ai.AITimeoutException("模型请求被中断");
        } catch (Exception error) {
            log.warn("模型调用失败: {}", error.getMessage());
            throw new IllegalStateException("MODEL_CALL_FAILED", error);
        }
        return buffer.get().toString();
    }
}
