package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * AI服务不可用异常
 * 
 * <p>
 * 当AI服务无法正常访问时抛出此异常，属于AI技术异常类别。
 * 主要涵盖网络连接失败、第三方服务异常、服务熔断等场景。
 * </p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 * <li><strong>网络连接问题</strong>：AI服务端点无法访问、DNS解析失败</li>
 * <li><strong>服务状态异常</strong>：第三方AI服务维护、服务熔断</li>
 * <li><strong>认证授权失败</strong>：API密钥失效、访问令牌过期</li>
 * <li><strong>负载保护</strong>：服务限流、配额耗尽</li>
 * </ul>
 * 
 * <h3>处理建议：</h3>
 * <ul>
 * <li>实施指数退避重试策略</li>
 * <li>提供降级方案（如返回缓存结果）</li>
 * <li>记录详细的服务状态信息</li>
 * <li>及时通知运维团队处理</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AITechnicalException AI技术异常基类
 * @see ResultCode.AI_SERVICE_UNAVAILABLE 对应的业务状态码
 */
public class AIServiceUnavailableException extends AITechnicalException {

    /**
     * 构造函数 - 包含消息和原因
     * 
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    public AIServiceUnavailableException(String message, Throwable cause) {
        super(ResultCode.AI_SERVICE_UNAVAILABLE, message, cause);
    }

    /**
     * 构造函数 - 仅包含消息
     * 
     * @param message 异常描述信息
     */
    public AIServiceUnavailableException(String message) {
        super(ResultCode.AI_SERVICE_UNAVAILABLE, message);
    }
}