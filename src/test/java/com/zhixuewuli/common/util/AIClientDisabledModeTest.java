package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhixuewuli.common.client.base.AbstractStreamingClient;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.*;
import com.zhixuewuli.common.config.properties.*;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class AIClientDisabledModeTest {
    @ParameterizedTest
    @ValueSource(strings = {"zhipu", "aliyun"})
    void disabledAiMakesNoUpstreamRequest(String vendor) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        AbstractStreamingClient client = vendor.equals("zhipu") ? new ZhipuAIClient() : new AliyunAIClient();
        try {
            var config = new AIBusinessProperties(); config.setEnabled(false);
            config.setZhipuApiKey("test"); config.setAliyunApiKey("test");
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            config.setZhipuBaseUrl(url); config.setAliyunBaseUrl(url);
            ReflectionTestUtils.setField(client, "aiBusinessProperties", config);
            ReflectionTestUtils.setField(client, "aiStreamingProperties", new AIStreamingProperties());
            ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.invokeMethod(client, "initHttpClient");
            AIStreamCallback callback = new AIStreamCallback() {
                public void onContent(String content, boolean last) { }
                public void onComplete(int a, int b, int c) { }
                public void onError(Exception error) { }
            };
            assertThatThrownBy(() -> {
                if (client instanceof ZhipuAIClient zhipu) zhipu.streamChat("question", "test", callback);
                else ((AliyunAIClient) client).streamGenerate("question", "test", callback);
            }).isInstanceOf(AIServiceUnavailableException.class);
            assertThat(calls.get()).isZero();
        } finally {
            server.stop(0);
            var http = (okhttp3.OkHttpClient) ReflectionTestUtils.getField(client, "httpClient");
            if (http != null) { http.connectionPool().evictAll(); http.dispatcher().executorService().shutdownNow(); }
        }
    }
}
