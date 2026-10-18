package com.zhixuewuli.service.auth;

import com.zhixuewuli.dto.request.RegisterRequest;
import com.zhixuewuli.entity.User;

/**
 * 认证服务接口
 * 
 * 处理用户认证和注册相关的核心业务逻辑。
 * 职责聚焦于用户身份验证和账户管理功能。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li>用户登录认证</li>
 * <li>JWT令牌生成</li>
 * <li>用户账户注册</li>
 * </ul>
 * 
 * <p>
 * 设计原则：
 * </p>
 * <ul>
 * <li><strong>单一职责</strong>: 专注认证和注册核心功能</li>
 * <li><strong>安全第一</strong>: 密码加密、输入验证、防重放攻击</li>
 * <li><strong>易于扩展</strong>: 接口设计便于未来功能扩展</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User 用户实体类
 * @see RegisterRequest 注册请求DTO
 */
public interface AuthService {
    void logout(Long userId);

    /**
     * 用户认证 - 验证用户名和密码
     * 
     * 验证用户提供的凭据是否正确，如果验证通过则返回用户对象。
     * 
     * @param username 用户名
     * @param password 密码
     * @return User 认证成功的用户对象，失败返回null
     * 
     * @implSpec 使用BCrypt验证密码安全性
     * @implSpec 检查用户账户状态（激活/禁用）
     * @implSpec 更新用户最后登录时间
     */
    User authenticate(String username, String password);

    /**
     * 生成JWT访问令牌
     * 
     * 为认证成功的用户生成JWT访问令牌，用于后续API调用的身份验证。
     * 
     * @param user 用户对象
     * @return String JWT令牌字符串
     * @throws IllegalArgumentException 当用户对象为空时抛出
     * 
     * @implSpec 包含用户ID和用户名信息
     * @implSpec 设置标准过期时间（24小时）
     * @implSpec 使用HS512签名算法保证安全性
     */
    String generateAccessToken(User user);

    /**
     * 用户注册 - 创建新用户账户
     * 
     * 根据注册请求创建新的用户账户，包括密码加密和数据验证。
     * 
     * @param request 注册请求对象，包含用户基本信息
     * @return User 注册成功的用户对象
     * @throws com.zhixuewuli.common.exception.BusinessException 当注册失败时抛出（如用户名已存在）
     * 
     * @implSpec 验证用户名唯一性
     * @implSpec 使用BCrypt加密密码
     * @implSpec 设置默认账户状态为激活
     * @implSpec 记录用户注册事件
     */
    User registerUser(RegisterRequest request);
}