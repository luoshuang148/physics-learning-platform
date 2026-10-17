package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 令牌配额异常
 * 
 * <p>
 * 当AI服务的令牌使用量超出配额限制时抛出此异常。
 * 属于AI技术异常类别，通常发生在令牌消耗过快或配额不足的场景。
 * </p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 * <li><strong>配额耗尽</strong>：达到单日/单月令牌使用上限</li>
 * <li><strong>速率限制</strong>：单位时间内请求频率过高</li>
 * <li><strong>预算控制</strong>：成本预算达到预警阈值</li>
 * <li><strong>账户限制</strong>：免费额度使用完毕</li>
 * </ul>
 * 
 * <h3>应对措施：</h3>
 * <ul>
 * <li>实施请求队列和流量控制</li>
 * <li>提供配额使用情况监控</li>
 * <li>支持配额升级或扩展机制</li>
 * <li>优化提示词减少令牌消耗</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AITechnicalException AI技术异常基类
 * @see ResultCode.TOKEN_QUOTA_EXCEEDED 对应的业务状态码
 */
public class TokenQuotaException extends AITechnicalException {

    protected TokenQuotaException(ResultCode code, String message) {
        super(code, message);
    }
}