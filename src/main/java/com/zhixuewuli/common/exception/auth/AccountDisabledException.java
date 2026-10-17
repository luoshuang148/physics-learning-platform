package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 用户账户禁用异常
 * 
 * <p>
 * 当用户账户处于非活跃状态（禁用、锁定、未激活等）时抛出此异常。
 * 属于认证模块的重要异常类型，用于处理账户生命周期管理问题。
 * </p>
 * 
 * <h3>触发条件：</h3>
 * <ul>
 * <li><strong>管理员操作</strong>：系统管理员手动禁用账户</li>
 * <li><strong>安全策略</strong>：多次认证失败触发账户锁定</li>
 * <li><strong>合规要求</strong>：违反使用条款导致账户受限</li>
 * <li><strong>注册流程</strong>：邮箱/手机未验证完成</li>
 * </ul>
 * 
 * <h3>处理建议：</h3>
 * <ul>
 * <li>提供明确的状态说明和恢复指引</li>
 * <li>记录账户状态变更审计日志</li>
 * <li>支持管理员审核和申诉流程</li>
 * <li>实施渐进式解锁机制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AuthenticationException 认证异常基类
 * @see ResultCode.USER_DISABLED 对应的业务状态码
 */
public class AccountDisabledException extends AuthenticationException {

    /**
     * 构造函数 - 使用默认错误消息
     */
    public AccountDisabledException() {
        super(ResultCode.USER_DISABLED, "用户账户已被禁用");
    }

    /**
     * 构造函数 - 使用自定义错误消息
     * 
     * @param message 自定义错误消息
     */
    public AccountDisabledException(String message) {
        super(ResultCode.USER_DISABLED, message);
    }
}