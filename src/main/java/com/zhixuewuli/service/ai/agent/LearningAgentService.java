package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.config.properties.AgentProperties;
import com.zhixuewuli.common.config.properties.LearningAssistantProperties;
import com.zhixuewuli.dto.agent.*;
import com.zhixuewuli.dto.response.PracticeCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningAgentService {
    private final ToolChatGateway gateway;
    private final LearningToolRegistry tools;
    private final LearningPlanValidator validator;
    private final AgentProperties agentProperties;
    private final LearningAssistantProperties limits;
    private final ObjectMapper json;

    public ValidatedPlanPreview preview(LearningToolContext context, String question, int days,
                                        java.util.function.BooleanSupplier cancelled,
                                        java.util.function.Consumer<Runnable> cancellationRegistration) {
        return preview(context, question, days, cancelled, cancellationRegistration, ignored -> {});
    }

    public ValidatedPlanPreview preview(LearningToolContext context, String question, int days,
                                        java.util.function.BooleanSupplier cancelled,
                                        java.util.function.Consumer<Runnable> cancellationRegistration,
                                        java.util.function.Consumer<LearningAssistantStep> onStep) {
        if (!agentProperties.isToolCallingEnabled()) throw new IllegalStateException("动态 Agent 尚未启用");
        if (days < 1 || days > 7 || question == null || question.isBlank() || question.length() > 800)
            throw new IllegalArgumentException("请求参数无效");
        long deadline = System.currentTimeMillis() + limits.getTotalDeadlineSeconds() * 1000;
        var step = new java.util.concurrent.atomic.AtomicInteger();
        boolean knowledgeOnly = isKnowledgeOnly(question);
        PersonalLearningSummary personal = knowledgeOnly ? null : readSummary(context, onStep, step);
        String revision = personal == null ? "no-personal-data" : personal.sourceAnswerRevision();
        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn(ChatTurn.Role.SYSTEM, systemPrompt(context.eventId(), revision, days), null, null, List.of()));
        turns.add(new ChatTurn(ChatTurn.Role.USER, question, null, null, List.of()));
        List<PracticeCandidate> candidates = new ArrayList<>();
        Set<String> evidence = new LinkedHashSet<>();
        Set<String> seenCalls = new HashSet<>();
        int toolCount = 0;
        ToolCallCancellation cancellation = ToolCallCancellation.from(cancelled, cancellationRegistration);
        for (int round = 0; round < limits.getMaxModelRounds(); round++) {
            check(deadline, cancellation);
            long remaining = Math.min(limits.getModelTimeoutSeconds() * 1000, deadline - System.currentTimeMillis());
            ToolModelConfig model = new ToolModelConfig(agentProperties.getBaseUrl(), agentProperties.getApiKey(),
                    agentProperties.getModel(), Duration.ofMillis(Math.max(1, remaining)));
            ToolChatResponse response = gateway.complete(List.copyOf(turns), tools.definitions(), model, cancellation);
            if (response.toolCalls().isEmpty()) {
                if (response.content() == null || response.content().isBlank()) throw new IllegalStateException("Agent 未返回计划");
                return validator.validate(response.content(), context.eventId(), revision, evidence,
                        candidates, days, "AGENT");
            }
            turns.add(new ChatTurn(ChatTurn.Role.ASSISTANT, response.content(), null, null, response.toolCalls()));
            for (ToolCall call : response.toolCalls()) {
                check(deadline, cancellation);
                if (++toolCount > limits.getMaxToolCalls()) throw new IllegalStateException("Agent 工具调用次数超过上限");
                String signature = call.name() + ":" + call.argumentsJson();
                if (!seenCalls.add(signature)) throw new IllegalStateException("Agent 重复调用相同工具，已停止");
                String resultJson;
                try {
                    long toolDeadline = Math.min(deadline, System.currentTimeMillis() + limits.getToolTimeoutSeconds() * 1000);
                    ToolResult result = tools.execute(context, call, toolDeadline);
                    resultJson = result.json();
                    collectEvidence(json.readTree(resultJson), evidence);
                    if ("recommend_practice".equals(call.name())) {
                        List<PracticeCandidate> found = json.readerForListOf(PracticeCandidate.class).readValue(resultJson);
                        candidates.clear(); candidates.addAll(found);
                    }
                    onStep.accept(new LearningAssistantStep(step.incrementAndGet(), call.name(), "SUCCEEDED", stepSummary(call.name())));
                } catch (Exception error) {
                    onStep.accept(new LearningAssistantStep(step.incrementAndGet(), call.name(), "FAILED", "此步骤暂未完成"));
                    resultJson = serialize(Map.of("error", "tool_failed", "message", "工具调用失败；请尝试其他证据或给出部分建议。"));
                }
                turns.add(new ChatTurn(ChatTurn.Role.TOOL, resultJson, call.callId(), call.name(), List.of()));
            }
        }
        throw new IllegalStateException("Agent 达到模型交互轮数上限，未生成经校验的计划");
    }

    private String systemPrompt(Long eventId, String revision, int days) {
        return "你是物理学习计划助手。只能用工具结果，不得猜测题目ID、证据ID或知识点代码。" +
                "需要时调用 get_learning_summary、get_wrong_questions、search_knowledge、recommend_practice。" +
                "最终只输出JSON：days,sourceEventId,sourceAnswerRevision,items，每个item含day,knowledgePointCode,questionIds,reasonSourceIds。" +
                "days最多" + days + "；sourceEventId固定为" + eventId + "，sourceAnswerRevision固定为" + revision + "。" +
                "信息不足可返回空items。";
    }

    private PersonalLearningSummary readSummary(LearningToolContext context,
                                                java.util.function.Consumer<LearningAssistantStep> onStep,
                                                java.util.concurrent.atomic.AtomicInteger step) {
        try {
            String args = json.writeValueAsString(Map.of("eventId", context.eventId()));
            ToolResult result = tools.execute(context, new ToolCall("agent-summary", "get_learning_summary", args),
                    System.currentTimeMillis() + limits.getToolTimeoutSeconds() * 1000);
            PersonalLearningSummary summary = json.readValue(result.json(), PersonalLearningSummary.class);
            onStep.accept(new LearningAssistantStep(step.incrementAndGet(), "get_learning_summary", "SUCCEEDED", "已读取本人本次作业统计"));
            return summary;
        } catch (Exception error) { throw new IllegalStateException("无法读取本人学习统计", error); }
    }

    private String stepSummary(String toolName) {
        return switch (toolName) {
            case "get_learning_summary" -> "已读取本人本次作业统计";
            case "get_wrong_questions" -> "已读取本人错题和历史快照";
            case "search_knowledge" -> "已检索当前作业可访问的资料";
            case "recommend_practice" -> "已筛选可练习题目";
            default -> "步骤完成";
        };
    }

    private boolean isKnowledgeOnly(String value) {
        String text = value.toLowerCase(Locale.ROOT);
        return text.matches(".*(解释|是什么|定义|原理|why|what is|explain).*")
                && !text.matches(".*(我的错题|我的作业|复习计划|学习计划|薄弱|错题).*");
    }

    private void collectEvidence(JsonNode node, Set<String> evidence) {
        if (node == null) return;
        if (node.isObject()) {
            if (node.hasNonNull("sourceId")) evidence.add(node.path("sourceId").asText());
            if (node.hasNonNull("recordId")) evidence.add(node.path("recordId").asText());
            node.elements().forEachRemaining(child -> collectEvidence(child, evidence));
        } else if (node.isArray()) node.forEach(child -> collectEvidence(child, evidence));
    }

    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { return "{\"error\":\"tool_failed\"}"; }
    }

    private void check(long deadline, ToolCallCancellation cancellation) {
        cancellation.throwIfCancelled();
        if (System.currentTimeMillis() >= deadline) throw new IllegalStateException("复习助手超过总时限");
    }
}
