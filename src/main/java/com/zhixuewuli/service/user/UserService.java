package com.zhixuewuli.service.user;

import com.zhixuewuli.dto.request.UserAdminUpdateRequest;
import com.zhixuewuli.dto.request.UserSelfUpdateRequest;
import com.zhixuewuli.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * 
 * 提供用户管理的核心业务逻辑接口，包括用户信息查询、更新等操作。
 * 遵循权限分离原则：普通用户只能操作自己的信息，教师可以管理所有用户。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see User 用户实体类
 * @see UserSelfUpdateRequest 用户自我更新请求DTO
 * @see UserAdminUpdateRequest 管理员更新请求DTO
 */
public interface UserService {

    /**
     * 获取当前用户信息
     * 
     * 所有认证用户都可以调用此方法获取自己的完整信息。
     * 即使账户被禁用，用户仍然可以查看自己的信息。
     * 
     * @param currentUserId 当前登录用户的ID
     * @return User 用户实体对象
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException 当用户不存在时抛出
     * 
     * @permission 所有认证用户
     */
    User getCurrentUser(Long currentUserId);

    /**
     * 更新当前用户信息
     * 
     * 用户更新自己的基本信息，支持增量更新（只更新非空字段）。
     * 可更新字段：用户名、密码、真实姓名、班级。
     * 会进行唯一性约束检查，防止用户名和真实姓名重复。
     * 
     * @param currentUserId 当前登录用户的ID
     * @param request       用户自我更新请求DTO
     * @return User 更新后的用户实体对象
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException      当用户不存在时抛出
     * @throws com.zhixuewuli.common.exception.user.UserAlreadyExistsException 当用户名或真实姓名已存在时抛出
     * 
     * @permission 所有认证用户
     * @implSpec 支持增量更新，只更新非空字段
     * @implSpec 进行用户名和真实姓名唯一性检查
     * @implSpec 密码会自动进行BCrypt加密
     */
    User updateCurrentUser(Long currentUserId, UserSelfUpdateRequest request);

    /**
     * 根据用户ID获取用户信息
     * 
     * 教师根据用户ID查询用户详细信息。
     * 
     * @param userId 用户ID
     * @return User 用户实体对象
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException 当用户不存在时抛出
     * 
     * @permission 教师权限
     */
    User getUserById(Long userId);

    /**
     * 根据真实姓名获取用户信息
     * 
     * 教师根据学生真实姓名查询用户信息。
     * 
     * @param realName 学生真实姓名
     * @return User 用户实体对象
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException 当用户不存在时抛出
     * 
     * @permission 教师权限
     */
    User getUserByRealName(String realName);

    /**
     * 获取所有用户列表
     * 
     * 教师获取系统中所有用户的列表信息。
     * 
     * @return List<User> 用户列表
     * 
     * @permission 教师权限
     */
    List<User> getAllUsers();

    /**
     * 管理员更新用户信息
     * 
     * 教师更新指定用户的信息，支持增量更新（只更新非空字段）。
     * 可更新字段：真实姓名、班级、账户状态、用户类型。
     * 管理员操作具有优先级，可以覆盖用户的自我更新。
     * 目标用户ID从请求DTO中获取。
     * 
     * @param request       管理员更新请求DTO（包含目标用户ID）
     * @param currentUserId 当前操作教师的ID
     * @return User 更新后的用户实体对象
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException      当目标用户不存在时抛出
     * @throws com.zhixuewuli.common.exception.user.UserAlreadyExistsException 当真实姓名已存在时抛出
     * @throws com.zhixuewuli.common.exception.InsufficientPermissionException 当操作者不是教师时抛出
     * 
     * @permission 教师权限
     * @implSpec 支持增量更新，只更新非空字段
     * @implSpec 进行真实姓名唯一性检查
     * @implSpec 验证操作者具有教师权限
     * @implSpec 管理员操作优先于用户自我更新
     * @implSpec 目标用户ID从请求DTO中获取
     */
    User updateUserByAdmin(UserAdminUpdateRequest request, Long currentUserId);

    /**
     * 删除指定用户
     * 
     * 教师删除指定用户账户。
     * 
     * @param userId        目标用户的ID
     * @param currentUserId 当前操作教师的ID
     * @throws com.zhixuewuli.common.exception.user.UserNotFoundException      当目标用户不存在时抛出
     * @throws com.zhixuewuli.common.exception.InsufficientPermissionException 当操作者不是教师时抛出
     * 
     * @permission 教师权限
     */
    void deleteUser(Long userId, Long currentUserId);
}