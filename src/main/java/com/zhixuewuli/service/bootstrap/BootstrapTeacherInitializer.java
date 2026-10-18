package com.zhixuewuli.service.bootstrap;

import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Offline operator provisioning; disabled by default, with no built-in password. */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap-teacher", name = "enabled", havingValue = "true")
public class BootstrapTeacherInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public BootstrapTeacherInitializer(UserRepository users, PasswordEncoder passwords,
            @Value("${app.bootstrap-teacher.username:}") String username,
            @Value("${app.bootstrap-teacher.password:}") String password) {
        this.users = users; this.passwords = passwords; this.username = username; this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!username.matches("[a-zA-Z0-9_]{3,50}")) throw new IllegalArgumentException("初始教师用户名须为3到50位字母、数字或下划线");
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            if (existing.get().getUserType() != User.UserType.TEACHER) {
                throw new IllegalArgumentException("初始教师用户名已被非教师账号占用，请更换用户名");
            }
            return;
        }
        if (password.length() < 12) throw new IllegalArgumentException("初始教师密码至少12个字符");
        User teacher = new User(); teacher.setUsername(username); teacher.setRealName(username);
        teacher.setUserType(User.UserType.TEACHER); teacher.setActive(true);
        teacher.setPasswordHash(passwords.encode(password)); users.save(teacher);
    }
}
