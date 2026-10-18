package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-answer-record-test-data.sql")
class AnswerRecordSessionQueryTest extends BaseControllerIntegrationTest {
    @Autowired EntityManager entityManager;

    @Test
    void studentSessionUsesTheSameReadForOwnershipAndResponse() throws Exception {
        String session = jdbcTemplate.queryForObject(
                "SELECT session_id FROM answer_records WHERE user_id=1003 LIMIT 1", String.class);
        int expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_records WHERE user_id=1003 AND session_id=?", Integer.class, session);
        String authorization = createAuthHeader(1003L, "student_answer_test");
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        mockMvc.perform(get("/answer-records/session/{id}", session).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(expected));
        assertThat(statistics.getQueryExecutionCount()).isEqualTo(1);
    }
}
