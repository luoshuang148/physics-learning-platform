package com.zhixuewuli.service.auth.impl;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.auth.*;
import com.zhixuewuli.common.util.JwtUtil;
import com.zhixuewuli.common.util.PasswordUtil;
import com.zhixuewuli.dto.request.RegisterRequest;
import com.zhixuewuli.entity.User;
import com.zhixuewuli.repository.UserRepository;
import com.zhixuewuli.service.auth.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 认证服务实现类
 * 
 * 处理用户认证和注册的核心业务逻辑。
 * 职责聚焦于用户身份验证、令牌管理和账户创建。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>用户登录认证（密码验证）</li>
 * <li>JWT访问令牌生成</li>
 * <li>新用户账户注册</li>
 * <li>用户数据安全管理</li>
 * </ul>
 * 
 * <p>
 * 安全特性：
 * </p>
 * <ul>
 * <li>BCrypt密码加密存储</li>
 * <li>JWT令牌签名验证</li>
 * <li>输入参数严格验证</li>
 * <li>操作日志完整记录</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthService 认证服务接口
 * @see JwtUtil JWT工具类
 * @see PasswordUtil 密码工具类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final com.zhixuewuli.common.security.AuthUserCache authUserCache;
    private final com.zhixuewuli.service.user.ClassroomGuard classroomGuard;
    private final com.zhixuewuli.service.teacherEvent.TaskRosterService roster;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public User authenticate(String username, String password) {
        // 输入验证
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidCredentialsException();
        }

        if (password == null || password.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        String loginId = username.trim();

        // 登录标识只有唯一用户名；真实姓名仅用于展示和教师检索。
        User user = userRepository.findByUsername(loginId)
                .orElseThrow(UserNotFoundException::new);

        // 检查账户状态
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new AccountDisabledException();
        }

        // 验证密码
        if (!passwordUtil.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // 更新最后登录时间
        var loginTime=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        int updated=userRepository.recordLoginIfVersionMatches(user.getId(),user.getTokenVersion(),loginTime);
        if(updated!=1) throw new InvalidCredentialsException();
        user.setLastLogin(loginTime); // detached after the narrow update; never merge stale security fields


        log.debug("用户登录成功，登录标识: {}, 实际用户名: {}", loginId, user.getUsername());
        return user;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generateAccessToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("用户对象不能为空");
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(),user.getTokenVersion()==null?0L:user.getTokenVersion());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public User registerUser(RegisterRequest request) {
        if (request.getUserType() != User.UserType.STUDENT) {
            throw new com.zhixuewuli.common.exception.InsufficientPermissionException(
                    "公开注册仅允许学生账号，教师身份须由已授权教师在用户管理中授予");
        }
        classroomGuard.lock();
        log.debug("开始处理用户注册请求，用户名: {}", request.getUsername());

        // 用户名唯一性只由用户名字段和数据库唯一约束保障。
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserNameDuplicateException(request.getUsername());
        }

        // 创建新用户对象
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setRealName(request.getRealName());
        newUser.setUserType(request.getUserType());
        newUser.setClassName(request.getClassName());
        newUser.setActive(true);

        // 加密密码
        String encryptedPassword = passwordUtil.encode(request.getPassword());
        newUser.setPasswordHash(encryptedPassword);

        // flush 让并发注册的数据库唯一约束冲突在事务内暴露并映射为业务错误。
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(newUser);
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            com.zhixuewuli.common.util.DatabaseExceptionParserUtil.throwAppropriateBusinessException(conflict);
            throw conflict;
        }

        roster.enroll(savedUser);
        log.debug("用户注册成功，用户ID: {}, 用户名: {}", savedUser.getId(), savedUser.getUsername());

        return savedUser;
    }
    @Override @Transactional
    public void logout(Long userId){
        userRepository.incrementTokenVersion(userId);
        authUserCache.invalidateAfterCommit(userId);
    }
}