package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.exception.ai.AITimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
public class AssistantModelGateway {
    public static final long MODEL_TIMEOUT_SECONDS = 25;
    private final ZhipuAIClient client;
    private final AIBusinessProperties properties;
    private final Executor executor;

    public AssistantModelGateway(ZhipuAIClient client, AIBusinessProperties properties,
                                 @Qualifier("aiTaskExecutor") Executor executor) {
        this.client = client; this.properties = properties; this.executor = executor;
    }

    public String complete(String systemAndContext, String userQuestion, BooleanSupplier cancelled) {
        return stream(systemAndContext, userQuestion, cancelled, ignored -> {});
    }

    /** The provider performs blocking I/O; supervise it separately so timeout and cancellation stay effective. */
    public String stream(String systemAndContext, String userQuestion, BooleanSupplier cancelled, Consumer<String> onContent) {
        if (cancelled.getAsBoolean()) return "";
        StringBuffer buffer = new StringBuffer();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<Runnable> cancelAction = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean completed = new AtomicBoolean();
        try {
            executor.execute(() -> {
                try {
                    if (stopped.get() || cancelled.getAsBoolean()) { latch.countDown(); return; }
                    client.streamChat(systemAndContext + "\n\n用户问题：" + userQuestion, properties.getRealTimeQaModel(), new AIStreamCallback() {
                        @Override public void onContent(String content, boolean isLast) {
                            if (!stopped.get() && !cancelled.getAsBoolean() && content != null) {
                                buffer.append(content);
                                try { onContent.accept(content); }
                                catch (Exception error) { failure.set(error); latch.countDown(); }
                            }
                            if (isLast) { completed.set(true); latch.countDown(); }
                        }
                        @Override public void onReasoningContent(String reasoning) {}
                        @Override public void onComplete(int p, int c, int t) { completed.set(true); latch.countDown(); }
                        @Override public void onError(Exception error) { failure.set(error); latch.countDown(); }
                        @Override public void onProgress(int progress) {}
                        @Override public boolean isCancelled() { return stopped.get() || cancelled.getAsBoolean(); }
                        @Override public void onCancel(Runnable action) {
                            cancelAction.set(action);
                            if (stopped.get() || cancelled.getAsBoolean()) action.run();
                        }
                    });
                } catch (Exception error) { failure.set(error); latch.countDown(); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MODEL_TIMEOUT_SECONDS);
            while (!latch.await(100, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) break;
                if (System.nanoTime() >= deadline) throw new AITimeoutException("模型请求超时");
            }
            if (failure.get() != null && !cancelled.getAsBoolean()) throw new IllegalStateException("模型服务暂不可用", failure.get());
            if (!cancelled.getAsBoolean() && buffer.isEmpty()) throw new IllegalStateException("模型未返回回答，请重试");
            return buffer.toString();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AITimeoutException("模型请求被中断");
        } finally {
            stopped.set(true);
            Runnable action = cancelAction.get();
            if (action != null && (!completed.get() || cancelled.getAsBoolean() || failure.get() != null)) action.run();
        }
    }
}
