package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.common.util.QuestionResponseConverter;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.AnswerRecordSubmitRequest;
import com.zhixuewuli.dto.request.RecordBatchDeleteRequest;
import com.zhixuewuli.dto.response.AnswerRecordBatchResponse;
import com.zhixuewuli.dto.response.AnswerRecordResponse;
import com.zhixuewuli.dto.response.AnswerStatsResponse;
import com.zhixuewuli.entity.AnswerRecord;
import com.zhixuewuli.entity.Question;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.answerRecord.impl.AnswerRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 答题记录服务单元测试
 * 
 * 测试AnswerRecordService的所有核心业务逻辑，包括：
 * - 答题记录提交（submitAnswerRecord）
 * - 答题记录查询（各种维度的查询）
 * - 权限控制验证
 * - 教师专属功能（删除、批量删除、统计等）
 * - 会话维度查询功能
 * 
 * 遵循Given-When-Then测试模式，使用Mockito进行依赖模拟
 */
class AnswerRecordServiceUnitTest extends BaseUnitTest {

        @Mock
        private AnswerRecordRepository answerRecordRepository;

        @Mock
        private QuestionRepository questionRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private UserPermissionUtil userPermissionUtil;

        @Mock
        private QuestionResponseConverter questionResponseConverter;

        @Mock
    private com.zhixuewuli.repository.TeacherEventRepository teacherEventRepository;

    @InjectMocks
        private AnswerRecordServiceImpl answerRecordService;

        private AnswerRecord mockAnswerRecord;
        private Question mockQuestion;
        private User mockUser;
        private String recordId = UUID.randomUUID().toString();
        private Long userId = 1001L;
        private Long questionId = 2001L;
        private String sessionId = "test-session-123";

        @BeforeEach
        void setUp() {
                // 初始化测试数据
                mockAnswerRecord = TestDataFactory.createAnswerRecord(userId, questionId);
                mockAnswerRecord.setId(recordId);

                mockQuestion = TestDataFactory.createQuestion();
                mockQuestion.setId(questionId);

                mockUser = TestDataFactory.createUser();
                mockUser.setId(userId);
                mockUser.setUserType(User.UserType.STUDENT);
        }

        // ==================== submitAnswerRecord 测试 ====================

        @Test
        void submitAnswerRecord_shouldCreate_whenValidInput() {
                // Given
                AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
                request.setQuestionId(questionId);
                request.setUserAnswer("A");
                request.setErrorReason("概念理解不够深入");
                request.setAnsweredAt(LocalDateTime.now());

                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(answerRecordRepository.save(any(AnswerRecord.class))).thenAnswer(invocation -> {
                        AnswerRecord record = invocation.getArgument(0);
                        record.setId(UUID.randomUUID().toString());
                        return record;
                });

                AnswerRecordResponse mockResponse = new AnswerRecordResponse();
                mockResponse.setAnsweredAt(request.getAnsweredAt());
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(mockResponse);

                // When
                AnswerRecordResponse result = answerRecordService.submitAnswerRecord(request, userId, sessionId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getAnsweredAt()).isNotNull();

                verify(questionRepository).findById(questionId);
                verify(answerRecordRepository).save(any(AnswerRecord.class));
        }

