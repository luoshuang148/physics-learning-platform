package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-answer-record-test-data.sql")
class AnswerRecordPageControllerTest extends BaseControllerIntegrationTest {
    private static final String ENDPOINT = "/answer-records/teacher/page";
    private String teacher() { return createAuthHeader(2003L, "teacher_answer_test"); }

    @Test
    void pagesHaveStableOrderingTotalsAndNoImageBodies() throws Exception {
        jdbcTemplate.update("UPDATE answer_records SET answered_at = '2026-01-01 12:00:00'");
        JsonNode first = page(1);
        JsonNode second = page(2);
        assertThat(first.path("total").asLong()).isGreaterThan(1);
        assertThat(first.path("records").size()).isEqualTo(1);
        assertThat(first.path("records").get(0).path("imageBase64").isNull()
                || first.path("records").get(0).path("imageBase64").isMissingNode()).isTrue();
        assertThat(first.path("records").get(0).path("questionAnalysis").isNull()
                || first.path("records").get(0).path("questionAnalysis").isMissingNode()).isTrue();
        assertThat(first.path("records").get(0).path("questionOptions").isNull()
                || first.path("records").get(0).path("questionOptions").isMissingNode()).isTrue();
        String id1 = first.path("records").get(0).path("recordId").asText();
        String id2 = second.path("records").get(0).path("recordId").asText();
        assertThat(id1).isGreaterThan(id2);
        assertThat(page(1).path("records")).isEqualTo(first.path("records"));
    }

    @Test
    void filtersRunOnTheServer() throws Exception {
        long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM answer_records WHERE correct = false AND user_id = 1003", Long.class);
        var response = mockMvc.perform(get(ENDPOINT).header("Authorization", teacher())
                .param("wrongOnly", "true").param("studentName", "答题测试学生"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(expected);
        assertThat(expected).isPositive();
        for (JsonNode row : data.path("records")) {
            assertThat(row.path("correct").asBoolean()).isFalse();
            assertThat(row.path("userId").asLong()).isEqualTo(1003L);
        }
    }

    @Test
    void enforcesAuthorizationAndPageBounds() throws Exception {
        mockMvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ENDPOINT).header("Authorization", createAuthHeader(1003L, "student_answer_test")))
                .andExpect(status().isForbidden());
        for (String size : new String[]{"0", "101"}) {
            mockMvc.perform(get(ENDPOINT).header("Authorization", teacher()).param("size", size))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(ENDPOINT).header("Authorization", teacher()).param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode page(int page) throws Exception {
        String response = mockMvc.perform(get(ENDPOINT).header("Authorization", teacher())
                .param("page", Integer.toString(page)).param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).path("data");
    }
}
