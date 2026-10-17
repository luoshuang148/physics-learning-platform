package com.zhixuewuli.controller;

import com.zhixuewuli.common.base.BaseControllerIntegrationTest;
import com.zhixuewuli.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"app.bootstrap-teacher.enabled=true", "app.bootstrap-teacher.username=first_teacher",
        "app.bootstrap-teacher.password=temporary-test-password-123"})
class BootstrapTeacherIntegrationTest extends BaseControllerIntegrationTest {
    @Autowired PasswordEncoder passwords;
    @Test
    void explicitlyConfiguredFirstTeacherCanSignInWithAnEncodedPassword() {
        User teacher = userRepository.findByUsername("first_teacher").orElse(null);
        assertThat(teacher).isNotNull();
        assertThat(teacher.getUserType()).isEqualTo(User.UserType.TEACHER);
        assertThat(teacher.getActive()).isTrue();
        assertThat(passwords.matches("temporary-test-password-123", teacher.getPasswordHash())).isTrue();
        assertThat(teacher.getPasswordHash()).doesNotContain("temporary-test-password");
    }
}
