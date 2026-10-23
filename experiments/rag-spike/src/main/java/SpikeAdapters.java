import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Two experiment adapters. They send the same prompt and preserve unknown token usage as null. */
public final class SpikeAdapters {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static RagSpike.ModelAdapter http(URI base, String key, String model) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return (prompt, cancelled) -> {
            var payload = Map.of("model", model, "stream", false, "temperature", 0,
                    "max_tokens", 256, "messages", List.of(
                            Map.of("role", "system", "content", prompt.system()),
                            Map.of("role", "user", "content", prompt.user())));
            var request = HttpRequest.newBuilder(URI.create(base.toString().replaceAll("/+$", "") + "/chat/completions"))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25)).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))).build();
            HttpResponse<String> response = await(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()), cancelled);
            if (response.statusCode() != 200) throw new java.io.IOException("Model HTTP " + response.statusCode());
            JsonNode body = JSON.readTree(response.body());
            String text = body.path("choices").path(0).path("message").path("content").asText();
            return new RagSpike.ModelReply(text, nullableInt(body.path("usage").get("prompt_tokens")),
                    nullableInt(body.path("usage").get("completion_tokens")));
        };
    }
    public static RagSpike.ModelAdapter sdk(URI base, String key, String model) {
        OpenAiChatModel chat = OpenAiChatModel.builder().baseUrl(base.toString().replaceAll("/+$", "") + "/")
                .apiKey(key).modelName(model).temperature(0d).maxTokens(256).maxRetries(0)
                .timeout(Duration.ofSeconds(25)).build();
        return (prompt, cancelled) -> {
            var response = await(chat.chatAsync(List.of(SystemMessage.from(prompt.system()), UserMessage.from(prompt.user()))), cancelled);
            var usage = response.tokenUsage();
            return new RagSpike.ModelReply(response.aiMessage().text(), usage == null ? null : usage.inputTokenCount(),
                    usage == null ? null : usage.outputTokenCount());
        };
    }
    private static Integer nullableInt(JsonNode node) { return node == null || !node.isNumber() ? null : node.asInt(); }
    private static <T> T await(CompletableFuture<T> future, AtomicBoolean cancelled) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(26).toNanos();
        try {
            while (true) {
                if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                    future.cancel(true); throw new CancellationException("Model request cancelled");
                }
                if (System.nanoTime() > deadline) {
                    future.cancel(true); throw new TimeoutException("Model request exceeded deadline");
                }
                try { return future.get(100, TimeUnit.MILLISECONDS); }
                catch (TimeoutException pending) { /* Poll for user cancellation; one total deadline. */ }
            }
        } catch (InterruptedException interrupted) {
            future.cancel(true); Thread.currentThread().interrupt(); throw new CancellationException("Interrupted");
        }
    }
}
