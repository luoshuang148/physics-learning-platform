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
import com.zhixuewuli.dto.request.VideoCreateRequest;
import com.zhixuewuli.dto.request.VideoUpdateRequest;
import com.zhixuewuli.entity.Video;
import com.zhixuewuli.repository.VideoRepository;

/**
 * 视频管理控制器集成测试
 * 
 * <p>
 * 测试视频管理相关的所有 API 接口，包括：
 * </p>
 * <ul>
 * <li>教师创建视频 - 验证权限和数据完整性</li>
 * <li>教师更新视频 - 全量覆盖更新</li>
 * <li>教师删除视频 - 按 ID 或标题删除，权限验证</li>
 * <li>查询视频详情 - 按 ID 或标题查询</li>
 * <li>查询所有视频 - 需要认证</li>
 * <li>学生权限限制 - 只能查询不能修改</li>
 * <li>数据验证 - 字段格式、唯一性约束验证</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 {@code scenario-video-test-data.sql} 提供基础测试数据</li>
 * <li>每个测试方法独立执行，通过事务回滚保证数据隔离</li>
 * <li>覆盖正常场景、异常场景和权限控制场景</li>
 * <li>验证 HTTP状态码、响应结构和业务逻辑</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BaseControllerIntegrationTest
 */
@DisplayName("视频管理集成测试")
@Tag("integration-controller")
@Sql(scripts = { "/sql/scenario-video-test-data.sql" }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class VideoControllerIntegrationTest extends BaseControllerIntegrationTest {

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private VideoRepository videoRepository;

        // 测试常量定义
        private static final Long TEACHER_USER_ID = 2001L;
        private static final String TEACHER_USERNAME = "video_test_teacher";
        private static final Long STUDENT_USER_ID = 1001L;
        private static final String STUDENT_USERNAME = "video_test_student";

        // 测试请求对象
        private VideoCreateRequest validCreateRequest;
        private VideoUpdateRequest validUpdateRequest;

        @BeforeEach
        void setUp() {
                // 初始化有效的创建请求
                validCreateRequest = new VideoCreateRequest();
                validCreateRequest.setTitle("Test Video " + System.currentTimeMillis());
                validCreateRequest.setUrl("https://test.example.com/video/test-" + System.currentTimeMillis() + ".mp4");
                validCreateRequest.setKnowledgePoint("Test Knowledge Point");
                validCreateRequest.setCoverUrl("https://test.example.com/cover/test.jpg");
                validCreateRequest.setDuration(300);
                validCreateRequest.setDescription("This is a test video for integration testing");

                // 初始化有效的更新请求
                validUpdateRequest = new VideoUpdateRequest();
                validUpdateRequest.setId(5001L);
                validUpdateRequest.setTitle("Updated Video Title");
                validUpdateRequest.setUrl("https://test.example.com/video/updated.mp4");
                validUpdateRequest.setKnowledgePoint("Updated Knowledge Point");
                validUpdateRequest.setCoverUrl("https://test.example.com/cover/updated.jpg");
                validUpdateRequest.setDuration(350);
                validUpdateRequest.setDescription("Updated video description");
        }

        // ============================================================
        // 创建视频测试 (CREATE)
        // ============================================================

        @DisplayName("创建视频成功 - 教师权限")
        @Test
        void testCreateVideo_Success_TeacherPermission() throws Exception {
                // Given: 教师用户和有效的创建请求
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("New Test Video");
                request.setUrl("https://test.example.com/video/new-test.mp4");
                request.setKnowledgePoint("New Knowledge Point");
                request.setDuration(400);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证创建成功
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.code").value(201))
                                .andExpect(jsonPath("$.data.id").exists())
                                .andExpect(jsonPath("$.data.title").value("New Test Video"))
                                .andExpect(jsonPath("$.data.url").value("https://test.example.com/video/new-test.mp4"))
                                .andExpect(jsonPath("$.data.knowledgePoint").value("New Knowledge Point"))
                                .andExpect(jsonPath("$.data.questionId").doesNotExist()) // 新创建的视频没有关联题目
                                .andExpect(jsonPath("$.data.duration").value(400));

                // 验证数据库中有新视频
                assertThat(videoRepository.count()).isEqualTo(6L); // SQL 脚本 5 个 + 新建 1 个
        }

        @DisplayName("创建视频失败 - 标题重复")
        @Test
        void testCreateVideo_Fail_DuplicateTitle() throws Exception {
                // Given: 使用已存在的标题
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Newton First Law Detailed Explanation"); // 已存在的标题
                request.setUrl("https://test.example.com/video/unique-url.mp4");
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证创建失败（违反唯一约束，返回 409）
                result.andExpect(status().isConflict());
        }

        @DisplayName("创建视频失败 - URL 重复")
        @Test
        void testCreateVideo_Fail_DuplicateUrl() throws Exception {
                // Given: 使用已存在的 URL
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Unique Title");
                request.setUrl("https://test.example.com/video/newton_first_detailed.mp4"); // 已存在的 URL
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证创建失败（违反唯一约束，返回 409）
                result.andExpect(status().isConflict());
        }

        @DisplayName("创建视频失败 - 学生权限（无权创建）")
        @Test
        void testCreateVideo_Fail_StudentPermission() throws Exception {
                // Given: 学生用户尝试创建视频
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Student Try Create");
                request.setUrl("https://test.example.com/video/student-create.mp4");
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME)))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden());
        }

        @DisplayName("创建视频失败 - 未认证用户")
        @Test
        void testCreateVideo_Fail_Unauthorized() throws Exception {
                // Given: 无认证信息
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Unauthorized Create");
                request.setUrl("https://test.example.com/video/unauthorized.mp4");
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求（无认证头）
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print());

                // Then: 验证未认证错误（拦截器正常工作，返回 401）
                result.andExpect(status().isUnauthorized());
        }

        @DisplayName("创建视频失败 - 数据验证失败（标题为空）")
        @Test
        void testCreateVideo_Fail_InvalidData_EmptyTitle() throws Exception {
                // Given: 标题为空的请求
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle(""); // 空标题
                request.setUrl("https://test.example.com/video/valid.mp4");
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("创建视频失败 - 数据验证失败（URL 格式错误）")
        @Test
        void testCreateVideo_Fail_InvalidData_BadUrl() throws Exception {
                // Given: URL 格式错误的请求
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Valid Title");
                request.setUrl("invalid-url"); // 无效的 URL
                request.setKnowledgePoint("Test Point");
                request.setDuration(300);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("创建视频成功 - URL 使用上传接口相对路径")
        @Test
        void testCreateVideo_Success_RelativeVideoPath() throws Exception {
                // Given: 使用上传接口返回的相对路径格式
                long suffix = System.currentTimeMillis();
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Relative Video " + suffix);
                request.setUrl("/videos/uploaded-" + suffix + ".mp4");
                request.setKnowledgePoint("Upload Result Path");
                request.setDuration(260);

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 创建成功且 URL 原样保存
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.url").value(request.getUrl()));
        }

        @DisplayName("创建视频成功 - URL 使用 API 前缀相对路径")
        @Test
        void testCreateVideo_Success_ApiPrefixedRelativeVideoPath() throws Exception {
                // Given: 兼容历史数据中的 /api/v1/videos/... 形式
                long suffix = System.currentTimeMillis();
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Api Relative Video " + suffix);
                request.setUrl("/api/v1/videos/uploaded-" + suffix + ".mp4");
                request.setDuration(180);

                // When
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.url").value(request.getUrl()));
        }

        @DisplayName("创建视频成功 - URL 使用 HTTP 绝对地址")
        @Test
        void testCreateVideo_Success_HttpUrl() throws Exception {
                // Given: 生产环境可能为 HTTP 部署
                long suffix = System.currentTimeMillis();
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Http Video " + suffix);
                request.setUrl("http://test.example.com/video/http-" + suffix + ".mp4");
                request.setDuration(220);

                // When
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.url").value(request.getUrl()));
        }

        @DisplayName("创建视频失败 - 数据验证失败（时长超出范围）")
        @Test
        void testCreateVideo_Fail_InvalidData_DurationOutOfRange() throws Exception {
                // Given: 时长超出范围的请求
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Valid Title");
                request.setUrl("https://test.example.com/video/valid.mp4");
                request.setKnowledgePoint("Test Point");
                request.setDuration(-100); // 负数时长

                // When: 发送创建视频请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证参数验证失败
                result.andExpect(status().isBadRequest());
        }

        // ============================================================
        // 删除视频测试 (DELETE)
        // ============================================================

        @DisplayName("删除视频成功 - 按 ID 删除")
        @Test
        void testDeleteVideoById_Success() throws Exception {
                // Given: 已存在的视频 ID 和教师用户
                Long videoId = 5001L;

                // When: 发送删除视频请求
                ResultActions result = mockMvc.perform(delete("/video/teacher/{id}", videoId)
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证删除成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                // 强制刷新 JPA 更改到数据库
                videoRepository.flush();

                // 验证数据库中视频已删除
                assertThat(videoRepository.count()).isEqualTo(4L); // SQL 脚本 5 个 - 删除 1 个
        }

        @DisplayName("删除视频成功 - 按标题删除")
        @Test
        void testDeleteVideoByTitle_Success() throws Exception {
                // Given: 已存在的视频标题和教师用户
                String videoTitle = "Newton Second Law Formula and Applications";

                // When: 发送删除视频请求
                ResultActions result = mockMvc.perform(delete("/video/teacher/title/{title}", videoTitle)
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证删除成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200));

                // 强制刷新 JPA 更改到数据库
                videoRepository.flush();

                // 验证数据库中视频已删除
                assertThat(videoRepository.count()).isEqualTo(4L); // SQL 脚本 5 个 - 删除 1 个
        }

        @DisplayName("删除视频失败 - 视频不存在")
        @Test
        void testDeleteVideo_Fail_NotFound() throws Exception {
                // Given: 不存在的视频 ID
                Long nonExistentId = 99999L;

                // When: 发送删除视频请求
                ResultActions result = mockMvc.perform(delete("/video/teacher/{id}", nonExistentId)
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证视频不存在
                result.andExpect(status().isNotFound());
        }

        @DisplayName("删除视频失败 - 学生权限")
        @Test
        void testDeleteVideo_Fail_StudentPermission() throws Exception {
                // Given: 学生用户尝试删除视频
                Long videoId = 5001L;

                // When: 发送删除视频请求
                ResultActions result = mockMvc.perform(delete("/video/teacher/{id}", videoId)
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME)))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden());
        }

        // ============================================================
        // 更新视频测试 (UPDATE)
        // ============================================================

        @DisplayName("更新视频成功 - 全量更新")
        @Test
        void testUpdateVideo_Success_FullUpdate() throws Exception {
                // Given: 教师用户和完整的更新请求
                VideoUpdateRequest request = new VideoUpdateRequest();
                request.setId(5001L);
                request.setTitle("Fully Updated Title");
                request.setUrl("https://test.example.com/video/fully-updated.mp4");
                request.setKnowledgePoint("Fully Updated Knowledge Point");
                request.setCoverUrl("https://test.example.com/cover/fully-updated.jpg");
                request.setDuration(500);
                request.setDescription("Fully updated description");

                // When: 发送更新视频请求
                ResultActions result = mockMvc.perform(put("/video/teacher/{id}", 5001L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证更新成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(5001L))
                                .andExpect(jsonPath("$.data.title").value("Fully Updated Title"))
                                .andExpect(jsonPath("$.data.url")
                                                .value("https://test.example.com/video/fully-updated.mp4"))
                                .andExpect(jsonPath("$.data.duration").value(500));
        }

        @DisplayName("更新视频失败 - 视频不存在")
        @Test
        void testUpdateVideo_Fail_NotFound() throws Exception {
                // Given: 不存在的视频 ID
                VideoUpdateRequest request = new VideoUpdateRequest();
                request.setId(99999L);
                request.setTitle("Updated Title");
                request.setUrl("https://test.example.com/video/updated.mp4");

                // When: 发送更新视频请求
                ResultActions result = mockMvc.perform(put("/video/teacher/{id}", 99999L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证视频不存在
                result.andExpect(status().isNotFound());
        }

        @DisplayName("更新视频失败 - 学生权限")
        @Test
        void testUpdateVideo_Fail_StudentPermission() throws Exception {
                // Given: 学生用户尝试更新视频
                VideoUpdateRequest request = new VideoUpdateRequest();
                request.setId(5001L);
                request.setTitle("Student Try Update");
                request.setUrl("https://test.example.com/video/student-update.mp4");

                // When: 发送更新视频请求
                ResultActions result = mockMvc.perform(put("/video/teacher/{id}", 5001L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME))
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andDo(print());

                // Then: 验证权限不足
                result.andExpect(status().isForbidden());
        }

        // ============================================================
        // 查询视频测试 (READ)
        // ============================================================

        @DisplayName("获取视频详情成功 - 按 ID 查询")
        @Test
        void testGetVideoById_Success() throws Exception {
                // Given: 已存在的视频 ID
                Long videoId = 5001L;

                // When: 发送获取视频详情请求（教师权限）
                ResultActions result = mockMvc.perform(get("/video/teacher/{id}", videoId)
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.id").value(videoId))
                                .andExpect(jsonPath("$.data.title").value("Newton First Law Detailed Explanation"))
                                .andExpect(jsonPath("$.data.url").exists())
                                .andExpect(jsonPath("$.data.duration").value(420))
                                .andExpect(jsonPath("$.data.knowledgePoint").exists())
                                .andExpect(jsonPath("$.data.questionId").value(6001)); // 验证关联的题目 ID
        }

        @DisplayName("获取视频详情成功 - 按标题查询")
        @Test
        void testGetVideoByTitle_Success() throws Exception {
                // Given: 已存在的视频标题
                String videoTitle = "Newton Second Law Formula and Applications";

                // When: 发送获取视频详情请求（学生权限）
                ResultActions result = mockMvc.perform(get("/video/teacher/title/{title}", videoTitle)
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data.title").value(videoTitle))
                                .andExpect(jsonPath("$.data.duration").value(540))
                                .andExpect(jsonPath("$.data.knowledgePoint")
                                                .value("Newton Second Law,Acceleration,Force,Mass,F=ma"))
                                .andExpect(jsonPath("$.data.questionId").value(6002)); // 验证关联的题目 ID
        }

        @DisplayName("获取所有视频成功 - 教师权限")
        @Test
        void testGetAllVideos_TeacherUser() throws Exception {
                // Given: 教师用户
                // When: 发送获取所有视频请求
                ResultActions result = mockMvc.perform(get("/video/teacher/all")
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功，返回 5 条记录
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(5));
        }

        @DisplayName("获取所有视频成功 - 学生权限")
        @Test
        void testGetAllVideos_StudentUser() throws Exception {
                // Given: 学生用户
                // When: 发送获取所有视频请求
                ResultActions result = mockMvc.perform(get("/video/teacher/all")
                                .header("Authorization", createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME)))
                                .andDo(print());

                // Then: 验证查询成功，返回 5 条记录
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.code").value(200))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(5));
        }

        @DisplayName("获取视频详情失败 - 不存在的 ID")
        @Test
        void testGetVideo_Fail_NotFound() throws Exception {
                // Given: 不存在的视频 ID
                Long nonExistentId = 99999L;

                // When: 发送获取视频详情请求
                ResultActions result = mockMvc.perform(get("/video/teacher/{id}", nonExistentId)
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME)))
                                .andDo(print());

                // Then: 验证视频不存在
                result.andExpect(status().isNotFound());
        }

        // ============================================================
        // 权限控制测试 (AUTHORIZATION)
        // ============================================================

        @DisplayName("教师完整权限 - CRUD操作全部成功")
        @Test
        void testTeacherPermission_AllCRUDSuccess() throws Exception {
                // Given: 教师用户
                String authHeader = createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME);

                // 1. 创建视频
                VideoCreateRequest createRequest = new VideoCreateRequest();
                createRequest.setTitle("Permission Test Video");
                createRequest.setUrl("https://test.example.com/video/permission-test.mp4");
                createRequest.setKnowledgePoint("Permission Test");
                createRequest.setDuration(200);

                ResultActions createResult = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                                .header("Authorization", authHeader)
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                createResult.andExpect(status().isCreated());
                String responseContent = createResult.andReturn().getResponse().getContentAsString();
                Long createdId = objectMapper.readTree(responseContent).get("data").get("id").asLong();

                // 2. 查询视频
                mockMvc.perform(get("/video/teacher/{id}", createdId)
                                .header("Authorization", authHeader)
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andExpect(status().isOk());

                // 3. 更新视频
                VideoUpdateRequest updateRequest = new VideoUpdateRequest();
                updateRequest.setId(createdId);
                updateRequest.setTitle("Updated Permission Test");
                updateRequest.setUrl("https://test.example.com/video/permission-updated.mp4");
                updateRequest.setDuration(250);

                mockMvc.perform(put("/video/teacher/{id}", createdId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                                .header("Authorization", authHeader)
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andExpect(status().isOk());

                // 4. 删除视频
                mockMvc.perform(delete("/video/teacher/{id}", createdId)
                                .header("Authorization", authHeader)
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andExpect(status().isOk());
        }

        @DisplayName("学生只读权限 - 只能查询不能修改")
        @Test
        void testStudentPermission_ReadOnly() throws Exception {
                // Given: 学生用户
                String authHeader = createAuthHeader(STUDENT_USER_ID, STUDENT_USERNAME);

                // 1. 查询应该成功
                mockMvc.perform(get("/video/teacher/{id}", 5001L)
                                .header("Authorization", authHeader)
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andExpect(status().isOk());

                // 2. 获取所有视频应该成功
                mockMvc.perform(get("/video/teacher/all")
                                .header("Authorization", authHeader)
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andExpect(status().isOk());

                // 3. 创建应该失败
                VideoCreateRequest createRequest = new VideoCreateRequest();
                createRequest.setTitle("Student Try Create");
                createRequest.setUrl("https://test.example.com/video/student.mp4");

                mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                                .header("Authorization", authHeader)
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andExpect(status().isForbidden());

                // 4. 更新应该失败
                VideoUpdateRequest updateRequest = new VideoUpdateRequest();
                updateRequest.setId(5001L);
                updateRequest.setTitle("Student Try Update");
                updateRequest.setUrl("https://test.example.com/video/student-update.mp4");

                mockMvc.perform(put("/video/teacher/{id}", 5001L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                                .header("Authorization", authHeader)
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andExpect(status().isForbidden());

                // 5. 删除应该失败
                mockMvc.perform(delete("/video/teacher/{id}", 5001L)
                                .header("Authorization", authHeader)
                                .requestAttr("userId", STUDENT_USER_ID)
                                .requestAttr("username", STUDENT_USERNAME))
                                .andExpect(status().isForbidden());
        }

        @DisplayName("未认证用户 - 所有操作失败")
        @Test
        void testUnauthorized_AllOperationsFail() throws Exception {
                // 1. 创建应该失败
                VideoCreateRequest createRequest = new VideoCreateRequest();
                createRequest.setTitle("Unauthorized Create");
                createRequest.setUrl("https://test.example.com/video/unauthorized.mp4");

                mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isUnauthorized());

                // 2. 更新应该失败
                VideoUpdateRequest updateRequest = new VideoUpdateRequest();
                updateRequest.setId(5001L);
                updateRequest.setTitle("Unauthorized Update");
                updateRequest.setUrl("https://test.example.com/video/unauthorized-update.mp4");

                mockMvc.perform(put("/video/teacher/{id}", 5001L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isUnauthorized());

                // 3. 删除应该失败
                mockMvc.perform(delete("/video/teacher/{id}", 5001L))
                                .andExpect(status().isUnauthorized());

                // 4. 查询可能成功或失败，取决于安全配置（这里不做强制验证）
        }

        // ============================================================
        // 数据验证测试 (VALIDATION)
        // ============================================================

        @DisplayName("数据验证 - 标题必填")
        @Test
        void testValidation_TitleRequired() throws Exception {
                // Given: 标题为空
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("");
                request.setUrl("https://test.example.com/video/valid.mp4");

                // When: 发送创建请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("数据验证 - URL 格式")
        @Test
        void testValidation_UrlFormat() throws Exception {
                // Given: URL 格式错误
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Valid Title");
                request.setUrl("not-a-valid-url");

                // When: 发送创建请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("数据验证 - 拒绝无效相对路径")
        @Test
        void testValidation_UrlFormat_RejectUnsupportedRelativePath() throws Exception {
                // Given: 不受支持的相对路径（不是 /videos/ 或 /api/v1/videos/）
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Valid Title");
                request.setUrl("/video/invalid-path.mp4");

                // When
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("数据验证 - 时长范围")
        @Test
        void testValidation_DurationRange() throws Exception {
                // Given: 时长超出范围（太大）
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle("Valid Title");
                request.setUrl("https://test.example.com/video/valid.mp4");
                request.setDuration(100000); // 超过 86400 秒

                // When: 发送创建请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证失败
                result.andExpect(status().isBadRequest());
        }

        @DisplayName("数据验证 - 字段长度限制")
        @Test
        void testValidation_FieldLengthLimits() throws Exception {
                // Given: 标题超长
                String longTitle = "A".repeat(201); // 超过 200 字符限制
                VideoCreateRequest request = new VideoCreateRequest();
                request.setTitle(longTitle);
                request.setUrl("https://test.example.com/video/valid.mp4");

                // When: 发送创建请求
                ResultActions result = mockMvc.perform(post("/video/teacher")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .header("Authorization", createAuthHeader(TEACHER_USER_ID, TEACHER_USERNAME))
                                .requestAttr("userId", TEACHER_USER_ID)
                                .requestAttr("username", TEACHER_USERNAME))
                                .andDo(print());

                // Then: 验证失败
                result.andExpect(status().isBadRequest());
        }
}
