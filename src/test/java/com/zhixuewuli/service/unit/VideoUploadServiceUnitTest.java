package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.video.VideoBusinessException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.response.VideoUploadResponse;
import com.zhixuewuli.service.video.impl.VideoUploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VideoUploadService 单元测试
 *
 * 测试视频上传/删除的所有核心业务逻辑分支，包括：
 * - 权限校验（仅教师可操作）
 * - 文件空/过大/过小/格式/MIME 类型校验
 * - 正常上传路径（>= 1KB 的有效 MP4 文件）
 * - 删除接口的 URL 安全校验
 */
@Tag("unit")
@DisplayName("VideoUploadService 单元测试")
class VideoUploadServiceUnitTest extends BaseUnitTest {

    @Mock
    private UserPermissionUtil userPermissionUtil;

    @InjectMocks
    private VideoUploadServiceImpl videoUploadService;

    private static final Long TEACHER_ID = 110L;
    private static final Long STUDENT_ID = 100L;

    private String testStoragePath;

    @BeforeEach
    void setUp() {
        testStoragePath = Paths.get(System.getProperty("java.io.tmpdir"), "zxwl-test-videos").toString();
        ReflectionTestUtils.setField(videoUploadService, "storagePath", testStoragePath);
        ReflectionTestUtils.setField(videoUploadService, "accessPath", "/videos");
        ReflectionTestUtils.setField(videoUploadService, "maxFileSize", 524288000L);
    }

    // ==================== uploadVideo 测试 ====================

    @Test
    @DisplayName("上传成功 - 有效 MP4 文件（>= 1KB）")
    void uploadVideo_shouldSuccess_whenValidMp4File() {
        // Given: 有效的 MP4 文件（2048 字节）
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                new byte[2048]
        );

        // When
        VideoUploadResponse response = videoUploadService.uploadVideo(validFile, TEACHER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUrl()).startsWith("/videos/");
        assertThat(response.getUrl()).endsWith(".mp4");
        assertThat(response.getOriginalFilename()).isEqualTo("test-video.mp4");
        assertThat(response.getFileSize()).isEqualTo(2048L);

        verify(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
    }

    @Test
    @DisplayName("上传失败 - 空文件")
    void uploadVideo_shouldThrowException_whenFileEmpty() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.mp4",
                "video/mp4",
                new byte[0]
        );

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(emptyFile, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("上传失败 - 文件超过大小限制")
    void uploadVideo_shouldThrowException_whenFileTooLarge() {
        // Given: 使用 mock 伪造超大文件 size，避免 OOM
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(600_000_000L); // 600MB > 524MB 限制

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(mockFile, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("上传失败 - 非 mp4 扩展名")
    void uploadVideo_shouldThrowException_whenInvalidExtension() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MockMultipartFile avi = new MockMultipartFile(
                "file",
                "video.avi",
                "video/avi",
                new byte[2048]
        );

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(avi, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_FORMAT_UNSUPPORTED);
                });
    }

    @Test
    @DisplayName("上传失败 - MIME 类型不是 video/*")
    void uploadVideo_shouldThrowException_whenInvalidMimeType() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file",
                "fake.mp4",
                "application/octet-stream",  // 伪装成非视频 MIME
                new byte[2048]
        );

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(fakeFile, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_FORMAT_UNSUPPORTED);
                });
    }

    @Test
    @DisplayName("上传失败 - 文件小于 1KB")
    void uploadVideo_shouldThrowException_whenFileTooSmall() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MockMultipartFile tinyFile = new MockMultipartFile(
                "file",
                "tiny.mp4",
                "video/mp4",
                new byte[512]  // 512 字节 < 1024
        );

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(tinyFile, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("上传失败 - transferTo 抛出 IOException")
    void uploadVideo_shouldThrowException_whenSaveFails() throws IOException {
        // Given: mock 文件 transferTo 抛出异常
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getSize()).thenReturn(2048L);
        when(mockFile.getOriginalFilename()).thenReturn("test.mp4");
        when(mockFile.getContentType()).thenReturn("video/mp4");
        doThrow(new IOException("磁盘已满")).when(mockFile).transferTo(org.mockito.ArgumentMatchers.any(java.io.File.class));

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(mockFile, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("上传失败 - 非教师用户（学生）上传")
    void uploadVideo_shouldThrowException_whenNotTeacher() {
        // Given
        doThrow(new InsufficientPermissionException(STUDENT_ID, "权限不足：仅教师可执行此操作"))
                .when(userPermissionUtil).checkTeacherPermission(STUDENT_ID);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                new byte[2048]
        );

        // When & Then
        assertThatThrownBy(() -> videoUploadService.uploadVideo(file, STUDENT_ID))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userPermissionUtil).checkTeacherPermission(STUDENT_ID);
    }

    // ==================== deleteVideoFile 测试 ====================

    @Test
    @DisplayName("删除成功 - 有效 URL（文件不存在时静默通过）")
    void deleteVideoFile_shouldSuccess_whenValidUrl() {
        // Given: 提供有效 /videos/ 前缀 URL，文件不存在则静默通过
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        String url = "/videos/valid-uuid_12345.mp4";

        // When & Then: 不抛出异常即通过
        videoUploadService.deleteVideoFile(url, TEACHER_ID);

        verify(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
    }

    @Test
    @DisplayName("删除失败 - URL 为 null")
    void deleteVideoFile_shouldThrowException_whenUrlNull() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);

        // When & Then
        assertThatThrownBy(() -> videoUploadService.deleteVideoFile(null, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("删除失败 - URL 包含路径穿越 ..")
    void deleteVideoFile_shouldThrowException_whenUrlContainsPathTraversal() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        String maliciousUrl = "/videos/../secret-file.mp4";

        // When & Then
        assertThatThrownBy(() -> videoUploadService.deleteVideoFile(maliciousUrl, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("删除失败 - URL 非 /videos/ 前缀")
    void deleteVideoFile_shouldThrowException_whenUrlInvalidPrefix() {
        // Given
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        String invalidUrl = "/other/path/file.mp4";

        // When & Then
        assertThatThrownBy(() -> videoUploadService.deleteVideoFile(invalidUrl, TEACHER_ID))
                .isInstanceOf(VideoBusinessException.class)
                .satisfies(ex -> {
                    VideoBusinessException vbe = (VideoBusinessException) ex;
                    assertThat(vbe.getCode()).isEqualTo(ResultCode.VIDEO_UPLOAD_FAILED);
                });
    }

    @Test
    @DisplayName("删除失败 - 非教师用户尝试删除")
    void deleteVideoFile_shouldThrowException_whenNotTeacher() {
        // Given
        doThrow(new InsufficientPermissionException(STUDENT_ID, "权限不足：仅教师可执行此操作"))
                .when(userPermissionUtil).checkTeacherPermission(STUDENT_ID);

        // When & Then
        assertThatThrownBy(() -> videoUploadService.deleteVideoFile("/videos/test.mp4", STUDENT_ID))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userPermissionUtil).checkTeacherPermission(STUDENT_ID);
    }

    @Test
    @DisplayName("删除成功 - 文件不存在时静默通过")
    void deleteVideoFile_shouldHandleNonExistentFile() {
        // Given: 文件不存在也不应抛出异常
        doNothing().when(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
        String url = "/videos/non-existent-file-uuid.mp4";

        // When & Then: 无异常则通过
        videoUploadService.deleteVideoFile(url, TEACHER_ID);

        verify(userPermissionUtil).checkTeacherPermission(TEACHER_ID);
    }
}
