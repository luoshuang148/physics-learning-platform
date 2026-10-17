package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VideoUploadController 集成测试
 *
 * 测试视频上传/删除接口的认证、鉴权、参数校验和业务异常映射。
 * 使用 test-data.sql 中的用户：教师 110/teacher001、学生 100/student001
 */
@DisplayName("视频上传控制器集成测试")
@Tag("integration-controller")
@Sql(scripts = "/sql/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class VideoUploadControllerIntegrationTest extends BaseControllerIntegrationTest {

    // 使用 test-data.sql 中的教师和学生
    private static final Long TEACHER_ID = 110L;
    private static final String TEACHER_USERNAME = "teacher001";
    private static final Long STUDENT_ID = 100L;
    private static final String STUDENT_USERNAME = "student001";

    @AfterEach
    void tearDown() throws IOException {
        // 清理测试过程中写入的视频文件目录
        Path testDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-video-uploads");
        if (Files.exists(testDir)) {
            try (Stream<Path> paths = Files.walk(testDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // Windows 下句柄占用时忽略删除失败
                            }
                        });
            }
        }
    }

    // ============================================================
    // POST /video/upload - 上传视频接口
    // ============================================================

    @DisplayName("上传视频成功 - 教师权限，有效 MP4 文件")
    @Test
    void testUploadVideo_Success() throws Exception {
        // Given: 有效 MP4 文件（>= 1024 字节）
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                new byte[2048]
        );

        // When
        ResultActions result = mockMvc.perform(multipart("/video/upload")
                .file(validFile)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 201 + 响应包含 url/originalFilename/fileSize
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.url").exists())
                .andExpect(jsonPath("$.data.originalFilename").value("test-video.mp4"))
                .andExpect(jsonPath("$.data.fileSize").value(2048));
    }

    @DisplayName("上传视频失败 - 学生权限（403）")
    @Test
    void testUploadVideo_Fail_StudentPermission() throws Exception {
        // Given
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                new byte[2048]
        );

        // When
        ResultActions result = mockMvc.perform(multipart("/video/upload")
                .file(validFile)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 403 权限不足
        result.andExpect(status().isForbidden());
    }

    @DisplayName("上传视频失败 - 未认证（401）")
    @Test
    void testUploadVideo_Fail_Unauthorized() throws Exception {
        // Given: 无认证 Header
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                new byte[2048]
        );

        // When
        ResultActions result = mockMvc.perform(multipart("/video/upload")
                .file(validFile))
                .andDo(print());

        // Then: 401 未认证
        result.andExpect(status().isUnauthorized());
    }

    @DisplayName("上传视频失败 - 非 MP4 格式（400，业务码 5304）")
    @Test
    void testUploadVideo_Fail_InvalidFileType() throws Exception {
        // Given: 使用 .avi 扩展名触发格式校验失败
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                "test-video.avi",
                "video/avi",
                new byte[2048]
        );

        // When
        ResultActions result = mockMvc.perform(multipart("/video/upload")
                .file(invalidFile)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 400 + 业务码 4305（VIDEO_FORMAT_UNSUPPORTED）
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4305));
    }

    // ============================================================
    // DELETE /video/upload - 删除视频接口
    // ============================================================

    @DisplayName("删除视频成功 - 教师权限（200）")
    @Test
    void testDeleteVideo_Success() throws Exception {
        // Given: 先上传一个视频，再获取 URL 删除
        // 这里直接用一个合法但不存在的 URL 测试（服务对不存在的文件静默通过）
        String url = "/videos/nonexistent-uuid_00000.mp4";

        // When
        ResultActions result = mockMvc.perform(delete("/video/upload")
                .param("url", url)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 200
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @DisplayName("删除视频失败 - 学生权限（403）")
    @Test
    void testDeleteVideo_Fail_StudentPermission() throws Exception {
        // Given
        String url = "/videos/some-file.mp4";

        // When
        ResultActions result = mockMvc.perform(delete("/video/upload")
                .param("url", url)
                .header("Authorization", createAuthHeader(STUDENT_ID, STUDENT_USERNAME)))
                .andDo(print());

        // Then: 403
        result.andExpect(status().isForbidden());
    }

    @DisplayName("删除视频失败 - 未认证（401）")
    @Test
    void testDeleteVideo_Fail_Unauthorized() throws Exception {
        // Given: 无认证 Header
        String url = "/videos/some-file.mp4";

        // When
        ResultActions result = mockMvc.perform(delete("/video/upload")
                .param("url", url))
                .andDo(print());

        // Then: 401
        result.andExpect(status().isUnauthorized());
    }

    @DisplayName("删除视频失败 - 无效 URL（400）")
    @Test
    void testDeleteVideo_Fail_InvalidUrl() throws Exception {
        // Given: URL 不以 /videos/ 开头，触发业务校验失败
        String invalidUrl = "/other/path/file.mp4";

        // When
        ResultActions result = mockMvc.perform(delete("/video/upload")
                .param("url", invalidUrl)
                .header("Authorization", createAuthHeader(TEACHER_ID, TEACHER_USERNAME)))
                .andDo(print());

        // Then: 400
        result.andExpect(status().isBadRequest());
    }
}
