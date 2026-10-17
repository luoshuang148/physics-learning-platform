package com.zhixuewuli.common.exception.ai;

import com.zhixuewuli.common.result.ResultCode;

/**
 * AI响应超时异常
 * 
 * <p>
 * 当AI服务响应时间超过预设超时阈值时抛出此异常。
 * 属于AI技术异常范畴，通常表明服务响应缓慢或网络延迟过高。
 * </p>
 * 
 * <h3>常见原因：</h3>
 * <ul>
 * <li><strong>模型推理耗时</strong>：复杂任务处理时间超出预期</li>
 * <li><strong>网络延迟</strong>：客户端与AI服务间网络状况不佳</li>
 * <li><strong>服务负载</strong>：AI服务并发量过大导致排队等待</li>
 * <li><strong>资源配置</strong>：计算资源不足影响处理速度</li>
 * </ul>
 * 
 * <h3>优化策略：</h3>
 * <ul>
 * <li>调整合理的超时时间配置</li>
 * <li>实施异步处理机制</li>
 * <li>增加请求缓存减少重复计算</li>
 * <li>提供进度反馈改善用户体验</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see AITechnicalException AI技术异常基类
 * @see ResultCode.AI_TIMEOUT 对应的业务状态码(4502)
 */
public class AITimeoutException extends AITechnicalException {

    /**
     * 构造函数
     * 
     * @param message 异常描述信息
     */
    public AITimeoutException(String message) {
        super(ResultCode.AI_TIMEOUT, message);
    }
}