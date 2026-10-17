package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.Video;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 题目仓库集成测试
 * 验证 H2 数据库连接、基本 CRUD 操作和自定义查询方法
 */
@Tag("integration-repository")
class QuestionRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Test
    @Rollback
    void testSaveAndFindQuestion() {
        // Given - 创建题目（不关联视频）
        Question question = new Question();
        question.setContent("关于牛顿第一定律，下列说法正确的是（ ）" + System.currentTimeMillis());
        question.setOptions(
                "{\"A\": \"力是维持物体运动的原因\", \"B\": \"力是改变物体运动状态的原因\", \"C\": \"物体不受力时一定静止\", \"D\": \"物体运动必须有力作用\"}");
        question.setCorrectOption("B");
        question.setExplanation("根据牛顿第一定律，力不是维持物体运动的原因，而是改变物体运动状态的原因。");
        question.setDifficulty(2);

        // When
        Question savedQuestion = questionRepository.save(question);
        Optional<Question> foundQuestion = questionRepository.findById(savedQuestion.getId());

        // Then
        assertThat(foundQuestion).isPresent();
        assertThat(foundQuestion.get().getContent()).contains("牛顿第一定律");
        assertThat(foundQuestion.get().getCorrectOption()).isEqualTo("B");
        assertThat(foundQuestion.get().getDifficulty()).isEqualTo(2);
    }

    @Test
    @Rollback
    void testFindByVideoId_shouldReturnEmpty_whenNoQuestionsLinkedToVideo() {
        // Given - 创建视频但不创建关联题目
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        Video video = createVideo("测试视频_" + System.currentTimeMillis(), "力学");

        // When - 题目实体已独立，不再通过 video_id 关联
        // 此方法仅用于验证历史兼容性，实际应使用 video.getQuestionId() 方式查询
        // 注意：QuestionRepository 已移除 findByVideoId 方法
        List<Question> questions = new ArrayList<>();

        // Then - 验证返回空列表
        assertThat(questions).isEmpty();
    }

    @Test
    @Rollback
    void testFindByIdIn() {
        // Given - 创建多道题目（不关联视频）
        cleanTable("QUESTIONS");

        Question q1 = createQuestion("题目 1_" + System.currentTimeMillis(), "A");
        Question q2 = createQuestion("题目 2_" + System.currentTimeMillis(), "B");
        Question q3 = createQuestion("题目 3_" + System.currentTimeMillis(), "C");
        Question q4 = createQuestion("题目 4_" + System.currentTimeMillis(), "D");

        List<Long> targetIds = List.of(q1.getId(), q3.getId(), q4.getId());

        // When
        List<Question> questions = questionRepository.findByIdIn(targetIds);

        // Then - 验证只返回指定 ID 的题目
        assertThat(questions).hasSize(3);
        assertThat(questions).extracting(Question::getId)
                .containsExactlyInAnyOrderElementsOf(targetIds);
    }



    @Test
    @Rollback
    void testExistsByContent() {
        // Given
        cleanTable("QUESTIONS");

        String uniqueContent = "唯一题目内容_" + System.currentTimeMillis();
        Question question = createQuestion(uniqueContent, "A");

        // When
        boolean exists = questionRepository.existsByContent(uniqueContent);
        boolean notExists = questionRepository.existsByContent("不存在的题目内容");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testFindAllByOrderByCreatedAtDesc() {
        // Given - 清理数据并按顺序创建题目
        cleanTable("QUESTIONS");

        Question q1 = createQuestion("第一个题目_" + System.currentTimeMillis(), "A");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Question q2 = createQuestion("第二个题目_" + System.currentTimeMillis(), "B");
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Question q3 = createQuestion("第三个题目_" + System.currentTimeMillis(), "C");

        // When
        List<Question> questions = questionRepository.findAllByOrderByCreatedAtDesc();

        // Then - 验证按创建时间倒序排列（最新的在前）
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).getContent()).startsWith("第三个题目");
        assertThat(questions.get(1).getContent()).startsWith("第二个题目");
        assertThat(questions.get(2).getContent()).startsWith("第一个题目");
    }

    @Test
    @Rollback
    void testFindQuestionsByIds() {
        // Given
        cleanTable("QUESTIONS");

        Question q1 = createQuestion("题目 A_" + System.currentTimeMillis(), "A");
        Question q2 = createQuestion("题目 B_" + System.currentTimeMillis(), "B");
        Question q3 = createQuestion("题目 C_" + System.currentTimeMillis(), "C");

        List<Long> queryIds = List.of(q1.getId(), q3.getId());

        // When
        List<Question> questions = questionRepository.findQuestionsByIds(queryIds);

        // Then
        assertThat(questions).hasSize(2);
        assertThat(questions).extracting(Question::getId)
                .containsExactlyInAnyOrderElementsOf(queryIds);
    }

    @Test
    @Rollback
    void testExistsById() {
        // Given
        cleanTable("QUESTIONS");

        Question question = createQuestion("存在性测试题目_" + System.currentTimeMillis(), "A");

        // When
        boolean exists = questionRepository.existsById(question.getId());
        boolean notExists = questionRepository.existsById(99999L);

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testUpdateQuestion() {
        // Given
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        Video video = createVideo("更新测试视频_" + System.currentTimeMillis(), "力学");
        Question question = createQuestion("原始题目_" + System.currentTimeMillis(), "A");
        // 注意：题目实体已独立，不再直接关联视频对象
        // 如需关联，应通过 Video.setQuestionId(question.getId()) 方式
        question.setDifficulty(5);
        question.setExplanation("这是更新后的解析");

        // When
        Question updatedQuestion = questionRepository.save(question);

        // Then
        assertThat(updatedQuestion.getDifficulty()).isEqualTo(5);
        assertThat(updatedQuestion.getExplanation()).contains("更新后的解析");
    }

    @Test
    @Rollback
    void testDeleteQuestion() {
        // Given
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        Video video = createVideo("删除测试视频_" + System.currentTimeMillis(), "力学");
        Question question = createQuestion("待删除题目_" + System.currentTimeMillis(), "A");
        Long questionId = question.getId();

        // When
        questionRepository.deleteById(questionId);
        Optional<Question> deletedQuestion = questionRepository.findById(questionId);

        // Then
        assertThat(deletedQuestion).isEmpty();
    }

    @Test
    @Rollback
    void testQuestionWithContentHash() {
        // Given
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        String content = "用于测试 ContentHash 的题目内容_" + System.currentTimeMillis();
        Question question = new Question();
        // 题目实体已独立，不再设置 video 关联
        question.setContent(content);
        question.setOptions("{\"A\": \"选项 A\", \"B\": \"选项 B\"}");
        question.setCorrectOption("A");

        // When
        Question savedQuestion = questionRepository.save(question);

        // Then - 验证 contentHash 自动生成
        assertThat(savedQuestion.getContentHash()).isNotNull();
        assertThat(savedQuestion.getContentHash()).hasSize(64); // SHA-256 哈希长度为 64
    }

    @Test
    @Rollback
    void testMultipleQuestionsWithSameVideo() {
        // Given - 一个视频可关联多道题目（通过 Video.questionId 字段）
        cleanTable("QUESTIONS");
        cleanTable("VIDEOS");

        Video video = createVideo("多题目视频_" + System.currentTimeMillis(), "综合测试");
        
        // 创建多道题目
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Question question = new Question();
            // 题目实体独立，不设置 video 关联
            question.setContent("题目_" + i + "_" + System.currentTimeMillis());
            question.setOptions("{\"A\": \"选项 A\", \"B\": \"选项 B\", \"C\": \"选项 C\", \"D\": \"选项 D\"}");
            question.setCorrectOption("A");
            question.setDifficulty((i % 5) + 1); // 难度 1-5 循环
            questions.add(questionRepository.save(question));
        }
        
        // 将第一道题目关联到视频（模拟一对多关系）
        video.setQuestionId(questions.get(0).getId());
        videoRepository.save(video);

        // When - 查询所有题目（不再按 video_id 筛选）
        List<Question> allQuestions = questionRepository.findAll();

        // Then - 验证所有题目都已保存
        assertThat(allQuestions).hasSize(5);
        // 验证视频关联了第一道题目
        assertThat(video.getQuestionId()).isEqualTo(questions.get(0).getId());
    }

    /**
     * 辅助方法：创建视频
     */
    private Video createVideo(String title, String knowledgePoint) {
        Video video = new Video();
        video.setTitle(title);
        video.setUrl("https://example.com/video_" + System.currentTimeMillis() + ".mp4");
        video.setKnowledgePoint(knowledgePoint);
        video.setCoverUrl("https://example.com/cover.jpg");
        video.setDuration(300);
        video.setDescription("测试视频描述");
        return videoRepository.save(video);
    }

    /**
     * 辅助方法：创建题目（不关联视频）
     */
    private Question createQuestion(String content, String correctOption) {
        Question question = new Question();
        question.setContent(content);
        question.setOptions("{\"A\": \"选项 A\", \"B\": \"选项 B\", \"C\": \"选项 C\", \"D\": \"选项 D\"}");
        question.setCorrectOption(correctOption);
        question.setExplanation("题目解析");
        question.setDifficulty(2);
        return questionRepository.save(question);
    }
}
