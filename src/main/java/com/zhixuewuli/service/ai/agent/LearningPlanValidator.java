package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.LearningPlanItemPreview;
import com.zhixuewuli.dto.agent.ValidatedPlanPreview;
import com.zhixuewuli.dto.response.PracticeCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class LearningPlanValidator {
    private final ObjectMapper json;

    public ValidatedPlanPreview validate(String planJson, Long sourceEventId, String answerRevision,
                                         Set<String> allowedSourceIds, List<PracticeCandidate> returnedCandidates,
                                         int maxDays, String executionMode) {
        try {
            JsonNode root = json.readTree(planJson);
            int days = root.path("days").asInt(0);
            if (!root.isObject() || days < 1 || days > Math.min(7, maxDays)) throw invalid("计划天数无效");
            if (root.path("sourceEventId").asLong(-1) != sourceEventId) throw invalid("计划来源任务不匹配");
            if (!Objects.equals(root.path("sourceAnswerRevision").asText(null), answerRevision)) throw invalid("作答数据已变化");
            JsonNode items = root.path("items");
            if (!items.isArray()) throw invalid("计划项目格式无效");
            Map<Long, PracticeCandidate> candidates = new HashMap<>();
            for (PracticeCandidate candidate : returnedCandidates) candidates.put(candidate.questionId(), candidate);
            Set<Long> usedQuestionIds = new HashSet<>();
            List<LearningPlanItemPreview> accepted = new ArrayList<>();
            for (JsonNode item : items) {
                int day = item.path("day").asInt(0);
                String code = item.path("knowledgePointCode").asText("");
                JsonNode ids = item.path("questionIds");
                JsonNode sources = item.path("reasonSourceIds");
                if (day < 1 || day > days || code.isBlank() || !ids.isArray() || !sources.isArray()) throw invalid("计划项目格式无效");
                List<String> reasonIds = new ArrayList<>();
                for (JsonNode source : sources) {
                    String id = source.asText();
                    if (!allowedSourceIds.contains(id)) throw invalid("计划引用了未返回的证据");
                    if (!reasonIds.contains(id)) reasonIds.add(id);
                }
                for (JsonNode idNode : ids) {
                    long id = idNode.asLong(-1);
                    PracticeCandidate candidate = candidates.get(id);
                    if (candidate == null || !candidate.matchedKnowledgePointCodes().contains(code)) throw invalid("计划引用了未推荐的题目或知识点");
                    if (!usedQuestionIds.add(id)) throw invalid("计划包含重复题目");
                    if (accepted.size() >= 6) throw invalid("计划最多包含6道题");
                    accepted.add(new LearningPlanItemPreview(day, code, id, reasonIds));
                }
            }
            return new ValidatedPlanPreview(days, sourceEventId, answerRevision, executionMode, accepted,
                    new ArrayList<>(allowedSourceIds), accepted.size() < Math.min(6, returnedCandidates.size()),
                    candidateRegistrySnapshot(returnedCandidates, allowedSourceIds));
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("计划不是有效 JSON", error); }
    }

    /**
     * 按本次运行 PlanCandidateRegistry 校验（任务5）：只允许引用 registry 中的 candidate/source。
     */
    public ValidatedPlanPreview validateFromRegistry(String planJson, Long sourceEventId, String answerRevision,
                                                     PlanCandidateRegistry registry, int maxDays,
                                                     String executionMode) {
        try {
            JsonNode root = json.readTree(planJson);
            int days = root.path("days").asInt(0);
            if (!root.isObject() || days < 1 || days > Math.min(7, maxDays)) throw invalid("计划天数无效");
            if (root.path("sourceEventId").asLong(-1) != sourceEventId) throw invalid("计划来源任务不匹配");
            if (!Objects.equals(root.path("sourceAnswerRevision").asText(null), answerRevision)) throw invalid("作答数据已变化");
            JsonNode items = root.path("items");
            if (!items.isArray()) throw invalid("计划项目格式无效");
            Set<Long> usedQuestionIds = new HashSet<>();
            List<LearningPlanItemPreview> accepted = new ArrayList<>();
            Set<String> allowedSourceIds = new LinkedHashSet<>();
            for (JsonNode item : items) {
                int day = item.path("day").asInt(0);
                String code = item.path("knowledgePointCode").asText("");
                JsonNode ids = item.path("questionIds");
                JsonNode sources = item.path("reasonSourceIds");
                if (day < 1 || day > days || code.isBlank() || !ids.isArray() || !sources.isArray()) throw invalid("计划项目格式无效");
                List<String> reasonIds = new ArrayList<>();
                for (JsonNode source : sources) {
                    String id = source.asText();
                    if (!registry.containsSource(id)) throw invalid("计划引用了本次运行未返回的证据");
                    if (!reasonIds.contains(id)) reasonIds.add(id);
                    allowedSourceIds.add(id);
                }
                for (JsonNode idNode : ids) {
                    long id = idNode.asLong(-1);
                    if (id <= 0) throw invalid("计划题号无效");
                    boolean known = registry.candidateList().stream()
                            .anyMatch(c -> c.questionId() != null && c.questionId() == id);
                    if (!known) throw invalid("计划引用了本次运行未返回的题目");
                    if (!usedQuestionIds.add(id)) throw invalid("计划包含重复题目");
                    if (accepted.size() >= 6) throw invalid("计划最多包含6道题");
                    accepted.add(new LearningPlanItemPreview(day, code, id, reasonIds));
                }
            }
            Map<String, Object> snapshot = registry.snapshot();
            return new ValidatedPlanPreview(days, sourceEventId, answerRevision, executionMode, accepted,
                    new ArrayList<>(allowedSourceIds), accepted.size() < 6, snapshot);
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("计划不是有效 JSON", error); }
    }

    private Map<String, Object> candidateRegistrySnapshot(List<PracticeCandidate> candidates,
                                                          Set<String> allowedSourceIds) {
        List<Map<String, Object>> rows = candidates.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", c.questionId());
            row.put("content", c.content());
            row.put("difficulty", c.difficulty());
            row.put("matchedKnowledgePointCodes", c.matchedKnowledgePointCodes());
            return row;
        }).toList();
        return Map.of("candidates", rows, "sources", new ArrayList<>(allowedSourceIds));
    }

    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
