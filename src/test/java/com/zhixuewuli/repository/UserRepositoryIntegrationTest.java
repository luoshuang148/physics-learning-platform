package com.zhixuewuli.repository;

import com.zhixuewuli.common.base.BaseIntegrationTest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户仓库集成测试
 * 验证 H2 数据库连接和基本 CRUD 操作
 */
@Tag("integration-repository")
class UserRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    // ==================== 按用户类型统计测试 ====================

    @Test
    @Rollback
    void testCountByUserType_student() {
        // Given - 清理现有数据
        cleanTable("USERS");

        // 创建 3 个学生用户
        User student1 = new User();
        student1.setUsername("count_student_1");
        student1.setPasswordHash("$2a$10$hash1");
        student1.setUserType(User.UserType.STUDENT);
        student1.setRealName("学生 1");
        student1.setActive(true);

        User student2 = new User();
        student2.setUsername("count_student_2");
        student2.setPasswordHash("$2a$10$hash2");
        student2.setUserType(User.UserType.STUDENT);
        student2.setRealName("学生 2");
        student2.setActive(true);

        User student3 = new User();
        student3.setUsername("count_student_3");
        student3.setPasswordHash("$2a$10$hash3");
        student3.setUserType(User.UserType.STUDENT);
        student3.setRealName("学生 3");
        student3.setActive(true);

        // 创建 2 个教师用户
        User teacher1 = new User();
        teacher1.setUsername("count_teacher_1");
        teacher1.setPasswordHash("$2a$10$thash1");
        teacher1.setUserType(User.UserType.TEACHER);
        teacher1.setRealName("教师 1");
        teacher1.setActive(true);

        User teacher2 = new User();
        teacher2.setUsername("count_teacher_2");
        teacher2.setPasswordHash("$2a$10$thash2");
        teacher2.setUserType(User.UserType.TEACHER);
        teacher2.setRealName("教师 2");
        teacher2.setActive(true);

        userRepository.save(student1);
        userRepository.save(student2);
        userRepository.save(student3);
        userRepository.save(teacher1);
        userRepository.save(teacher2);

        // When
        long studentCount = userRepository.countByUserType(User.UserType.STUDENT);
        long teacherCount = userRepository.countByUserType(User.UserType.TEACHER);

        // Then
        assertThat(studentCount).isEqualTo(3L);
        assertThat(teacherCount).isEqualTo(2L);
    }

    @Test
    @Rollback
    void testCountByUserType_noUsers() {
        // Given - 清理现有数据
        cleanTable("USERS");

        // When
        long studentCount = userRepository.countByUserType(User.UserType.STUDENT);

        // Then - 没有学生时返回 0
        assertThat(studentCount).isEqualTo(0L);
    }

    @Test
    @Rollback
    void testCountByUserType_includesInactive() {
        // Given - 清理现有数据
        cleanTable("USERS");

        User activeStudent = new User();
        activeStudent.setUsername("active_student_count");
        activeStudent.setPasswordHash("$2a$10$hash");
        activeStudent.setUserType(User.UserType.STUDENT);
        activeStudent.setRealName("活跃学生");
        activeStudent.setActive(true);

        User inactiveStudent = new User();
        inactiveStudent.setUsername("inactive_student_count");
        inactiveStudent.setPasswordHash("$2a$10$hash");
        inactiveStudent.setUserType(User.UserType.STUDENT);
        inactiveStudent.setRealName("非活跃学生");
        inactiveStudent.setActive(false);

        userRepository.save(activeStudent);
        userRepository.save(inactiveStudent);

        // When
        long studentCount = userRepository.countByUserType(User.UserType.STUDENT);

        // Then - 包括活跃和非活跃的学生
        assertThat(studentCount).isEqualTo(2L);
    }

    // ==================== 基础 CRUD 测试 ====================

    @Test
    @Rollback
    void testSaveAndFindUser() {
        // Given
        User user = new User();
        user.setUsername("integration_test_user");
        user.setPasswordHash("$2a$10$integration_test_hash");
        user.setUserType(User.UserType.STUDENT);
        user.setRealName("集成测试用户");
        user.setClassName("测试班级");
        user.setActive(true);

        // When
        User savedUser = userRepository.save(user);
        Optional<User> foundUser = userRepository.findById(savedUser.getId());

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo("integration_test_user");
        assertThat(foundUser.get().getRealName()).isEqualTo("集成测试用户");
        assertThat(foundUser.get().getUserType()).isEqualTo(User.UserType.STUDENT);
    }

    @Test
    @Rollback
    void testFindByUsername() {
        // Given
        User user = new User();
        user.setUsername("test_username_find");
        user.setPasswordHash("$2a$10$test_hash");
        user.setUserType(User.UserType.TEACHER);
        user.setRealName("测试查找用户");
        user.setActive(true);
        userRepository.save(user);

        // When
        Optional<User> foundUser = userRepository.findByUsername("test_username_find");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getRealName()).isEqualTo("测试查找用户");
        assertThat(foundUser.get().getUserType()).isEqualTo(User.UserType.TEACHER);
    }

    @Test
    @Rollback
    void testExistsByUsername() {
        // Given
        User user = new User();
        user.setUsername("exists_test_user");
        user.setPasswordHash("$2a$10$exists_hash");
        user.setUserType(User.UserType.STUDENT);
        user.setRealName("存在性测试用户");
        user.setActive(true);
        userRepository.save(user);

        // When
        boolean exists = userRepository.existsByUsername("exists_test_user");
        boolean notExists = userRepository.existsByUsername("non_existent_user");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @Rollback
    void testFindActiveStudentsByClass() {
        // Given
        User student1 = new User();
        student1.setUsername("student1");
        student1.setPasswordHash("$2a$10$hash1");
        student1.setUserType(User.UserType.STUDENT);
        student1.setRealName("学生1");
        student1.setClassName("高三(1)班");
        student1.setActive(true);

        User student2 = new User();
        student2.setUsername("student2");
        student2.setPasswordHash("$2a$10$hash2");
        student2.setUserType(User.UserType.STUDENT);
        student2.setRealName("学生2");
        student2.setClassName("高三(1)班");
        student2.setActive(true);

        User teacher = new User();
        teacher.setUsername("teacher1");
        teacher.setPasswordHash("$2a$10$teacher_hash");
        teacher.setUserType(User.UserType.TEACHER);
        teacher.setRealName("教师1");
        teacher.setClassName("高三(1)班");
        teacher.setActive(true);

        User inactiveStudent = new User();
        inactiveStudent.setUsername("inactive_student");
        inactiveStudent.setPasswordHash("$2a$10$inactive_hash");
        inactiveStudent.setUserType(User.UserType.STUDENT);
        inactiveStudent.setRealName("非活跃学生");
        inactiveStudent.setClassName("高三(1)班");
        inactiveStudent.setActive(false);

        userRepository.save(student1);
        userRepository.save(student2);
        userRepository.save(teacher);
        userRepository.save(inactiveStudent);

        // When
        List<User> activeStudents = userRepository.findActiveStudentsByClass("高三(1)班");

        // Then
        assertThat(activeStudents).hasSize(2);
        assertThat(activeStudents).extracting(User::getRealName)
                .containsExactlyInAnyOrder("学生1", "学生2");
    }
}