package com.zhixuewuli.common.util;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.common.factory.TestDataFactory;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户权限工具类单元测试
 * 
 * 测试 UserPermissionUtil 的数据归属判断，包括：
 * - 教师身份判断（isTeacherUser）
 * - 数据归属判断（canQueryUserData）
 * 
 * 遵循 Given-When-Then 测试模式，使用 Mockito 进行依赖模拟
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@DisplayName("UserPermissionUtil 单元测试")
class UserPermissionUtilUnitTest extends BaseUnitTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPermissionUtil userPermissionUtil;

    private User mockStudent;
    private User mockTeacher;

    private static final Long STUDENT_ID = 100L;
    private static final Long TEACHER_ID = 110L;
    private static final Long NON_EXISTENT_USER_ID = 999L;
    private static final String TEST_SESSION_ID = "test-session-12345";

    @BeforeEach
    void setUp() {
        // 创建测试学生用户
        mockStudent = TestDataFactory.createUser();
        mockStudent.setId(STUDENT_ID);
        mockStudent.setUserType(User.UserType.STUDENT);
        mockStudent.setUsername("student_test");
        mockStudent.setRealName("测试学生");

        // 创建测试教师用户
        mockTeacher = TestDataFactory.createUser();
        mockTeacher.setId(TEACHER_ID);
        mockTeacher.setUserType(User.UserType.TEACHER);
        mockTeacher.setUsername("teacher_test");
        mockTeacher.setRealName("测试教师");
    }

    // ============================================================
    // isTeacherUser 方法测试模块
    // ============================================================

    @Nested
    @DisplayName("isTeacherUser - 检查用户是否为教师")
    class IsTeacherUserTests {

        @Test
        @DisplayName("应返回 true - 当用户是教师时")
        void isTeacherUser_shouldReturnTrue_whenUserIsTeacher() {
            // Given
            when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.of(mockTeacher));

            // When
            boolean result = userPermissionUtil.isTeacherUser(TEACHER_ID);

            // Then
            assertThat(result).isTrue();
            verify(userRepository).findById(TEACHER_ID);
        }

        @Test
        @DisplayName("应返回 false - 当用户是学生时")
        void isTeacherUser_shouldReturnFalse_whenUserIsStudent() {
            // Given
            when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(mockStudent));

            // When
            boolean result = userPermissionUtil.isTeacherUser(STUDENT_ID);

            // Then
            assertThat(result).isFalse();
            verify(userRepository).findById(STUDENT_ID);
        }

        @Test
        @DisplayName("应抛出 UserNotFoundException - 当用户不存在时")
        void isTeacherUser_shouldThrowUserNotFoundException_whenUserNotFound() {
            // Given
            when(userRepository.findById(NON_EXISTENT_USER_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userPermissionUtil.isTeacherUser(NON_EXISTENT_USER_ID))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("用户不存在");

            verify(userRepository).findById(NON_EXISTENT_USER_ID);
        }

        @Test
        @DisplayName("应返回 false - 当用户 ID 为 null 时")
        void isTeacherUser_shouldReturnFalse_whenUserIdIsNull() {
            // When
            boolean result = userPermissionUtil.isTeacherUser(null);

            // Then
            assertThat(result).isFalse();
            verify(userRepository, never()).findById(anyLong());
        }
    }

    // ============================================================
    // canQueryUserData 方法测试模块
    // ============================================================

    @Nested
    @DisplayName("canQueryUserData - 检查用户是否有权查询目标用户数据")
    class CanQueryUserDataTests {

        @Test
        @DisplayName("应返回 true - 当用户查询自己的数据时")
        void canQueryUserData_shouldReturnTrue_whenUserQueriesOwnData() {
            // When
            boolean result = userPermissionUtil.canQueryUserData(STUDENT_ID, STUDENT_ID);

            // Then
            assertThat(result).isTrue();
            verify(userRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("应返回 true - 当教师查询学生数据时")
        void canQueryUserData_shouldReturnTrue_whenTeacherQueriesStudentData() {
            // Given
            when(userRepository.findById(TEACHER_ID)).thenReturn(Optional.of(mockTeacher));

            // When
            boolean result = userPermissionUtil.canQueryUserData(STUDENT_ID, TEACHER_ID);

            // Then
            assertThat(result).isTrue();
            verify(userRepository).findById(TEACHER_ID);
        }

        @Test
        @DisplayName("应返回 false - 当学生查询其他学生数据时")
        void canQueryUserData_shouldReturnFalse_whenStudentQueriesOtherStudentData() {
            // Given
            Long anotherStudentId = 102L;
            User anotherStudent = TestDataFactory.createUser();
            anotherStudent.setId(anotherStudentId);
            anotherStudent.setUserType(User.UserType.STUDENT);

            when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(mockStudent));

            // When
            boolean result = userPermissionUtil.canQueryUserData(anotherStudentId, STUDENT_ID);

            // Then
            assertThat(result).isFalse();
            verify(userRepository).findById(STUDENT_ID);
        }

        @Test
        @DisplayName("应返回 false - 当目标用户 ID 为 null 时")
        void canQueryUserData_shouldReturnFalse_whenTargetUserIdIsNull() {
            // When
            boolean result = userPermissionUtil.canQueryUserData(null, TEACHER_ID);

            // Then
            assertThat(result).isFalse();
            verify(userRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("应返回 false - 当当前用户 ID 为 null 时")
        void canQueryUserData_shouldReturnFalse_whenCurrentUserIdIsNull() {
            // When
            boolean result = userPermissionUtil.canQueryUserData(STUDENT_ID, null);

            // Then
            assertThat(result).isFalse();
            verify(userRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("应返回 false - 当两个用户 ID 都为 null 时")
        void canQueryUserData_shouldReturnFalse_whenBothUserIdsAreNull() {
            // When
            boolean result = userPermissionUtil.canQueryUserData(null, null);

            // Then
            assertThat(result).isFalse();
            verify(userRepository, never()).findById(anyLong());
        }
    }

}
