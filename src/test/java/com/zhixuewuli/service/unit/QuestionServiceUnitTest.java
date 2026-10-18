package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.question.QuestionContentDuplicateException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.QuestionCreateRequest;
import com.zhixuewuli.dto.request.QuestionUpdateRequest;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.question.impl.QuestionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 题目服务单元测试
 * 
 * 测试QuestionService的所有核心业务逻辑，包括：
 * - 题目创建（验证唯一性和关联视频）
 * - 题目删除
 * - 题目更新
 * - 题目查询
 * 
 * 遵循Given-When-Then测试模式，使用Mockito进行依赖模拟
 */
class QuestionServiceUnitTest extends BaseUnitTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private UserPermissionUtil userPermissionUtil;

    @Mock private com.zhixuewuli.repository.TeacherEventQuestionRepository taskQuestionRepository;
    @Mock private com.zhixuewuli.repository.AnswerRecordRepository answerRepository;

    @InjectMocks
    private QuestionServiceImpl questionService;

    private Question mockQuestion;
    private Long teacherId = 110L;

    @BeforeEach
    void setUp() {
        mockQuestion = TestDataFactory.createQuestion();
        mockQuestion.setId(1000L);
    }

    // ==================== createQuestion 测试 ====================

    @Test
    void createQuestion_shouldCreate_whenContentUnique() {
        // Given
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setContent("一个新的物理题目内容");
        request.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        request.setCorrectOption("A");
        request.setExplanation("题目解析");
        request.setDifficulty(3);
    
        when(questionRepository.existsByContent("一个新的物理题目内容")).thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1001L);
            return question;
        });
    
        // When
        Question result = questionService.createQuestion(request, teacherId);
    
        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Question::getContent, Question::getCorrectOption, Question::getDifficulty)
                .containsExactly("一个新的物理题目内容", "A", 3);
    
        assertThat(result.getId()).isEqualTo(1001L);
    
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).existsByContent("一个新的物理题目内容");
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void createQuestion_shouldThrowQuestionContentDuplicateException_whenContentDuplicate() {
        // Given
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setContent("已存在的题目内容");
        request.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        request.setCorrectOption("A");
    
        when(questionRepository.existsByContent("已存在的题目内容")).thenReturn(true);
    
        // When & Then
        assertThatThrownBy(() -> questionService.createQuestion(request, teacherId))
                .isInstanceOf(QuestionContentDuplicateException.class)
                .hasMessage("题目内容已存在：已存在的题目内容");
    
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).existsByContent("已存在的题目内容");
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void createQuestion_shouldThrowInsufficientPermissionException_whenPermissionDenied() {
        // Given
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setContent("权限受限题目");
        request.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\"}");
        request.setCorrectOption("A");
        doThrow(new InsufficientPermissionException("仅教师可创建题目"))
                .when(userPermissionUtil).checkTeacherPermission(teacherId);

        // When & Then
        assertThatThrownBy(() -> questionService.createQuestion(request, teacherId))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository, never()).existsByContent(any());
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void createQuestion_shouldPersistOptionalFields_whenProvided() {
        // Given
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setContent("包含可选字段的题目");
        request.setOptions("{\"A\":\"甲\",\"B\":\"乙\"}");
        request.setCorrectOption("B");
        request.setExplanation("可选解析");
        request.setDifficulty(2);
        request.setImageBase64("base64-image");

        when(questionRepository.existsByContent("包含可选字段的题目")).thenReturn(false);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(1010L);
            return question;
        });

        // When
        Question result = questionService.createQuestion(request, teacherId);

        // Then
        assertThat(result.getImageBase64()).isEqualTo("base64-image");
    }

    // ==================== deleteQuestionById 测试 ====================

    @Test
    void deleteQuestionById_shouldDelete_whenQuestionExists() {
        // Given
        Long questionId = 1000L;
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));

        // When
        questionService.deleteQuestionById(questionId, teacherId);

        // Then
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).findById(questionId);
        verify(questionRepository).delete(mockQuestion);
    }

    @Test
    void deleteQuestionById_shouldThrowQuestionNotFoundException_whenNotFound() {
        // Given
        Long questionId = 9999L;
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> questionService.deleteQuestionById(questionId, teacherId))
                .isInstanceOf(QuestionNotFoundException.class)
                .hasMessage("题目不存在，ID: 9999");

        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).findById(questionId);
        verify(questionRepository, never()).delete(any(Question.class));
    }

    @Test
    void deleteQuestionById_shouldThrowInsufficientPermissionException_whenPermissionDenied() {
        // Given
        Long questionId = 1000L;
        doThrow(new InsufficientPermissionException("仅教师可删除题目"))
                .when(userPermissionUtil).checkTeacherPermission(teacherId);

        // When & Then
        assertThatThrownBy(() -> questionService.deleteQuestionById(questionId, teacherId))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository, never()).findById(anyLong());
        verify(questionRepository, never()).delete(any(Question.class));
    }

    // ==================== updateQuestion 测试 ====================

    @Test
    void updateQuestion_shouldFullUpdate_whenValid() {
        // Given
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setId(1000L);
        request.setContent("更新后的题目内容");
        request.setOptions("{\"A\":\"新选项 A\",\"B\":\"新选项 B\",\"C\":\"新选项 C\",\"D\":\"新选项 D\"}");
        request.setCorrectOption("B");
        request.setExplanation("更新后的解析");
        request.setDifficulty(4);
    
        Question existingQuestion = TestDataFactory.createQuestion();
        existingQuestion.setId(1000L);
    
        when(questionRepository.findById(1000L)).thenReturn(Optional.of(existingQuestion));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));
    
        // When
        Question result = questionService.updateQuestion(request, teacherId);
    
        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Question::getContent, Question::getCorrectOption, Question::getDifficulty)
                .containsExactly("更新后的题目内容", "B", 4);
            
        assertThat(result.getOptions()).isEqualTo("{\"A\":\"新选项 A\",\"B\":\"新选项 B\",\"C\":\"新选项 C\",\"D\":\"新选项 D\"}");
            
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).findById(1000L);
        verify(questionRepository).save(existingQuestion);
    }

    @Test
    void updateQuestion_shouldThrowQuestionNotFoundException_whenQuestionNotFound() {
        // Given
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setId(9999L);
        request.setContent("题目内容");
        request.setOptions("{\"A\":\"选项 A\",\"B\":\"选项 B\",\"C\":\"选项 C\",\"D\":\"选项 D\"}");
        request.setCorrectOption("A");
    
        when(questionRepository.findById(9999L)).thenReturn(Optional.empty());
    
        // When & Then
        assertThatThrownBy(() -> questionService.updateQuestion(request, teacherId))
                .isInstanceOf(QuestionNotFoundException.class)
                .hasMessage("题目不存在，ID: 9999");
    
        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository).findById(9999L);
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void updateQuestion_shouldThrowInsufficientPermissionException_whenPermissionDenied() {
        // Given
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setId(1000L);
        doThrow(new InsufficientPermissionException("仅教师可更新题目"))
                .when(userPermissionUtil).checkTeacherPermission(teacherId);

        // When & Then
        assertThatThrownBy(() -> questionService.updateQuestion(request, teacherId))
                .isInstanceOf(InsufficientPermissionException.class);

        verify(userPermissionUtil).checkTeacherPermission(teacherId);
        verify(questionRepository, never()).findById(anyLong());
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void updateQuestion_shouldSetNullFields_whenRequestContainsNullValues() {
        // Given
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setId(1000L);
        request.setContent(null);
        request.setOptions(null);
        request.setCorrectOption(null);
        request.setExplanation(null);
        request.setDifficulty(null);
        request.setImageBase64(null);

        Question existingQuestion = TestDataFactory.createQuestion();
        existingQuestion.setId(1000L);
        existingQuestion.setContent("旧内容");
        existingQuestion.setImageBase64("old-image");

        when(questionRepository.findById(1000L)).thenReturn(Optional.of(existingQuestion));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Question result = questionService.updateQuestion(request, teacherId);

        // Then
        assertThat(result.getContent()).isNull();
        assertThat(result.getOptions()).isNull();
        assertThat(result.getCorrectOption()).isNull();
        assertThat(result.getExplanation()).isNull();
        assertThat(result.getDifficulty()).isNull();
        assertThat(result.getImageBase64()).isNull();
    }

    @Test
    void updateQuestion_shouldOverwriteImage_whenProvided() {
        // Given
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setId(1000L);
        request.setContent("更新内容");
        request.setOptions("{\"A\":\"新 A\",\"B\":\"新 B\"}");
        request.setCorrectOption("A");
        request.setExplanation("更新解析");
        request.setDifficulty(5);
        request.setImageBase64("new-image-base64");

        Question existingQuestion = TestDataFactory.createQuestion();
        existingQuestion.setId(1000L);
        existingQuestion.setImageBase64("old-image");

        when(questionRepository.findById(1000L)).thenReturn(Optional.of(existingQuestion));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Question result = questionService.updateQuestion(request, teacherId);

        // Then
        assertThat(result.getImageBase64()).isEqualTo("new-image-base64");
    }

    

    // ==================== getQuestionById 测试 ====================

    @Test
    void getQuestionById_shouldReturnQuestion() {
        // Given
        Long questionId = 1000L;
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));

        // When
        Question result = questionService.getQuestionById(questionId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(Question::getId, Question::getContent)
                .containsExactly(1000L, mockQuestion.getContent());

        verify(questionRepository).findById(questionId);
    }

    @Test
    void getQuestionById_shouldThrowQuestionNotFoundException_whenNotFound() {
        // Given
        Long questionId = 9999L;
        when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> questionService.getQuestionById(questionId))
                .isInstanceOf(QuestionNotFoundException.class)
                .hasMessage("题目不存在，ID: 9999");

        verify(questionRepository).findById(questionId);
    }

    // ==================== getAllQuestions 测试 ====================

    @Test
    void getAllQuestions_shouldReturnListSortedByCreatedAt() {
        // Given
        List<Question> mockQuestions = List.of(
                TestDataFactory.createQuestion(),
                TestDataFactory.createQuestion(),
                TestDataFactory.createQuestion());

        // 设置不同的ID以便区分
        mockQuestions.get(0).setId(1001L);
        mockQuestions.get(1).setId(1002L);
        mockQuestions.get(2).setId(1003L);

        when(questionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(mockQuestions);

        // When
        List<Question> result = questionService.getAllQuestions(teacherId);

        // Then
        assertThat(result)
                .hasSize(3)
                .extracting(Question::getId)
                .containsExactly(1001L, 1002L, 1003L);

        // 注意：getAllQuestions 不再需要教师权限，任何认证用户都可以访问
        verify(questionRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getAllQuestions_shouldReturnEmptyList_whenNoQuestions() {
        // Given
        when(questionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        // When
        List<Question> result = questionService.getAllQuestions(teacherId);

        // Then
        assertThat(result).isEmpty();

        // 注意：getAllQuestions 不再需要教师权限，任何认证用户都可以访问
        verify(questionRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void getAllQuestions_shouldNotCheckTeacherPermission() {
        // Given
        when(questionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(TestDataFactory.createQuestion()));

        // When
        List<Question> result = questionService.getAllQuestions(100L);

        // Then
        assertThat(result).hasSize(1);
        verifyNoInteractions(userPermissionUtil);
    }

    @Test
    void getAllQuestions_shouldAllowStudentAccess() {
        // Given
        Long studentId = 100L;
        when(questionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(TestDataFactory.createQuestion()));

        // When
        List<Question> result = questionService.getAllQuestions(studentId);

        // Then
        assertThat(result).hasSize(1);
        verify(questionRepository).findAllByOrderByCreatedAtDesc();
    }
}