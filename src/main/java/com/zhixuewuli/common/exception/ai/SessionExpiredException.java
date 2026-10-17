package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * AI会话过期异常
 * 
 * <p>
 * 当AI交互会话已过期、失效或超出有效时间范围时抛出此异常。
 * 属于AI业务异常类别，用于处理会话生命周期管理相关的业务问题。
 * </p>
 * 
 * <h3>触发条件：</h3>
 * <ul>
 * <li><strong>时间超限</strong>：会话持续时间超过最大允许时长</li>
 * <li><strong>空闲超时</strong>：会话长时间无活动自动过期</li>
 * <li><strong>主动终止</strong>：用户或系统主动结束会话</li>
 * <li><strong>资源回收</strong>：系统清理过期会话释放资源</li>
 * </ul>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 * <li>提供清晰的过期时间提示</li>
 * <li>支持会话续期机制</li>
 * <li>优雅处理正在进行的交互</li>
 * <li>记录会话使用统计信息</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AIBusinessException AI业务异常基类
 * @see ResultCode.SESSION_EXPIRED 对应的业务状态码(5508)
 */
public class SessionExpiredException extends AIBusinessException {

    private static final ResultCode ERROR_CODE = ResultCode.SESSION_EXPIRED;

    private final String sessionId;

    /**
     * 构造函数 - 根据会话ID创建异常
     * 
     * @param sessionId 会话ID
     */
    public SessionExpiredException(String sessionId) {
        super(ERROR_CODE, String.format("会话 %s 已过期", sessionId));
        this.sessionId = sessionId;
    }

    /**
     * 构造函数 - 使用自定义消息和原因
     * 
     * @param message 自定义异常消息
     * @param cause   异常原因
     */
    public SessionExpiredException(String message, Throwable cause) {
        super(ERROR_CODE, message);
        initCause(cause);
        this.sessionId = null;
    }

    /**
     * 获取会话ID
     * 
     * @return String 会话ID，如果未设置则返回null
     */
    public String getSessionId() {
        return sessionId;
    }
}