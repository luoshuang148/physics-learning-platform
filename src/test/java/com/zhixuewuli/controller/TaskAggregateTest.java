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
 @Autowired TeacherEventService tasks;@Autowired AnswerRecordService answers;@Autowired QuestionRepository bank;@Autowired UserRepository users;
 @Test void currentAndArchivedSummarySeparateAssignmentScoresFromPracticeMastery() throws Exception {
  Question q=new Question();q.setContent("aggregate fixture");q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");q.setCorrectOption("A");q.setDifficulty(1);q=bank.saveAndFlush(q);
  var task=new TeacherEventCreateRequest();task.setEventTitle("A");task.setQuestionIds(List.of(q.getId()));task.setEventTimestamp(LocalDateTime.now());
  Long a=tasks.replaceEvent(1L,task,2001L).getId();
  var request=new AnswerRecordSubmitRequest();request.setIntent("ASSIGNMENT");request.setEventId(a);request.setQuestionId(q.getId());request.setUserAnswer("B");request.setRequestId("original");answers.submitAnswerRecord(request,1001L,"aggregate-session");
  var other=new Question();other.setContent("another aggregate question");other.setOptions(q.getOptions());other.setCorrectOption("A");other.setDifficulty(1);other=bank.saveAndFlush(other);task.setQuestionIds(List.of(other.getId()));
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
 @Test void completionUsesDurableRecipientsDespiteAccountDeactivation() throws Exception {
  Question q=new Question();q.setContent("active student fixture");q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");q.setCorrectOption("A");q.setDifficulty(1);q=bank.saveAndFlush(q);
  var task=new TeacherEventCreateRequest();task.setEventTitle("C");task.setQuestionIds(List.of(q.getId()));task.setEventTimestamp(LocalDateTime.now());
  Long id=tasks.replaceEvent(1L,task,2001L).getId();
  User second=new User();second.setUsername("second_student");second.setRealName("第二名学生");second.setUserType(User.UserType.STUDENT);second.setActive(true);second.setPasswordHash("x");second=users.saveAndFlush(second);
  for(Long userId:List.of(1001L,second.getId())){
   var request=new AnswerRecordSubmitRequest();request.setIntent("ASSIGNMENT");request.setEventId(id);request.setQuestionId(q.getId());request.setUserAnswer("A");request.setRequestId("complete-"+userId);
   answers.submitAnswerRecord(request,userId,"active-session");
  }
  second.setActive(false);users.saveAndFlush(second);
  mockMvc.perform(get("/answer-records/teacher/summary").param("eventId",id.toString()).header("Authorization",createAuthHeader(2001L,"teacher_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligibleStudents").value(2))
   .andExpect(jsonPath("$.data.completedStudents").value(2)).andExpect(jsonPath("$.data.participatingStudents").value(2));
  User first=users.findById(1001L).orElseThrow();first.setActive(false);users.saveAndFlush(first);
  mockMvc.perform(get("/answer-records/teacher/summary").param("eventId",id.toString()).header("Authorization",createAuthHeader(2001L,"teacher_user")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.eligibleStudents").value(2))
   .andExpect(jsonPath("$.data.completedStudents").value(2)).andExpect(jsonPath("$.data.participatingStudents").value(2));
 }
 @Test void studentsCannotReadClassroomSummary() throws Exception {
  mockMvc.perform(get("/answer-records/teacher/summary").header("Authorization",createAuthHeader(1001L,"student_user"))).andExpect(status().isForbidden());
 }
}
