package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.ReportBatchCreateRequest;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.TeachingClassRepository;
import com.zhixuewuli.service.classroom.ClassAccessService;
import com.zhixuewuli.service.teacherEvent.TeacherEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 批量报告接口：权限、幂等、进度查询、重试与报告读取的 HTTP 契约。 */
@Sql("/sql/scenario-teacher-event-test-data.sql")
@DisplayName("批量报告控制器")
class ReportBatchControllerIntegrationTest extends BaseControllerIntegrationTest {

    private static final long STUDENT_ID = 1001L;
    private static final long TEACHER_ID = 2001L;
    private static final long OTHER_TEACHER_ID = 2002L;

    @Autowired private TeacherEventService events;
    @Autowired private ClassAccessService classAccess;
    @Autowired private TeachingClassRepository classes;
    @Autowired private QuestionRepository questionBank;
    @Autowired private ObjectMapper objectMapper;

    private record Fixture(Long eventId, Long classId) {}

    private Fixture closedTask(boolean withAnswer) {
        Long classId = newClassFixture().getId();
        return closedTaskIn(classId, withAnswer);
    }

    private TeachingClass newClassFixture() {
        TeachingClass clazz = new TeachingClass();
        clazz.setName("接口班级-" + UUID.randomUUID().toString().substring(0, 8));
        clazz.setGrade("初一");
        clazz.setStatus(TeachingClass.Status.ACTIVE);
        classes.saveAndFlush(clazz);
        classAccess.addTeacher(clazz.getId(), TEACHER_ID, ClassTeacher.Role.OWNER);
        classAccess.ensureStudent(clazz.getId(), STUDENT_ID);
        return clazz;
    }

    private Fixture closedTaskIn(Long classId, boolean withAnswer) {
        Question question = new Question();
        question.setContent("接口夹具-" + UUID.randomUUID());
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A");
        question.setDifficulty(1);
        question = questionBank.saveAndFlush(question);

        TeacherEventCreateRequest request = new TeacherEventCreateRequest();
        request.setEventTitle("API-" + UUID.randomUUID().toString().substring(0, 8));
        request.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        request.setStatus(TeacherEvent.Status.PUBLISHED);
        request.setQuestionIds(List.of(question.getId()));
        request.setClassId(classId);
        Long eventId = events.publishEvent(request, TEACHER_ID).getId();
        if (withAnswer) {
            var answer = new com.zhixuewuli.dto.request.AnswerRecordSubmitRequest();
            answer.setEventId(eventId);
            answer.setQuestionId(question.getId());
            answer.setIntent("ASSIGNMENT");
            answer.setRequestId("api-" + UUID.randomUUID());
            answer.setUserAnswer("A");
            answerService.submitAnswerRecord(answer, STUDENT_ID, "api-session");
        }
        events.closeTask(eventId);
        return new Fixture(eventId, classId);
    }

    @Autowired private com.zhixuewuli.service.answerRecord.AnswerRecordService answerService;

    @Autowired private com.zhixuewuli.service.reportbatch.ReportBatchService batchService;

    @Test
    void eventFilterFindsAnOldBatchPastFiftyNewerBatches() throws Exception {
        var clazz = newClassFixture();
        var oldTask = closedTaskIn(clazz.getId(), false);
        var newerTask = closedTaskIn(clazz.getId(), false);
        var request = new ReportBatchCreateRequest(); request.setEventId(oldTask.eventId());
        request.setIdempotencyKey(UUID.randomUUID().toString());
        Long oldId = batchService.createBatch(TEACHER_ID, request).getId();
        for (int i = 0; i < 51; i++) {
            request.setEventId(newerTask.eventId()); request.setIdempotencyKey(UUID.randomUUID().toString());
            batchService.createBatch(TEACHER_ID, request);
        }
        mockMvc.perform(get("/teacher/report-batches").param("eventId", oldTask.eventId().toString())
                .param("size", "1").header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(oldId));
    }

    private String token(long userId, String username) {
        return createAuthHeader(userId, username);
    }

