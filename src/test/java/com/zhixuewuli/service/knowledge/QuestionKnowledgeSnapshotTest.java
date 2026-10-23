package com.zhixuewuli.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.entity.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class QuestionKnowledgeSnapshotTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void old_snapshot_without_tags_stays_unknown() throws Exception {
        Question oldSnapshot = json.readValue("{\"id\":7,\"content\":\"旧题\",\"options\":\"{}\",\"correctOption\":\"A\"}", Question.class);

        assertThat(oldSnapshot.getKnowledgePointCodes()).isEmpty();
    }

    @Test
    void editing_tags_does_not_rewrite_old_task_snapshot() throws Exception {
        Question captured = question(List.of("mechanics.newton-second-law"));
        String taskSnapshot = json.writeValueAsString(captured);
        captured.setKnowledgePointCodes(List.of("mechanics.friction"));

        Question decoded = json.readValue(taskSnapshot, Question.class);
        assertThat(decoded.getKnowledgePointCodes()).containsExactly("mechanics.newton-second-law");
        assertThat(captured.getKnowledgePointCodes()).containsExactly("mechanics.friction");
    }

    private Question question(List<String> codes) {
        Question question = new Question();
        question.setId(7L);
        question.setContent("题目");
        question.setOptions("{}");
        question.setCorrectOption("A");
        question.setKnowledgePointCodes(codes);
        return question;
    }
}
