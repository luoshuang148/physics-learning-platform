package com.zhixuewuli.service.ai.assistant;

import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AssistantModelGatewayTest {
    @Test void registeringCancellationDoesNotCancelAnActiveRequest() {
        var client = mock(ZhipuAIClient.class);
        var cancelled = new AtomicBoolean();
        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onCancel(() -> cancelled.set(true));
            if (!cancelled.get()) callback.onContent("动量守恒", true);
            else callback.onComplete(0, 0, 0);
            return null;
        }).when(client).streamChat(anyString(), any(), any(AIStreamCallback.class));
        var gateway = new AssistantModelGateway(client, new AIBusinessProperties(), java.util.concurrent.ForkJoinPool.commonPool());
        assertEquals("动量守恒", gateway.complete("物理教师", "解释动量守恒", () -> false));
        assertFalse(cancelled.get());
    }

    @Test void cancelsABlockedUpstreamCallBeforeItReturns() throws Exception {
        var client = mock(ZhipuAIClient.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var upstreamCancelled = new java.util.concurrent.CountDownLatch(1);
        var cancelled = new AtomicBoolean();
        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onCancel(upstreamCancelled::countDown);
            entered.countDown();
            if (!upstreamCancelled.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("cancel did not reach upstream");
            callback.onComplete(0, 0, 0);
            return null;
        }).when(client).streamChat(anyString(), any(), any(AIStreamCallback.class));
        var gateway = new AssistantModelGateway(client, new AIBusinessProperties(), java.util.concurrent.ForkJoinPool.commonPool());
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var output = executor.submit(() -> gateway.complete("物理教师", "解释动量守恒", cancelled::get));
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            cancelled.set(true);
            assertTrue(upstreamCancelled.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("", output.get(1, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void providerFailureCannotBecomeAnEmptySuccessfulAnswer() {
        var client = mock(ZhipuAIClient.class);
        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onError(new IllegalStateException("provider unavailable"));
            return null;
        }).when(client).streamChat(anyString(), any(), any(AIStreamCallback.class));
        var gateway = new AssistantModelGateway(client, new AIBusinessProperties(), java.util.concurrent.ForkJoinPool.commonPool());
        assertThrows(IllegalStateException.class, () -> gateway.complete("物理教师", "解释动量守恒", () -> false));
    }
}
