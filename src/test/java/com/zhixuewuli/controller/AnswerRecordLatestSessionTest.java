package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql("/sql/scenario-answer-record-test-data.sql")
class AnswerRecordLatestSessionTest extends BaseControllerIntegrationTest {
    @Test
    void latestSessionUsesAnsweredAtNotSessionIdOrder() throws Exception {
        jdbcTemplate.update("INSERT INTO answer_records(id,user_id,question_id,session_id,selected_option,correct,answered_at,created_at,updated_at) "
                + "VALUES('latest_small_id',1003,4001,'aaa_newer','B',TRUE,'2026-01-12 08:00:00',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

        mockMvc.perform(get("/answer-records/teacher/1003/latest-session")
                .header("Authorization", createAuthHeader(2003L, "teacher_answer_test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.records[0].sessionId").value("aaa_newer"));

        mockMvc.perform(get("/answer-records/teacher/realname/{name}/latest-session", "答题测试学生")
                .header("Authorization", createAuthHeader(2003L, "teacher_answer_test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].sessionId").value("aaa_newer"));
    }

    @Test
    void otherStudentCannotQueryLatestSession() throws Exception {
        mockMvc.perform(get("/answer-records/teacher/1003/latest-session")
                .header("Authorization", createAuthHeader(1004L, "student_other_test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void noHistoryReturnsNormalEmptyResult() throws Exception {
        jdbcTemplate.update("INSERT INTO users(id,username,password_hash,real_name,user_type,class_name,active,created_at,updated_at) "
                + "VALUES(1005,'empty_student','x','无记录学生','STUDENT','三年一班',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        mockMvc.perform(get("/answer-records/teacher/1005/latest-session")
                .header("Authorization", createAuthHeader(2003L, "teacher_answer_test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }
}