        @Test
        void submitAnswerRecord_shouldThrowQuestionNotFoundException_whenQuestionNotExists() {
                // Given
                AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
                request.setQuestionId(questionId);
                request.setUserAnswer("A");

                when(questionRepository.findById(questionId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.submitAnswerRecord(request, userId, sessionId))
                                .isInstanceOf(com.zhixuewuli.common.exception.answerrecord.AnswerRecordProcessingException.class)
                                .hasMessageContaining("题目不存在");

                verify(questionRepository).findById(questionId);
                verify(answerRecordRepository, never()).save(any(AnswerRecord.class));
        }

        @Test
        void submitAnswerRecord_shouldMarkCorrect_whenAnswerMatches() {
                // Given
                AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
                request.setQuestionId(questionId);
                request.setUserAnswer("A"); // 与题目正确答案一致

                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(answerRecordRepository.save(any(AnswerRecord.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                answerRecordService.submitAnswerRecord(request, userId, sessionId);

                // Then
                verify(answerRecordRepository)
                                .save(argThat(record -> record.getCorrect() != null && record.getCorrect()));
        }

        @Test
        void submitAnswerRecord_shouldMarkIncorrect_whenAnswerMismatch() {
                // Given
                AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
                request.setQuestionId(questionId);
                request.setUserAnswer("B"); // 与题目正确答案"A"不一致

                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(answerRecordRepository.save(any(AnswerRecord.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                answerRecordService.submitAnswerRecord(request, userId, sessionId);

                // Then
                verify(answerRecordRepository)
                                .save(argThat(record -> record.getCorrect() != null && !record.getCorrect()));
        }

        @Test
        void submitAnswerRecord_shouldThrowAnswerRecordProcessingException_whenRepositorySaveFails() {
                // Given
                AnswerRecordSubmitRequest request = new AnswerRecordSubmitRequest();
                request.setQuestionId(questionId);
                request.setUserAnswer("A");

                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(answerRecordRepository.save(any(AnswerRecord.class)))
                                .thenThrow(new RuntimeException("db save failed"));

                // When & Then
                assertThatThrownBy(() -> answerRecordService.submitAnswerRecord(request, userId, sessionId))
                                .isInstanceOf(com.zhixuewuli.common.exception.answerrecord.AnswerRecordProcessingException.class)
                                .hasMessageContaining("答题记录提交失败");
        }

        // ==================== getAnswerRecordById 测试 ====================

        @Test
        void getAnswerRecordById_shouldReturnRecord_whenOwnRecord() {
                // Given
                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.of(mockAnswerRecord));
                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(mockAnswerRecord, mockQuestion))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordResponse result = answerRecordService.getAnswerRecordById(recordId, userId);

                // Then
                assertThat(result).isNotNull();
                verify(answerRecordRepository).findById(recordId);
                verify(questionRepository).findById(questionId);
        }

        @Test
        void getAnswerRecordById_shouldThrowInsufficientPermissionException_whenNotOwnRecordAndNotTeacher() {
                // Given
                Long otherUserId = 999L;
                mockAnswerRecord.setUserId(otherUserId);

                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.of(mockAnswerRecord));
                when(userPermissionUtil.isTeacherUser(userId)).thenReturn(false);

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getAnswerRecordById(recordId, userId))
                                .isInstanceOf(InsufficientPermissionException.class)
                                .hasMessageContaining("学生只能访问自己的答题记录");

                verify(answerRecordRepository).findById(recordId);
                verify(userPermissionUtil).isTeacherUser(userId);
        }

        @Test
        void getAnswerRecordById_shouldReturnRecord_whenNotOwnButTeacher() {
                // Given
                Long otherUserId = 999L;
                mockAnswerRecord.setUserId(otherUserId);

                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.of(mockAnswerRecord));
                when(userPermissionUtil.isTeacherUser(userId)).thenReturn(true);
                when(questionRepository.findById(questionId)).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(mockAnswerRecord, mockQuestion))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordResponse result = answerRecordService.getAnswerRecordById(recordId, userId);

                // Then
                assertThat(result).isNotNull();
                verify(userPermissionUtil).isTeacherUser(userId);
        }

        @Test
        void getAnswerRecordById_shouldThrowAnswerRecordNotFoundException_whenRecordNotExists() {
                // Given
                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getAnswerRecordById(recordId, userId))
                                .isInstanceOf(AnswerRecordNotFoundException.class)
                                .hasMessageContaining("答题记录不存在");

                verify(answerRecordRepository).findById(recordId);
                verify(questionRepository, never()).findById(any());
        }

        // ==================== getCurrentUserWrongAnswers 测试 ====================

