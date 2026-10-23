package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.agent.PersonalLearningSummary;
import com.zhixuewuli.dto.agent.WrongQuestionFact;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LearningAssistantDataRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PersonalLearningSummary summary(Long viewerId, Long eventId) {
        var row = jdbc.queryForMap("""
                SELECT COUNT(*) AS attempts,
                       COALESCE(SUM(CASE WHEN correct=1 THEN 1 ELSE 0 END),0) AS correct_attempts,
                       COUNT(DISTINCT CASE WHEN correct=0 THEN question_id END) AS wrong_questions,
                       COALESCE(MAX(attempt_order),0) AS latest_attempt
                FROM answer_records
                WHERE user_id=? AND event_id=? AND practice=0
                """, viewerId, eventId);
        long attempts = ((Number) row.get("attempts")).longValue();
        long correct = ((Number) row.get("correct_attempts")).longValue();
        long wrong = ((Number) row.get("wrong_questions")).longValue();
        long latest = ((Number) row.get("latest_attempt")).longValue();
        String revision = digest(viewerId + ":" + eventId + ":" + attempts + ":" + latest);
        return new PersonalLearningSummary(attempts, correct, wrong, revision);
    }

    public List<WrongQuestionFact> wrongQuestions(Long viewerId, Long eventId, int limit) {
        int boundedLimit = Math.clamp(limit, 1, 10);
        return jdbc.query("""
                SELECT id,question_id,selected_option,content_snapshot,question_snapshot,difficulty_snapshot
                FROM (
                    SELECT a.id,a.question_id,a.selected_option,a.content_snapshot,a.question_snapshot,a.difficulty_snapshot,
                           a.correct,ROW_NUMBER() OVER (PARTITION BY a.question_id ORDER BY a.attempt_order DESC) AS row_num
                    FROM answer_records a
                    WHERE a.user_id=? AND a.event_id=? AND a.practice=0
                ) latest
                WHERE row_num=1 AND correct=0
                ORDER BY question_id DESC LIMIT ?
                """, (rs, index) -> {
            String questionJson = rs.getString("question_snapshot");
            List<String> codes = List.of();
            Integer difficulty = rs.getObject("difficulty_snapshot", Integer.class);
            if (questionJson != null) {
                try {
                    var snapshot = json.readTree(questionJson);
                    var points = snapshot.path("knowledgePointCodes");
                    if (points.isArray()) {
                        var values = new java.util.ArrayList<String>();
                        points.forEach(value -> values.add(value.asText()));
                        codes = List.copyOf(values);
                    }
                    if (difficulty == null && snapshot.hasNonNull("difficulty")) difficulty = snapshot.path("difficulty").asInt();
                } catch (Exception ignored) { /* Legacy/invalid snapshots remain untagged and are not inferred. */ }
            }
            return new WrongQuestionFact(rs.getString("id"), rs.getLong("question_id"), rs.getString("content_snapshot"),
                    rs.getString("selected_option"), difficulty, codes);
        }, viewerId, eventId, boundedLimit);
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
