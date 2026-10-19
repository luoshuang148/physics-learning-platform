package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.auth.RealNameDuplicateException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.auth.UserNameDuplicateException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.dto.request.UserSelfUpdateRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.user.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试
 * 
 * 测试UserService的所有核心业务逻辑，包括：
 * - 当前用户信息获取
 * - 用户自我更新
 * - 教师查询用户信息
 * - 管理员更新用户
 * - 用户删除功能
 * 
 * 遵循Given-When-Then测试模式，使用Mockito进行依赖模拟
 */
class UserServiceUnitTest extends BaseUnitTest {
    @Mock private com.zhixuewuli.service.user.ClassroomGuard classroomGuard;
    @Mock private com.zhixuewuli.service.teacherEvent.TaskRosterService roster;
    @Mock private com.zhixuewuli.repository.TaskRecipientRepository recipients;


    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;


    @Mock
    private com.zhixuewuli.repository.TeacherEventRepository teacherEventRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockStudent;
    private User mockTeacher;

    @BeforeEach
    void setUp() {
        mockStudent = TestDataFactory.createUser();
        mockStudent.setId(100L);
        mockStudent.setUserType(User.UserType.STUDENT);
        mockStudent.setUsername("student001");
        mockStudent.setRealName("学生一号");

        mockTeacher = TestDataFactory.createUser();
        mockTeacher.setId(110L);
        mockTeacher.setUserType(User.UserType.TEACHER);
        mockTeacher.setUsername("teacher001");
        mockTeacher.setRealName("教师一号");
    }

