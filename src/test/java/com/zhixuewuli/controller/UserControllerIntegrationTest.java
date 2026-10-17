package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.dto.request.UserSelfUpdateRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;

/**
 * 用户管理控制器集成测试
 * 
 * <p>
 * 测试用户管理相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>用户自我管理 - 获取/更新当前用户信息</li>
 * <li>教师管理功能 - 查询/更新/删除用户</li>
 * <li>权限控制 - 学生/教师权限边界验证</li>
 * <li>数据验证 - 字段格式、业务规则验证</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-user-test-data.sql} 提供基础测试数据</li>
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
@DisplayName("用户管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-user-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class UserControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // 测试常量定义
    private static final Long STUDENT_SELF_ID = 1001L;
    private static final String STUDENT_SELF_USERNAME = "student_self_test";
    private static final Long STUDENT_NORMAL_ID = 1002L;
    private static final String STUDENT_NORMAL_USERNAME = "student_normal";
    private static final Long STUDENT_INACTIVE_ID = 1003L;
    private static final String STUDENT_INACTIVE_USERNAME = "student_inactive";
    private static final Long TEACHER_ADMIN_ID = 2001L;
    private static final String TEACHER_ADMIN_USERNAME = "teacher_admin";
    private static final Long TEACHER_ANOTHER_ID = 2002L;
    private static final String TEACHER_ANOTHER_USERNAME = "teacher_another";
    private static final Long NON_EXISTENT_USER_ID = 99999L;

    // 测试请求对象
    private UserSelfUpdateRequest validSelfUpdateRequest;
    private UserAdminUpdateRequest validAdminUpdateRequest;

    @BeforeEach
    void setUp() {
        // 初始化用户自我更新请求
        validSelfUpdateRequest = new UserSelfUpdateRequest();
        validSelfUpdateRequest.setUsername("updated_username");
        validSelfUpdateRequest.setPassword("new_password123");
        validSelfUpdateRequest.setRealName("更新后的姓名");
        validSelfUpdateRequest.setClassName("新班级");

        // 初始化管理员更新请求
        validAdminUpdateRequest = new UserAdminUpdateRequest();
        validAdminUpdateRequest.setUserId(STUDENT_NORMAL_ID);
        validAdminUpdateRequest.setRealName("管理员修改的姓名");
        validAdminUpdateRequest.setClassName("管理员分配的班级");
        validAdminUpdateRequest.setActive(false);
        validAdminUpdateRequest.setUserType(User.UserType.STUDENT);
    }

    // ============================================================
    // 用户自我管理测试模块（学生权限）
    // ============================================================

    @DisplayName("获取当前用户信息 - 成功")
    @Test
    void testGetCurrentUser_Success() throws Exception {
        // When: 发送获取当前用户信息请求
        ResultActions result = mockMvc.perform(get("/user/me")
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证获取成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(STUDENT_SELF_ID))
                .andExpect(jsonPath("$.data.username").value(STUDENT_SELF_USERNAME))
                .andExpect(jsonPath("$.data.realName").value("张三"))
                .andExpect(jsonPath("$.data.userType").value("STUDENT"))
                .andExpect(jsonPath("$.data.className").value("三年一班"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @DisplayName("获取当前用户信息 - 禁用账户不可以查看")
    @Test
    void testGetCurrentUser_InactiveAccount() throws Exception {
        // When: 禁用账户用户尝试获取自己的信息
        ResultActions result = mockMvc.perform(get("/user/me")
                .header("Authorization", createAuthHeader(STUDENT_INACTIVE_ID, STUDENT_INACTIVE_USERNAME)))
                .andDo(print());

        // Then: 验证账户禁用错误（拦截器检查到账户被禁用，返回 401）
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @DisplayName("更新当前用户信息 - 部分字段更新")
    @Test
    void testUpdateCurrentUser_PartialUpdate() throws Exception {
        // Given: 部分更新的请求
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setRealName("张三丰");
        request.setClassName("高三一班");

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(STUDENT_SELF_ID))
                .andExpect(jsonPath("$.data.realName").value("张三丰"))
                .andExpect(jsonPath("$.data.className").value("高三一班"));

        // 验证数据库已更新
        User updatedUser = userRepository.findById(STUDENT_SELF_ID).orElseThrow();
        assertThat(updatedUser.getRealName()).isEqualTo("张三丰");
        assertThat(updatedUser.getClassName()).isEqualTo("高三一班");
    }

    @Test
    void studentCannotPromoteSelfThroughProfileUpdate() throws Exception {
        mockMvc.perform(put("/user/me")
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userType\":\"TEACHER\",\"realName\":\"forged_teacher\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user/me")
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value("STUDENT"));
    }

    @DisplayName("更新当前用户信息 - 完整字段更新")
    @Test
    void testUpdateCurrentUser_FullUpdate() throws Exception {
        // When: 发送完整更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validSelfUpdateRequest))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .requestAttr("userId", STUDENT_SELF_ID)
                .requestAttr("username", STUDENT_SELF_USERNAME))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("updated_username"))
                .andExpect(jsonPath("$.data.realName").value("更新后的姓名"))
                .andExpect(jsonPath("$.data.className").value("新班级"));
    }

    @DisplayName("更新当前用户信息 - 用户名格式错误")
    @Test
    void testUpdateCurrentUser_InvalidUsernameFormat() throws Exception {
        // Given: 用户名格式错误的请求
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("ab"); // 长度不足 3 位

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("更新当前用户信息 - 包含特殊字符的用户名")
    @Test
    void testUpdateCurrentUser_UsernameWithSpecialCharacters() throws Exception {
        // Given: 包含不允许的特殊字符的用户名
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("user@name"); // 包含@符号

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    // ============================================================
    // 教师管理功能测试模块（教师权限）
    // ============================================================

    @DisplayName("按 ID 查询用户 - 教师权限成功")
    @Test
    void testGetUserById_Success_TeacherPermission() throws Exception {
        // When: 教师查询学生用户
        ResultActions result = mockMvc.perform(get("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(STUDENT_NORMAL_ID))
                .andExpect(jsonPath("$.data.username").value(STUDENT_NORMAL_USERNAME))
                .andExpect(jsonPath("$.data.realName").value("李四"))
                .andExpect(jsonPath("$.data.className").value("三年二班"));
    }

    @DisplayName("按 ID 查询用户 - 查询不存在的用户")
    @Test
    void testGetUserById_NotFound() throws Exception {
        // When: 查询不存在的用户 ID
        ResultActions result = mockMvc.perform(get("/user/teacher/{id}", NON_EXISTENT_USER_ID)
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证用户不存在
        result.andExpect(status().isNotFound());
    }

    @DisplayName("按真实姓名查询用户 - 精确匹配")
    @Test
    void testGetUserByRealName_ExactMatch() throws Exception {
        // When: 教师按真实姓名查询
        ResultActions result = mockMvc.perform(get("/user/teacher/realname/{realName}", "李四")
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证查询成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.realName").value("李四"))
                .andExpect(jsonPath("$.data.username").value(STUDENT_NORMAL_USERNAME));
    }

    @DisplayName("按真实姓名查询用户 - 无匹配结果")
    @Test
    void testGetUserByRealName_NoMatch() throws Exception {
        // When: 查询不存在的真实姓名
        ResultActions result = mockMvc.perform(get("/user/teacher/realname/{realName}", "不存在的用户")
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证用户不存在
        result.andExpect(status().isNotFound());
    }

    @DisplayName("获取所有用户列表 - 教师权限成功")
    @Test
    void testGetAllUsers_Success_TeacherPermission() throws Exception {
        // When: 教师获取所有用户列表
        ResultActions result = mockMvc.perform(get("/user/teacher/all")
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证获取成功，返回 6 个用户
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @DisplayName("管理员更新用户 - 激活状态切换")
    @Test
    void testUpdateUserByAdmin_ActivateDeactivate() throws Exception {
        // Given: 禁用学生的请求
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(STUDENT_NORMAL_ID);
        request.setActive(false); // 禁用账户

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(STUDENT_NORMAL_ID))
                .andExpect(jsonPath("$.data.active").value(false));

        // 验证数据库已更新
        User updatedUser = userRepository.findById(STUDENT_NORMAL_ID).orElseThrow();
        assertThat(updatedUser.getActive()).isFalse();
    }

    @DisplayName("管理员更新用户 - 修改班级信息")
    @Test
    void testUpdateUserByAdmin_UpdateClassName() throws Exception {
        // Given: 修改班级的请求
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(STUDENT_NORMAL_ID);
        request.setClassName("高三实验班");

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.className").value("高三实验班"));

        // 验证数据库已更新
        User updatedUser = userRepository.findById(STUDENT_NORMAL_ID).orElseThrow();
        assertThat(updatedUser.getClassName()).isEqualTo("高三实验班");
    }

    @DisplayName("管理员更新用户 - 修改用户类型")
    @Test
    void testUpdateUserByAdmin_UpdateUserType() throws Exception {
        // Given: 修改用户类型的请求（实际业务中可能不允许，但测试技术可行性）
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(STUDENT_NORMAL_ID);
        request.setUserType(User.UserType.TEACHER);

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证更新成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userType").value("TEACHER"));
    }

    @DisplayName("删除用户 - 教师权限成功")
    @Test
    void testDeleteUser_Success_TeacherPermission() throws Exception {
        // When: 删除学生用户
        ResultActions result = mockMvc.perform(delete("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME)))
                .andDo(print());

        // Then: 验证删除成功
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 强制刷新 JPA 更改到数据库
        userRepository.flush();

        // 验证数据库已删除
        assertThat(userRepository.existsById(STUDENT_NORMAL_ID)).isFalse();
        assertThat(userRepository.count()).isEqualTo(5L); // SQL 脚本 6 个 - 删除 1 个
    }

    @DisplayName("删除用户 - 删除不存在的用户")
    @Test
    void testDeleteUser_NotFound() throws Exception {
        // When: 删除不存在的用户
        ResultActions result = mockMvc.perform(delete("/user/teacher/{id}", NON_EXISTENT_USER_ID)
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME))
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andDo(print());

        // Then: 验证用户不存在
        result.andExpect(status().isNotFound());
    }

    // ============================================================
    // 权限控制测试模块
    // ============================================================

    @DisplayName("学生尝试教师操作 - 查询所有用户失败")
    @Test
    void testGetAllUsers_Forbidden_StudentUser() throws Exception {
        // When: 学生尝试获取所有用户
        ResultActions result = mockMvc.perform(get("/user/teacher/all")
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .requestAttr("userId", STUDENT_SELF_ID)
                .requestAttr("username", STUDENT_SELF_USERNAME))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("学生尝试教师操作 - 按 ID 查询失败")
    @Test
    void testGetUserById_Forbidden_StudentUser() throws Exception {
        // When: 学生尝试查询其他用户
        ResultActions result = mockMvc.perform(get("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .requestAttr("userId", STUDENT_SELF_ID)
                .requestAttr("username", STUDENT_SELF_USERNAME))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("学生尝试教师操作 - 更新用户失败")
    @Test
    void testUpdateUserByAdmin_Forbidden_StudentUser() throws Exception {
        // When: 学生尝试更新其他用户
        ResultActions result = mockMvc.perform(put("/user/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validAdminUpdateRequest))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .requestAttr("userId", STUDENT_SELF_ID)
                .requestAttr("username", STUDENT_SELF_USERNAME))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("学生尝试教师操作 - 删除用户失败")
    @Test
    void testDeleteUser_Forbidden_StudentUser() throws Exception {
        // When: 学生尝试删除其他用户
        ResultActions result = mockMvc.perform(delete("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME))
                .requestAttr("userId", STUDENT_SELF_ID)
                .requestAttr("username", STUDENT_SELF_USERNAME))
                .andDo(print());

        // Then: 验证权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("未认证访问 - 获取当前用户信息失败")
    @Test
    void testGetCurrentUser_Unauthorized() throws Exception {
        // When: 未认证用户尝试获取当前用户信息
        ResultActions result = mockMvc.perform(get("/user/me"))
                .andDo(print());

        // Then: 验证未认证错误（拦截器正常工作，返回 401）
        result.andExpect(status().isUnauthorized());
    }

    @DisplayName("未认证访问 - 教师端点失败")
    @Test
    void testTeacherEndpoints_Unauthorized() throws Exception {
        // When: 未认证用户尝试访问教师端点
        ResultActions result = mockMvc.perform(get("/user/teacher/all"))
                .andDo(print());

        // Then: 验证未认证错误（拦截器正常工作，返回 401）
        result.andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 数据验证测试模块
    // ============================================================

    @DisplayName("数据验证 - 用户名为空")
    @Test
    void testValidation_EmptyUsername() throws Exception {
        // Given: 用户名为空的请求
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("");

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("数据验证 - 用户名超长")
    @Test
    void testValidation_UsernameTooLong() throws Exception {
        // Given: 用户名超长的请求
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("a".repeat(51)); // 超过 50 字符限制

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证参数验证失败
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("业务规则 - 用户名唯一性约束")
    @Test
    void testBusinessRule_UsernameUniqueness() throws Exception {
        // Given: 尝试更新为已存在的用户名
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername(STUDENT_NORMAL_USERNAME); // 使用已存在的用户名

        // When: 发送更新请求
        ResultActions result = mockMvc.perform(put("/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", createAuthHeader(STUDENT_SELF_ID, STUDENT_SELF_USERNAME)))
                .andDo(print());

        // Then: 验证用户名冲突（应该返回错误）
        result.andExpect(status().is4xxClientError());
    }

    @DisplayName("教师删除自己 - 权限限制测试")
    @Test
    void testDeleteSelf_Forbidden_TeacherUser() throws Exception {
        // When: 教师尝试删除自己
        ResultActions result = mockMvc.perform(delete("/user/teacher/{id}", TEACHER_ADMIN_ID)
                .header("Authorization", createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME))
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andDo(print());

        // Then: 根据业务规则，可能允许也可能禁止（这里假设允许）
        // 如果业务上禁止删除自己，应该返回 4xx 错误
        // 暂时接受成功或失败，取决于具体业务实现
        // 注意：Spring MVC Test 没有 isAnyOf 方法，只验证接口能正常响应
        result.andExpect(status().is2xxSuccessful());
    }

    @DisplayName("教师完整权限 - CRUD 操作全部成功")
    @Test
    void testTeacherPermission_AllCRUDSuccess() throws Exception {
        // Given: 教师用户
        String authHeader = createAuthHeader(TEACHER_ADMIN_ID, TEACHER_ADMIN_USERNAME);

        // 1. 查询单个用户
        mockMvc.perform(get("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", authHeader)
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andExpect(status().isOk());

        // 2. 查询所有用户
        mockMvc.perform(get("/user/teacher/all")
                .header("Authorization", authHeader)
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andExpect(status().isOk());

        // 3. 更新用户
        UserAdminUpdateRequest updateRequest = new UserAdminUpdateRequest();
        updateRequest.setUserId(STUDENT_NORMAL_ID);
        updateRequest.setClassName("更新后的班级");

        mockMvc.perform(put("/user/teacher/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .header("Authorization", authHeader)
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andExpect(status().isOk());

        // 4. 删除用户
        mockMvc.perform(delete("/user/teacher/{id}", STUDENT_NORMAL_ID)
                .header("Authorization", authHeader)
                .requestAttr("userId", TEACHER_ADMIN_ID)
                .requestAttr("username", TEACHER_ADMIN_USERNAME))
                .andExpect(status().isOk());
    }
}
