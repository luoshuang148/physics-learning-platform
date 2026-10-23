import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
class SpikeToolProbeTest {
    private void verify(boolean sdk) throws Exception {
        var json = new ObjectMapper();
        var received = new CopyOnWriteArrayList<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions", exchange -> {
            received.add(json.readTree(exchange.getRequestBody()));
            String message = received.size()==1
                    ? "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-fixture\",\"type\":\"function\",\"function\":{\"name\":\"lookup_fixture\",\"arguments\":\"{\\\"value\\\":2}\"}}]}"
                    : "{\"role\":\"assistant\",\"content\":\"工具返回的数值是2\"}";
            String finish=received.size()==1?"tool_calls":"stop";
            byte[] body=("{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"fixture-model\",\"choices\":[{\"index\":0,\"message\":"+message+",\"finish_reason\":\""+finish+"\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.start();
        try {
            var result=SpikeToolProbe.run(sdk,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1"),"fixture-key","fixture-model");
            assertEquals("lookup_fixture",result.toolName());
            assertEquals(2,json.readTree(result.arguments()).path("value").asInt());
            assertTrue(result.finalText().contains("2"));
            assertEquals(2,received.size());
            var returned=received.get(1).path("messages").get(2);
            assertEquals("tool",returned.path("role").asText());
            assertEquals("call-fixture",returned.path("tool_call_id").asText());
            assertEquals("2",returned.path("content").asText());
        } finally {server.stop(0);}
    }
    @Test void directHttpPreservesToolIdAndResult() throws Exception {verify(false);}
    @Test void sdkPreservesToolIdAndResult() throws Exception {verify(true);}
}
