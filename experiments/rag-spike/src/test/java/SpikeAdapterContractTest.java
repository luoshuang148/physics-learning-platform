import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class SpikeAdapterContractTest {
    final ObjectMapper json = new ObjectMapper();
    private void verifyAdapter(boolean sdk) throws Exception {
        var received = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            received.set(json.readTree(exchange.getRequestBody()));
            byte[] body = ("{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixture-model\","
                    + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"回答 [S1]\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":5,\"total_tokens\":25}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var adapter = sdk ? SpikeAdapters.sdk(uri, "fixture-key", "fixture-model")
                    : SpikeAdapters.http(uri, "fixture-key", "fixture-model");
            var reply = adapter.complete(new RagSpike.Prompt("system-fixture", "question and [S1] evidence"), new AtomicBoolean());
            assertEquals("回答 [S1]", reply.text());
            assertEquals(20, reply.inputTokens());
            assertEquals(5, reply.outputTokens());
            assertEquals("fixture-model", received.get().path("model").asText());
            assertEquals("system", received.get().path("messages").get(0).path("role").asText());
            assertEquals("question and [S1] evidence", received.get().path("messages").get(1).path("content").asText());
        } finally { server.stop(0); }
    }
    @Test void httpPreservesMessagesSourcesAndReportedUsage() throws Exception { verifyAdapter(false); }
    @Test void sdkPreservesMessagesSourcesAndReportedUsage() throws Exception { verifyAdapter(true); }
    @Test void httpCancellationReturnsBeforeDelayedUpstreamCompletes() throws Exception { verifyCancellation(false); }
    // Compatibility characterization of the selected upstream library, not a new product behavior.
    @Test void sdkCancellationReturnsBeforeDelayedUpstreamCompletes() throws Exception { verifyCancellation(true); }
    private void verifyCancellation(boolean sdk) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var cancelled = new AtomicBoolean();
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            var adapter = sdk ? SpikeAdapters.sdk(uri, "fixture-key", "fixture-model") : SpikeAdapters.http(uri, "fixture-key", "fixture-model");
            var call = executor.submit(() -> adapter.complete(new RagSpike.Prompt("system", "question"), cancelled));
            assertTrue(entered.await(2, TimeUnit.SECONDS), "Request must reach the delayed upstream");
            cancelled.set(true);
            var failure = assertThrows(ExecutionException.class, () -> call.get(2, TimeUnit.SECONDS));
            assertInstanceOf(CancellationException.class, failure.getCause());
        } finally {
            release.countDown(); server.stop(0); executor.shutdownNow();
        }
    }
}
