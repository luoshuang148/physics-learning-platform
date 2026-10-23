import com.fasterxml.jackson.databind.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** A fixed synthetic two-turn protocol probe, not a business Agent. */
public final class SpikeToolProbe {
    public record Result(String toolName, String arguments, String finalText) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    public static Result run(boolean sdk, URI base, String key, String model) throws Exception {
        String question="调用lookup_fixture工具读取value=2，并根据工具返回结果说明数值。";
        if (sdk) {
            var chat=OpenAiChatModel.builder().baseUrl(base.toString().replaceAll("/+$","")+"/")
                    .apiKey(key).modelName(model).temperature(0d).maxTokens(64).maxRetries(0).timeout(Duration.ofSeconds(20)).build();
            var tool=ToolSpecification.builder().name("lookup_fixture").description("Read a synthetic fixture")
                    .parameters(JsonObjectSchema.builder().addIntegerProperty("value").required("value").build()).build();
            var user=UserMessage.from(question);
            var first=chat.chat(ChatRequest.builder().messages(user).toolSpecifications(tool).build()).aiMessage();
            if(first.toolExecutionRequests().size()!=1)throw new IllegalArgumentException("Expected one tool call");
            var call=first.toolExecutionRequests().getFirst();
            validate(call.name(),call.arguments());
            var second=chat.chat(ChatRequest.builder().messages(user,first,ToolExecutionResultMessage.from(call,"2")).build());
            return new Result(call.name(),call.arguments(),second.aiMessage().text());
        }
        var messages=new ArrayList<Object>();messages.add(Map.of("role","user","content",question));
        var tool=Map.of("type","function","function",Map.of("name","lookup_fixture","description","Read a synthetic fixture",
                "parameters",Map.of("type","object","properties",Map.of("value",Map.of("type","integer")),"required",List.of("value"))));
        var first=post(base,key,Map.of("model",model,"messages",messages,"tools",List.of(tool),"max_tokens",64,"temperature",0));
        var assistant=first.path("choices").path(0).path("message");
        var calls=assistant.path("tool_calls");if(calls.size()!=1)throw new IllegalArgumentException("Expected one tool call");
        var call=calls.get(0);String name=call.path("function").path("name").asText();
        String arguments=call.path("function").path("arguments").asText();validate(name,arguments);
        messages.add(assistant);messages.add(Map.of("role","tool","tool_call_id",call.path("id").asText(),"content","2"));
        var second=post(base,key,Map.of("model",model,"messages",messages,"max_tokens",64,"temperature",0));
        return new Result(name,arguments,second.path("choices").path(0).path("message").path("content").asText());
    }
    private static void validate(String name,String arguments)throws Exception {
        var args=JSON.readTree(arguments);
        if(!name.equals("lookup_fixture") || !args.path("value").isInt() || args.path("value").asInt()!=2)
            throw new IllegalArgumentException("Unexpected synthetic tool call");
    }
    private static JsonNode post(URI base,String key,Object payload)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base.toString().replaceAll("/+$","")+"/chat/completions"))
                .header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new java.io.IOException("Model HTTP "+response.statusCode());
        return JSON.readTree(response.body());
    }
}
