package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.dto.request.TeacherEventCreateRequest;
import com.zhixuewuli.dto.response.TeacherEventResponse;
import com.zhixuewuli.entity.TeacherEvent;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.StudentReportRepository;
import com.zhixuewuli.repository.TeacherEventRepository;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.teacherEvent.impl.TeacherEventServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("unit")
class TeacherEventServiceUnitTest {
    @Mock private com.zhixuewuli.service.user.ClassroomGuard classroomGuard;
    @Mock private com.zhixuewuli.service.teacherEvent.TaskRosterService roster;
    @Mock private com.zhixuewuli.repository.TaskRecipientRepository recipients;
    @Mock private com.zhixuewuli.service.ai.report.SavedReportReader savedReports;


    @Mock
    private TeacherEventRepository teacherEventRepository;

    @Mock
    private UserRepository userRepository;


    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private ConversationHistoryRepository conversationHistoryRepository;

    @Mock
    private StudentReportRepository studentReportRepository;

    @Mock
    private com.zhixuewuli.service.teacherEvent.TaskQuestionService taskQuestions;

    @InjectMocks
    private TeacherEventServiceImpl teacherEventService;

    private Long teacherId;
    private User mockTeacher;
    private TeacherEvent mockEvent;
    private TeacherEventCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        teacherId = 1001L;

        mockTeacher = new User();
        mockTeacher.setId(teacherId);
        mockTeacher.setUsername("teacher001");
        mockTeacher.setRealName("张老师");
        mockTeacher.setUserType(User.UserType.TEACHER);

        mockEvent = new TeacherEvent();
        mockEvent.setId(2001L);
        mockEvent.setTeacherId(teacherId);
        mockEvent.setTeacherName("张老师");
        mockEvent.setEventTitle("新学期开始");
        mockEvent.setEventDescription("2026 年春季学期正式开始");
        mockEvent.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));
        mockEvent.setIsGlobal(true);

        createRequest = new TeacherEventCreateRequest();
        createRequest.setEventTitle("新学期开始");
        createRequest.setEventDescription("2026 年春季学期正式开始");
        createRequest.setEventTimestamp(LocalDateTime.of(2026, 3, 1, 8, 0, 0));
    }

    @Test
    void replacementTakesClassroomGuardBeforeEventLockToAvoidEnrollmentDeadlock() {
        when(teacherEventRepository.lockById(2001L)).thenReturn(Optional.of(mockEvent));
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.saveAndFlush(any(TeacherEvent.class))).thenReturn(mockEvent);
        teacherEventService.replaceEvent(2001L,createRequest,teacherId);
        var locks=inOrder(classroomGuard,teacherEventRepository);
        locks.verify(classroomGuard).lock();
        locks.verify(teacherEventRepository).lockById(2001L);
    }

    @Test
    void publishEvent_shouldCreateEvent_whenValidRequest() {
        // Given
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.saveAndFlush(any(TeacherEvent.class))).thenReturn(mockEvent);

        // When
        TeacherEventResponse result = teacherEventService.publishEvent(createRequest, teacherId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2001L);
        assertThat(result.getTeacherId()).isEqualTo(teacherId);
        assertThat(result.getTeacherName()).isEqualTo("张老师");
        assertThat(result.getEventTitle()).isEqualTo("新学期开始");
        assertThat(result.getEventDescription()).isEqualTo("2026 年春季学期正式开始");

        verify(userRepository).findById(teacherId);
        verify(teacherEventRepository).saveAndFlush(any(TeacherEvent.class));
    }


    @Test
    void publishEvent_shouldAllowParallelTasks_whenPublishedTaskExists() {
        when(userRepository.findById(teacherId)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.saveAndFlush(any(TeacherEvent.class))).thenReturn(mockEvent);

        TeacherEventResponse result = teacherEventService.publishEvent(createRequest, teacherId);

        assertThat(result).isNotNull();
        verify(userRepository).findById(teacherId);
        verify(teacherEventRepository).saveAndFlush(any(TeacherEvent.class));
    }

    @Test
    void publishEvent_shouldThrowUserNotFoundException_whenTeacherNotFound() {
        // Given
        when(userRepository.findById(teacherId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> teacherEventService.publishEvent(createRequest, teacherId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository).findById(teacherId);
        verify(teacherEventRepository, never()).saveAndFlush(any(TeacherEvent.class));
    }

    @Test
    void deleteCurrentEvent_shouldDeleteEvent_whenEventExists() {
        // Given
        when(teacherEventRepository.findByIsGlobalTrue()).thenReturn(Optional.of(mockEvent));

        // When
        teacherEventService.deleteCurrentEvent();

        // Then
        assertThat(mockEvent.getIsGlobal()).isFalse();
        verify(teacherEventRepository).saveAndFlush(mockEvent);
        verify(answerRecordRepository, never()).deleteAll();
        verify(conversationHistoryRepository, never()).deleteAll();
        verify(studentReportRepository, never()).deleteAll();
    }

    @Test
    void deleteCurrentEvent_shouldDoNothing_whenEventNotExists() {
        // Given
        when(teacherEventRepository.findByIsGlobalTrue()).thenReturn(Optional.empty());

        // When
        teacherEventService.deleteCurrentEvent();

        // Then
        verify(teacherEventRepository, never()).deleteAllEvents();
        verify(answerRecordRepository, never()).deleteAll();
        verify(conversationHistoryRepository, never()).deleteAll();
        verify(studentReportRepository, never()).deleteAll();
    }


    @Test
    void getGlobalEvent_shouldReturnEvent_whenEventExists() {
        // Given
        when(teacherEventRepository.findByIsGlobalTrue()).thenReturn(Optional.of(mockEvent));

        // When
        TeacherEventResponse result = teacherEventService.getGlobalEvent();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(2001L);
        assertThat(result.getTeacherId()).isEqualTo(teacherId);
        assertThat(result.getTeacherName()).isEqualTo("张老师");
        assertThat(result.getEventTitle()).isEqualTo("新学期开始");

        verify(teacherEventRepository).findByIsGlobalTrue();
    }

    @Test
    void getGlobalEvent_shouldReturnNull_whenEventNotExists() {
        // Given
        when(teacherEventRepository.findByIsGlobalTrue()).thenReturn(Optional.empty());

        // When
        TeacherEventResponse result = teacherEventService.getGlobalEvent();

        // Then
        assertThat(result).isNull();

        verify(teacherEventRepository).findByIsGlobalTrue();
    }

    @Test
    void publishEvent_shouldHandleNullDescription() {
        // Given
        createRequest.setEventDescription(null);

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.saveAndFlush(any(TeacherEvent.class))).thenReturn(mockEvent);

        // When
        TeacherEventResponse result = teacherEventService.publishEvent(createRequest, teacherId);

        // Then
        assertThat(result).isNotNull();
        verify(teacherEventRepository).saveAndFlush(any(TeacherEvent.class));
    }

    @Test
    void publishEvent_shouldHandleEmptyDescription() {
        // Given
        createRequest.setEventDescription("");

        when(userRepository.findById(teacherId)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.saveAndFlush(any(TeacherEvent.class))).thenReturn(mockEvent);

        // When
        TeacherEventResponse result = teacherEventService.publishEvent(createRequest, teacherId);

        // Then
        assertThat(result).isNotNull();
        verify(teacherEventRepository).saveAndFlush(any(TeacherEvent.class));
    }
}