    private String createBody(Long eventId, String key) throws Exception {
        ReportBatchCreateRequest request = new ReportBatchCreateRequest();
        request.setEventId(eventId);
        request.setIdempotencyKey(key);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("教师创建批次后可查看列表、明细，无作答学生被跳过")
    void teacherCreatesAndQueriesBatch() throws Exception {
        Fixture fixture = closedTask(false);
        String body = createBody(fixture.eventId(), "api-" + UUID.randomUUID());

        String response = mockMvc.perform(post("/teacher/report-batches")
                        .header("Authorization", token(TEACHER_ID, "teacher_user"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.finishedCount").value(1))
                .andReturn().getResponse().getContentAsString();
        long batchId = objectMapper.readTree(response).path("data").path("id").asLong();

        mockMvc.perform(get("/teacher/report-batches")
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value((int) batchId));

        mockMvc.perform(get("/teacher/report-batches/{id}", batchId)
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.eventId").value(fixture.eventId().intValue()));

        String items = mockMvc.perform(get("/teacher/report-batches/{id}/items", batchId)
                        .param("status", "SKIPPED")
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.records[0].skipReason").value("该任务下无作答记录"))
                .andReturn().getResponse().getContentAsString();
        long itemId = objectMapper.readTree(items).path("data").path("records").get(0).path("id").asLong();

        mockMvc.perform(get("/teacher/report-batches/{id}/items/{itemId}/report", batchId, itemId)
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该学生报告尚未生成完成"));

        mockMvc.perform(post("/teacher/report-batches/{id}/items/{itemId}/retry", batchId, itemId)
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅失败状态的子任务可以手动重试"));

        mockMvc.perform(get("/teacher/report-batches/{id}/items", batchId)
                        .param("status", "NOT_A_STATUS")
                        .header("Authorization", token(TEACHER_ID, "teacher_user")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的状态筛选值"));
    }

    @Test
    @DisplayName("同键同参返回原批次，同键不同任务被拒绝")
    void idempotencyKeyIsEnforced() throws Exception {
        Long classId = newClassFixture().getId();
        Fixture first = closedTaskIn(classId, false);
        Fixture second = closedTaskIn(classId, false);
        String key = "same-key-" + UUID.randomUUID();
        String token = token(TEACHER_ID, "teacher_user");

        String body1 = createBody(first.eventId(), key);
        String r1 = mockMvc.perform(post("/teacher/report-batches").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id1 = objectMapper.readTree(r1).path("data").path("id").asLong();

        String r2 = mockMvc.perform(post("/teacher/report-batches").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body1))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(r2).path("data").path("id").asLong())
                .isEqualTo(id1);

        mockMvc.perform(post("/teacher/report-batches").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(second.eventId(), key)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该幂等键已用于不同的任务参数，请更换幂等键"));
    }

    @Test
    @DisplayName("未截止任务拒绝创建")
    void openTaskIsRejected() throws Exception {
        Fixture closed = closedTask(false);
        Question question = new Question();
        question.setContent("开放任务-" + UUID.randomUUID());
        question.setOptions("{\"A\":\"a\",\"B\":\"b\",\"C\":\"c\",\"D\":\"d\"}");
        question.setCorrectOption("A");
        question.setDifficulty(1);
        question = questionBank.saveAndFlush(question);
        TeacherEventCreateRequest open = new TeacherEventCreateRequest();
        open.setEventTitle("OPEN-" + UUID.randomUUID().toString().substring(0, 8));
        open.setEventTimestamp(LocalDateTime.now(ZoneOffset.UTC));
        open.setStatus(TeacherEvent.Status.PUBLISHED);
        open.setQuestionIds(List.of(question.getId()));
        open.setClassId(closed.classId());
        Long openId = events.publishEvent(open, TEACHER_ID).getId();

        mockMvc.perform(post("/teacher/report-batches")
                        .header("Authorization", token(TEACHER_ID, "teacher_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(openId, "open-" + UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("任务尚未截止，截止后才能批量生成报告"));
    }

    @Test
    @DisplayName("学生被角色守卫拒绝，其他教师按班级权限拒绝")
    void accessIsRestrictedByRoleAndClass() throws Exception {
        Fixture fixture = closedTask(false);
        String body = createBody(fixture.eventId(), "access-" + UUID.randomUUID());

        mockMvc.perform(post("/teacher/report-batches")
                        .header("Authorization", token(STUDENT_ID, "student_user"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4801));

        String created = mockMvc.perform(post("/teacher/report-batches")
                        .header("Authorization", token(TEACHER_ID, "teacher_user"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long batchId = objectMapper.readTree(created).path("data").path("id").asLong();

        mockMvc.perform(get("/teacher/report-batches/{id}", batchId)
                        .header("Authorization", token(OTHER_TEACHER_ID, "teacher_no_event")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权访问该班级"));
        mockMvc.perform(get("/teacher/report-batches/{id}/items", batchId)
                        .header("Authorization", token(OTHER_TEACHER_ID, "teacher_no_event")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权访问该班级"));
        mockMvc.perform(get("/teacher/report-batches/{id}/items/1/report", batchId)
                        .header("Authorization", token(OTHER_TEACHER_ID, "teacher_no_event")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("无权访问该班级"));
    }

    @Test
    @DisplayName("未登录访问批量报告接口返回 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/teacher/report-batches"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/teacher/report-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"idempotencyKey\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("请求体缺少必填字段返回 400")
    void invalidRequestIsRejected() throws Exception {
        mockMvc.perform(post("/teacher/report-batches")
                        .header("Authorization", token(TEACHER_ID, "teacher_user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":null,\"idempotencyKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
