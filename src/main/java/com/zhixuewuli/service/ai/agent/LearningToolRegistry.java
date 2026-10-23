package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.*;
import com.zhixuewuli.dto.response.PracticeCandidate;
import com.zhixuewuli.service.knowledge.KnowledgeRetriever;
import com.zhixuewuli.service.knowledge.PracticeRecommendationService;
import com.zhixuewuli.service.knowledge.RetrievalMode;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LearningToolRegistry {
    private final ObjectMapper json;
    private final LearningAssistantDataRepository learningData;
    private final KnowledgeRetriever knowledge;
    private final PracticeRecommendationService recommendations;
    private final TeacherEventService tasks;

    public List<ToolDefinition> definitions() {
        return List.of(
                definition("get_learning_summary", "读取当前学生在本次作业的个人统计", """
                        {"type":"object","properties":{"eventId":{"type":"integer"}},"required":["eventId"],"additionalProperties":false}
                        """),
                definition("get_wrong_questions", "读取当前学生的错题及历史题目快照", """
                        {"type":"object","properties":{"eventId":{"type":"integer"},"limit":{"type":"integer"}},"required":["eventId"],"additionalProperties":false}
                        """),
                definition("search_knowledge", "搜索当前作业班级已授权的知识资料", """
                        {"type":"object","properties":{"eventId":{"type":"integer"},"query":{"type":"string"},"limit":{"type":"integer"}},"required":["eventId","query"],"additionalProperties":false}
                        """),
                definition("recommend_practice", "按人工维护知识点查找可练题目", """
                        {"type":"object","properties":{"eventId":{"type":"integer"},"knowledgePointCodes":{"type":"array","items":{"type":"string"}},"limit":{"type":"integer"}},"required":["eventId","knowledgePointCodes"],"additionalProperties":false}
                        """));
    }

    public ToolResult execute(LearningToolContext context, ToolCall call, long deadlineEpochMillis) {
        if (System.currentTimeMillis() >= deadlineEpochMillis) throw new IllegalStateException("复习助手超过总时限");
        JsonNode arguments = parseArguments(call);
        String tool = call.name();
        Set<String> allowed = switch (tool) {
            case "get_learning_summary" -> Set.of("eventId");
            case "get_wrong_questions" -> Set.of("eventId", "limit");
            case "search_knowledge" -> Set.of("eventId", "query", "limit");
            case "recommend_practice" -> Set.of("eventId", "knowledgePointCodes", "limit");
            default -> throw new IllegalArgumentException("未注册的学习工具");
        };
        arguments.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("工具参数不允许字段: " + name);
        });
        JsonNode eventNode = arguments.path("eventId");
        if (!eventNode.isMissingNode() && eventNode.asLong() != context.eventId())
            throw new IllegalArgumentException("工具不能切换当前作业");

        // Re-check task visibility at each call. The model never supplies viewerId or class scope.
        tasks.getEventQuestions(context.eventId(), context.viewerId());
        return switch (tool) {
            case "get_learning_summary" -> result(tool, learningData.summary(context.viewerId(), context.eventId()));
            case "get_wrong_questions" -> result(tool, learningData.wrongQuestions(context.viewerId(), context.eventId(), arguments.path("limit").asInt(10)));
            case "search_knowledge" -> {
                String query = arguments.path("query").asText("").strip();
                if (query.isEmpty() || query.length() > 500) throw new IllegalArgumentException("资料检索问题须为1到500字");
                var retrieval = knowledge.retrieve(context.viewerId(), context.eventId(), query, RetrievalMode.HYBRID,
                        () -> System.currentTimeMillis() >= deadlineEpochMillis);
                yield result(tool, retrieval);
            }
            case "recommend_practice" -> {
                JsonNode codesNode = arguments.path("knowledgePointCodes");
                if (!codesNode.isArray()) throw new IllegalArgumentException("知识点代码必须为数组");
                var codes = new java.util.ArrayList<String>();
                codesNode.forEach(code -> codes.add(code.asText()));
                List<PracticeCandidate> candidates = recommendations.recommend(context.viewerId(), context.eventId(), codes,
                        arguments.path("limit").asInt(6));
                yield result(tool, candidates);
            }
            default -> throw new IllegalArgumentException("未注册的学习工具");
        };
    }

    private ToolDefinition definition(String name, String description, String schema) {
        try { return new ToolDefinition(name, description, json.readTree(schema)); }
        catch (Exception error) { throw new IllegalStateException("学习工具 schema 无效", error); }
    }

    private JsonNode parseArguments(ToolCall call) {
        if (call == null || call.name() == null || call.callId() == null || call.argumentsJson() == null)
            throw new IllegalArgumentException("工具调用缺少必要字段");
        try {
            JsonNode value = json.readTree(call.argumentsJson());
            if (value == null || !value.isObject()) throw new IllegalArgumentException("工具参数必须是 JSON 对象");
            return value;
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception invalid) { throw new IllegalArgumentException("工具参数不是有效 JSON", invalid); }
    }

    private ToolResult result(String name, Object value) {
        try { return new ToolResult(name, true, json.writeValueAsString(value)); }
        catch (Exception error) { throw new IllegalStateException("工具结果序列化失败", error); }
    }
}
