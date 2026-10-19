package com.zhixuewuli.common.util;

import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 数据归属判断。
 *
 * <p>接口级角色限制由 Spring Security 的 {@code @TeacherOnly} 完成；
 * 这里只回答“当前用户能否访问某个用户的数据”这类资源级问题，
 * 供同一接口同时服务学生与教师的场景使用。</p>
 */
@Component
@RequiredArgsConstructor
public class UserPermissionUtil {

    private final UserRepository userRepository;

    /**
     * @throws UserNotFoundException 用户不存在时抛出
     */
    public boolean isTeacherUser(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("用户不存在"));
        return user.getUserType() == User.UserType.TEACHER;
    }

    /** 用户可以访问自己的数据；访问他人数据需要教师身份。 */
    public boolean canQueryUserData(Long targetUserId, Long currentUserId) {
        if (targetUserId == null || currentUserId == null) {
            return false;
        }
        return targetUserId.equals(currentUserId) || isTeacherUser(currentUserId);
    }
}
