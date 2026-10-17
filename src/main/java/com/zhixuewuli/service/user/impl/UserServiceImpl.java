package com.zhixuewuli.service.user.impl;

import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.auth.*;
import com.zhixuewuli.common.util.UserPermissionUtil;
import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.dto.request.UserSelfUpdateRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现类
 * 
 * 实现UserService接口，提供用户管理的核心业务逻辑。
 * 采用增量更新策略，支持权限控制和唯一性约束检查。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see UserService 用户服务接口
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final com.zhixuewuli.repository.TeacherEventRepository teacherEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionUtil userPermissionUtil;

    @Override
    @Transactional(readOnly = true)
    public User getCurrentUser(Long currentUserId) {
        log.info("获取当前用户信息，用户ID: {}", currentUserId);
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));
    }

    @Override
    public User updateCurrentUser(Long currentUserId, UserSelfUpdateRequest request) {
        log.info("用户更新自己的信息，用户ID: {}", currentUserId);
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));

        if (request.getUserType() != null && request.getUserType() != user.getUserType()) {
            throw new InsufficientPermissionException(currentUserId, "个人资料接口不能修改用户角色");
        }

        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            String newUsername = request.getUsername();
            if (!newUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsername(newUsername)) {
                    throw new UserNameDuplicateException(newUsername);
                }
                if (userRepository.existsByRealName(newUsername)) {
                    throw new UserNameDuplicateException(newUsername);
                }
                user.setUsername(newUsername);
            }
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getRealName() != null && !request.getRealName().isEmpty()) {
            if (userRepository.existsByRealName(request.getRealName()) &&
                    !request.getRealName().equals(user.getRealName())) {
                throw new RealNameDuplicateException(request.getRealName());
            }
            user.setRealName(request.getRealName());
        }
        if (request.getClassName() != null) {
            user.setClassName(request.getClassName());
        }


        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId, Long currentUserId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        log.info("根据ID获取用户信息，用户ID: {}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByRealName(String realName, Long currentUserId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        log.info("根据真实姓名获取用户信息，真实姓名: {}", realName);
        return userRepository.findByRealName(realName)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers(Long currentUserId) {
        userPermissionUtil.checkTeacherPermission(currentUserId);
        log.info("获取所有用户列表");
        return userRepository.findAll();
    }

    @Override
    public User updateUserByAdmin(UserAdminUpdateRequest request, Long currentUserId) {
        Long userId = request.getUserId();
        log.info("管理员更新用户信息，目标用户ID: {}, 操作者ID: {}", userId, currentUserId);

        userPermissionUtil.checkTeacherPermission(currentUserId);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));

        if (request.getRealName() != null && !request.getRealName().isEmpty()) {
            if (userRepository.existsByRealName(request.getRealName()) &&
                    !request.getRealName().equals(targetUser.getRealName())) {
                throw new RealNameDuplicateException(request.getRealName());
            }
            targetUser.setRealName(request.getRealName());
        }
        if (request.getClassName() != null) {
            targetUser.setClassName(request.getClassName());
        }
        if (request.getActive() != null) {
            targetUser.setActive(request.getActive());
        }
        if (request.getUserType() != null) {
            targetUser.setUserType(request.getUserType());
        }

        return userRepository.save(targetUser);
    }

    @Override
    public void deleteUser(Long userId, Long currentUserId) {
        log.info("删除用户，目标用户ID: {}, 操作者ID: {}", userId, currentUserId);

        User currentUser = getCurrentUser(currentUserId);
        if (currentUser.getUserType() != User.UserType.TEACHER) {
            throw new InsufficientPermissionException("仅教师可以执行此操作");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("用户不存在"));

        if (teacherEventRepository.existsByTeacherId(userId)) {
            throw new com.zhixuewuli.common.exception.BusinessException("该用户有关联教学任务，请保留账号并停用以保留历史数据");
        }
        userRepository.delete(targetUser);
    }
}
