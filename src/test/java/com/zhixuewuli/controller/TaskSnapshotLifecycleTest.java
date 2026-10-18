package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class TaskSnapshotLifecycleTest extends BaseControllerIntegrationTest {
 @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
 @Autowired TeacherEventService tasks;
 @Autowired QuestionRepository questions;
 private Question question() { Question q=new Question();q.setContent("immutable task question");q.setOptions("{\"A\":\"one\",\"B\":\"two\",\"C\":\"three\",\"D\":\"four\"}");q.setCorrectOption("A");q.setExplanation("解析");q.setDifficulty(1);return questions.saveAndFlush(q); }
 private long publish(Question q) throws Exception {
  tasks.deleteCurrentEvent(2001L);
  var result=mockMvc.perform(post("/teacher/events").header("Authorization",createAuthHeader(2001L,"teacher_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventTitle\":\"Task B\",\"eventTimestamp\":\"2026-10-18T10:00:00\",\"questionIds\":["+q.getId()+"]}"))
   .andExpect(status().isCreated()).andReturn();
  return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
 }
 @Test void publishedQuestionsSurviveQuestionBankEditsAndUnlockOnlyAfterFirstAttempt() throws Exception {
  var q=question();long id=publish(q);q.setContent("changed bank content");q.setCorrectOption("B");questions.saveAndFlush(q);
  mockMvc.perform(get("/teacher/events/"+id+"/questions").header("Authorization",createAuthHeader(1001L,"student_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("immutable task question"))
   .andExpect(jsonPath("$.data[0].correctOption").doesNotExist())
   .andExpect(jsonPath("$.data[0].explanation").doesNotExist())
   .andExpect(jsonPath("$.data[0].snapshotOrigin").value("PUBLISHED"));
  mockMvc.perform(post("/answer-records/student/submit").header("Authorization",createAuthHeader(1001L,"student_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":"+id+",\"requestId\":\"unlock\",\"questionId\":"+q.getId()+",\"userAnswer\":\"B\",\"intent\":\"ASSIGNMENT\"}"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.data.correctAnswer").value("A"))
   .andExpect(jsonPath("$.data.questionAnalysis").value("解析"));
  mockMvc.perform(get("/teacher/events/"+id+"/questions").header("Authorization",createAuthHeader(1001L,"student_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].correctOption").value("A"));
 }
 @Test void archivingOldTaskDoesNotAffectNewTask() throws Exception {
  long id=publish(question());
  mockMvc.perform(delete("/teacher/events/1").header("Authorization",createAuthHeader(2001L,"teacher_user"))).andExpect(status().isOk());
  assertThat(tasks.getGlobalEvent().getId()).isEqualTo(id);
 }
 @Test void invalidReplacementLeavesCurrentTaskUntouched() throws Exception {
  long id=publish(question());
  mockMvc.perform(put("/teacher/events/"+id+"/replace").header("Authorization",createAuthHeader(2001L,"teacher_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventTitle\":\"invalid replacement\",\"eventTimestamp\":\"2026-10-18T10:00:00\",\"questionIds\":[999999]}"))
   .andExpect(status().isBadRequest());
  assertThat(tasks.getGlobalEvent().getId()).isEqualTo(id);
 }
}
