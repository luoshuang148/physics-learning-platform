package com.zhixuewuli.common.exception.auth;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 认证异常基类
 * 
 * <p>
 * 继承自{@link BusinessException}，专门处理用户身份认证相关的业务异常。
 * 为整个认证领域提供统一的异常处理框架，支持细粒度的认证错误分类。
 * </p>
 * 
 * <h3>错误码体系：</h3>
 * <table border="1">
 * <tr>
 * <th>范围</th>
 * <th>用途</th>
 * <th>示例</th>
 * </tr>
 * <tr>
 * <td>4100-4109</td>
 * <td>基础认证异常</td>
 * <td>认证失败、凭证无效</td>
 * </tr>
 * <tr>
 * <td>4110-4119</td>
 * <td>用户认证异常</td>
 * <td>用户不存在、账户禁用</td>
 * </tr>
 * <tr>
 * <td>4120-4129</td>
 * <td>权限认证异常</td>
 * <td>权限不足、角色缺失</td>
 * </tr>
 * <tr>
 * <td>4130-4139</td>
 * <td>Token认证异常</td>
 * <td>令牌过期、签名无效</td>
 * </tr>
 * </table>
 * 
 * <h3>核心价值：</h3>
 * <ul>
 * <li><strong>领域专精</strong>：专注解决认证场景下的业务问题</li>
 * <li><strong>层次清晰</strong>：通过继承关系组织认证异常类型</li>
 * <li><strong>安全合规</strong>：遵循认证安全最佳实践</li>
 * <li><strong>易于扩展</strong>：支持新的认证方式和协议</li>
 * </ul>
 * 
 * <h3>安全设计：</h3>
 * <ul>
 * <li>避免泄露敏感认证信息</li>
 * <li>统一错误响应防止信息探测</li>
 * <li>支持审计日志记录</li>
 * <li>实施防暴力破解机制</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 * @see ResultCode 认证相关状态码定义
 */
public class AuthenticationException extends BusinessException {

    /**
     * 构造函数 - 使用默认用户状态不正确错误码
     * 
     * @param message 异常描述信息
     */
    public AuthenticationException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.USER_STATUS_INVALID, message);
    }

    /**
     * 构造函数 - 指定错误码和消息
     * 
     * @param code    自定义认证错误码
     * @param message 异常描述信息
     */
    public AuthenticationException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public AuthenticationException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.USER_STATUS_INVALID, message);
        initCause(cause);
    }
}