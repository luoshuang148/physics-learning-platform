package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.*;
import com.zhixuewuli.dto.response.PracticeCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningWorkflowService {
    private final ObjectMapper json;
    private final LearningToolRegistry tools;
    private final LearningPlanValidator validator;

    public ValidatedPlanPreview preview(LearningToolContext context, String question, int days, long deadlineMillis,
                                        java.util.function.BooleanSupplier cancelled) {
        if (days < 1 || days > 7) throw new IllegalArgumentException("计划天数须为1到7天");
        if (question == null || question.isBlank() || question.length() > 800) throw new IllegalArgumentException("问题须为1到800字");
        check(deadlineMillis, cancelled);
        List<String> evidence = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        String revision = "no-personal-data";
        if (!isKnowledgeOnly(question)) {
            PersonalLearningSummary summary = read("get_learning_summary", "{\"eventId\":" + context.eventId() + "}", context, deadlineMillis, PersonalLearningSummary.class);
            revision = summary.sourceAnswerRevision();
            List<WrongQuestionFact> wrong = readList("get_wrong_questions", "{\"eventId\":" + context.eventId() + ",\"limit\":10}", context, deadlineMillis, json.getTypeFactory().constructCollectionType(List.class, WrongQuestionFact.class));
            for (WrongQuestionFact fact : wrong) {
                evidence.add(fact.recordId());
                codes.addAll(fact.knowledgePointCodes());
            }
            if (wrong.isEmpty()) evidence.add("PERSONAL_SUMMARY");
        }
        check(deadlineMillis, cancelled);
        String search = serialize(Map.of("eventId", context.eventId(), "query", question, "limit", 5));
        JsonNode retrieval = parse(tools.execute(context, new ToolCall("workflow-knowledge", "search_knowledge", search), deadlineMillis).json());
        for (JsonNode passage : retrieval.path("passages")) evidence.add(passage.path("sourceId").asText());
        List<String> pointCodes = codes.stream().filter(Objects::nonNull).distinct().limit(15).toList();
        List<PracticeCandidate> candidates = List.of();
        if (!pointCodes.isEmpty()) {
            String args = serialize(Map.of("eventId", context.eventId(), "knowledgePointCodes", pointCodes, "limit", 6));
            candidates = readList("recommend_practice", args, context, deadlineMillis,
                    json.getTypeFactory().constructCollectionType(List.class, PracticeCandidate.class));
        }
        check(deadlineMillis, cancelled);
        String plan = codePlan(context.eventId(), revision, days, candidates, evidence);
        return validator.validate(plan, context.eventId(), revision, Set.copyOf(evidence), candidates, days, "WORKFLOW");
    }

    private boolean isKnowledgeOnly(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return text.matches(".*(解释|是什么|定义|原理|why|what is|explain).*" )
                && !text.matches(".*(我的错题|我的作业|复习计划|学习计划|薄弱|错题).*" );
    }

    private String codePlan(Long eventId, String revision, int days, List<PracticeCandidate> candidates, List<String> evidence) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            int day = 1;
            for (PracticeCandidate candidate : candidates) {
                for (String code : candidate.matchedKnowledgePointCodes()) {
                    items.add(Map.of("day", day, "knowledgePointCode", code, "questionIds", List.of(candidate.questionId()), "reasonSourceIds", evidence));
                    day = day % days + 1;
                    break;
                }
            }
            return json.writeValueAsString(Map.of("days", days, "sourceEventId", eventId, "sourceAnswerRevision", revision, "items", items));
        } catch (Exception error) { throw new IllegalStateException("无法构造复习预览", error); }
    }

    private <T> T read(String tool, String args, LearningToolContext context, long deadline, Class<T> type) {
        try { return json.readValue(tools.execute(context, new ToolCall("workflow-" + tool, tool, args), deadline).json(), type); }
        catch (Exception error) { throw new IllegalStateException("无法解析学习工具结果", error); }
    }

    private <T> T readList(String tool, String args, LearningToolContext context, long deadline, com.fasterxml.jackson.databind.JavaType type) {
        try { return json.readValue(tools.execute(context, new ToolCall("workflow-" + tool, tool, args), deadline).json(), type); }
        catch (Exception error) { throw new IllegalStateException("无法解析学习工具结果", error); }
    }

    private void check(long deadline, java.util.function.BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("学习计划请求已取消");
        if (System.currentTimeMillis() >= deadline) throw new IllegalStateException("复习助手超过总时限");
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("无法编码学习工具参数", error); }
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception error) { throw new IllegalStateException("无法解析学习工具结果", error); }
    }
}