        @Test
        void getCurrentUserWrongAnswers_shouldReturnWrongAnswers() {
                // Given
                List<AnswerRecord> wrongAnswers = List.of(
                                TestDataFactory.createAnswerRecord(userId, questionId),
                                TestDataFactory.createAnswerRecord(userId, questionId + 1));
                wrongAnswers.forEach(record -> record.setCorrect(false));

                when(answerRecordRepository.findWrongAnswersByUserId(userId)).thenReturn(wrongAnswers);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getCurrentUserWrongAnswers(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(2L);
                assertThat(result.getRecords()).hasSize(2);

                verify(answerRecordRepository).findWrongAnswersByUserId(userId);
                verify(questionRepository, times(2)).findById(any());
        }

        // ==================== getCurrentUserAnswerRecords 测试 ====================

        @Test
        void getCurrentUserAnswerRecords_shouldReturnAllRecords() {
                // Given
                List<AnswerRecord> records = List.of(
                                TestDataFactory.createAnswerRecord(userId, questionId),
                                TestDataFactory.createAnswerRecord(userId, questionId + 1));

                when(answerRecordRepository.findByUserIdOrderByAnsweredAtDesc(userId)).thenReturn(records);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getCurrentUserAnswerRecords(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(2L);
                assertThat(result.getRecords()).hasSize(2);

                verify(answerRecordRepository).findByUserIdOrderByAnsweredAtDesc(userId);
        }

        // ==================== deleteAnswerRecord 测试 ====================

        @Test
        void deleteAnswerRecord_shouldDelete_whenTeacherAndRecordExists() {
                // Given
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.of(mockAnswerRecord));

                // When
                answerRecordService.deleteAnswerRecord(recordId, userId);

                // Then
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository).findById(recordId);
                verify(answerRecordRepository).deleteById(recordId);
        }

