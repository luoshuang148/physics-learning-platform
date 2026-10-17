package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhixuewuli.common.client.base.AbstractStreamingClient;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.*;
import com.zhixuewuli.common.config.properties.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import okhttp3.OkHttpClient;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class UpstreamCancellationRegressionTest {
    @ParameterizedTest
    @ValueSource(strings = {"zhipu", "aliyun"})
    void cancellationUnblocksAStalledUpstreamRead(String provider) throws Exception {
        var contentRead = new CountDownLatch(1);
        var releaseServer = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newSingleThreadExecutor();
        var http = new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();
        server.createContext("/chat/completions", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                releaseServer.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var config = new AIBusinessProperties();
            var url = "http://127.0.0.1:" + server.getAddress().getPort();
            config.setZhipuApiKey("test"); config.setZhipuBaseUrl(url);
            config.setAliyunApiKey("test"); config.setAliyunBaseUrl(url);
            AbstractStreamingClient client = provider.equals("zhipu") ? new ZhipuAIClient() : new AliyunAIClient();
            ReflectionTestUtils.setField(client, "aiBusinessProperties", config);
            ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.setField(client, "aiStreamingProperties", new AIStreamingProperties());
            ReflectionTestUtils.setField(client, "httpClient", http);
            var cancelled = new AtomicBoolean();
            var cancelHook = new AtomicReference<Runnable>();
            AIStreamCallback callback = new AIStreamCallback() {
                public void onContent(String value, boolean last) { contentRead.countDown(); }
                public void onComplete(int prompt, int completion, int total) { }
                public void onError(Exception error) { }
                public boolean isCancelled() { return cancelled.get(); }
                public void onCancel(Runnable action) { cancelHook.set(action); }
            };
            var future = workers.submit(() -> {
                if (client instanceof ZhipuAIClient zhipu) zhipu.streamChat("physics", "test-model", callback);
                else ((AliyunAIClient) client).streamGenerate("physics", "test-model", callback);
            });
            assertThat(contentRead.await(5, TimeUnit.SECONDS)).isTrue();
            cancelled.set(true);
            if (cancelHook.get() != null) cancelHook.get().run();
            assertThatCode(() -> future.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException();
        } finally {
            releaseServer.countDown(); http.dispatcher().cancelAll(); server.stop(0); workers.shutdownNow();
            http.connectionPool().evictAll(); http.dispatcher().executorService().shutdownNow();
        }
    }
}
