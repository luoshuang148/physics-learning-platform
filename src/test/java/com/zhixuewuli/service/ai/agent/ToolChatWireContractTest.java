package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.ChatTurn;
import com.zhixuewuli.dto.agent.ToolCall;
import com.zhixuewuli.dto.agent.ToolCallCancellation;
import com.zhixuewuli.dto.agent.ToolChatResponse;
import com.zhixuewuli.dto.agent.ToolDefinition;
import com.zhixuewuli.dto.agent.ToolModelConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ToolChatWireContractTest {
    private final WireMockServer server = new WireMockServer(0);
    private LangChain4jToolChatClient client;

    @BeforeEach void start() {
        server.start();
        WireMock.configureFor("localhost", server.port());
        client = new LangChain4jToolChatClient(new ObjectMapper());
    }

    @AfterEach void stop() { server.stop(); }

    @Test void sends_typed_messages_and_schema_then_returns_multiple_tool_calls() throws Exception {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"mock-tool","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call-a","type":"function","function":{"name":"get_summary","arguments":"{\\"eventId\\":7}"}},{"id":"call-b","type":"function","function":{"name":"get_wrong_questions","arguments":"{\\"eventId\\":7}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":11,"completion_tokens":4,"total_tokens":15}}
                """)));
        var turns = List.of(new ChatTurn(ChatTurn.Role.SYSTEM, "只用工具事实回答", null, null, List.of()),
                new ChatTurn(ChatTurn.Role.USER, "总结我的作业", null, null, List.of()));
        var tool = new ToolDefinition("get_summary", "读取本人汇总", new ObjectMapper().readTree("""
                {"type":"object","properties":{"eventId":{"type":"integer"}},"required":["eventId"],"additionalProperties":false}
                """));

        var response = client.complete(turns, List.of(tool),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.none());

        assertThat(response.toolCalls()).extracting(ToolCall::callId).containsExactly("call-a", "call-b");
        assertThat(response.toolCalls()).extracting(ToolCall::name).containsExactly("get_summary", "get_wrong_questions");
        assertThat(response.usageSource()).isEqualTo(ToolChatResponse.UsageSource.REPORTED);
        assertThat(response.inputTokens()).isEqualTo(11);
        var request = server.getAllServeEvents().getFirst().getRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        var body = new ObjectMapper().readTree(request.getBodyAsString());
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(body.at("/tools/0/function/name").asText()).isEqualTo("get_summary");
        assertThat(body.at("/tools/0/function/parameters/required/0").asText()).isEqualTo("eventId");
    }

    @Test void ordinary_assistant_text_is_not_parsed_as_a_tool_call_and_missing_usage_is_unknown() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"chatcmpl-2","object":"chat.completion","created":1,"model":"mock-tool","choices":[{"index":0,"message":{"role":"assistant","content":"普通回答中的 get_summary 不是真实工具调用"},"finish_reason":"stop"}]}
                """)));

        var response = client.complete(List.of(new ChatTurn(ChatTurn.Role.USER, "问题", null, null, List.of())), List.of(),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.none());

        assertThat(response.content()).contains("普通回答");
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.usageSource()).isEqualTo(ToolChatResponse.UsageSource.UNKNOWN);
        assertThat(response.inputTokens()).isNull();
    }

    @Test void upstream_rate_limit_is_not_converted_to_a_successful_chat() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(429).withBody("rate limited")));

        assertThatThrownBy(() -> client.complete(List.of(new ChatTurn(ChatTurn.Role.USER, "问题", null, null, List.of())), List.of(),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.none())).isInstanceOf(RuntimeException.class);
    }

    @Test void assistant_tool_request_and_tool_result_keep_their_protocol_roles() throws Exception {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"chatcmpl-3","object":"chat.completion","created":1,"model":"mock-tool","choices":[{"index":0,"message":{"role":"assistant","content":"收到工具结果"},"finish_reason":"stop"}]}
                """)));
        var turns = List.of(
                new ChatTurn(ChatTurn.Role.ASSISTANT, null, null, null,
                        List.of(new ToolCall("call-1", "get_summary", "{\"eventId\":7}"))),
                new ChatTurn(ChatTurn.Role.TOOL, "完成数为3", "call-1", "get_summary", List.of()));

        client.complete(turns, List.of(), new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.none());

        var messages = new ObjectMapper().readTree(server.getAllServeEvents().getFirst().getRequest().getBodyAsString()).path("messages");
        assertThat(messages.get(0).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(0).path("tool_calls").get(0).path("id").asText()).isEqualTo("call-1");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("tool");
        assertThat(messages.get(1).path("tool_call_id").asText()).isEqualTo("call-1");
    }

    @Test void malformed_tool_arguments_are_rejected_instead_of_passed_to_business_tools() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"chatcmpl-4","object":"chat.completion","created":1,"model":"mock-tool","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call-bad","type":"function","function":{"name":"get_summary","arguments":"not-json"}}]},"finish_reason":"tool_calls"}]}
                """)));

        assertThatThrownBy(() -> client.complete(List.of(new ChatTurn(ChatTurn.Role.USER, "问题", null, null, List.of())), List.of(),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.none())).isInstanceOf(IllegalStateException.class).hasMessageContaining("JSON");
    }

    @Test void pre_cancelled_call_does_not_reach_the_model() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        assertThatThrownBy(() -> client.complete(List.of(new ChatTurn(ChatTurn.Role.USER, "问题", null, null, List.of())), List.of(),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(3)),
                ToolCallCancellation.from(cancelled::get, ignored -> {}))).isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test void cancelling_an_in_flight_call_cancels_the_sdk_future() throws Exception {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(200)
                .withFixedDelay(3000).withBody("{\"id\":\"late\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"late\"},\"finish_reason\":\"stop\"}]}")));
        var registered = new CountDownLatch(1);
        var callback = new AtomicReference<Runnable>();
        var signal = ToolCallCancellation.from(() -> false, action -> { callback.set(action); registered.countDown(); });
        var pending = java.util.concurrent.CompletableFuture.supplyAsync(() -> client.complete(
                List.of(new ChatTurn(ChatTurn.Role.USER, "问题", null, null, List.of())), List.of(),
                new ToolModelConfig(server.baseUrl() + "/v1", "test-key", "mock-tool", Duration.ofSeconds(5)), signal));

        assertThat(registered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        callback.get().run();
        assertThatThrownBy(pending::join).hasCauseInstanceOf(java.util.concurrent.CancellationException.class);
    }
}