        @Test
        void deleteAnswerRecord_shouldThrowInsufficientPermissionException_whenNotTeacher() {
                // Given
                doThrow(new InsufficientPermissionException(userId, "需要教师权限"))
                                .when(userPermissionUtil).checkTeacherPermission(userId);

                // When & Then
                assertThatThrownBy(() -> answerRecordService.deleteAnswerRecord(recordId, userId))
                                .isInstanceOf(InsufficientPermissionException.class);

                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository, never()).findById(any());
                verify(answerRecordRepository, never()).deleteById(any());
        }

        @Test
        void deleteAnswerRecord_shouldThrowAnswerRecordNotFoundException_whenRecordNotExists() {
                // Given
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.findById(recordId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.deleteAnswerRecord(recordId, userId))
                                .isInstanceOf(AnswerRecordNotFoundException.class);

                verify(answerRecordRepository).findById(recordId);
                verify(answerRecordRepository, never()).deleteById(any());
        }

        // ==================== batchDeleteByTimeRange 测试 ====================

        @Test
        void batchDeleteByTimeRange_shouldDelete_whenValidRequest() {
                // Given
                RecordBatchDeleteRequest request = new RecordBatchDeleteRequest();
                LocalDateTime startTime = LocalDateTime.now().minusDays(7);
                LocalDateTime endTime = LocalDateTime.now();
                request.setStartTime(startTime);
                request.setEndTime(endTime);

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.deleteByTimeRange(startTime, endTime)).thenReturn(5);

                // When
                answerRecordService.batchDeleteByTimeRange(request, userId);

                // Then
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository).deleteByTimeRange(startTime, endTime);
        }

        @Test
        void batchDeleteByTimeRange_shouldThrowBusinessException_whenStartTimeAfterEndTime() {
                // Given
                RecordBatchDeleteRequest request = new RecordBatchDeleteRequest();
                LocalDateTime startTime = LocalDateTime.now();
                LocalDateTime endTime = LocalDateTime.now().minusDays(1); // 结束时间早于开始时间
                request.setStartTime(startTime);
                request.setEndTime(endTime);

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);

                // When & Then
                assertThatThrownBy(() -> answerRecordService.batchDeleteByTimeRange(request, userId))
                                .isInstanceOf(BusinessException.class)
                                .hasMessage("批量删除的开始时间不能晚于结束时间");

                verify(answerRecordRepository, never()).deleteByTimeRange(any(), any());
        }

        // ==================== countTotalAnswers 测试 ====================

        @Test
        void countTotalAnswers_shouldReturnCount_whenTeacher() {
                // Given
                Long expectedCount = 150L;
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.count()).thenReturn(expectedCount);

                // When
                Long result = answerRecordService.countTotalAnswers(userId);

                // Then
                assertThat(result).isEqualTo(expectedCount);
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository).count();
        }

        // ==================== getAllUsersAnswerRecords 测试 ====================

        @Test
        void getAllUsersAnswerRecords_shouldReturnAllRecords_whenTeacher() {
                // Given
                List<AnswerRecord> allRecords = List.of(
                                TestDataFactory.createAnswerRecord(1001L, questionId),
                                TestDataFactory.createAnswerRecord(1002L, questionId + 1));

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.findAll()).thenReturn(allRecords);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getAllUsersAnswerRecords(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(2L);
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository).findAll();
        }

        // ==================== getSessionAnswerRecords 测试 ====================

        @Test
        void getSessionAnswerRecords_shouldReturnRecords_whenValidSession() {
                // Given
                List<AnswerRecord> sessionRecords = List.of(
                                TestDataFactory.createAnswerRecord(userId, questionId));

                when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
                when(answerRecordRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(sessionRecords);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getSessionAnswerRecords(sessionId, userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(1L);
                verify(answerRecordRepository, times(2)).findBySessionIdAndUserId(sessionId, userId);
        }

        @Test
        void getSessionAnswerRecords_shouldThrowInsufficientPermissionException_whenNoRecordsForStudent() {
                // Given
                when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
                when(answerRecordRepository.findBySessionIdAndUserId(sessionId, userId)).thenReturn(List.of());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getSessionAnswerRecords(sessionId, userId))
                                .isInstanceOf(com.zhixuewuli.common.exception.InsufficientPermissionException.class);

                verify(answerRecordRepository).findBySessionIdAndUserId(sessionId, userId);
        }

        @Test
        void getSessionAnswerRecords_shouldUseSessionWideQuery_whenCurrentUserIsTeacher() {
                // Given
                User teacher = TestDataFactory.createUser();
                teacher.setId(userId);
                teacher.setUserType(User.UserType.TEACHER);

                List<AnswerRecord> sessionRecords = List.of(
                                TestDataFactory.createAnswerRecord(1001L, questionId),
                                TestDataFactory.createAnswerRecord(1002L, questionId + 1));

                when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
                when(answerRecordRepository.findBySessionIdOrderByAnsweredAtDesc(sessionId)).thenReturn(sessionRecords);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getSessionAnswerRecords(sessionId, userId);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(2L);
                verify(answerRecordRepository).findBySessionIdOrderByAnsweredAtDesc(sessionId);
                verify(answerRecordRepository, never()).findBySessionIdAndUserId(sessionId, userId);
        }

        // ==================== getStudentLatestSessionRecords 测试 ====================

        @Test
        void getStudentLatestSessionRecords_shouldReturnRecords_whenValidStudent() {
                // Given
                Long studentId = 2001L;
                String latestSessionId = "latest-session-456";
                List<AnswerRecord> latestRecords = List.of(
                                TestDataFactory.createAnswerRecord(studentId, questionId));

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(userRepository.findById(studentId)).thenReturn(Optional.of(mockUser));
                when(answerRecordRepository.findDistinctSessionIdsByUserId(studentId))
                                .thenReturn(List.of(latestSessionId));
                when(answerRecordRepository.findBySessionIdAndUserId(latestSessionId, studentId))
                                .thenReturn(latestRecords);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getStudentLatestSessionRecords(studentId,
                                userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(1L);
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(userRepository).findById(studentId);
                verify(answerRecordRepository).findDistinctSessionIdsByUserId(studentId);
        }

        @Test
        void getStudentLatestSessionRecords_shouldThrowUserNotFoundException_whenStudentNotExists() {
                // Given
                Long studentId = 9999L;
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(userRepository.findById(studentId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getStudentLatestSessionRecords(studentId, userId))
                                .isInstanceOf(UserNotFoundException.class)
                                .hasMessage("学生不存在");

                verify(userRepository).findById(studentId);
                verify(answerRecordRepository, never()).findDistinctSessionIdsByUserId(any());
        }

        @Test
        void getStudentLatestSessionRecords_shouldReturnEmpty_whenStudentHasNoSessions() {
                // Given
                Long studentId = 3001L;
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(userRepository.findById(studentId)).thenReturn(Optional.of(mockUser));
                when(answerRecordRepository.findDistinctSessionIdsByUserId(studentId)).thenReturn(List.of());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getStudentLatestSessionRecords(studentId, userId);

                // Then
                assertThat(result.getTotalCount()).isEqualTo(0L);
                assertThat(result.getRecords()).isEmpty();
                verify(answerRecordRepository, never()).findBySessionIdAndUserId(any(), eq(studentId));
        }

        // ==================== getStudentLatestSessionRecordsByName 测试
        // ====================

        @Test
        void getStudentLatestSessionRecordsByName_shouldReturnRecords_whenValidName() {
                // Given
                String studentName = "张三";
                Long studentId = 2001L;
                String latestSessionId = "latest-session-789";
                List<AnswerRecord> latestRecords = List.of(
                                TestDataFactory.createAnswerRecord(studentId, questionId));

                mockUser.setId(studentId);
                mockUser.setRealName(studentName);

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(userRepository.findByRealName(studentName)).thenReturn(Optional.of(mockUser));
                when(answerRecordRepository.findDistinctSessionIdsByUserId(studentId))
                                .thenReturn(List.of(latestSessionId));
                when(answerRecordRepository.findBySessionIdAndUserId(latestSessionId, studentId))
                                .thenReturn(latestRecords);
                when(questionRepository.findById(any())).thenReturn(Optional.of(mockQuestion));
                when(questionResponseConverter.convertToFullResponseDTO(any(), any()))
                                .thenReturn(new AnswerRecordResponse());

                // When
                AnswerRecordBatchResponse result = answerRecordService.getStudentLatestSessionRecordsByName(studentName,
                                userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getTotalCount()).isEqualTo(1L);
                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(userRepository).findByRealName(studentName);
        }

        @Test
        void getStudentLatestSessionRecordsByName_shouldThrowUserNotFoundException_whenStudentNotExists() {
                // Given
                String studentName = "不存在的学生";
                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(userRepository.findByRealName(studentName)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getStudentLatestSessionRecordsByName(studentName, userId))
                                .isInstanceOf(UserNotFoundException.class)
                                .hasMessage("学生不存在");

                verify(userRepository).findByRealName(studentName);
                verify(answerRecordRepository, never()).findDistinctSessionIdsByUserId(any());
        }

        // ==================== getAnswerStats 测试 ====================

        @Test
        void getAnswerStats_shouldReturnStats_whenTeacher() {
                // Given
                long answeredCount = 45L;
                long totalCount = 50L;
                double expectedRate = 90.0;

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.countDistinctUserIds()).thenReturn(answeredCount);
                when(userRepository.countByUserType(User.UserType.STUDENT)).thenReturn(totalCount);

                // When
                AnswerStatsResponse result = answerRecordService.getAnswerStats(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getAnsweredStudentCount()).isEqualTo(answeredCount);
                assertThat(result.getTotalStudentCount()).isEqualTo(totalCount);
                assertThat(result.getCoverageRate()).isEqualTo(expectedRate);

                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository).countDistinctUserIds();
                verify(userRepository).countByUserType(User.UserType.STUDENT);
        }

        @Test
        void getAnswerStats_shouldReturnZeroCoverage_whenNoStudents() {
                // Given
                long answeredCount = 0L;
                long totalCount = 0L;

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.countDistinctUserIds()).thenReturn(answeredCount);
                when(userRepository.countByUserType(User.UserType.STUDENT)).thenReturn(totalCount);

                // When
                AnswerStatsResponse result = answerRecordService.getAnswerStats(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getAnsweredStudentCount()).isEqualTo(0L);
                assertThat(result.getTotalStudentCount()).isEqualTo(0L);
                assertThat(result.getCoverageRate()).isEqualTo(0.0);
        }

        @Test
        void getAnswerStats_shouldReturnZeroCoverage_whenNoAnswerRecords() {
                // Given
                long answeredCount = 0L;
                long totalCount = 30L;

                doNothing().when(userPermissionUtil).checkTeacherPermission(userId);
                when(answerRecordRepository.countDistinctUserIds()).thenReturn(answeredCount);
                when(userRepository.countByUserType(User.UserType.STUDENT)).thenReturn(totalCount);

                // When
                AnswerStatsResponse result = answerRecordService.getAnswerStats(userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getAnsweredStudentCount()).isEqualTo(0L);
                assertThat(result.getTotalStudentCount()).isEqualTo(30L);
                assertThat(result.getCoverageRate()).isEqualTo(0.0);
        }

        @Test
        void getAnswerStats_shouldThrowInsufficientPermissionException_whenNotTeacher() {
                // Given
                doThrow(new InsufficientPermissionException(userId, "需要教师权限"))
                                .when(userPermissionUtil).checkTeacherPermission(userId);

                // When & Then
                assertThatThrownBy(() -> answerRecordService.getAnswerStats(userId))
                                .isInstanceOf(InsufficientPermissionException.class);

                verify(userPermissionUtil).checkTeacherPermission(userId);
                verify(answerRecordRepository, never()).countDistinctUserIds();
                verify(userRepository, never()).countByUserType(any());
        }
}