package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhixuewuli.common.client.base.AbstractStreamingClient;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.AliyunAIClient;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AIWireContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AIBusinessProperties properties = new AIBusinessProperties();
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final List<Exception> errors = new ArrayList<>();
    private final List<String> callbacks = new ArrayList<>();
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
    private HttpServer server;
    private JsonNode sentRequest;
    private String response;
    private final AtomicInteger calls = new AtomicInteger();
    private int initialStatus = 200;
    private boolean failEveryAttempt;
    private boolean delayFirstHeaders;
    private boolean stallAfterFirstContent;

    @BeforeEach
    void startUpstream() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            sentRequest = mapper.readTree(exchange.getRequestBody());
            int attempt = calls.incrementAndGet();
            if (attempt == 1 && delayFirstHeaders) {
                try { Thread.sleep(250); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(attempt == 1 || failEveryAttempt ? initialStatus : 200, bytes.length);
            if (stallAfterFirstContent) {
                int boundary = response.indexOf("\n\n") + 2;
                byte[] first = response.substring(0, boundary).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseBody().write(first);
                exchange.getResponseBody().flush();
                try { Thread.sleep(250); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                exchange.getResponseBody().write(bytes, first.length, bytes.length - first.length);
            } else exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.setAliyunBaseUrl(url);
        properties.setZhipuBaseUrl(url);
        properties.setAliyunApiKey("report-fixture-key");
        properties.setZhipuApiKey("tutoring-fixture-key");
    }

    @AfterEach
    void stopUpstream() {
        server.stop(0);
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdownNow();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void reportParametersAreSentAtRoot(boolean multimodal, boolean useGlobalDefaults) throws Exception {
        properties.setTemperature(0.4);
        properties.setMaxTokens(77);
        properties.setAliyunTemperature(useGlobalDefaults ? null : 0.25);
        properties.setAliyunMaxTokens(useGlobalDefaults ? null : 96);
        response = frame(Map.of("content", "answer")) + end();

        invoke(new AliyunAIClient(), multimodal);

        assertThat(sentRequest.path("temperature").asDouble()).isEqualTo(useGlobalDefaults ? 0.4 : 0.25);
        assertThat(sentRequest.path("max_tokens").asInt()).isEqualTo(useGlobalDefaults ? 77 : 96);
        assertThat(sentRequest.has("extra_body")).isFalse();
        assertThat(content.toString()).isEqualTo("answer");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void glmReportExplicitlyDisablesThinking(boolean multimodal) throws Exception {
        response = frame(Map.of("content", "report")) + end();
        invoke(new AliyunAIClient(), multimodal);

        assertThat(sentRequest.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportRetriesOneRateLimitBeforeReturningContent(boolean multimodal) throws Exception {
        initialStatus = 429;
        response = frame(Map.of("content", "report")) + end();
        invoke(new AliyunAIClient(), multimodal);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(content.toString()).isEqualTo("report");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportStopsAfterTwoRateLimitedAttempts(boolean multimodal) throws Exception {
        initialStatus = 429;
        failEveryAttempt = true;
        response = "{}";
        invoke(new AliyunAIClient(), multimodal);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(content.toString()).isEmpty();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst()).isInstanceOf(com.zhixuewuli.common.exception.ai.AIUpstreamException.class)
                .extracting("status").isEqualTo(429);
        assertThat(callbacks).doesNotContain("complete");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportDoesNotRetryAuthenticationFailures(boolean multimodal) throws Exception {
        initialStatus = 401;
        response = "{}";
        invoke(new AliyunAIClient(), multimodal);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst()).isInstanceOf(com.zhixuewuli.common.exception.ai.AIUpstreamException.class)
                .extracting("status").isEqualTo(401);
        assertThat(callbacks).doesNotContain("complete");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportRetriesATimeoutBeforeResponseHeaders(boolean multimodal) throws Exception {
        delayFirstHeaders = true;
        response = frame(Map.of("content", "report")) + end();
        var client = new AliyunAIClient();
        ReflectionTestUtils.setField(client, "httpClient", http.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build());
        invoke(client, multimodal);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(content.toString()).isEqualTo("report");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportNeverReplaysAPartiallyDeliveredResponse(boolean multimodal) throws Exception {
        stallAfterFirstContent = true;
        response = frame(Map.of("content", "partial")) + frame(Map.of("content", "tail")) + end();
        var client = new AliyunAIClient();
        ReflectionTestUtils.setField(client, "httpClient", http.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build());
        invoke(client, multimodal);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(content.toString()).isEqualTo("partial");
        assertThat(errors).hasSize(1);
        assertThat(callbacks).doesNotContain("complete");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void separatesInlineThinkingEvenWhenEveryTagIsSplitAcrossChunks(boolean multimodal) throws Exception {
        String raw = "<think>private-reasoning</think><answer>final answer</answer>";
        var stream = new StringBuilder();
        for (char character : raw.toCharArray()) stream.append(frame(Map.of("content", String.valueOf(character))));
        response = stream + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEqualTo("final answer");
        assertThat(reasoning.toString()).isEqualTo("private-reasoning");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void separatesInlineThinkingInOneChunkAndPreservesSurroundingText(boolean multimodal) throws Exception {
        response = frame(Map.of("content", "before <think>private-reasoning</think> final answer")) + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEqualTo("before  final answer");
        assertThat(reasoning.toString()).isEqualTo("private-reasoning");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retainsNativeReasoningAndIgnoresNullDeltas(boolean multimodal) throws Exception {
        response = "data: {\"choices\":[{\"delta\":{\"content\":null,\"reasoning_content\":null}}]}\n\n"
                + frame(Map.of("reasoning_content", "native-reasoning"))
                + frame(Map.of("content", "final answer")) + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEqualTo("final answer");
        assertThat(reasoning.toString()).isEqualTo("native-reasoning");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unfinishedThinkingStaysOutOfTheAnswer(boolean multimodal) throws Exception {
        response = frame(Map.of("content", "<thi")) + frame(Map.of("content", "nk>private-reasoning")) + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEmpty();
        assertThat(reasoning.toString()).isEqualTo("private-reasoning");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anAnswerTagInsideUnclosedThinkingDoesNotExposeReasoning(boolean multimodal) throws Exception {
        response = frame(Map.of("content", "<think>private <answer>literal</answer> reasoning")) + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEmpty();
        assertThat(reasoning.toString()).isEqualTo("private <answer>literal</answer> reasoning");
        assertCompleted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesPlainTextWhitespaceAndAnIncompleteLiteralTagAtEnd(boolean multimodal) throws Exception {
        response = frame(Map.of("content", "\nAnswer < threshold\n<thi")) + end();

        invoke(new ZhipuAIClient(), multimodal);

        assertThat(content.toString()).isEqualTo("\nAnswer < threshold\n<thi");
        assertThat(reasoning.toString()).isEmpty();
        assertCompleted();
    }

    private String frame(Map<String, Object> delta) throws Exception {
        return "data: " + mapper.writeValueAsString(Map.of("choices", List.of(Map.of("delta", delta)))) + "\n\n";
    }

    private String end() {
        return "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n";
    }

    private void invoke(AbstractStreamingClient client, boolean multimodal) {
        ReflectionTestUtils.setField(client, "aiBusinessProperties", properties);
        ReflectionTestUtils.setField(client, "objectMapper", mapper);
        ReflectionTestUtils.setField(client, "aiStreamingProperties", new AIStreamingProperties());
        if (ReflectionTestUtils.getField(client, "httpClient") == null) ReflectionTestUtils.setField(client, "httpClient", http);
        var callback = new AIStreamCallback() {
            public void onContent(String value, boolean last) { content.append(value); callbacks.add("content"); }
            public void onReasoningContent(String value) { reasoning.append(value); callbacks.add("reasoning"); }
            public void onComplete(int prompt, int completion, int total) { callbacks.add("complete"); }
            public void onError(Exception error) { errors.add(error); }
        };
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", "physics"))));
        if (client instanceof AliyunAIClient aliyun) {
            if (multimodal) aliyun.streamGenerateWithMessages(messages, "glm-4.7-flash", callback);
            else aliyun.streamGenerate("physics", "glm-4.7-flash", callback);
        } else {
            var zhipu = (ZhipuAIClient) client;
            if (multimodal) zhipu.streamChatWithMessages(messages, "glm-4.1v-thinking-flash", callback);
            else zhipu.streamChat("physics", "glm-4.1v-thinking-flash", callback);
        }
    }

    private void assertCompleted() {
        assertThat(errors).isEmpty();
        assertThat(callbacks).endsWith("complete");
        assertThat(callbacks.stream().filter("complete"::equals)).hasSize(1);
    }
}
