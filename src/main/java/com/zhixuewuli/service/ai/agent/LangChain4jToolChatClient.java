package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.ChatTurn;
import com.zhixuewuli.dto.agent.ToolCall;
import com.zhixuewuli.dto.agent.ToolCallCancellation;
import com.zhixuewuli.dto.agent.ToolChatResponse;
import com.zhixuewuli.dto.agent.ToolDefinition;
import com.zhixuewuli.dto.agent.ToolModelConfig;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class LangChain4jToolChatClient implements ToolChatGateway {
    private final ObjectMapper json;

    @Override
    public ToolChatResponse complete(List<ChatTurn> turns, List<ToolDefinition> definitions,
                                     ToolModelConfig modelConfig, ToolCallCancellation cancellation) {
        if (turns == null || turns.isEmpty()) throw new IllegalArgumentException("模型对话不能为空");
        if (modelConfig == null) throw new IllegalArgumentException("工具模型配置不能为空");
        cancellation = cancellation == null ? ToolCallCancellation.none() : cancellation;
        cancellation.throwIfCancelled();

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(modelConfig.baseUrl())
                .apiKey(modelConfig.apiKey())
                .modelName(modelConfig.modelName())
                .timeout(modelConfig.timeout())
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false)
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(toMessages(turns))
                .toolSpecifications(toSpecifications(definitions))
                .build();

        CompletableFuture<ChatResponse> pending = model.chatAsync(request);
        cancellation.onCancel(() -> pending.cancel(true));
        try {
            ChatResponse response = pending.get(modelConfig.timeout().toMillis(), TimeUnit.MILLISECONDS);
            cancellation.throwIfCancelled();
            return toResponse(response);
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("等待工具模型响应时线程被中断");
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw new IllegalStateException("工具模型请求超过总截止时间", timeout);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("工具模型请求失败", cause);
        }
    }

    private List<ChatMessage> toMessages(List<ChatTurn> turns) {
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatTurn turn : turns) {
            if (turn == null || turn.role() == null) throw new IllegalArgumentException("对话角色不能为空");
            switch (turn.role()) {
                case SYSTEM -> messages.add(SystemMessage.from(value(turn.content())));
                case USER -> messages.add(UserMessage.from(value(turn.content())));
                case ASSISTANT -> {
                    List<ToolExecutionRequest> calls = turn.toolCalls().stream().map(call -> ToolExecutionRequest.builder()
                            .id(call.callId()).name(call.name()).arguments(call.argumentsJson()).build()).toList();
                    messages.add(AiMessage.from(turn.content(), calls));
                }
                case TOOL -> messages.add(ToolExecutionResultMessage.builder().id(turn.toolCallId())
                        .toolName(turn.toolName()).text(value(turn.content())).build());
            }
        }
        return messages;
    }

    private List<ToolSpecification> toSpecifications(List<ToolDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.stream().map(definition -> {
            if (definition == null || definition.name() == null || definition.name().isBlank())
                throw new IllegalArgumentException("工具名称不能为空");
            JsonObjectSchema schema = objectSchema(definition.parameters());
            return ToolSpecification.builder().name(definition.name())
                    .description(value(definition.description())).parameters(schema).build();
        }).toList();
    }

    private JsonObjectSchema objectSchema(JsonNode schema) {
        if (schema == null || !schema.isObject() || !"object".equals(schema.path("type").asText()))
            throw new IllegalArgumentException("工具参数必须使用 JSON object schema");
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) properties.fields().forEachRemaining(entry ->
                builder.addProperty(entry.getKey(), schemaElement(entry.getValue())));
        JsonNode required = schema.path("required");
        if (required.isArray()) {
            List<String> names = new ArrayList<>();
            required.forEach(name -> names.add(name.asText()));
            builder.required(names);
        }
        if (schema.has("additionalProperties")) builder.additionalProperties(schema.path("additionalProperties").asBoolean());
        if (schema.has("description")) builder.description(schema.path("description").asText());
        return builder.build();
    }

    private JsonSchemaElement schemaElement(JsonNode schema) {
        String type = schema.path("type").asText("string");
        String description = schema.path("description").asText(null);
        return switch (type) {
            case "integer" -> dev.langchain4j.model.chat.request.json.JsonIntegerSchema.builder().description(description).build();
            case "number" -> dev.langchain4j.model.chat.request.json.JsonNumberSchema.builder().description(description).build();
            case "boolean" -> dev.langchain4j.model.chat.request.json.JsonBooleanSchema.builder().description(description).build();
            case "array" -> JsonArraySchema.builder().items(schemaElement(schema.path("items"))).description(description).build();
            case "object" -> objectSchema(schema);
            default -> dev.langchain4j.model.chat.request.json.JsonStringSchema.builder().description(description).build();
        };
    }

    private ToolChatResponse toResponse(ChatResponse response) {
        if (response == null || response.aiMessage() == null) throw new IllegalStateException("工具模型返回空响应");
        AiMessage assistant = response.aiMessage();
        List<ToolCall> calls = assistant.toolExecutionRequests() == null ? List.of() : assistant.toolExecutionRequests().stream()
                .map(call -> {
                    try {
                        JsonNode arguments = json.readTree(call.arguments());
                        if (arguments == null || !arguments.isObject()) throw new IllegalStateException("工具参数必须是 JSON 对象");
                    } catch (Exception invalid) {
                        throw new IllegalStateException("工具参数不是有效 JSON", invalid);
                    }
                    return new ToolCall(call.id(), call.name(), call.arguments());
                }).toList();
        var usage = response.tokenUsage();
        var source = usage == null ? ToolChatResponse.UsageSource.UNKNOWN : ToolChatResponse.UsageSource.REPORTED;
        return new ToolChatResponse(assistant.text(), calls, response.finishReason() == null ? null : response.finishReason().name(),
                usage == null ? null : usage.inputTokenCount(), usage == null ? null : usage.outputTokenCount(), source);
    }

    private String value(String value) { return value == null ? "" : value; }
}
