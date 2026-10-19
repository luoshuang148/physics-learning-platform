package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.auth.AuthService;
import com.zhixuewuli.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class HistoricalTaskRosterTest extends BaseControllerIntegrationTest {
 @Autowired TeacherEventService tasks;
 @Autowired QuestionRepository bank;
 @Autowired AnswerRecordService answers;
 @Autowired AuthService auth;
 @Autowired UserService users;

 private Long task(String title, LocalDateTime due) {
  var q=new Question();q.setContent(title);q.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");q.setCorrectOption("A");q.setDifficulty(1);q=bank.saveAndFlush(q);
  var request=new TeacherEventCreateRequest();request.setEventTitle(title);request.setQuestionIds(List.of(q.getId()));
  request.setStatus(TeacherEvent.Status.PUBLISHED);request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));request.setDueAt(due);
  return tasks.publishEvent(request,2001L).getId();
 }
 private User register(String name) {
  var request=new RegisterRequest();request.setUsername(name);request.setRealName(name);request.setPassword("Roster-fixture-2026");request.setUserType(User.UserType.STUDENT);
  return auth.registerUser(request);
 }
 @Test void teacherCannotAddThemselfToTheStudentRosterBySubmittingAnAssignment() {
  Long id=task("Student roster only",null);
  var answer=new AnswerRecordSubmitRequest();answer.setEventId(id);answer.setQuestionId(tasks.getEvent(id).getQuestionIds().getFirst());
  answer.setIntent("ASSIGNMENT");answer.setRequestId("teacher-roster-bypass");answer.setUserAnswer("A");
  assertThatThrownBy(()->answers.submitAnswerRecord(answer,2001L,"teacher-fixture"))
    .isInstanceOf(com.zhixuewuli.common.exception.InsufficientPermissionException.class);
 }
 @Test void closedRosterAndScoresSurviveRegistrationAndAccountDeactivation() {
  Long id=task("Historical roster",null);
  var answer=new AnswerRecordSubmitRequest();answer.setEventId(id);answer.setQuestionId(tasks.getEvent(id).getQuestionIds().getFirst());
  answer.setIntent("ASSIGNMENT");answer.setRequestId("roster-first");answer.setUserAnswer("A");answers.submitAnswerRecord(answer,1001L,"roster");
  tasks.closeTask(id);
  register("after_close_student");
  var update=new UserAdminUpdateRequest();update.setUserId(1001L);update.setActive(false);users.updateUserByAdmin(update,2001L);
  var detail=tasks.getTaskDetail(id,2001L);
  assertThat(detail.getTotalStudents()).isEqualTo(1);assertThat(detail.getCompletedStudents()).isEqualTo(1);
  assertThat(detail.getQuestions().getFirst().getCorrectStudents()).isEqualTo(1);
  assertThat(tasks.getTaskOverview().stream().filter(row->row.getTaskId().equals(id)).findFirst().orElseThrow().getCompletedStudents()).isEqualTo(1);
 }
 @Test void newStudentJoinsOpenAssignmentsButNotEndedAssignments() {
  Long open=task("Open roster",null), ended=task("Ended roster",LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
  User newcomer=register("new_roster_student");
  assertThat(tasks.getTaskDetail(open,2001L).getTotalStudents()).isEqualTo(2);
  assertThat(tasks.getTaskDetail(ended,2001L).getTotalStudents()).isEqualTo(1);
  assertThat(tasks.listStudentTaskResults(newcomer.getId())).extracting(row->row.task().getId()).contains(open).doesNotContain(ended);
 }
}
