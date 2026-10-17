package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.auth.AccountDisabledException;
import com.zhixuewuli.common.exception.auth.InvalidCredentialsException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.exception.auth.UserNameDuplicateException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.common.util.PasswordUtil;
import com.zhixuewuli.dto.request.RegisterRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.auth.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试
 * 
 * 测试AuthService的所有核心业务逻辑，包括：
 * - 用户认证（用户名密码验证）
 * - JWT令牌生成
 * - 用户注册
 * 
 * 遵循Given-When-Then测试模式，使用Mockito进行依赖模拟
 */
class AuthServiceUnitTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private String validUsername = "testuser";
    private String validPassword = "password123";

    @BeforeEach
    void setUp() {
        mockUser = TestDataFactory.createUser();
        mockUser.setId(100L);
        mockUser.setUsername(validUsername);
        mockUser.setPasswordHash("$2a$10$example_bcrypt_hash");
        mockUser.setActive(true);
        mockUser.setLastLogin(null);
    }

    // ==================== authenticate 测试 ====================

    @Test
    void authenticate_shouldReturnUser_whenCredentialsValid() {
        // Given
        when(userRepository.findByRealName(validUsername)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(validUsername)).thenReturn(Optional.of(mockUser));
        when(passwordUtil.matches(validPassword, mockUser.getPasswordHash())).thenReturn(true);

        // When
        User result = authService.authenticate(validUsername, validPassword);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(User::getId, User::getUsername, User::getActive)
                .containsExactly(100L, validUsername, true);

        assertThat(result.getLastLogin()).isNotNull();
        verify(userRepository).findByRealName(validUsername);
        verify(userRepository).findByUsername(validUsername);
        verify(passwordUtil).matches(validPassword, mockUser.getPasswordHash());
        verify(userRepository).save(result);
    }

    @Test
    void authenticate_shouldPreferRealName_whenRealNameMatched() {
        // Given
        String loginId = "张三";
        User realNameMatchedUser = TestDataFactory.createUser();
        realNameMatchedUser.setId(103L);
        realNameMatchedUser.setUsername("student_zhangsan");
        realNameMatchedUser.setRealName(loginId);
        realNameMatchedUser.setPasswordHash("$2a$10$example_bcrypt_hash");
        realNameMatchedUser.setActive(true);

        when(userRepository.findByRealName(loginId)).thenReturn(Optional.of(realNameMatchedUser));
        when(passwordUtil.matches(validPassword, realNameMatchedUser.getPasswordHash())).thenReturn(true);

        // When
        User result = authService.authenticate(loginId, validPassword);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(103L);
        verify(userRepository).findByRealName(loginId);
        verify(userRepository, never()).findByUsername(loginId);
        verify(userRepository).save(result);
    }

    @Test
    void authenticate_shouldThrowInvalidCredentialsException_whenUsernameIsNull() {
        // Given
        String nullUsername = null;

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(nullUsername, validPassword))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).findByRealName(anyString());
        verify(userRepository, never()).findByUsername(anyString());
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowInvalidCredentialsException_whenUsernameIsEmpty() {
        // Given
        String emptyUsername = "";

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(emptyUsername, validPassword))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).findByRealName(anyString());
        verify(userRepository, never()).findByUsername(anyString());
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowInvalidCredentialsException_whenPasswordIsNull() {
        // Given
        String nullPassword = null;

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validUsername, nullPassword))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).findByRealName(anyString());
        verify(userRepository, never()).findByUsername(anyString());
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowInvalidCredentialsException_whenPasswordIsEmpty() {
        // Given
        String emptyPassword = "";

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validUsername, emptyPassword))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).findByRealName(anyString());
        verify(userRepository, never()).findByUsername(anyString());
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowUserNotFoundException_whenUserNotFound() {
        // Given
        String nonExistentUsername = "nonexistent";
        when(userRepository.findByRealName(nonExistentUsername)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(nonExistentUsername, validPassword))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findByRealName(nonExistentUsername);
        verify(userRepository).findByUsername(nonExistentUsername);
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowAccountDisabledException_whenUserInactive() {
        // Given
        User inactiveUser = TestDataFactory.createUser();
        inactiveUser.setId(101L);
        inactiveUser.setUsername("inactive_user");
        inactiveUser.setActive(false);

        when(userRepository.findByRealName("inactive_user")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("inactive_user")).thenReturn(Optional.of(inactiveUser));

        // When & Then
        assertThatThrownBy(() -> authService.authenticate("inactive_user", validPassword))
                .isInstanceOf(AccountDisabledException.class);

        verify(userRepository).findByRealName("inactive_user");
        verify(userRepository).findByUsername("inactive_user");
        verify(passwordUtil, never()).matches(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_shouldThrowInvalidCredentialsException_whenPasswordIncorrect() {
        // Given
        when(userRepository.findByRealName(validUsername)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(validUsername)).thenReturn(Optional.of(mockUser));
        when(passwordUtil.matches(validPassword, mockUser.getPasswordHash())).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.authenticate(validUsername, validPassword))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository).findByRealName(validUsername);
        verify(userRepository).findByUsername(validUsername);
        verify(passwordUtil).matches(validPassword, mockUser.getPasswordHash());
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== generateAccessToken 测试 ====================

    @Test
    void generateAccessToken_shouldReturnJwtString() {
        // Given
        String expectedToken = "mock.jwt.token.string";
        when(jwtUtil.generateToken(mockUser.getId(), mockUser.getUsername())).thenReturn(expectedToken);

        // When
        String result = authService.generateAccessToken(mockUser);

        // Then
        assertThat(result).isEqualTo(expectedToken);
        verify(jwtUtil).generateToken(mockUser.getId(), mockUser.getUsername());
    }

    @Test
    void generateAccessToken_shouldThrowIllegalArgumentException_whenUserNull() {
        // Given
        User nullUser = null;

        // When & Then
        assertThatThrownBy(() -> authService.generateAccessToken(nullUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户对象不能为空");

        verify(jwtUtil, never()).generateToken(anyLong(), anyString());
    }

    @Test
    void registerUser_shouldRejectPublicTeacherRegistration() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("forged_teacher");
        request.setPassword("password123");
        request.setRealName("Unauthorised teacher");
        request.setUserType(User.UserType.TEACHER);
        org.mockito.Mockito.lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(com.zhixuewuli.common.exception.InsufficientPermissionException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    // ==================== registerUser 测试 ====================

    @Test
    void registerUser_shouldReturnUser_whenRegistrationValid() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("newpassword123");
        request.setRealName("新用户");
        request.setUserType(User.UserType.STUDENT);
        request.setClassName("高三(1)班");

        User savedUser = TestDataFactory.createUser();
        savedUser.setId(102L);
        savedUser.setUsername("newuser");
        savedUser.setRealName("新用户");
        savedUser.setUserType(User.UserType.STUDENT);
        savedUser.setClassName("高三(1)班");
        savedUser.setActive(true);
        savedUser.setPasswordHash("$2a$10$new_encoded_password");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByRealName("newuser")).thenReturn(false);
        when(passwordUtil.encode("newpassword123")).thenReturn("$2a$10$new_encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(102L);
            return user;
        });

        // When
        User result = authService.registerUser(request);

        // Then
        assertThat(result)
                .isNotNull()
                .extracting(User::getUsername, User::getRealName, User::getUserType, User::getActive)
                .containsExactly("newuser", "新用户", User.UserType.STUDENT, true);

        assertThat(result.getId()).isEqualTo(102L);
        assertThat(result.getPasswordHash()).isEqualTo("$2a$10$new_encoded_password");

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByRealName("newuser");
        verify(passwordUtil).encode("newpassword123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_shouldThrowUserNameDuplicateException_whenUsernameExists() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");
        request.setPassword("password123");
        request.setRealName("_existing_user");
        request.setUserType(User.UserType.STUDENT);

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(UserNameDuplicateException.class)
                .hasMessage("用户名 'existinguser' 已存在");

        verify(userRepository).existsByUsername("existinguser");
        verify(userRepository, never()).existsByRealName(anyString());
        verify(passwordUtil, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_shouldThrowUserNameDuplicateException_whenUsernameMatchesExistingRealName() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("shared_name");
        request.setPassword("password123");
        request.setRealName("新用户");
        request.setUserType(User.UserType.STUDENT);

        when(userRepository.existsByUsername("shared_name")).thenReturn(false);
        when(userRepository.existsByRealName("shared_name")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(UserNameDuplicateException.class)
                .hasMessage("用户名 'shared_name' 已存在");

        verify(userRepository).existsByUsername("shared_name");
        verify(userRepository).existsByRealName("shared_name");
        verify(passwordUtil, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_shouldSetDefaultActiveStatus() {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setRealName("新用户");
        request.setUserType(User.UserType.STUDENT);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByRealName("newuser")).thenReturn(false);
        when(passwordUtil.encode("password123")).thenReturn("$2a$10$encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User result = authService.registerUser(request);

        // Then
        assertThat(result.getActive()).isTrue();

        verify(userRepository).existsByUsername("newuser");
        verify(userRepository).existsByRealName("newuser");
        verify(passwordUtil).encode("password123");
        verify(userRepository).save(any(User.class));
    }
}