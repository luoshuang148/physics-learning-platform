package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql("/sql/scenario-teacher-event-test-data.sql")
class AnswerAccessPolicyTest extends BaseControllerIntegrationTest {
    @Autowired TeacherEventService tasks;
    @Autowired QuestionRepository questions;
    @Autowired ObjectMapper json;
    @Autowired com.zhixuewuli.repository.TeacherEventRepository events;
    private Question question;
    private long taskId;
    private User second;

    @BeforeEach
    void setUp() {
        question = new Question();
        question.setContent("access-policy-question");
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("D"); question.setExplanation("受保护解析"); question.setDifficulty(2);
        question = questions.saveAndFlush(question);
        TeacherEventCreateRequest request = new TeacherEventCreateRequest();
        request.setEventTitle("Access policy"); request.setQuestionIds(List.of(question.getId()));
        request.setEventTimestamp(LocalDateTime.now());
        taskId = tasks.replaceEvent(1L, request, 2001L).getId();
        second = new User(); second.setUsername("policy_student_two"); second.setRealName("策略学生二");
        second.setUserType(User.UserType.STUDENT); second.setActive(true); second.setPasswordHash("x");
        second = userRepository.saveAndFlush(second);
    }

    @Test
    void taskAndBankHideProtectedFieldsUntilTheSameStudentFirstAnswers() throws Exception {
        hiddenTask();
        hiddenBank();
        practiceBypassBlocked("ASSIGNMENT");

        submit(second.getId(), "student-two-key", "ASSIGNMENT", 201);
        hiddenBank();

        submit(1001L, "student-one-key", "ASSIGNMENT", 201);
        visibleTask();
        visibleBank();
    }

    @Test
    void teacherAlwaysSeesFullQuestionManagementData() throws Exception {
        mockMvc.perform(get("/questions/teacher/get/{id}", question.getId())
                .header("Authorization", createAuthHeader(2001L, "teacher_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correctOption").value("D"))
                .andExpect(jsonPath("$.data.explanation").value("受保护解析"));
    }

    @Test
    void scheduledTaskBlocksScopedAndFreePracticeBeforeOpening() throws Exception {
        var task = events.findById(taskId).orElseThrow();
        task.setStartAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        events.saveAndFlush(task);
        practiceBypassBlocked("PRACTICE");
        hiddenBank();
    }

    @Test
    void archivedTaskCannotDiscloseAnUnattemptedQuestionInAnotherPublishedTask() throws Exception {
        var request = new TeacherEventCreateRequest();
        request.setEventTitle("Old task"); request.setEventTimestamp(LocalDateTime.now());
        request.setQuestionIds(List.of(question.getId()));
        long archivedId = tasks.publishEvent(request, 2001L).getId();
        tasks.archiveEvent(archivedId);
        mockMvc.perform(post("/answer-records/student/submit")
                .header("Authorization", createAuthHeader(1001L, "student_user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + archivedId + ",\"requestId\":\"other-task\",\"questionId\":"
                        + question.getId() + ",\"userAnswer\":\"A\",\"intent\":\"PRACTICE\"}"))
                .andExpect(status().isConflict());
        submit(1001L, "first-before-practice", "ASSIGNMENT", 201);
        tasks.archiveEvent(taskId);
        submit(1001L, "practice-after-close", "PRACTICE", 201);
    }

    private void hiddenTask() throws Exception {
        mockMvc.perform(get("/teacher/events/{id}/questions", taskId)
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("access-policy-question"))
                .andExpect(jsonPath("$.data[0].correctOption").doesNotExist())
                .andExpect(jsonPath("$.data[0].explanation").doesNotExist());
    }

    private void visibleTask() throws Exception {
        mockMvc.perform(get("/teacher/events/{id}/questions", taskId)
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].correctOption").value("D"))
                .andExpect(jsonPath("$.data[0].explanation").value("受保护解析"));
    }

    private void hiddenBank() throws Exception {
        mockMvc.perform(get("/questions/teacher/get/{id}", question.getId())
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correctOption").doesNotExist())
                .andExpect(jsonPath("$.data.explanation").doesNotExist());
    }

    private void visibleBank() throws Exception {
        mockMvc.perform(get("/questions/teacher/get/{id}", question.getId())
                .header("Authorization", createAuthHeader(1001L, "student_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.correctOption").value("D"));
    }

    private void practiceBypassBlocked(String intent) throws Exception {
        mockMvc.perform(post("/answer-records/student/submit")
                .header("Authorization", createAuthHeader(1001L, "student_user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + taskId + ",\"requestId\":\"bypass-practice\",\"questionId\":"
                        + question.getId() + ",\"userAnswer\":\"D\",\"intent\":\"PRACTICE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/answer-records/student/submit")
                .header("Authorization", createAuthHeader(1001L, "student_user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"bypass-free\",\"questionId\":" + question.getId()
                        + ",\"userAnswer\":\"D\",\"intent\":\"PRACTICE\"}"))
                .andExpect(status().isConflict());
    }

    private void submit(Long userId, String key, String intent, int expected) throws Exception {
        String username = userId == 1001L ? "student_user" : "policy_student_two";
        mockMvc.perform(post("/answer-records/student/submit")
                .header("Authorization", createAuthHeader(userId, username))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":" + taskId + ",\"requestId\":\"" + key + "\",\"questionId\":"
                        + question.getId() + ",\"userAnswer\":\"D\",\"intent\":\"" + intent + "\"}"))
                .andExpect(status().is(expected));
    }
}
