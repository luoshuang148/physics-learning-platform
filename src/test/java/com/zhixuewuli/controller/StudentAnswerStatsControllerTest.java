package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql("/sql/scenario-answer-record-test-data.sql")
class StudentAnswerStatsControllerTest extends BaseControllerIntegrationTest {
    private static final String ENDPOINT = "/answer-records/teacher/student-stats";
    @Autowired EntityManager entityManager;

    @Test
    void aggregatesAttemptsWithoutLoadingRecordsOrQuestionImages() throws Exception {
        jdbcTemplate.update("DELETE FROM answer_records");
        attempt("a", 1003, true);
        attempt("b", 1003, false);
        attempt("c", 1003, true);
        attempt("d", 1004, false);
        attempt("teacher", 2003, false);
        // All attempts (including practice) count, matching the existing student analysis page.
        jdbcTemplate.update("UPDATE answer_records SET practice = true WHERE id = 'c'");
        jdbcTemplate.update("UPDATE users SET active = false WHERE id = 1004");
        String authorization = createAuthHeader(2003L, "teacher_answer_test");
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        String response = mockMvc.perform(get(ENDPOINT).header("Authorization", authorization))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows = new ObjectMapper().readTree(response).path("data");
        assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.get(0).path("userId").asLong()).isEqualTo(1003);
        assertThat(rows.get(0).path("totalCount").asLong()).isEqualTo(3);
        assertThat(rows.get(0).path("wrongCount").asLong()).isEqualTo(1);
        assertThat(rows.get(1).path("userId").asLong()).isEqualTo(1004);
        assertThat(rows.get(1).path("totalCount").asLong()).isEqualTo(1);
        assertThat(rows.get(1).path("wrongCount").asLong()).isEqualTo(1);
        assertThat(statistics.getEntityStatistics(AnswerRecord.class.getName()).getLoadCount()).isZero();
        assertThat(statistics.getEntityStatistics(Question.class.getName()).getLoadCount()).isZero();
    }

    @Test
    void emptyAttemptsReturnAnEmptyStatsList() throws Exception {
        jdbcTemplate.update("DELETE FROM answer_records");
        String response = mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", createAuthHeader(2003L, "teacher_answer_test")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(new ObjectMapper().readTree(response).path("data").size()).isZero();
    }

    @Test
    void requiresTeacherAuthorization() throws Exception {
        mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ENDPOINT)
                        .header("Authorization", createAuthHeader(1003L, "student_answer_test")))
                .andExpect(status().isForbidden());
    }

    private void attempt(String id, long userId, boolean correct) {
        jdbcTemplate.update("INSERT INTO answer_records " +
                        "(id,user_id,question_id,session_id,selected_option,correct,answered_at,created_at,updated_at) " +
                        "VALUES (?,?,4001,'stats-session','A',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, userId, correct);
    }
}
