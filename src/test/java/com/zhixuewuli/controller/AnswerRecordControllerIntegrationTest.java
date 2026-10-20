package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.UserRepository;

/**
 * 答题记录管理集成测试
 * 
 * <p>
 * 测试答题记录相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>学生功能 - 提交答题记录、查询个人记录、查询错题</li>
 * <li>权限控制查询 - 按用户 ID 查询、权限边界验证</li>
 * <li>教师管理功能 - 删除记录、批量删除、统计功能</li>
 * <li>会话维度查询 - 按会话查询、最近会话查询</li>
 * <li>数据验证 - 参数验证、业务规则验证</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-answer-record-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景和权限控制场景</li>
 * <li>验证 HTTP 状态码、响应结构和业务逻辑</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("答题记录管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-answer-record-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class AnswerRecordControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnswerRecordRepository answerRecordRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    // 测试常量定义
    private static final Long STUDENT_ID = 1003L;
    private static final String STUDENT_USERNAME = "student_answer_test";
    private static final Long OTHER_STUDENT_ID = 1004L;
    private static final String OTHER_STUDENT_USERNAME = "student_other_test";
    private static final Long TEACHER_ID = 2003L;
    private static final String TEACHER_USERNAME = "teacher_answer_test";
    private static final Long OTHER_TEACHER_ID = 2004L;
    private static final String OTHER_TEACHER_USERNAME = "teacher_another_test";
    private static final Long NON_EXISTENT_USER_ID = 99999L;
    private static final String NON_EXISTENT_RECORD_ID = "non_existent_record";
    private static final String SESSION_001 = "session_001";
    private static final String SESSION_002 = "session_002";
    private static final String SESSION_003 = "session_003";

    // ============================================================
    // 学生功能测试模块（提交答题记录）
    // ============================================================

    @DisplayName("提交答题记录成功 - 正确答案")
    @Test
    void testSubmitAnswer_Success_Correct() throws Exception {
        // Given: 学生用户和正确答案
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(4001L);
        request.setUserAnswer("B"); // 正确答案

        // When: 发送提交请求
        ResultActions result = mockMvc.perform(post("/answer-records/student/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证提交成功
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.recordId").exists())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.userAnswer").value("B"));

        // 验证数据库中有新记录（通过 Repository 查询）
        List<AnswerRecord> userRecords = answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(STUDENT_ID);
        assertThat(userRecords.size()).isEqualTo(7); // SQL 脚本 6 个 + 新建 1 个
    }

    @DisplayName("提交答题记录成功 - 错误答案")
    @Test
    void testSubmitAnswer_Success_Wrong() throws Exception {
        // Given: 学生用户和错误答案
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(4002L);
        request.setUserAnswer("A"); // 错误答案

        // When: 发送提交请求
        ResultActions result = mockMvc.perform(post("/answer-records/student/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证提交成功
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.recordId").exists())
                .andExpect(jsonPath("$.data.correct").value(false));
    }

    @DisplayName("提交答题记录失败 - 题目不存在")
    @Test
    void testSubmitAnswer_NotFound_QuestionNotExist() throws Exception {
        // Given: 不存在的题目ID
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(NON_EXISTENT_USER_ID);
        request.setUserAnswer("A");

        // When: 发送提交请求
        ResultActions result = mockMvc.perform(post("/answer-records/student/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证题目不存在
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("提交答题记录失败 - 答案为空")
    @Test
    void testSubmitAnswer_BadRequest_EmptyAnswer() throws Exception {
        // Given: 空答案
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(4001L);
        request.setUserAnswer("");

        // When: 发送提交请求
        ResultActions result = mockMvc.perform(post("/answer-records/student/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("提交答题记录失败 - 未认证访问")
    @Test
    void testSubmitAnswer_Unauthorized() throws Exception {
        // Given: 无认证信息
        AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
        request.setQuestionId(4001L);
        request.setUserAnswer("B");

        // When: 发送提交请求（无认证头）
        ResultActions result = mockMvc.perform(post("/answer-records/student/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print());

        // Then: 验证未认证错误
        result.andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 学生功能测试模块（查询个人记录）
    // ============================================================

    @DisplayName("查询我的答题记录成功 - 返回所有记录")
    @Test
    void testGetMyAnswerRecords_Success() throws Exception {
        // When: 查询当前用户的所有答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/student/my")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 6 条记录（SQL 脚本中该学生的记录）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(6))
                .andExpect(jsonPath("$.data.totalCount").value(6));
    }

    @DisplayName("查询我的答题记录成功 - 时间倒序排列")
    @Test
    void testGetMyAnswerRecords_Success_TimeOrder() throws Exception {
        // When: 查询当前用户的所有答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/student/my")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证按时间倒序排列（最新的在前）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].answeredAt")
                        .value(org.hamcrest.Matchers.containsString("2026-01-11")))
                .andExpect(jsonPath("$.data.records[5].answeredAt")
                        .value(org.hamcrest.Matchers.containsString("2026-01-10")));
    }

    @DisplayName("查询我的错题记录成功 - 只返回错误记录")
    @Test
    void testGetMyWrongAnswers_Success() throws Exception {
        // When: 查询当前用户的错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/student/my/wrong")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证只返回错误记录（3 条）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.records[*].correct")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));
    }

    @DisplayName("查询我的错题记录成功 - 返回数组")
    @Test
    void testGetMyWrongAnswers_ReturnsArray() throws Exception {
        // When: 查询当前用户的错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/student/my/wrong")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @DisplayName("查询我的错题记录成功 - 无错题时返回空列表")
    @Test
    void testGetMyWrongAnswers_Success_NoWrongAnswers() throws Exception {
        // Given: 使用另一个学生（1004 只有 1 条错题）
        // When: 查询错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/student/my/wrong")
                .header("Authorization", createAuthHeader(OTHER_STUDENT_ID, OTHER_STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证返回 1 条错题
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1));
    }

    // ============================================================
    // 权限控制查询测试模块
    // ============================================================

    @DisplayName("查询用户答题记录成功 - 教师查询学生记录")
    @Test
    void testGetUserAnswerRecords_Success_TeacherQueryStudent() throws Exception {
        // When: 教师查询学生的答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/{userId}", STUDENT_ID)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(6));
    }

    @DisplayName("查询用户答题记录成功 - 学生查询自己记录")
    @Test
    void testGetUserAnswerRecords_Success_StudentQuerySelf() throws Exception {
        // When: 学生查询自己的答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/{userId}", STUDENT_ID)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(6));
    }

    @DisplayName("查询用户答题记录失败 - 学生查询其他学生记录（403）")
    @Test
    void testGetUserAnswerRecords_Forbidden_StudentQueryOtherStudent() throws Exception {
        // When: 学生查询其他学生的答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/{userId}", OTHER_STUDENT_ID)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("查询用户答题记录失败 - 用户不存在")
    @Test
    void testGetUserAnswerRecords_NotFound_UserNotExist() throws Exception {
        // When: 查询不存在用户的答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/{userId}", NON_EXISTENT_USER_ID)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证用户不存在
        result.andExpect(status().isNotFound());
    }

    @DisplayName("查询用户错题记录成功 - 教师查询学生错题")
    @Test
    void testGetUserWrongAnswers_Success_TeacherQueryStudent() throws Exception {
        // When: 教师查询学生的错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/wrong/{userId}", STUDENT_ID)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 3 条错题
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @DisplayName("查询用户错题记录失败 - 学生查询其他学生错题（403）")
    @Test
    void testGetUserWrongAnswers_Forbidden_StudentQueryOtherStudent() throws Exception {
        // When: 学生查询其他学生的错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/user/wrong/{userId}", OTHER_STUDENT_ID)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    // ============================================================
    // 教师管理功能测试模块（删除操作）
    // ============================================================

    @DisplayName("删除答题记录成功 - 教师权限")
    @Test
    void testDeleteAnswerRecord_Success_TeacherPermission() throws Exception {
        // Given: 已存在的答题记录 ID
        String recordId = "rec_stu1003_001";

        // When: 删除答题记录
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/{recordId}", recordId)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证删除成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 强制刷新 JPA 更改到数据库
        answerRecordRepository.flush();

        // 验证数据库中记录已删除
        assertThat(answerRecordRepository.findById(recordId).isEmpty()).isTrue();
        Long count = queryForObject("SELECT COUNT(*) FROM answer_records", Long.class);
        assertThat(count).isEqualTo(8L); // SQL 脚本 9 个 - 删除 1 个
    }

    @DisplayName("删除答题记录失败 - 学生权限（403）")
    @Test
    void testDeleteAnswerRecord_Forbidden_StudentPermission() throws Exception {
        // Given: 学生用户尝试删除记录
        String recordId = "rec_stu1003_001";

        // When: 学生尝试删除
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/{recordId}", recordId)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("删除答题记录失败 - 记录不存在")
    @Test
    void testDeleteAnswerRecord_NotFound_RecordNotExist() throws Exception {
        // Given: 不存在的记录 ID
        String nonExistentRecordId = NON_EXISTENT_RECORD_ID;

        // When: 删除不存在的记录
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/{recordId}", nonExistentRecordId)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证记录不存在
        result.andExpect(status().isNotFound());
    }

    @DisplayName("批量删除答题记录成功 - 按时间范围")
    @Test
    void testBatchDeleteAnswerRecord_Success_ByTimeRange() throws Exception {
        // Given: 指定时间范围（2026-01-10 的记录）
        RecordBatchDeleteRequest request = new RecordBatchDeleteRequest();
        request.setStartTime(LocalDateTime.of(2026, 1, 10, 0, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 1, 10, 23, 59, 59));

        // When: 批量删除
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证删除成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 强制刷新 JPA 更改到数据库
        answerRecordRepository.flush();

        // 验证数据库：应该删除 session_001 的 3 条记录
        Long count = queryForObject("SELECT COUNT(*) FROM answer_records", Long.class);
        assertThat(count).isEqualTo(6L); // SQL 脚本 9 个 - 删除 3 个
    }

    @DisplayName("批量删除答题记录失败 - 时间范围非法（结束时间早于开始时间）")
    @Test
    void testBatchDeleteAnswerRecord_BadRequest_InvalidTimeRange() throws Exception {
        // Given: 非法的时间范围
        RecordBatchDeleteRequest request = new RecordBatchDeleteRequest();
        request.setStartTime(LocalDateTime.of(2026, 1, 11, 0, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 1, 10, 0, 0, 0)); // 早于开始时间

        // When: 批量删除
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("批量删除答题记录失败 - 学生权限（403）")
    @Test
    void testBatchDeleteAnswerRecord_Forbidden_StudentPermission() throws Exception {
        // Given: 学生用户尝试批量删除
        RecordBatchDeleteRequest request = new RecordBatchDeleteRequest();
        request.setStartTime(LocalDateTime.of(2026, 1, 10, 0, 0, 0));
        request.setEndTime(LocalDateTime.of(2026, 1, 10, 23, 59, 59));

        // When: 学生尝试批量删除
        ResultActions result = mockMvc.perform(delete("/answer-records/teacher/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    // ============================================================
    // 教师统计功能测试模块
    // ============================================================

    @DisplayName("统计系统总答题记录数成功 - 教师权限")
    @Test
    void testCountTotalAnswers_Success_TeacherPermission() throws Exception {
        // When: 统计总记录数
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/count")
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证统计准确
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(9L)); // SQL 脚本 9 条记录
    }

    @DisplayName("统计系统总答题记录数失败 - 学生权限（403）")
    @Test
    void testCountTotalAnswers_Forbidden_StudentPermission() throws Exception {
        // When: 学生尝试统计
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/count")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("获取所有用户答题记录成功 - 教师权限")
    @Test
    void testGetAllUsersAnswerRecords_Success_TeacherPermission() throws Exception {
        // When: 获取所有用户答题记录
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/all")
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证获取成功，返回 9 条记录
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(9))
                .andExpect(jsonPath("$.data.totalCount").value(9));
    }

    @DisplayName("获取所有用户答题记录失败 - 学生权限（403）")
    @Test
    void testGetAllUsersAnswerRecords_Forbidden_StudentPermission() throws Exception {
        // When: 学生尝试获取所有用户记录
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/all")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("获取所有用户错题记录成功 - 教师权限")
    @Test
    void testGetAllUsersWrongAnswers_Success_TeacherPermission() throws Exception {
        // When: 获取所有用户错题记录
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/wrong/all")
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证获取成功，返回 4 条错题（学生 1003 有 3 条，学生 1004 有 1 条）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(4));
    }

    // ============================================================
    // 会话维度查询测试模块
    // ============================================================

    @DisplayName("获取会话答题记录成功 - 学生查询自己会话")
    @Test
    void testGetSessionAnswerRecords_Success_StudentQuerySelfSession() throws Exception {
        // When: 学生查询自己的会话记录
        ResultActions result = mockMvc.perform(get("/answer-records/session/{sessionId}", SESSION_001)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 3 条记录
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @DisplayName("获取会话答题记录成功 - 教师查询任意会话")
    @Test
    void testGetSessionAnswerRecords_Success_TeacherQueryAnySession() throws Exception {
        // When: 教师查询会话记录
        ResultActions result = mockMvc.perform(get("/answer-records/session/{sessionId}", SESSION_003)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 3 条记录（学生 1004 的记录）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3));
    }

    @DisplayName("获取会话答题记录失败 - 学生查询他人会话（403）")
    @Test
    void testGetSessionAnswerRecords_Forbidden_StudentQueryOtherSession() throws Exception {
        // When: 学生查询其他学生的会话记录
        ResultActions result = mockMvc.perform(get("/answer-records/session/{sessionId}", SESSION_003)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("获取学生最近会话记录成功 - 教师权限")
    @Test
    void testGetStudentLatestSessionRecords_Success_TeacherPermission() throws Exception {
        // When: 教师查询学生的最近会话记录
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/{studentId}/latest-session", STUDENT_ID)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 session_002 的 3 条记录（最近的会话）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.records[0].sessionId").value(SESSION_002));
    }

    @DisplayName("获取学生最近会话记录失败 - 学生不存在")
    @Test
    void testGetStudentLatestSessionRecords_NotFound_StudentNotExist() throws Exception {
        // When: 查询不存在学生的最近会话
        ResultActions result = mockMvc
                .perform(get("/answer-records/teacher/{studentId}/latest-session", NON_EXISTENT_USER_ID)
                        .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证学生不存在
        result.andExpect(status().isNotFound());
    }

    @DisplayName("根据姓名获取学生最近会话记录成功 - 精确匹配")
    @Test
    void testGetStudentLatestSessionRecordsByName_Success_ExactMatch() throws Exception {
        // When: 教师根据姓名查询学生最近会话
        ResultActions result = mockMvc
                .perform(get("/answer-records/teacher/realname/{studentName}/latest-session", "答题测试学生")
                        .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功，返回 session_002 的 3 条记录
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.records[0].sessionId").value(SESSION_002));
    }

    @DisplayName("根据姓名获取学生最近会话记录失败 - 姓名不存在")
    @Test
    void testGetStudentLatestSessionRecordsByName_NotFound_NameNotExist() throws Exception {
        // When: 查询不存在姓名的学生
        ResultActions result = mockMvc
                .perform(get("/answer-records/teacher/realname/{studentName}/latest-session", "不存在的姓名")
                        .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证学生不存在
        result.andExpect(status().isNotFound());
    }

    // ============================================================
    // 未认证访问测试模块
    // ============================================================

    @DisplayName("未认证访问 - 查询我的答题记录失败")
    @Test
    void testGetMyAnswerRecords_Unauthorized() throws Exception {
        // When: 未认证访问
        ResultActions result = mockMvc.perform(get("/answer-records/student/my"))
                .andDo(print());

        // Then: 验证未认证错误
        result.andExpect(status().isUnauthorized());
    }

    @DisplayName("未认证访问 - 教师端点查询失败")
    @Test
    void testTeacherEndpoints_Unauthorized() throws Exception {
        // When: 未认证访问教师端点
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/count"))
                .andDo(print());

        // Then: 验证未认证错误
        result.andExpect(status().isUnauthorized());
    }

    @DisplayName("未认证访问 - 会话查询失败")
    @Test
    void testGetSessionAnswerRecords_Unauthorized() throws Exception {
        // When: 未认证访问会话查询
        ResultActions result = mockMvc.perform(get("/answer-records/session/{sessionId}", SESSION_001))
                .andDo(print());

        // Then: 验证未认证错误
        result.andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 教师统计功能测试模块（答题统计）
    // ============================================================

    @DisplayName("获取答题统计数据成功 - 教师权限")
    @Test
    void testGetAnswerStats_Success_TeacherPermission() throws Exception {
        // When: 获取答题统计数据
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/stats")
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证统计准确（SQL脚本中有2个学生有答题记录，学生总数为2）
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answeredStudentCount").value(2L))
                .andExpect(jsonPath("$.data.totalStudentCount").value(2L))
                .andExpect(jsonPath("$.data.coverageRate").value(100.0));
    }

    @DisplayName("获取答题统计数据成功 - 包含所有统计字段")
    @Test
    void testGetAnswerStats_Success_AllFields() throws Exception {
        // When: 获取答题统计数据
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/stats")
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 验证所有字段都存在
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answeredStudentCount").exists())
                .andExpect(jsonPath("$.data.totalStudentCount").exists())
                .andExpect(jsonPath("$.data.coverageRate").exists());
    }

    @DisplayName("获取答题统计数据失败 - 学生权限（403）")
    @Test
    void testGetAnswerStats_Forbidden_StudentPermission() throws Exception {
        // When: 学生尝试获取答题统计
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/stats")
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("获取答题统计数据失败 - 未认证访问")
    @Test
    void testGetAnswerStats_Unauthorized() throws Exception {
        // When: 未认证访问
        ResultActions result = mockMvc.perform(get("/answer-records/teacher/stats"))
                .andDo(print());

        // Then: 验证未认证错误
        result.andExpect(status().isUnauthorized());
    }
}
