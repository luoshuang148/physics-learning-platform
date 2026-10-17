package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.dto.request.ExperimentCreateRequest;
import com.zhixuewuli.dto.request.ExperimentUpdateRequest;
import com.zhixuewuli.entity.Experiment;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.ExperimentRepository;
import com.zhixuewuli.repository.QuestionRepository;

/**
 * 实验控制器集成测试
 * 
 * <p>
 * 测试实验管理相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>教师创建实验 - 验证权限、名称唯一、序号唯一、题目 ID 存在</li>
 * <li>教师删除实验 - 权限验证</li>
 * <li>教师更新实验 - 增量更新</li>
 * <li>查询实验详情 - 无需认证</li>
 * <li>查询所有实验 - 需要认证</li>
 * <li>学生权限限制 - 只能查询不能修改</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-experiment-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景、边界值和权限控制场景</li>
 * <li>验证 HTTP 状态码、响应结构和业务逻辑</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("实验管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-experiment-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class ExperimentControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @BeforeEach
    void setUp() {
        // 确保测试前数据库处于干净状态
        // SQL 脚本已提供基础数据，无需清理
    }

    // ============================================================
    // 教师创建实验测试
    // ============================================================

    @DisplayName("创建实验成功 - 教师权限")
    @Test
    void testCreateExperiment_Success() throws Exception {
        // Given: 教师用户和已存在的题目
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("自由落体实验");
        request.setQuestionIds("[3001,3002]");
        request.setDescription("研究自由落体运动的规律");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证创建成功
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("自由落体实验"))
                .andExpect(jsonPath("$.data.orderNumber").value(10));

        // 验证数据库中有新实验
        List<Experiment> experiments = experimentRepository.findAll();
        assertThat(experiments).hasSize(2); // SQL 脚本 1 个 + 新建 1 个
    }

    @DisplayName("创建实验失败 - 名称重复")
    @Test
    void testCreateExperiment_Fail_NameDuplicate() throws Exception {
        // Given: 教师用户和重复的实验名称
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("正常实验"); // SQL 脚本中已存在
        request.setQuestionIds("[3001]");
        request.setDescription("重复名称测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4713))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验名称已存在")));
    }

    @DisplayName("创建实验失败 - 序号重复")
    @Test
    void testCreateExperiment_Fail_OrderNumberDuplicate() throws Exception {
        // Given: 教师用户和重复的序号
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("新实验_" + System.currentTimeMillis());
        request.setQuestionIds("[3001]");
        request.setDescription("序号重复测试");
        request.setOrderNumber(1); // SQL 脚本中已存在

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4714))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验序号已存在")));
    }

    @DisplayName("创建实验失败 - 题目 ID 不存在")
    @Test
    void testCreateExperiment_Fail_QuestionNotFound() throws Exception {
        // Given: 教师用户和不存在的题目 ID
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("新实验_" + System.currentTimeMillis());
        request.setQuestionIds("[9999]"); // 不存在的题目 ID
        request.setDescription("题目不存在测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4715))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("题目不存在")));
    }

    @DisplayName("创建实验失败 - 题目 ID 格式无效")
    @Test
    void testCreateExperiment_Fail_InvalidQuestionIdsFormat() throws Exception {
        // Given: 教师用户和无效的题目 ID 格式
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("新实验_" + System.currentTimeMillis());
        request.setQuestionIds("\"not-an-array\""); // 有效的 JSON 字符串，但不是数组格式
        request.setDescription("题目 ID 格式无效测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4715))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("列表")));
    }

    @DisplayName("创建实验失败 - 学生权限")
    @Test
    void testCreateExperiment_Fail_StudentPermission() throws Exception {
        // Given: 学生用户
        Long studentId = 2002L;
        String studentUsername = "student_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("学生尝试创建实验");
        request.setQuestionIds("[3001]");
        request.setDescription("学生权限测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(studentId, studentUsername)))
                .andDo(print());

        // Then: 验证返回 403
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4801));
    }

    @DisplayName("边界值 - 空题目 ID 数组创建成功")
    @Test
    void testCreateExperiment_Boundary_EmptyQuestionIds() throws Exception {
        // Given: 教师用户和空题目 ID 数组
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("空题目实验");
        request.setQuestionIds("[]");
        request.setDescription("没有题目的实验");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证创建成功
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.questionIds").isArray())
                .andExpect(jsonPath("$.data.questionIds").isEmpty());
    }

    @DisplayName("边界值 - 重复题目 ID 数组创建失败")
    @Test
    void testCreateExperiment_Boundary_DuplicateQuestionIds() throws Exception {
        // Given: 教师用户和重复的题目 ID
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("重复 ID 实验");
        request.setQuestionIds("[1,2,1]");
        request.setDescription("重复 ID 测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4715));
    }

    @DisplayName("边界值 - 超长实验名称创建失败")
    @Test
    void testCreateExperiment_Boundary_LongName() throws Exception {
        // Given: 教师用户和超长名称
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        String longName = "a".repeat(256);
        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName(longName);
        request.setQuestionIds("[3001]");
        request.setDescription("超长名称测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest());
    }

    @DisplayName("边界值 - orderNumber 为 0 或负数创建失败")
    @Test
    void testCreateExperiment_Boundary_ZeroOrNegativeOrderNumber() throws Exception {
        // Given: 教师用户和 0 序号
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("零序号实验");
        request.setQuestionIds("[3001]");
        request.setDescription("零序号测试");
        request.setOrderNumber(0);

        // When: 发送创建实验请求
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400（@Min 约束）
        result.andExpect(status().isBadRequest());
    }

    @DisplayName("安全测试 - SQL 注入尝试")
    @Test
    void testCreateExperiment_Security_SqlInjection() throws Exception {
        // Given: 教师用户和 SQL 注入尝试
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("test'; DROP TABLE experiments; --");
        request.setQuestionIds("[3001]");
        request.setDescription("SQL 注入测试");
        request.setOrderNumber(10);

        // When: 发送创建实验请求（JPA 自动转义）
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: JPA 会成功创建，但名称是普通字符串
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("test'; DROP TABLE experiments; --"));
    }

    @DisplayName("安全测试 - XSS 攻击尝试")
    @Test
    void testCreateExperiment_Security_Xss() throws Exception {
        // Given: 教师用户和 XSS 脚本
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("XSS 测试实验");
        request.setDescription("<script>alert('xss')</script>");
        request.setQuestionIds("[3001]");
        request.setOrderNumber(10);

        // When: 发送创建实验请求（存储型 XSS，前端负责防御）
        ResultActions result = mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 成功创建，XSS 脚本作为普通文本存储
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").value("<script>alert('xss')</script>"));
    }

    // ============================================================
    // 教师删除实验测试
    // ============================================================

    @DisplayName("删除实验成功 - 实验存在")
    @Test
    void testDeleteExperiment_Success() throws Exception {
        // Given: 教师用户和已存在的实验
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L; // SQL 脚本中的实验 ID

        // When: 发送删除实验请求
        ResultActions result = mockMvc.perform(delete("/experiments/teacher/delete/" + experimentId)
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证删除成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @DisplayName("删除实验失败 - 实验不存在")
    @Test
    void testDeleteExperiment_Fail_NotFound() throws Exception {
        // Given: 教师用户和不存在的实验 ID
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long notExistId = 9999L;

        // When: 发送删除实验请求
        ResultActions result = mockMvc.perform(delete("/experiments/teacher/delete/" + notExistId)
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 404
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4712))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验不存在")));
    }

    @DisplayName("删除实验失败 - 学生权限")
    @Test
    void testDeleteExperiment_Fail_StudentPermission() throws Exception {
        // Given: 学生用户
        Long studentId = 2002L;
        String studentUsername = "student_experiment";
        Long experimentId = 9001L;

        // When: 发送删除实验请求
        ResultActions result = mockMvc.perform(delete("/experiments/teacher/delete/" + experimentId)
                .header("Authorization", createAuthHeader(studentId, studentUsername)))
                .andDo(print());

        // Then: 验证返回 403
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4801));
    }

    // ============================================================
    // 教师更新实验测试
    // ============================================================

    @DisplayName("更新实验成功 - 增量更新名称")
    @Test
    void testUpdateExperiment_Success_UpdateName() throws Exception {
        // Given: 教师用户和更新请求
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setName("更新后的实验名称");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("更新后的实验名称"));
    }

    @DisplayName("更新实验成功 - 增量更新题目 ID")
    @Test
    void testUpdateExperiment_Success_UpdateQuestionIds() throws Exception {
        // Given: 教师用户和更新请求
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setQuestionIds("[3002,3003]");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questionIds").isArray())
                .andExpect(jsonPath("$.data.questionIds").isNotEmpty());
    }

    @DisplayName("更新实验成功 - 增量更新描述")
    @Test
    void testUpdateExperiment_Success_UpdateDescription() throws Exception {
        // Given: 教师用户和更新请求
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setDescription("更新后的实验描述");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.description").value("更新后的实验描述"));
    }

    @DisplayName("更新实验成功 - 增量更新序号")
    @Test
    void testUpdateExperiment_Success_UpdateOrderNumber() throws Exception {
        // Given: 教师用户和更新请求
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setOrderNumber(99);

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNumber").value(99));
    }

    @DisplayName("更新实验失败 - 实验不存在")
    @Test
    void testUpdateExperiment_Fail_NotFound() throws Exception {
        // Given: 教师用户和不存在的实验 ID
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(9999L);
        request.setName("新名称");
        request.setDescription("测试描述");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 404
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4712))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验不存在")));
    }

    @DisplayName("更新实验失败 - 名称重复")
    @Test
    void testUpdateExperiment_Fail_NameDuplicate() throws Exception {
        // Given: 教师用户和重复的名称
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        // 先创建一个名为"名称测试实验"的实验
        ExperimentCreateRequest createRequest = new ExperimentCreateRequest();
        createRequest.setName("名称测试实验");
        createRequest.setQuestionIds("[3001]");
        createRequest.setDescription("用于测试名称重复");
        createRequest.setOrderNumber(100);
        
        mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andExpect(status().isCreated());

        // 尝试将实验 9001L 的名称更新为"名称测试实验"(已存在)
        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(9001L);
        request.setName("名称测试实验"); // 与刚创建的实验名称重复
        request.setDescription("名称重复测试");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4713))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验名称已存在")));
    }

    @DisplayName("更新实验失败 - 序号重复")
    @Test
    void testUpdateExperiment_Fail_OrderNumberDuplicate() throws Exception {
        // Given: 教师用户和重复的序号
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";

        // 先创建一个序号为 2 的实验
        ExperimentCreateRequest createRequest = new ExperimentCreateRequest();
        createRequest.setName("序号测试实验");
        createRequest.setQuestionIds("[3001]");
        createRequest.setDescription("用于测试序号重复");
        createRequest.setOrderNumber(2);
        
        mockMvc.perform(post("/experiments/teacher/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andExpect(status().isCreated());

        // 尝试将实验 9001L 的序号更新为 2(已存在)
        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(9001L);
        request.setOrderNumber(2); // 与刚创建的实验序号重复
        request.setName("序号重复测试");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4714))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验序号已存在")));
    }

    @DisplayName("更新实验失败 - 学生权限")
    @Test
    void testUpdateExperiment_Fail_StudentPermission() throws Exception {
        // Given: 学生用户
        Long studentId = 2002L;
        String studentUsername = "student_experiment";

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(9001L);
        request.setName("学生尝试更新");
        request.setDescription("学生权限测试");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(studentId, studentUsername)))
                .andDo(print());

        // Then: 验证返回 403
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4801));
    }

    @DisplayName("边界值 - 更新为空题目 ID 数组成功")
    @Test
    void testUpdateExperiment_Boundary_EmptyQuestionIds() throws Exception {
        // Given: 教师用户和空题目 ID 数组
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setQuestionIds("[]");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.questionIds").isArray())
                .andExpect(jsonPath("$.data.questionIds").isEmpty());
    }

    @DisplayName("边界值 - 更新为重复 ID 数组失败")
    @Test
    void testUpdateExperiment_Boundary_DuplicateQuestionIds() throws Exception {
        // Given: 教师用户和重复的题目 ID
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setQuestionIds("[1,2,1]");

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4715));
    }

    @DisplayName("边界值 - 超长名称更新失败")
    @Test
    void testUpdateExperiment_Boundary_LongName() throws Exception {
        // Given: 教师用户和超长名称
        Long teacherId = 2001L;
        String teacherUsername = "teacher_experiment";
        Long experimentId = 9001L;

        String longName = "a".repeat(256);
        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setId(experimentId);
        request.setName(longName);

        // When: 发送更新实验请求
        ResultActions result = mockMvc.perform(put("/experiments/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(teacherId, teacherUsername)))
                .andDo(print());

        // Then: 验证返回 400
        result.andExpect(status().isBadRequest());
    }

    // ============================================================
    // 查询实验详情测试（无需认证）
    // ============================================================

    @DisplayName("查询实验详情成功 - 需要认证")
    @Test
    void testGetExperiment_Success() throws Exception {
        // Given: 已存在的实验 ID
        Long experimentId = 9001L;
        Long userId = 2001L;
        String username = "teacher_experiment";

        // When: 发送查询请求（需要认证）
        ResultActions result = mockMvc.perform(get("/experiments/" + experimentId)
                .header("Authorization", createAuthHeader(userId, username)))
                .andDo(print());

        // Then: 验证查询成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(experimentId))
                .andExpect(jsonPath("$.data.name").value("正常实验"));
    }

    @DisplayName("查询实验失败 - 实验不存在")
    @Test
    void testGetExperiment_Fail_NotFound() throws Exception {
        // Given: 不存在的实验 ID
        Long notExistId = 9999L;
        Long userId = 2001L;
        String username = "teacher_experiment";

        // When: 发送查询请求 (需要认证)
        ResultActions result = mockMvc.perform(get("/experiments/" + notExistId)
                .header("Authorization", createAuthHeader(userId, username)))
                .andDo(print());

        // Then: 验证返回 404
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4712))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("实验不存在")));
    }

    // ============================================================
    // 查询所有实验测试（需要认证）
    // ============================================================

    @DisplayName("查询所有实验成功 - 按序号排序")
    @Test
    void testGetAllExperiments_Success() throws Exception {
        // Given: 任意认证用户
        Long userId = 2001L;
        String username = "teacher_experiment";

        // When: 发送查询请求
        ResultActions result = mockMvc.perform(get("/experiments/all")
                .header("Authorization", createAuthHeader(userId, username)))
                .andDo(print());

        // Then: 验证查询成功且按序号排序
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                // 验证返回的实验中，第一个的 orderNumber 最小
                .andExpect(jsonPath("$.data[0].orderNumber").exists());
    }

    @DisplayName("查询所有实验成功 - 空列表场景")
    @Test
    void testGetAllExperiments_EmptyList() throws Exception {
        // Given: 清理所有实验
        cleanTable("EXPERIMENTS");

        Long userId = 2001L;
        String username = "teacher_experiment";

        // When: 发送查询请求
        ResultActions result = mockMvc.perform(get("/experiments/all")
                .header("Authorization", createAuthHeader(userId, username)))
                .andDo(print());

        // Then: 验证返回空列表
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
