package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 用户名称重复异常
 * 
 * 当尝试创建或更新用户时，用户名违反唯一性约束时抛出此异常。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 */
public class UserNameDuplicateException extends AuthenticationException {

    /**
     * 构造函数
     * 
     * @param username 重复的用户名
     */
    public UserNameDuplicateException(String username) {
        super(ResultCode.USER_NAME_DUPLICATE,
                String.format("用户名 '%s' 已存在", username));
    }
}