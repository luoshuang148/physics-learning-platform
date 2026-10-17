package com.zhixuewuli.controller;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.dto.request.*;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Sql("/sql/scenario-teacher-event-test-data.sql")
class TaskAggregateTest extends BaseControllerIntegrationTest {
 @Autowired TeacherEventService tasks;@Autowired AnswerRecordService answers;@Autowired QuestionRepository bank;
 @Test void currentAndArchivedSummarySeparateAssignmentScoresFromPracticeMastery() throws Exception {
  Question q=new Question();q.setContent("aggregate fixture");q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");q.setCorrectOption("A");q.setDifficulty(1);q=bank.saveAndFlush(q);
  var task=new TeacherEventCreateRequest();task.setEventTitle("A");task.setQuestionIds(List.of(q.getId()));task.setEventTimestamp(LocalDateTime.now());
  Long a=tasks.replaceEvent(1L,task,2001L).getId();
  var request=new AnswerRecordSubmitRequest();request.setIntent("ASSIGNMENT");request.setEventId(a);request.setQuestionId(q.getId());request.setUserAnswer("B");request.setRequestId("original");answers.submitAnswerRecord(request,1001L,"aggregate-session");
  task.setEventTitle("B");Long b=tasks.replaceEvent(a,task,2001L).getId();
  request.setIntent("PRACTICE");request.setUserAnswer("A");request.setRequestId("practice");answers.submitAnswerRecord(request,1001L,"aggregate-session");
  mockMvc.perform(get("/answer-records/teacher/summary").param("eventId",a.toString()).header("Authorization",createAuthHeader(2001L,"teacher_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignmentAnswers").value(1)).andExpect(jsonPath("$.data.assignmentCorrect").value(0))
   .andExpect(jsonPath("$.data.completedStudents").value(1)).andExpect(jsonPath("$.data.practiceAttempts").value(1)).andExpect(jsonPath("$.data.masteredQuestions").value(1));
  mockMvc.perform(get("/answer-records/teacher/summary").param("eventId",b.toString()).header("Authorization",createAuthHeader(2001L,"teacher_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignmentAnswers").value(0)).andExpect(jsonPath("$.data.completedStudents").value(0));
  mockMvc.perform(get("/answer-records/student/practice-review").header("Authorization",createAuthHeader(1001L,"student_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].correct").value(true)).andExpect(jsonPath("$.data.records[0].eventId").value(a));
 }
 @Test void studentsCannotReadClassroomSummary() throws Exception {
  mockMvc.perform(get("/answer-records/teacher/summary").header("Authorization",createAuthHeader(1001L,"student_user"))).andExpect(status().isForbidden());
 }
}
