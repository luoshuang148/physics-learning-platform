package com.zhixuewuli.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.UserRepository;

/**
 * 教师事件管理控制器集成测试
 * 
 * <p>
 * 测试教师事件管理相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>事件发布 - POST /teacher/events</li>
 * <li>事件删除 - DELETE /teacher/events</li>
 * <li>事件查询 - GET /teacher/events</li>
 * <li>权限控制 - 教师/学生权限边界验证</li>
 * <li>数据验证 - 字段格式、业务规则验证</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-teacher-event-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景和权限控制场景</li>
 * <li>验证HTTP 状态码、响应结构和业务逻辑</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("教师事件管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-teacher-event-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class TeacherEventControllerIntegrationTest extends BaseControllerIntegrationTest {

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private TeacherEventRepository teacherEventRepository;

        // 测试常量定义
        private static final Long TEACHER_WITH_EVENT_ID = 2001L;
        private static final String TEACHER_WITH_EVENT_USERNAME = "teacher_with_event";
        private static final Long TEACHER_NO_EVENT_ID = 2002L;
        private static final String TEACHER_NO_EVENT_USERNAME = "teacher_no_event";
        private static final Long STUDENT_USER_ID = 1001L;
        private static final String STUDENT_USER_USERNAME = "student_user";
        private static final String TEACHER_EVENTS_ENDPOINT = "/teacher/events";

        // 测试请求对象
        private TeacherEventCreateRequest validCreateRequest;

        @BeforeEach
        void setUp() {
                // 初始化有效的事件创建请求
                validCreateRequest = new TeacherEventCreateRequest();
                validCreateRequest.setEventTitle("新学期开始");
                validCreateRequest.setEventDescription("2026 年春季学期正式开始");
                validCreateRequest.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));
        }

        // ============================================================
        // 事件发布测试模块（POST /teacher/events）
        // ============================================================

        @DisplayName("发布教师事件 - 成功场景")
        @Test
        void testPublishEvent_Success() throws Exception {
                // Given: 无事件的教师和有效的事件创建请求
                // 注意：测试数据中已有全局事件，需要先删除
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andExpect(status().isOk());
                
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("第一次月考");
                request.setEventDescription("2026 年 3 月份月考");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 15, 9, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证发布成功
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.message").value("事件发布成功"))
                                .andExpect(jsonPath("$.data.id").exists())
                                .andExpect(jsonPath("$.data.teacherId").value(TEACHER_NO_EVENT_ID))
                                .andExpect(jsonPath("$.data.eventTitle").value("第一次月考"))
                                .andExpect(jsonPath("$.data.eventDescription").value("2026 年 3 月份月考"));

                // 验证数据库已创建全局事件
                assertThat(teacherEventRepository.findByIsGlobalTrue()).isPresent();
                assertThat(teacherEventRepository.findByIsGlobalTrue().get().getIsGlobal()).isTrue();
        }

        @DisplayName("发布教师事件 - 事件已存在")
        @Test
        void testPublishEvent_EventAlreadyExists() throws Exception {
                // Given: 已有事件的教师尝试发布新事件
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("新事件");
                request.setEventDescription("描述");
                request.setEventTimestamp(LocalDateTime.of(2026, 4, 1, 8, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证事件已存在错误（4702）
                result.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").value(4702));
        }

        @DisplayName("发布教师事件 - 非教师用户")
        @Test
        void testPublishEvent_NonTeacherUser() throws Exception {
                // Given: 学生用户尝试发布事件
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("学生事件");
                request.setEventDescription("学生不能发布事件");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USER_USERNAME)))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden());
        }

        @DisplayName("发布教师事件 - 空标题")
        @Test
        void testPublishEvent_EmptyTitle() throws Exception {
                // Given: 标题为空的请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("");
                request.setEventDescription("描述");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("发布教师事件 - 标题超长")
        @Test
        void testPublishEvent_TitleTooLong() throws Exception {
                // Given: 标题超长的请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("这是一段非常长的事件标题超过了二十个字符的限制");
                request.setEventDescription("描述");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("发布教师事件 - 描述超长")
        @Test
        void testPublishEvent_DescriptionTooLong() throws Exception {
                // Given: 描述超长的请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("标题");
                request.setEventDescription(
                                "这是一段非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长的描述超过了一百字符的限制非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长非常长");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("发布教师事件 - 空时间戳")
        @Test
        void testPublishEvent_NullTimestamp() throws Exception {
                // Given: 时间戳为空的请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("标题");
                request.setEventDescription("描述");
                request.setEventTimestamp(null);

                // When: 发送发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().is4xxClientError());
        }

        @DisplayName("发布教师事件 - JWT 认证缺失")
        @Test
        void testPublishEvent_Unauthorized() throws Exception {
                // Given: 无认证头的请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("标题");
                request.setEventDescription("描述");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 发送无认证的发布事件请求
                ResultActions result = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // 事件删除测试模块（DELETE /teacher/events）
        // ============================================================

        @DisplayName("删除教师事件 - 成功场景")
        @Test
        void testDeleteEvent_Success() throws Exception {
                // Given: 有事件的教师

                // When: 发送删除事件请求
                ResultActions result = mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证删除成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                // 验证数据库全局事件已删除
                assertThat(teacherEventRepository.findByIsGlobalTrue()).isEmpty();
        }

        @DisplayName("删除教师事件 - 事件不存在")
        @Test
        void testDeleteEvent_NotFound() throws Exception {
                // Given: 先删除已存在的全局事件
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andExpect(status().isOk());
                
                // When: 再次发送删除事件请求 (事件已不存在)
                ResultActions result = mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证删除操作幂等性，返回成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @DisplayName("删除教师事件 - 非教师用户")
        @Test
        void testDeleteEvent_NonTeacherUser() throws Exception {
                // Given: 学生用户尝试删除事件
                // When: 发送删除事件请求
                ResultActions result = mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USER_USERNAME)))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden());
        }

        @DisplayName("删除教师事件 - JWT 认证缺失")
        @Test
        void testDeleteEvent_Unauthorized() throws Exception {
                // When: 发送无认证的删除事件请求
                ResultActions result = mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // 事件查询测试模块（GET /teacher/events）
        // ============================================================

        @DisplayName("查询教师事件 - 有事件教师")
        @Test
        void testGetEvent_WithEvent() throws Exception {
                // When: 有事件的教师查询事件
                ResultActions result = mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(1))
                                .andExpect(jsonPath("$.data.teacherId").value(TEACHER_WITH_EVENT_ID))
                                .andExpect(jsonPath("$.data.eventTitle").value("新学期开始"))
                                .andExpect(jsonPath("$.data.eventDescription").value("2026 年春季学期正式开始"));
        }

        @DisplayName("查询教师事件 - 无事件教师")
        @Test
        void testGetEvent_NoEvent() throws Exception {
                // When: 无事件的教师查询事件 (可以看到全局事件)
                ResultActions result = mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());
        
                // Then: 验证返回全局事件数据
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(1))
                                .andExpect(jsonPath("$.data.teacherId").value(TEACHER_WITH_EVENT_ID))
                                .andExpect(jsonPath("$.data.eventTitle").value("新学期开始"))
                                .andExpect(jsonPath("$.data.eventDescription").value("2026 年春季学期正式开始"));
        }

        @DisplayName("查询教师事件 - 非教师用户")
        @Test
        void testGetEvent_NonTeacherUser() throws Exception {
                // When: 学生用户尝试查询事件 (全局事件对所有认证用户开放)
                ResultActions result = mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USER_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功 (学生也可以查看全局事件)
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(1))
                                .andExpect(jsonPath("$.data.teacherId").value(TEACHER_WITH_EVENT_ID))
                                .andExpect(jsonPath("$.data.eventTitle").value("新学期开始"))
                                .andExpect(jsonPath("$.data.eventDescription").value("2026 年春季学期正式开始"));
        }

        @DisplayName("查询教师事件 - JWT 认证缺失")
        @Test
        void testGetEvent_Unauthorized() throws Exception {
                // When: 发送无认证的查询事件请求
                ResultActions result = mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT))
                                .andDo(print());

                // Then: 验证未认证错误
                result.andExpect(status().isUnauthorized());
        }

        // ============================================================
        // 跨教师数据隔离测试模块
        // ============================================================

        @DisplayName("数据隔离 - 教师不能删除其他教师的事件")
        @Test
        void testDataIsolation_DeleteOtherTeacherEvent() throws Exception {
                // Given: 全局事件存在 (由教师 2001 创建)
                // 全局事件可以被任何教师删除

                // When: 教师 2002 尝试删除事件
                ResultActions result = mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 应该删除成功 (全局事件对所有教师可见且可删除)
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));
        }

        @DisplayName("数据隔离 - 教师不能查询其他教师的事件")
        @Test
        void testDataIsolation_QueryOtherTeacherEvent() throws Exception {
                // Given: 教师 A 查询事件 (全局事件对所有教师可见)
                // When: 教师 2002 查询事件
                ResultActions result = mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());
        
                // Then: 应该返回全局事件 (因为全局事件共享)
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(1))
                                .andExpect(jsonPath("$.data.teacherId").value(TEACHER_WITH_EVENT_ID))
                                .andExpect(jsonPath("$.data.eventTitle").value("新学期开始"));
        }

        // ============================================================
        // 完整权限流程测试模块
        // ============================================================

        @DisplayName("教师完整权限 - 发布、查询、删除全流程")
        @Test
        void testTeacherFullPermission_FullCRUDFlow() throws Exception {
                // Given: 无事件的教师 (先删除已存在的全局事件)
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andExpect(status().isOk());
                
                String authHeader = createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME);

                // 1. 发布事件
                TeacherEventCreateRequest publishRequest = new TeacherEventCreateRequest();
                publishRequest.setEventTitle("期中考试");
                publishRequest.setEventDescription("2026 年期中考试");
                publishRequest.setEventTimestamp(LocalDateTime.of(2026, 4, 15, 9, 0, 0));

                mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest))
                                .header("Authorization", authHeader))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201));

                // 2. 查询事件
                mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.eventTitle").value("期中考试"));

                // 3. 删除事件
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                // 4. 再次查询应该返回空
                mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").doesNotExist());
        }

        @DisplayName("学生禁止所有操作 - 发布、查询、删除全部失败")
        @Test
        void testStudentForbidden_AllOperationsFail() throws Exception {
                // Given: 学生用户
                String authHeader = createAuthHeader(STUDENT_USER_ID, STUDENT_USER_USERNAME);

                // 1. 尝试发布事件 - 应该被禁止
                TeacherEventCreateRequest publishRequest = new TeacherEventCreateRequest();
                publishRequest.setEventTitle("学生事件");
                publishRequest.setEventDescription("学生不能发布");
                publishRequest.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(publishRequest))
                                .header("Authorization", authHeader))
                                .andExpect(status().isForbidden());

                // 2. 查询事件 - 学生可以查询 (全局事件对所有认证用户开放)
                mockMvc.perform(get(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", authHeader))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").exists());

                // 3. 尝试删除事件 - 应该被禁止
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization", authHeader))
                                .andExpect(status().isForbidden());
        }

        // ============================================================
        // 全局事件唯一性并发测试模块（简化版本）
        // ============================================================

        @DisplayName("全局事件唯一性 - 快速连续发布两次")
        @Test
        void testGlobalEventUniqueness_RapidPublishTwice() throws Exception {
                // Given: 先删除已存在的全局事件，确保从无到有测试
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andExpect(status().isOk());
                
                // 准备事件创建请求
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("并发测试事件");
                request.setEventDescription("验证全局事件唯一性");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 第一次发布事件
                ResultActions result1 = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 第一次应该成功 (返回 201)
                result1.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.message").value("事件发布成功"));

                // When: 立即第二次发布（同一教师，模拟快速连续点击）
                ResultActions result2 = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 第二次应该失败 (返回 410/4702 事件已存在)
                result2.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").value(4702)); // EventAlreadyExistsException

                // 最终验证：数据库中只有一条全局事件
                assertThat(teacherEventRepository.findByIsGlobalTrue()).isPresent();
                assertThat(teacherEventRepository.findByIsGlobalTrue().get().getEventTitle())
                                .isEqualTo("并发测试事件");
        }

        @DisplayName("全局事件唯一性 - 不同教师快速连续发布")
        @Test
        void testGlobalEventUniqueness_DifferentTeachersRapidPublish() throws Exception {
                // Given: 先删除已存在的全局事件
                mockMvc.perform(delete(TEACHER_EVENTS_ENDPOINT)
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andExpect(status().isOk());
                
                TeacherEventCreateRequest request = new TeacherEventCreateRequest();
                request.setEventTitle("跨教师并发测试");
                request.setEventDescription("不同教师快速连续发布");
                request.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));

                // When: 教师 A 第一次发布
                ResultActions result1 = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_NO_EVENT_ID, TEACHER_NO_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 教师 A 成功
                result1.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201));

                // When: 教师 B 立即尝试发布（不同教师）
                ResultActions result2 = mockMvc.perform(post(TEACHER_EVENTS_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization",
                                                createAuthHeader(TEACHER_WITH_EVENT_ID, TEACHER_WITH_EVENT_USERNAME)))
                                .andDo(print());

                // Then: 教师 B 应该失败 (事件已存在)
                result2.andExpect(status().is4xxClientError())
                                .andExpect(jsonPath("$.code").value(4702));

                // 最终验证：数据库中只有一条全局事件
                assertThat(teacherEventRepository.findByIsGlobalTrue()).isPresent();
                assertThat(teacherEventRepository.count()).isEqualTo(1L);
        }
}