    @Test
    void cannotDeletePublisherWithRetainedTeachingTasks() {
        when(userRepository.findById(110L)).thenReturn(Optional.of(mockTeacher));
        when(teacherEventRepository.existsByTeacherId(110L)).thenReturn(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> userService.deleteUser(110L, 110L))
                .isInstanceOf(com.zhixuewuli.common.exception.BusinessException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    // ==================== getCurrentUser 测试 ====================

    @Test
    void getCurrentUser_shouldReturnUser_whenUserExists() {
        // Given
        Long userId = 100L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));

        // When
        User result = userService.getCurrentUser(userId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(User::getId, User::getUsername, User::getUserType)
                .containsExactly(100L, "student001", User.UserType.STUDENT);

        verify(userRepository).findById(userId);
    }

    @Test
    void getCurrentUser_shouldThrowUserNotFoundException_whenUserNotFound() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository).findById(userId);
    }

    // ==================== updateCurrentUser 测试 ====================

    @Test
    void updateCurrentUser_shouldUpdateUsername_whenValidRequest() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("new_username");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByUsername("new_username")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getUsername()).isEqualTo("new_username");
        verify(userRepository).existsByUsername("new_username");
        verify(userRepository, never()).existsByRealName("new_username");
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldThrowUserNameDuplicateException_whenUsernameExists() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("existing_username");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByUsername("existing_username")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.updateCurrentUser(userId, request))
                .isInstanceOf(UserNameDuplicateException.class)
                .hasMessage("用户名 'existing_username' 已存在");

        verify(userRepository, never()).existsByRealName(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateCurrentUser_shouldAllowUsernameThatMatchesAnotherUsersRealName() {
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("shared_name");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByUsername("shared_name")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateCurrentUser(userId, request);

        assertThat(result.getUsername()).isEqualTo("shared_name");
        verify(userRepository).existsByUsername("shared_name");
        verify(userRepository, never()).existsByRealName(anyString());
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldUpdatePassword_whenValidRequest() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setPassword("new_password123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(passwordEncoder.encode("new_password123")).thenReturn("encoded_password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getPasswordHash()).isEqualTo("encoded_password");
        verify(passwordEncoder).encode("new_password123");
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldUpdateRealName_whenValidRequest() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setRealName("新姓名");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByRealName("新姓名")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getRealName()).isEqualTo("新姓名");
        verify(userRepository).existsByRealName("新姓名");
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldThrowRealNameDuplicateException_whenRealNameExists() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setRealName("existing_realname");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByRealName("existing_realname")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.updateCurrentUser(userId, request))
                .isInstanceOf(RealNameDuplicateException.class)
                .hasMessage("真实姓名 'existing_realname' 已存在");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateCurrentUser_shouldUpdateClassName_whenValidRequest() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setClassName("高三(2)班");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getClassName()).isEqualTo("高三(2)班");
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldThrowUserNotFoundException_whenUserNotFound() {
        // Given
        Long userId = 999L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("new_username");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateCurrentUser(userId, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateCurrentUser_shouldNotValidateUsernameDuplicate_whenUsernameUnchanged() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("student001");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getUsername()).isEqualTo("student001");
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByRealName(anyString());
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldNotValidateRealNameDuplicate_whenRealNameUnchanged() {
        // Given
        Long userId = 100L;
        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setRealName("学生一号");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByRealName("学生一号")).thenReturn(true);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getRealName()).isEqualTo("学生一号");
        verify(userRepository).existsByRealName("学生一号");
        verify(userRepository).saveAndFlush(mockStudent);
    }

    @Test
    void updateCurrentUser_shouldIgnoreEmptyUsernamePasswordAndRealName() {
        // Given
        Long userId = 100L;
        String originalUsername = mockStudent.getUsername();
        String originalRealName = mockStudent.getRealName();
        String originalPasswordHash = mockStudent.getPasswordHash();

        UserSelfUpdateRequest request = new UserSelfUpdateRequest();
        request.setUsername("");
        request.setPassword("");
        request.setRealName("");
        request.setClassName("高三(5)班");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateCurrentUser(userId, request);

        // Then
        assertThat(result.getUsername()).isEqualTo(originalUsername);
        assertThat(result.getRealName()).isEqualTo(originalRealName);
        assertThat(result.getPasswordHash()).isEqualTo(originalPasswordHash);
        assertThat(result.getClassName()).isEqualTo("高三(5)班");
        verify(userRepository, never()).existsByUsername(anyString());
        verify(userRepository, never()).existsByRealName(anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ==================== getUserById 测试 ====================

    @Test
    void getUserById_shouldReturnUser_whenPermissionGranted() {
        // Given
        Long userId = 100L;
        Long teacherId = 110L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockStudent));

        // When
        User result = userService.getUserById(userId);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(User::getId, User::getUsername)
                .containsExactly(100L, "student001");

        verify(userRepository).findById(userId);
    }

    @Test
    void getUserById_shouldThrowUserNotFoundException_whenUserNotFound() {
        // Given
        Long userId = 999L;
        Long teacherId = 110L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository).findById(userId);
    }

    // ==================== getUserByRealName 测试 ====================

    @Test
    void getUserByRealName_shouldReturnUser_whenFound() {
        // Given
        String realName = "学生一号";
        Long teacherId = 110L;

        when(userRepository.findByRealName(realName)).thenReturn(Optional.of(mockStudent));

        // When
        User result = userService.getUserByRealName(realName);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(User::getRealName, User::getUserType)
                .containsExactly("学生一号", User.UserType.STUDENT);

        verify(userRepository).findByRealName(realName);
    }

    @Test
    void getUserByRealName_shouldThrowUserNotFoundException_whenNotFound() {
        // Given
        String realName = "不存在的用户";
        Long teacherId = 110L;

        when(userRepository.findByRealName(realName)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserByRealName(realName))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository).findByRealName(realName);
    }

    // ==================== getAllUsers 测试 ====================

    @Test
    void getAllUsers_shouldReturnUserList() {
        // Given
        Long teacherId = 110L;
        List<User> mockUsers = List.of(mockStudent, mockTeacher);

        when(userRepository.findAll()).thenReturn(mockUsers);

        // When
        List<User> result = userService.getAllUsers();

        // Then
        assertThat(result)
                .hasSize(2)
                .extracting(User::getId)
                .containsExactly(100L, 110L);

        verify(userRepository).findAll();
    }


    // ==================== updateUserByAdmin 测试 ====================

    @Test
    void updateUserByAdmin_shouldUpdateRealName_whenValidRequest() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(100L);
        request.setRealName("管理员修改的姓名");

        when(userRepository.findById(100L)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByRealName("管理员修改的姓名")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateUserByAdmin(request, 110L);

        // Then
        assertThat(result.getRealName()).isEqualTo("管理员修改的姓名");
        verify(userRepository).existsByRealName("管理员修改的姓名");
        verify(userRepository).save(mockStudent);
    }

    @Test
    void updateUserByAdmin_shouldUpdateClassName_whenValidRequest() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(100L);
        request.setClassName("高三(3)班");

        when(userRepository.findById(100L)).thenReturn(Optional.of(mockStudent));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateUserByAdmin(request, 110L);

        // Then
        assertThat(result.getClassName()).isEqualTo("高三(3)班");
        verify(userRepository).save(mockStudent);
    }

    @Test
    void updateUserByAdmin_shouldUpdateActiveStatus_whenValidRequest() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(100L);
        request.setActive(false);

        when(userRepository.findById(100L)).thenReturn(Optional.of(mockStudent));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateUserByAdmin(request, 110L);

        // Then
        assertThat(result.getActive()).isFalse();
        verify(userRepository).save(mockStudent);
    }

    @Test
    void updateUserByAdmin_shouldUpdateUserType_whenValidRequest() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(100L);
        request.setUserType(User.UserType.TEACHER);

        when(userRepository.findById(100L)).thenReturn(Optional.of(mockStudent));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateUserByAdmin(request, 110L);

        // Then
        assertThat(result.getUserType()).isEqualTo(User.UserType.TEACHER);
        verify(userRepository).save(mockStudent);
    }

    @Test
    void updateUserByAdmin_shouldThrowUserNotFoundException_whenUserNotFound() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(999L);
        request.setRealName("新姓名");

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateUserByAdmin(request, 110L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserByAdmin_shouldNotValidateRealNameDuplicate_whenRealNameUnchanged() {
        // Given
        UserAdminUpdateRequest request = new UserAdminUpdateRequest();
        request.setUserId(100L);
        request.setRealName("学生一号");

        when(userRepository.findById(100L)).thenReturn(Optional.of(mockStudent));
        when(userRepository.existsByRealName("学生一号")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = userService.updateUserByAdmin(request, 110L);

        // Then
        assertThat(result.getRealName()).isEqualTo("学生一号");
        verify(userRepository).existsByRealName("学生一号");
        verify(userRepository).save(mockStudent);
    }

    // ==================== deleteUser 测试 ====================

    @Test
    void deleteUser_shouldDeleteTargetUser() {
        // Given
        Long targetUserId = 100L;
        Long teacherId = 110L;

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(mockStudent));

        // When
        userService.deleteUser(targetUserId, teacherId);

        // Then
        verify(userRepository).findById(targetUserId);
        verify(userRepository).delete(mockStudent);
    }

    @Test
    void deleteUser_shouldThrowUserNotFoundException_whenTargetUserNotFound() {
        // Given
        Long targetUserId = 999L;
        Long teacherId = 110L;

        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(targetUserId, teacherId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("用户不存在");

        verify(userRepository).findById(targetUserId);
        verify(userRepository, never()).delete(any(User.class));
    }
}