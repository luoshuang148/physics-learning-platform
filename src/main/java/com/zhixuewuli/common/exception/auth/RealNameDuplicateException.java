package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 真实姓名重复异常
 * 
 * 当尝试创建或更新用户时，真实姓名违反唯一性约束时抛出此异常。
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 */
public class RealNameDuplicateException extends AuthenticationException {

    /**
     * 构造函数
     * 
     * @param realName 重复的真实姓名
     */
    public RealNameDuplicateException(String realName) {
        super(ResultCode.REAL_NAME_DUPLICATE,
                String.format("真实姓名 '%s' 已存在", realName));
    }
}