package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.Video;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 视频仓库集成测试
 * 验证 H2 数据库连接和基本 CRUD 操作
 */
@Tag("integration-repository")
class VideoRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    @Rollback
    void testSaveAndFindVideo() {
        // Given
        Video video = new Video();
        video.setTitle("集成测试视频_" + System.currentTimeMillis());
        video.setUrl("https://example.com/test_video_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("力学基础");
        video.setCoverUrl("https://example.com/cover.jpg");
        video.setDuration(300);
        video.setDescription("这是一个集成测试视频");

        // When
        Video savedVideo = videoRepository.save(video);
        Optional<Video> foundVideo = videoRepository.findById(savedVideo.getId());

        // Then
        assertThat(foundVideo).isPresent();
        assertThat(foundVideo.get().getTitle()).startsWith("集成测试视频_");
        assertThat(foundVideo.get().getKnowledgePoint()).isEqualTo("力学基础");
        assertThat(foundVideo.get().getDuration()).isEqualTo(300);
    }

    @Test
    @Rollback
    void testExistsByTitle() {
        // Given
        Video video = new Video();
        String uniqueTitle = "exists_test_title_" + System.currentTimeMillis();
        video.setTitle(uniqueTitle);
        video.setUrl("https://example.com/exists_test_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("电磁学");
        videoRepository.save(video);

        // When
        boolean exists = videoRepository.existsByTitle(uniqueTitle);
        boolean notExists = videoRepository.existsByTitle("non_existent_title");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testExistsByUrl() {
        // Given
        Video video = new Video();
        String uniqueUrl = "https://example.com/exists_url_test_" + System.currentTimeMillis() + ".mp4";
        video.setTitle("存在性测试视频");
        video.setUrl(uniqueUrl);
        video.setKnowledgePoint("热学");
        videoRepository.save(video);

        // When
        boolean exists = videoRepository.existsByUrl(uniqueUrl);
        boolean notExists = videoRepository.existsByUrl("https://example.com/non_existent.mp4");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testFindByTitle() {
        // Given
        Video video = new Video();
        String searchTitle = "find_by_title_test_" + System.currentTimeMillis();
        video.setTitle(searchTitle);
        video.setUrl("https://example.com/find_test_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("光学");
        video.setCoverUrl("https://example.com/cover_find.jpg");
        video.setDuration(450);
        video.setDescription("查找测试视频描述");
        videoRepository.save(video);

        // When
        Optional<Video> foundVideo = videoRepository.findByTitle(searchTitle);

        // Then
        assertThat(foundVideo).isPresent();
        assertThat(foundVideo.get().getKnowledgePoint()).isEqualTo("光学");
        assertThat(foundVideo.get().getCoverUrl()).isEqualTo("https://example.com/cover_find.jpg");
        assertThat(foundVideo.get().getDuration()).isEqualTo(450);
    }

    @Test
    @Rollback
    void testFindAllByOrderByCreatedAtDesc() {
        // Given - 清理视频表以避免数据干扰
        cleanTable("VIDEOS");

        Video video1 = new Video();
        video1.setTitle("第一个视频_" + System.currentTimeMillis());
        video1.setUrl("https://example.com/video1_" + System.currentTimeMillis() + ".mp4");
        video1.setKnowledgePoint("力学");

        Video video2 = new Video();
        video2.setTitle("第二个视频_" + System.currentTimeMillis());
        video2.setUrl("https://example.com/video2_" + System.currentTimeMillis() + ".mp4");
        video2.setKnowledgePoint("电磁学");

        Video video3 = new Video();
        video3.setTitle("第三个视频_" + System.currentTimeMillis());
        video3.setUrl("https://example.com/video3_" + System.currentTimeMillis() + ".mp4");
        video3.setKnowledgePoint("热学");

        // 依次保存，确保创建时间有先后
        videoRepository.save(video1);
        try {
            Thread.sleep(10); // 短暂延迟以确保创建时间不同
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        videoRepository.save(video2);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        videoRepository.save(video3);

        // When
        List<Video> videos = videoRepository.findAllByOrderByCreatedAtDesc();

        // Then - 验证按创建时间倒序排列（最新的在前）
        assertThat(videos).hasSize(3);
        assertThat(videos.get(0).getTitle()).startsWith("第三个视频_");
        assertThat(videos.get(1).getTitle()).startsWith("第二个视频_");
        assertThat(videos.get(2).getTitle()).startsWith("第一个视频_");
    }

    @Test
    @Rollback
    void testUpdateVideo() {
        // Given
        Video video = new Video();
        video.setTitle("更新前标题_" + System.currentTimeMillis());
        video.setUrl("https://example.com/update_test_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("原始知识点");
        video.setDuration(200);
        video.setQuestionId(1L); // 设置初始关联题目 ID
        Video savedVideo = videoRepository.save(video);

        // When
        savedVideo.setTitle("更新后标题_" + System.currentTimeMillis());
        savedVideo.setKnowledgePoint("更新后的知识点");
        savedVideo.setDuration(500);
        savedVideo.setQuestionId(2L); // 更新关联题目 ID
        Video updatedVideo = videoRepository.save(savedVideo);

        // Then
        assertThat(updatedVideo.getTitle()).startsWith("更新后标题_");
        assertThat(updatedVideo.getKnowledgePoint()).isEqualTo("更新后的知识点");
        assertThat(updatedVideo.getDuration()).isEqualTo(500);
        assertThat(updatedVideo.getQuestionId()).isEqualTo(2L); // 验证关联题目 ID 已更新
    }

    @Test
    @Rollback
    void testDeleteVideo() {
        // Given
        Video video = new Video();
        video.setTitle("待删除视频_" + System.currentTimeMillis());
        video.setUrl("https://example.com/delete_test_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("力学");
        Video savedVideo = videoRepository.save(video);

        // When
        videoRepository.deleteById(savedVideo.getId());
        Optional<Video> deletedVideo = videoRepository.findById(savedVideo.getId());

        // Then
        assertThat(deletedVideo).isEmpty();
    }

    @Test
    @Rollback
    void testVideoWithQuestionRelation() {
        // Given - 创建视频并关联题目
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        Question question = new Question();
        question.setContent("测试题目_" + System.currentTimeMillis());
        question.setOptions("{\"A\": \"选项 A\", \"B\": \"选项 B\"}");
        question.setCorrectOption("A");
        Question savedQuestion = questionRepository.save(question);

        Video video = new Video();
        video.setTitle("关联题目视频_" + System.currentTimeMillis());
        video.setUrl("https://example.com/relation_test_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint("综合测试");
        video.setQuestionId(savedQuestion.getId()); // 通过 questionId 字段关联
        Video savedVideo = videoRepository.save(video);

        // Then - 验证视频的 questionId 已正确设置
        assertThat(savedVideo.getQuestionId()).isEqualTo(savedQuestion.getId());
        
        // 可以通过查询视频来验证关联关系
        Video foundVideo = videoRepository.findById(savedVideo.getId()).orElse(null);
        assertThat(foundVideo).isNotNull();
        assertThat(foundVideo.getQuestionId()).isEqualTo(savedQuestion.getId());
    }
}
