package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.*;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.answerRecord.AnswerRecordService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class StudentTaskResultTest extends BaseControllerIntegrationTest {
    @Autowired TeacherEventService tasks;
    @Autowired AnswerRecordService answers;
    @Autowired QuestionRepository bank;
    @Autowired TeacherEventRepository events;
    @Autowired UserRepository users;
    @Autowired StudentReportRepository reports;

    @Test void savedReportIsReusableOnlyForItsExactAnswerRevision() throws Exception {
        Long id=createTask("Reusable report");
        var request=new AnswerRecordSubmitRequest();request.setEventId(id);
        request.setQuestionId(tasks.getEvent(id).getQuestionIds().getFirst());request.setIntent("ASSIGNMENT");
        request.setUserAnswer("B");request.setRequestId("report-first");
        var first=answers.submitAnswerRecord(request,1001L,"report-fixture");
        var report=new StudentReport();report.setUserId(1001L);report.setRealName("学生");report.setEventId(id);
        report.setSessionId("report-fixture");report.setReportContent("已保存复盘");
        report.setEvidenceJson("{\"answerRevision\":\""+first.getAttemptOrder()+"\"}");reports.saveAndFlush(report);
        mockMvc.perform(get("/teacher/events/"+id+"/my-result").header("Authorization",createAuthHeader(1001L,"student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.savedReport.content").value("已保存复盘"));
        request.setIntent("PRACTICE");request.setRequestId("report-practice");request.setUserAnswer("A");
        answers.submitAnswerRecord(request,1001L,"report-fixture");
        mockMvc.perform(get("/teacher/events/"+id+"/my-result").header("Authorization",createAuthHeader(1001L,"student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.savedReport").doesNotExist());
    }

    private Long createTask(String title) {
        Question q = new Question(); q.setContent(title); q.setOptions("{\"A\":\"a\",\"B\":\"b\"}");
        q.setCorrectOption("A"); q.setDifficulty(1); q = bank.saveAndFlush(q);
        var request = new TeacherEventCreateRequest(); request.setEventTitle(title);
        request.setQuestionIds(List.of(q.getId())); request.setEventTimestamp(LocalDateTime.now());
        return tasks.publishEvent(request, 2001L).getId();
    }

    @Test void archivedResultsRemainAccessibleAndPracticeDoesNotRewriteOriginalScore() throws Exception {
        Long id = createTask("历史作业");
        var request = new AnswerRecordSubmitRequest(); request.setEventId(id);
        request.setQuestionId(tasks.getEvent(id).getQuestionIds().getFirst());
        request.setIntent("ASSIGNMENT"); request.setUserAnswer("B"); request.setRequestId("first");
        answers.submitAnswerRecord(request, 1001L, "review-session");
        tasks.archiveEvent(id);
        request.setIntent("PRACTICE"); request.setUserAnswer("A"); request.setRequestId("practice");
        answers.submitAnswerRecord(request, 1001L, "review-session");
        mockMvc.perform(get("/teacher/events/student-tasks").header("Authorization", createAuthHeader(1001L, "student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.task.id == " + id + ")].answeredQuestions", contains(1)))
            .andExpect(jsonPath("$.data[?(@.task.id == " + id + ")].correctQuestions", contains(0)));
        mockMvc.perform(get("/teacher/events/" + id + "/my-result").header("Authorization", createAuthHeader(1001L, "student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.task.status").value("ARCHIVED"))
            .andExpect(jsonPath("$.data.answeredQuestions").value(1)).andExpect(jsonPath("$.data.correctQuestions").value(0));
    }

    @Test void closedTasksRemainVisibleWithoutAnswersWhileDraftsAndUnansweredArchivesStayHidden() throws Exception {
        Long closed = createTask("未作答的已截止任务"); tasks.closeTask(closed);
        Long archived = createTask("未作答的已归档任务"); tasks.archiveEvent(archived);
        Long draft = createTask("草稿"); var entity = events.findById(draft).orElseThrow();
        entity.setStatus(TeacherEvent.Status.DRAFT); events.saveAndFlush(entity);
        mockMvc.perform(get("/teacher/events/student-tasks").header("Authorization", createAuthHeader(1001L, "student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].task.id", hasItem(closed.intValue())))
            .andExpect(jsonPath("$.data[?(@.task.id == " + closed + ")].answeredQuestions", contains(0)))
            .andExpect(jsonPath("$.data[*].task.id", not(hasItems(archived.intValue(), draft.intValue()))));
        mockMvc.perform(get("/teacher/events/" + draft + "/my-result").header("Authorization", createAuthHeader(1001L, "student_user")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/teacher/events/student-tasks")).andExpect(status().isUnauthorized());
    }

    @Test void anotherStudentsAnswersDoNotAppearInMyProgress() throws Exception {
        Long id = createTask("独立进度");
        User other = new User(); other.setUsername("other_review_student"); other.setRealName("另一位学生");
        other.setPasswordHash("x"); other.setActive(true); other.setUserType(User.UserType.STUDENT);
        other = users.saveAndFlush(other);
        var request = new AnswerRecordSubmitRequest(); request.setEventId(id);
        request.setQuestionId(tasks.getEvent(id).getQuestionIds().getFirst());
        request.setIntent("ASSIGNMENT"); request.setUserAnswer("A"); request.setRequestId("teacher-answer");
        answers.submitAnswerRecord(request, other.getId(), "other-session");
        mockMvc.perform(get("/teacher/events/" + id + "/my-result").header("Authorization", createAuthHeader(1001L, "student_user")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.answeredQuestions").value(0))
            .andExpect(jsonPath("$.data.correctQuestions").value(0));
    }
}
