package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.video.VideoAlreadyExistsException;
import com.zhixuewuli.common.exception.video.VideoNotFoundException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.VideoCreateRequest;
import com.zhixuewuli.dto.request.VideoUpdateRequest;
import com.zhixuewuli.entity.Video;
import com.zhixuewuli.repository.VideoRepository;
import com.zhixuewuli.service.video.impl.VideoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频服务单元测试
 * 
 * 测试 VideoService 的所有核心业务逻辑，包括：
 * - 视频创建（验证唯一性和关联知识点）
 * - 视频删除（按 ID 和按标题）
 * - 视频更新
 * - 视频查询
 * 
 * 遵循 Given-When-Then 测试模式，使用 Mockito 进行依赖模拟
 */
@Tag("unit")
class VideoServiceUnitTest extends BaseUnitTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private UserPermissionUtil userPermissionUtil;

    @InjectMocks
    private VideoServiceImpl videoService;

    private Video mockVideo;
    private Long videoId = 2000L;
    private Long teacherId = 2001L; // 教师用户 ID

    @BeforeEach
    void setUp() {
        mockVideo = TestDataFactory.createVideo();
        mockVideo.setId(videoId);
        mockVideo.setTitle("测试视频标题"); // 固定标题用于测试
    }

    // ==================== createVideo 测试 ====================

    @Test
    void createVideo_shouldCreate_whenTitleAndUrlUnique() {
        // Given
        VideoCreateRequest request = new VideoCreateRequest();
        request.setTitle("新视频标题");
        request.setUrl("https://example.com/new-video.mp4");
        request.setKnowledgePoint("力学，运动学");

        when(videoRepository.existsByTitle("新视频标题")).thenReturn(false);
        when(videoRepository.existsByUrl("https://example.com/new-video.mp4")).thenReturn(false);
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setId(2001L);
            return video;
        });

        // When
        Video result = videoService.createVideo(request, teacherId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Video::getTitle, Video::getUrl, Video::getKnowledgePoint)
                .containsExactly("新视频标题", "https://example.com/new-video.mp4", "力学，运动学");

        assertThat(result.getId()).isEqualTo(2001L);

        verify(videoRepository).existsByTitle("新视频标题");
        verify(videoRepository).existsByUrl("https://example.com/new-video.mp4");
        verify(videoRepository).save(any(Video.class));
    }

    @Test
    void createVideo_shouldThrowVideoAlreadyExistsException_whenTitleDuplicate() {
        // Given
        VideoCreateRequest request = new VideoCreateRequest();
        request.setTitle("已存在的视频标题");
        request.setUrl("https://example.com/new-video.mp4");
        request.setKnowledgePoint("力学");

        when(videoRepository.existsByTitle("已存在的视频标题")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> videoService.createVideo(request, teacherId))
                .isInstanceOf(VideoAlreadyExistsException.class)
                .hasMessage("视频标题 '已存在的视频标题' 已存在");

        verify(videoRepository).existsByTitle("已存在的视频标题");
        verify(videoRepository, never()).existsByUrl(any());
        verify(videoRepository, never()).save(any(Video.class));
    }

    @Test
    void createVideo_shouldThrowVideoAlreadyExistsException_whenUrlDuplicate() {
        // Given
        VideoCreateRequest request = new VideoCreateRequest();
        request.setTitle("新视频标题");
        request.setUrl("https://example.com/existing-video.mp4");
        request.setKnowledgePoint("力学");

        when(videoRepository.existsByTitle("新视频标题")).thenReturn(false);
        when(videoRepository.existsByUrl("https://example.com/existing-video.mp4")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> videoService.createVideo(request, teacherId))
                .isInstanceOf(VideoAlreadyExistsException.class)
                .hasMessage("视频播放链接 'https://example.com/existing-video.mp4' 已存在");

        verify(videoRepository).existsByTitle("新视频标题");
        verify(videoRepository).existsByUrl("https://example.com/existing-video.mp4");
        verify(videoRepository, never()).save(any(Video.class));
    }

    // ==================== deleteVideoById 测试 ====================

    @Test
    void deleteVideoById_shouldDelete_whenVideoExists() {
        // Given
        Long targetVideoId = 2000L;
        when(videoRepository.findById(targetVideoId)).thenReturn(Optional.of(mockVideo));

        // When
        videoService.deleteVideoById(targetVideoId, teacherId);

        // Then
        verify(videoRepository).findById(targetVideoId);
        verify(videoRepository).delete(mockVideo);
    }

    @Test
    void deleteVideoById_shouldThrowVideoNotFoundException_whenNotFound() {
        // Given
        Long targetVideoId = 9999L;
        when(videoRepository.findById(targetVideoId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> videoService.deleteVideoById(targetVideoId, teacherId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("视频 ID 9999 不存在");

        verify(videoRepository).findById(targetVideoId);
        verify(videoRepository, never()).delete(any(Video.class));
    }

    // ==================== deleteVideoByTitle 测试 ====================

    @Test
    void deleteVideoByTitle_shouldDelete_whenVideoExists() {
        // Given
        String targetTitle = "测试视频标题";
        when(videoRepository.findByTitle(targetTitle)).thenReturn(Optional.of(mockVideo));

        // When
        videoService.deleteVideoByTitle(targetTitle, teacherId);

        // Then
        verify(videoRepository).findByTitle(targetTitle);
        verify(videoRepository).delete(mockVideo);
    }

    @Test
    void deleteVideoByTitle_shouldThrowVideoNotFoundException_whenNotFound() {
        // Given
        String targetTitle = "不存在的视频标题";
        when(videoRepository.findByTitle(targetTitle)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> videoService.deleteVideoByTitle(targetTitle, teacherId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("视频标题 '不存在的视频标题' 不存在");

        verify(videoRepository).findByTitle(targetTitle);
        verify(videoRepository, never()).delete(any(Video.class));
    }

    // ==================== updateVideo 测试 ====================

    @Test
    void updateVideo_shouldFullUpdate_whenValid() {
        // Given
        VideoUpdateRequest request = new VideoUpdateRequest();
        request.setTitle("更新后的视频标题");
        request.setUrl("https://example.com/updated-video.mp4");
        request.setKnowledgePoint("电磁学,电路");

        Video existingVideo = TestDataFactory.createVideo();
        existingVideo.setId(videoId);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(existingVideo));
        when(videoRepository.existsByTitle("更新后的视频标题")).thenReturn(false);
        when(videoRepository.existsByUrl("https://example.com/updated-video.mp4")).thenReturn(false);
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Video result = videoService.updateVideo(videoId, request, teacherId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Video::getTitle, Video::getUrl, Video::getKnowledgePoint)
                .containsExactly("更新后的视频标题", "https://example.com/updated-video.mp4", "电磁学,电路");

        verify(videoRepository).findById(videoId);
        verify(videoRepository).existsByTitle("更新后的视频标题");
        verify(videoRepository).existsByUrl("https://example.com/updated-video.mp4");
        verify(videoRepository).save(existingVideo);
    }

    @Test
    void updateVideo_shouldThrowVideoNotFoundException_whenVideoNotFound() {
        // Given
        VideoUpdateRequest request = new VideoUpdateRequest();
        request.setTitle("更新标题");
        request.setUrl("https://example.com/video.mp4");
        request.setKnowledgePoint("力学");

        when(videoRepository.findById(9999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> videoService.updateVideo(9999L, request, teacherId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("视频 ID 9999 不存在");

        verify(videoRepository).findById(9999L);
        verify(videoRepository, never()).existsByTitle(any());
        verify(videoRepository, never()).existsByUrl(any());
        verify(videoRepository, never()).save(any(Video.class));
    }

    @Test
    void updateVideo_shouldThrowVideoAlreadyExistsException_whenTitleDuplicate() {
        // Given
        VideoUpdateRequest request = new VideoUpdateRequest();
        request.setTitle("已存在的标题");
        request.setUrl("https://example.com/video.mp4");
        request.setKnowledgePoint("力学");

        Video existingVideo = TestDataFactory.createVideo();
        existingVideo.setId(videoId);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(existingVideo));
        when(videoRepository.existsByTitle("已存在的标题")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> videoService.updateVideo(videoId, request, teacherId))
                .isInstanceOf(VideoAlreadyExistsException.class)
                .hasMessage("视频标题 '已存在的标题' 已存在");

        verify(videoRepository).findById(videoId);
        verify(videoRepository).existsByTitle("已存在的标题");
        verify(videoRepository, never()).existsByUrl(any());
        verify(videoRepository, never()).save(any(Video.class));
    }

    // ==================== getVideoById 测试 ====================

    @Test
    void getVideoById_shouldReturnVideo() {
        // Given
        Long targetVideoId = 2000L;
        when(videoRepository.findById(targetVideoId)).thenReturn(Optional.of(mockVideo));

        // When
        Video result = videoService.getVideoById(targetVideoId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Video::getId, Video::getTitle)
                .containsExactly(2000L, mockVideo.getTitle());

        verify(videoRepository).findById(targetVideoId);
    }

    @Test
    void getVideoById_shouldThrowVideoNotFoundException_whenNotFound() {
        // Given
        Long targetVideoId = 9999L;
        when(videoRepository.findById(targetVideoId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> videoService.getVideoById(targetVideoId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("视频 ID 9999 不存在");

        verify(videoRepository).findById(targetVideoId);
    }

    // ==================== getVideoByTitle 测试 ====================

    @Test
    void getVideoByTitle_shouldReturnVideo() {
        // Given
        String targetTitle = "测试视频标题";
        when(videoRepository.findByTitle(targetTitle)).thenReturn(Optional.of(mockVideo));

        // When
        Video result = videoService.getVideoByTitle(targetTitle);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Video::getId, Video::getTitle)
                .containsExactly(2000L, "测试视频标题");

        verify(videoRepository).findByTitle(targetTitle);
    }

    @Test
    void getVideoByTitle_shouldThrowVideoNotFoundException_whenNotFound() {
        // Given
        String targetTitle = "不存在的视频标题";
        when(videoRepository.findByTitle(targetTitle)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> videoService.getVideoByTitle(targetTitle))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("视频标题 '不存在的视频标题' 不存在");

        verify(videoRepository).findByTitle(targetTitle);
    }

    // ==================== getAllVideos 测试 ====================

    @Test
    void getAllVideos_shouldReturnListSortedByCreatedAt() {
        // Given
        List<Video> mockVideos = List.of(
                TestDataFactory.createVideo(),
                TestDataFactory.createVideo(),
                TestDataFactory.createVideo());

        // 设置不同的ID以便区分
        mockVideos.get(0).setId(2001L);
        mockVideos.get(1).setId(2002L);
        mockVideos.get(2).setId(2003L);

        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(mockVideos);

        // When
        List<Video> result = videoService.getAllVideos();

        // Then
        assertThat(result)
                .hasSize(3)
                .extracting(Video::getId)
                .containsExactly(2001L, 2002L, 2003L);

        verify(videoRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getAllVideos_shouldReturnEmptyList_whenNoVideos() {
        // Given
        when(videoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        // When
        List<Video> result = videoService.getAllVideos();

        // Then
        assertThat(result).isEmpty();

        verify(videoRepository).findAllByOrderByCreatedAtDesc();
    }
}