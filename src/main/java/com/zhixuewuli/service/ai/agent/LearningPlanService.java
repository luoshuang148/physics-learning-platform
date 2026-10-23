package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.*;
import com.zhixuewuli.dto.response.PracticeCandidate;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.knowledge.PracticeRecommendationService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LearningPlanService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LearningAssistantDataRepository learningData;
    private final PracticeRecommendationService recommendations;
    private final TeacherEventService tasks;
    private final QuestionRepository questionRepository;

    @Transactional
    public void storeSuccessfulPreview(Long viewerId, String runId, ValidatedPlanPreview preview) {
        if (viewerId == null || runId == null || preview == null || preview.sourceEventId() == null)
            throw new IllegalArgumentException("预览运行信息不完整");
        if (!runId.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("runId 格式无效");
        try {
            jdbc.update("""
                    INSERT INTO learning_plan_previews(run_id,user_id,event_id,source_answer_revision,plan_json,status,expires_at)
                    VALUES(?,?,?,?,?,'SUCCEEDED',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 24 HOUR))
                    """, runId, viewerId, preview.sourceEventId(), preview.sourceAnswerRevision(), json.writeValueAsString(preview));
        } catch (Exception error) { throw new IllegalStateException("无法保存复习预览运行结果", error); }
    }

    @Transactional
    public LearningPlanView saveDraft(Long viewerId, String runId, String requestId) {
        if (viewerId == null || !validUuid(runId) || !validUuid(requestId)) throw new IllegalArgumentException("运行或请求标识无效");
        var existing = findByRequest(viewerId, requestId);
        if (existing != null) return existing;
        Map<String, Object> artifact = jdbc.queryForMap("""
                SELECT event_id,source_answer_revision,plan_json,status
                FROM learning_plan_previews WHERE run_id=? AND user_id=? AND expires_at>UTC_TIMESTAMP(6)
                """, runId, viewerId);
        if (!"SUCCEEDED".equals(artifact.get("status"))) throw new IllegalArgumentException("只能保存成功生成的计划预览");
        long eventId = ((Number) artifact.get("event_id")).longValue();
        String revision = String.valueOf(artifact.get("source_answer_revision"));
        if (!"no-personal-data".equals(revision) && !revision.equals(learningData.summary(viewerId, eventId).sourceAnswerRevision()))
            throw new IllegalStateException("作答记录已变化，请重新生成复习建议");
        tasks.getEventQuestions(eventId, viewerId);
        ValidatedPlanPreview preview = decodePreview(artifact.get("plan_json"));
        if (!Objects.equals(preview.sourceEventId(), eventId) || !Objects.equals(preview.sourceAnswerRevision(), revision))
            throw new IllegalStateException("预览来源数据校验失败");

        // 保存计划校验实际返回的 registry 与当前授权，而非重新检索再取 Top6 判断。
        boolean fromRegistry = preview.candidateRegistry() != null
                && preview.candidateRegistry().get("candidates") instanceof java.util.List<?> list && !list.isEmpty();
        Map<Long, PracticeCandidate> allowedById = new HashMap<>();
        Map<Long, Long> revisionById = new HashMap<>();
        if (fromRegistry) {
            for (var item : preview.items()) {
                boolean known = false;
                Object candidatesObj = preview.candidateRegistry().get("candidates");
                if (candidatesObj instanceof java.util.List<?> candidates) {
                    for (Object raw : candidates) {
                        if (raw instanceof java.util.Map<?, ?> row) {
                            Object qid = row.get("questionId");
                            if (qid instanceof Number number && number.longValue() == item.questionId()) {
                                known = true;
                                if (row.get("revision") instanceof Number rev) {
                                    revisionById.put(item.questionId(), rev.longValue());
                                }
                                break;
                            }
                        }
                    }
                }
                if (!known) throw new IllegalStateException("题目可练范围已变化，请重新生成计划");
                // 回源当前授权
                Question question = questionRepository.findById(item.questionId())
                        .orElseThrow(() -> new IllegalStateException("题目已不可用，请重新生成计划"));
                if (!question.isAiPracticeEnabled())
                    throw new IllegalStateException("题目已不可用，请重新生成计划");
            }
        } else {
            List<String> codes = preview.items().stream().map(LearningPlanItemPreview::knowledgePointCode).distinct().toList();
            List<PracticeCandidate> allowed = recommendations.recommend(viewerId, eventId, codes, 6);
            allowed.forEach(candidate -> allowedById.put(candidate.questionId(), candidate));
            for (var item : preview.items()) {
                PracticeCandidate candidate = allowedById.get(item.questionId());
                if (candidate == null || !candidate.matchedKnowledgePointCodes().contains(item.knowledgePointCode()))
                    throw new IllegalStateException("题目可练范围已变化，请重新生成计划");
            }
        }
        try {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                        INSERT INTO learning_plans(user_id,event_id,source_run_id,request_id,source_answer_revision,days,status,version)
                        VALUES(?,?,?,?,?,?,'DRAFT',1)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, viewerId); statement.setLong(2, eventId); statement.setString(3, runId);
                statement.setString(4, requestId); statement.setString(5, revision); statement.setInt(6, preview.days());
                return statement;
            }, key);
            long planId = Objects.requireNonNull(key.getKey()).longValue();
            for (var item : preview.items()) {
                String content = allowedById.containsKey(item.questionId())
                        ? allowedById.get(item.questionId()).content()
                        : questionRepository.findById(item.questionId()).map(Question::getContent).orElse("");
                jdbc.update("""
                        INSERT INTO learning_plan_items(plan_id,day_no,question_id,question_content_snapshot,knowledge_point_code,reason_source_ids)
                        VALUES(?,?,?,?,?,?)
                        """, planId, item.day(), item.questionId(), content, item.knowledgePointCode(), json.writeValueAsString(item.reasonSourceIds()));
            }
            return get(viewerId, planId);
        } catch (DuplicateKeyException raced) {
            LearningPlanView byRequest = findByRequest(viewerId, requestId);
            return byRequest != null ? byRequest : findByRun(viewerId, runId);
        } catch (Exception error) { throw new IllegalStateException("无法保存复习计划", error); }
    }

    @Transactional
    public LearningPlanView activate(Long viewerId, Long planId, long expectedVersion) {
        LearningPlanView current = get(viewerId, planId);
        if ("ACTIVE".equals(current.status())) return current;
        if (!"DRAFT".equals(current.status()) || current.version() != expectedVersion)
            throw new IllegalStateException("计划状态已变化，请刷新后重试");
        if (!"no-personal-data".equals(current.sourceAnswerRevision())
                && !current.sourceAnswerRevision().equals(learningData.summary(viewerId, current.eventId()).sourceAnswerRevision()))
            throw new IllegalStateException("原作业作答已变化，请重新生成复习建议");
        tasks.getEventQuestions(current.eventId(), viewerId);
        // 激活时再校验候选 revision 与当前授权，并保存题目快照
        for (var item : current.items()) {
            Question question = questionRepository.findById(item.questionId())
                    .orElseThrow(() -> new IllegalStateException("计划中的题目已不可练，请重新生成复习建议"));
            if (!question.isAiPracticeEnabled())
                throw new IllegalStateException("计划中的题目已不可练，请重新生成复习建议");
            try {
                jdbc.update("""
                        UPDATE learning_plan_items
                        SET question_revision = ?, question_snapshot = ?, question_options_snapshot = ?, question_correct_option = ?
                        WHERE plan_id = ? AND question_id = ?
                        """,
                        question.getAiContentRevision(),
                        json.writeValueAsString(Map.of(
                                "id", question.getId(),
                                "content", question.getContent() == null ? "" : question.getContent(),
                                "difficulty", question.getDifficulty() == null ? 0 : question.getDifficulty())),
                        question.getOptions(),
                        question.getCorrectOption(),
                        planId, item.questionId());
            } catch (Exception error) {
                throw new IllegalStateException("无法保存题目快照", error);
            }
        }
        int changed = jdbc.update("""
                UPDATE learning_plans SET status='ACTIVE',version=version+1,activated_at=UTC_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status='DRAFT' AND version=?
                """, planId, viewerId, expectedVersion);
        if (changed == 0) {
            LearningPlanView raced = get(viewerId, planId);
            if ("ACTIVE".equals(raced.status())) return raced;
            throw new IllegalStateException("计划激活状态已变化");
        }
        return get(viewerId, planId);
    }

    @Transactional(readOnly = true)
    public LearningPlanView get(Long viewerId, Long planId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT id,event_id,status,version,days,source_answer_revision,activated_at,source_run_id
                FROM learning_plans WHERE id=? AND user_id=?
                """, planId, viewerId);
        List<LearningPlanItemPreview> items = jdbc.query("""
                SELECT day_no,question_id,knowledge_point_code,reason_source_ids FROM learning_plan_items WHERE plan_id=? ORDER BY day_no,id
                """, (rs, n) -> new LearningPlanItemPreview(rs.getInt("day_no"), rs.getString("knowledge_point_code"),
                rs.getLong("question_id"), parseStrings(rs.getString("reason_source_ids"))), planId);
        ValidatedPlanPreview preview = decodePreview(jdbc.queryForObject("SELECT plan_json FROM learning_plan_previews WHERE run_id=? AND user_id=?",
                String.class, row.get("source_run_id"), viewerId));
        int attempts = 0, correct = 0;
        if ("ACTIVE".equals(row.get("status")) && !items.isEmpty()) {
            var ids = items.stream().map(LearningPlanItemPreview::questionId).toList();
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            Map<String, Object> stats = jdbc.queryForMap("""
                    SELECT COUNT(*) AS attempts,COALESCE(SUM(CASE WHEN correct=1 THEN 1 ELSE 0 END),0) AS correct_count
                    FROM answer_records WHERE user_id=? AND event_id=? AND practice=1 AND answered_at>=? AND question_id IN (%s)
                    """.formatted(placeholders), concat(viewerId, row.get("event_id"), row.get("activated_at"), ids));
            attempts = ((Number) stats.get("attempts")).intValue(); correct = ((Number) stats.get("correct_count")).intValue();
        }
        String status = "CANCELLED".equals(row.get("status")) ? "CANCELLED" : attempts == 0 ? "NOT_STARTED" : correct == attempts ? "ON_TRACK" : "IN_PROGRESS";
        return new LearningPlanView(((Number) row.get("id")).longValue(), ((Number) row.get("event_id")).longValue(),
                String.valueOf(row.get("status")), ((Number) row.get("version")).longValue(), ((Number) row.get("days")).intValue(),
                items, String.valueOf(row.get("source_answer_revision")), attempts, correct, status,
                preview.executionMode(), preview.evidenceSummary());
    }

    @Transactional(readOnly = true)
    public List<LearningPlanView> list(Long viewerId) {
        return jdbc.queryForList("SELECT id FROM learning_plans WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT 30", Long.class, viewerId)
                .stream().map(id -> get(viewerId, id)).toList();
    }

    @Transactional
    public LearningPlanView cancel(Long viewerId, Long planId, long expectedVersion) {
        int changed = jdbc.update("UPDATE learning_plans SET status='CANCELLED',version=version+1 WHERE id=? AND user_id=? AND status IN ('DRAFT','ACTIVE') AND version=?",
                planId, viewerId, expectedVersion);
        if (changed == 0) {
            LearningPlanView current = get(viewerId, planId);
            if ("CANCELLED".equals(current.status())) return current;
            throw new IllegalStateException("计划状态已变化，请刷新后重试");
        }
        return get(viewerId, planId);
    }

    private LearningPlanView findByRequest(Long viewerId, String requestId) {
        try {
            Long id = jdbc.queryForObject("SELECT id FROM learning_plans WHERE user_id=? AND request_id=?", Long.class, viewerId, requestId);
            return id == null ? null : get(viewerId, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) { return null; }
    }

    private LearningPlanView findByRun(Long viewerId, String runId) {
        try {
            Long id = jdbc.queryForObject("SELECT id FROM learning_plans WHERE user_id=? AND source_run_id=?", Long.class, viewerId, runId);
            return id == null ? null : get(viewerId, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException missing) { return null; }
    }

    private ValidatedPlanPreview decodePreview(Object value) {
        try { return json.readValue(value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(value), ValidatedPlanPreview.class); }
        catch (Exception error) { throw new IllegalStateException("预览计划数据损坏", error); }
    }

    private List<String> parseStrings(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception error) { throw new IllegalStateException("计划证据数据损坏", error); }
    }

    private Object[] concat(Object a, Object b, Object c, List<Long> ids) {
        List<Object> values = new ArrayList<>(List.of(a, b, c)); values.addAll(ids); return values.toArray();
    }
    private boolean validUuid(String value) {
        try { return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
