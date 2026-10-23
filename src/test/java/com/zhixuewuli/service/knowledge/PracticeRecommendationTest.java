package com.zhixuewuli.service.knowledge;

import com.zhixuewuli.entity.KnowledgePoint;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.QuestionKnowledgePoint;
import com.zhixuewuli.repository.KnowledgePointRepository;
import com.zhixuewuli.repository.QuestionKnowledgePointRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.AnswerAccessPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PracticeRecommendationTest {
    @Mock KnowledgePointRepository knowledgePoints;
    @Mock QuestionKnowledgePointRepository questionKnowledgePoints;
    @Mock QuestionRepository questions;
    @Mock AnswerAccessPolicy answerAccess;
    @InjectMocks PracticeRecommendationService service;

    @Test
    void recommendation_respects_answer_access_and_never_returns_answer_fields() {
        KnowledgePoint point = new KnowledgePoint();
        point.setCode("mechanics.friction");
        Question protectedQuestion = question(1L, "尚未作答作业题");
        Question availableQuestion = question(2L, "可练习题");
        when(knowledgePoints.findByCodeIn(List.of("mechanics.friction"))).thenReturn(List.of(point));
        when(questionKnowledgePoints.findQuestionIdsByKnowledgePointCodes(eq(List.of("mechanics.friction")), any())).thenReturn(List.of(1L, 2L));
        when(questions.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(protectedQuestion, availableQuestion));
        when(answerAccess.blocksPracticeBeforeAssignment(9L, 4L, 1L)).thenReturn(true);
        when(answerAccess.blocksPracticeBeforeAssignment(9L, 4L, 2L)).thenReturn(false);

        var result = service.recommend(9L, 4L, List.of("mechanics.friction"), 50);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().questionId()).isEqualTo(2L);
        assertThat(result.getFirst().content()).isEqualTo("可练习题");
        assertThat(result.getFirst().matchedKnowledgePointCodes()).containsExactly("mechanics.friction");
        assertThat(result.getFirst().toString()).doesNotContain("correctOption", "explanation", "options");
        verify(answerAccess).blocksPracticeBeforeAssignment(9L, 4L, 1L);
        verify(answerAccess).blocksPracticeBeforeAssignment(9L, 4L, 2L);
    }

    private Question question(Long id, String content) {
        Question question = new Question();
        question.setId(id);
        question.setContent(content);
        question.setOptions("{\"A\":\"secret\"}");
        question.setCorrectOption("A");
        question.setExplanation("secret explanation");
        question.setDifficulty(2);
        question.setKnowledgePointCodes(List.of("mechanics.friction"));
        return question;
    }
}
