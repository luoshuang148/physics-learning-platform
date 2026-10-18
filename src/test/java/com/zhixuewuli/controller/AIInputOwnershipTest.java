package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
@TestPropertySource(properties="ai.context.enabled=false")
class AIInputOwnershipTest extends BaseControllerIntegrationTest {
 @org.springframework.beans.factory.annotation.Autowired com.zhixuewuli.repository.QuestionRepository questions;
 @org.springframework.beans.factory.annotation.Autowired com.zhixuewuli.repository.AnswerRecordRepository answers;
 @Test void disabledCapabilityIsExplicitWithoutDisclosingKeys() throws Exception {
  mockMvc.perform(get("/ai/capabilities").header("Authorization",createAuthHeader(1001L,"student_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.reports").value(false)).andExpect(jsonPath("$.data.tutoring").value(false))
   .andExpect(jsonPath("$.data.aliyunApiKey").doesNotExist());
 }
 @Test void wrongAnalysisRejectsOtherOwnersRecordBeforeOpeningStream() throws Exception {
  var q=new com.zhixuewuli.entity.Question();q.setContent("owner fixture");q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");q.setCorrectOption("A");q.setDifficulty(1);questions.saveAndFlush(q);
  var row=new com.zhixuewuli.entity.AnswerRecord();row.setUserId(2001L);row.setQuestionId(q.getId());row.setSessionId("other-owner");row.setUserAnswer("B");row.setCorrect(false);row.setAnsweredAt(java.time.LocalDateTime.now());answers.saveAndFlush(row);
  mockMvc.perform(post("/ai/student/wrong-question-analysis").header("Authorization",createAuthHeader(1001L,"student_user"))
   .header("X-Session-Id","new-owned-session").contentType(MediaType.APPLICATION_JSON)
   .content("{\"question\":\"分析思路\",\"questionId\":"+q.getId()+",\"recordId\":\""+row.getId()+"\"}"))
   .andExpect(status().isForbidden()).andExpect(request().asyncNotStarted());
 }
}
