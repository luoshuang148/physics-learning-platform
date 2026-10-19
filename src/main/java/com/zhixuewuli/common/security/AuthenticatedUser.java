package com.zhixuewuli.common.security;

import com.zhixuewuli.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 已认证用户的身份快照，作为 Spring Security 的 principal。
 * 控制器通过 {@code @AuthenticationPrincipal AuthenticatedUser user} 获取当前用户。
 */
public record AuthenticatedUser(Long id, String username, User.UserType userType) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getUserType());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + userType.name()));
    }

    public boolean isTeacher() {
        return userType == User.UserType.TEACHER;
    }
}
