package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
class TaskSubmissionContractTest extends BaseControllerIntegrationTest {
 @Autowired QuestionRepository bank; @Autowired AnswerRecordRepository answers;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 long taskId;Question q;
 @BeforeEach void task() throws Exception {
  q=new Question();q.setContent("scoped immutable question");q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");q.setCorrectOption("A");q.setDifficulty(1);q=bank.saveAndFlush(q);
  var response=mockMvc.perform(put("/teacher/events/1/replace").header("Authorization",createAuthHeader(2001L,"teacher_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventTitle\":\"task\",\"questionIds\":["+q.getId()+"],\"eventTimestamp\":\"2026-10-18T00:00:00\"}"))
   .andExpect(status().isOk()).andReturn();taskId=json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
 }
 private JsonNode submit(String key,String answer,String intent,int expected) throws Exception {
  var r=mockMvc.perform(post("/answer-records/student/submit").header("Authorization",createAuthHeader(1001L,"student_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":"+taskId+",\"requestId\":\""+key+"\",\"questionId\":"+q.getId()+",\"userAnswer\":\""+answer+"\",\"intent\":\""+intent+"\"}"))
   .andExpect(status().is(expected)).andReturn();return json.readTree(r.getResponse().getContentAsString()).path("data");
 }
 @Test void retryAfterArchiveReturnsOriginalAndConflictingPayloadRejects() throws Exception {
  var first=submit("retry-key","B","ASSIGNMENT",201);
  mockMvc.perform(delete("/teacher/events/"+taskId).header("Authorization",createAuthHeader(2001L,"teacher_user"))).andExpect(status().isOk());
  var retry=submit("retry-key","B","ASSIGNMENT",201);assertThat(retry.path("recordId").asText()).isEqualTo(first.path("recordId").asText());
  assertThat(answers.count()).isEqualTo(1);submit("retry-key","A","ASSIGNMENT",409);
 }
 @Test void archivedTaskRejectsNewAssignmentButAllowsExplicitPracticeUsingOriginalAnswer() throws Exception {
  mockMvc.perform(delete("/teacher/events/"+taskId).header("Authorization",createAuthHeader(2001L,"teacher_user"))).andExpect(status().isOk());
  submit("new-assignment","A","ASSIGNMENT",409);
  q.setCorrectOption("B");bank.saveAndFlush(q);
  var practice=submit("practice-key","A","PRACTICE",201);assertThat(practice.path("correct").asBoolean()).isTrue();
  assertThat(practice.path("practice").asBoolean()).isTrue();assertThat(practice.path("eventId").asLong()).isEqualTo(taskId);
 }
 @Test void questionOutsideTaskAndInvalidOptionCannotBeRecorded() throws Exception {
  submit("invalid-option","Z","ASSIGNMENT",400);assertThat(answers.count()).isZero();
  Question other=new Question();other.setContent("not assigned");other.setOptions(q.getOptions());other.setCorrectOption("A");other.setDifficulty(1);other=bank.saveAndFlush(other);
  mockMvc.perform(post("/answer-records/student/submit").header("Authorization",createAuthHeader(1001L,"student_user"))
   .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":"+taskId+",\"requestId\":\"outside\",\"questionId\":"+other.getId()+",\"userAnswer\":\"A\"}"))
   .andExpect(status().isBadRequest());assertThat(answers.count()).isZero();
 }
 @Test void secondAssignmentRequiresExplicitPracticeIntent() throws Exception {
  submit("assignment-first","B","ASSIGNMENT",201);
  submit("assignment-again","A","ASSIGNMENT",409);
  submit("practice-next","A","PRACTICE",201);
  assertThat(answers.count()).isEqualTo(2);
 }
 @Test
 @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 @org.springframework.test.context.jdbc.SqlMergeMode(org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE)
 @Sql(scripts="/sql/cleanup-all.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD)
 void concurrentRetriesCreateOneAttempt() throws Exception {
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var start=new java.util.concurrent.CountDownLatch(1);
   var first=pool.submit(()->{start.await();return submit("parallel-retry","B","ASSIGNMENT",201);});
   var second=pool.submit(()->{start.await();return submit("parallel-retry","B","ASSIGNMENT",201);});
   start.countDown();
   assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS).path("recordId").asText())
     .isEqualTo(second.get(15,java.util.concurrent.TimeUnit.SECONDS).path("recordId").asText());
   assertThat(answers.count()).isEqualTo(1);
  }
 }
 @Test void usedTaskQuestionsCannotBePhysicallyDeleted() throws Exception {
  mockMvc.perform(delete("/questions/teacher/delete/"+q.getId()).header("Authorization",createAuthHeader(2001L,"teacher_user")))
   .andExpect(status().isBadRequest());
  assertThat(bank.findById(q.getId())).isPresent();
 }
}
