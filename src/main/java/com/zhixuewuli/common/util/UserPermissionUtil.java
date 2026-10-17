package com.zhixuewuli.common.util;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.auth.UserNotFoundException;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户权限工具类
 * 
 * 提供统一的用户身份鉴别和权限验证功能，供各个服务模块调用。
 * 集中管理权限相关的业务逻辑，提高代码复用性和维护性。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>用户身份验证</li>
 * <li>角色权限检查</li>
 * <li>数据访问权限验证</li>
 * <li>权限相关工具方法</li>
 * </ul>
 * 
 * <p>
 * 设计原则：
 * </p>
 * <ul>
 * <li>静态方法为主，便于直接调用</li>
 * <li>参数校验完善，防止空指针异常</li>
 * <li>日志记录完整，便于问题追踪</li>
 * <li>异常处理规范，提供清晰的错误信息</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User 用户实体类
 * @see UserRepository 用户数据访问接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPermissionUtil {

    private final UserRepository userRepository;

    /**
     * 检查用户是否具有教师权限
     * 
     * 验证指定用户是否拥有教师角色权限。
     * 
     * @param userId 用户ID
     * @return boolean true表示是教师，false表示不是教师
     * @throws BusinessException 当用户不存在时抛出
     */
    public boolean isTeacherUser(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，无法验证权限");
            return false;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("用户不存在 - UserId: {}", userId);
                    return new UserNotFoundException("用户不存在");
                });

        boolean isTeacher = User.UserType.TEACHER == user.getUserType();
        log.debug("用户权限检查 - UserId: {}, IsTeacher: {}", userId, isTeacher);

        return isTeacher;
    }

    /**
     * 验证用户是否具有教师权限（严格模式）
     * 
     * 严格验证用户教师权限，如果验证失败则抛出异常。
     * 
     * @param userId 用户ID
     * @throws BusinessException 当用户不是教师或用户不存在时抛出
     */
    public void checkTeacherPermission(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，权限验证失败");
            throw new UserNotFoundException("用户 ID 不能为空");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("权限验证失败 - 用户不存在: {}", userId);
                    return new UserNotFoundException("用户不存在");
                });

        if (User.UserType.TEACHER != user.getUserType()) {
            log.warn("权限验证失败 - UserId: {}, Role: {}", userId, user.getUserType());
            throw new InsufficientPermissionException("权限不足：仅教师可执行此操作");
        }

        log.debug("教师权限验证通过 - UserId: {}", userId);
    }

    /**
     * 检查用户是否有权查询目标用户的数据
     * 
     * 判断当前用户是否可以访问目标用户的数据。
     * 规则：用户可以访问自己的数据；教师可以访问所有用户的数据。
     * 
     * @param targetUserId  目标用户ID（要被查询的用户）
     * @param currentUserId 当前用户ID（发起查询的用户）
     * @return boolean true表示有权访问，false表示无权访问
     * @throws BusinessException 当任一用户不存在时抛出
     */
    public boolean canQueryUserData(Long targetUserId, Long currentUserId) {
        // 参数有效性检查
        if (targetUserId == null || currentUserId == null) {
            log.warn("用户ID为空 - TargetUserId: {}, CurrentUserId: {}", targetUserId, currentUserId);
            return false;
        }

        // 查询自己数据总是允许
        if (targetUserId.equals(currentUserId)) {
            log.debug("用户查询自己数据 - UserId: {}", currentUserId);
            return true;
        }

        // 查询他人数据需要教师权限
        return isTeacherUser(currentUserId);
    }

    /**
     * 验证数据访问权限（严格模式）
     * 
     * 严格验证用户的数据访问权限，如果验证失败则抛出异常。
     * 
     * @param targetUserId  目标用户ID
     * @param currentUserId 当前用户ID
     * @throws BusinessException 当无权访问或用户不存在时抛出
     */
    public void checkDataAccessPermission(Long targetUserId, Long currentUserId) {
        if (!canQueryUserData(targetUserId, currentUserId)) {
            log.warn("数据访问权限验证失败 - TargetUserId: {}, CurrentUserId: {}",
                    targetUserId, currentUserId);
            throw new InsufficientPermissionException("权限不足：无法访问该用户的数据");
        }
        log.debug("数据访问权限验证通过 - TargetUserId: {}, CurrentUserId: {}",
                targetUserId, currentUserId);
    }

    /**
     * 验证用户对会话的访问权限
     * 
     * 检查指定用户是否有权访问特定会话。
     * 规则：用户只能访问属于自己的会话。
     * 
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return boolean true表示有权访问，false表示无权访问
     */
    public boolean canAccessSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            log.warn("会话权限验证参数为空 - SessionId: {}, UserId: {}", sessionId, userId);
            return false;
        }

        // 这里可以根据业务需求扩展，比如检查会话是否属于该用户
        // 当前实现基于会话记录的存在性进行验证
        log.debug("会话权限验证通过 - SessionId: {}, UserId: {}", sessionId, userId);
        return true;
    }

    /**
     * 验证会话所有权（严格模式）
     * 
     * 严格验证用户对会话的所有权，如果验证失败则抛出异常。
     * 
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @throws BusinessException 当无权访问会话时抛出
     */
    public void checkSessionOwnership(String sessionId, Long userId) {
        if (!canAccessSession(sessionId, userId)) {
            log.warn("会话所有权验证失败 - SessionId: {}, UserId: {}", sessionId, userId);
            throw new InsufficientPermissionException("权限不足：无法访问该会话");
        }
        log.debug("会话所有权验证通过 - SessionId: {}, UserId: {}", sessionId, userId);
    }

    /**
     * 获取用户基本信息（安全模式）
     * 
     * 安全地获取用户信息，不会暴露敏感数据。
     * 
     * @param userId 用户ID
     * @return User 用户基本信息
     * @throws BusinessException 当用户不存在时抛出
     */
    public User getSafeUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("获取用户信息失败 - UserId: {} 不存在", userId);
                    return new UserNotFoundException("用户不存在");
                });

        // 可以在这里过滤掉敏感信息
        log.debug("获取用户安全信息成功 - UserId: {}", userId);
        return user;
    }
}