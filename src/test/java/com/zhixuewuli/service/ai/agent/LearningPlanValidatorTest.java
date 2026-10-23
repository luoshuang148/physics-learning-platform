package com.zhixuewuli.service.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.dto.response.PracticeCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class LearningPlanValidatorTest {
    private final LearningPlanValidator validator = new LearningPlanValidator(new ObjectMapper());

    @Test void acceptsOnlyReturnedQuestionIdsAndSources() {
        var valid = """
                {"days":2,"sourceEventId":7,"sourceAnswerRevision":"rev-1","items":[{"day":1,"knowledgePointCode":"NEWTON_2","questionIds":[31],"reasonSourceIds":["W1","S1"]}]}
                """;
        var plan = validator.validate(valid, 7L, "rev-1", Set.of("W1", "S1"),
                List.of(new PracticeCandidate(31L, "题干", 2, List.of("NEWTON_2"))), 2, "AGENT");
        assertEquals(31L, plan.items().get(0).questionId());
        assertEquals("AGENT", plan.executionMode());
    }

    @Test void rejectsQuestionNotReturnedByRecommendationTool() {
        var invalid = """
                {"days":1,"sourceEventId":7,"sourceAnswerRevision":"rev-1","items":[{"day":1,"knowledgePointCode":"NEWTON_2","questionIds":[999],"reasonSourceIds":[]}] }
                """;
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid, 7L, "rev-1", Set.of(),
                List.of(new PracticeCandidate(31L, "题干", 2, List.of("NEWTON_2"))), 1, "AGENT"));
    }

    @Test void rejectsUnknownEvidenceAndOverBudgetPlans() {
        var unknownEvidence = """
                {"days":1,"sourceEventId":7,"sourceAnswerRevision":"rev-1","items":[{"day":1,"knowledgePointCode":"NEWTON_2","questionIds":[31],"reasonSourceIds":["W9"]}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> validator.validate(unknownEvidence, 7L, "rev-1", Set.of("W1"),
                List.of(new PracticeCandidate(31L, "题干", 2, List.of("NEWTON_2"))), 1, "AGENT"));
        var tooManyDays = """
                {"days":8,"sourceEventId":7,"sourceAnswerRevision":"rev-1","items":[]}
                """;
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooManyDays, 7L, "rev-1", Set.of(), List.of(), 1, "AGENT"));
    }
}
