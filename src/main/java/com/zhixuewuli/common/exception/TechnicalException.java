package com.zhixuewuli.common.exception;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 技术异常基类
 * 
 * <p>
 * 用于表示系统技术层面的异常情况，是所有技术相关异常的基类。
 * 区别于业务异常({@link BusinessException})，主要用于处理系统故障、网络问题、
 * 第三方服务异常等底层技术问题。
 * </p>
 * 
 * <h3>核心特性：</h3>
 * <ul>
 * <li><strong>继承RuntimeException</strong>：支持运行时抛出，简化异常传播</li>
 * <li><strong>包含技术错误码</strong>：通过{@link ResultCode}提供系统级错误标识</li>
 * <li><strong>支持根因追踪</strong>：可包装底层异常便于问题定位</li>
 * <li><strong>统一异常处理</strong>：与{@link GlobalExceptionHandler}无缝集成</li>
 * </ul>
 * 
 * <h3>典型应用场景：</h3>
 * <ul>
 * <li><strong>基础设施故障</strong>：数据库连接中断、缓存服务不可用</li>
 * <li><strong>外部服务异常</strong>：第三方API调用失败、网络超时</li>
 * <li><strong>系统资源问题</strong>：内存溢出、线程池耗尽、磁盘空间不足</li>
 * <li><strong>通信协议错误</strong>：HTTP请求失败、消息队列连接异常</li>
 * </ul>
 * 
 * <h3>设计考量：</h3>
 * <ul>
 * <li><strong>故障隔离</strong>：将技术问题与业务逻辑分离</li>
 * <li><strong>可观测性</strong>：提供详细的错误信息支持系统监控</li>
 * <li><strong>恢复能力</strong>：支持优雅降级和自动重试机制</li>
 * <li><strong>安全防护</strong>：避免敏感技术细节泄露给客户端</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see ResultCode 技术状态码枚举定义
 * @see BusinessException 业务异常基类
 * @see GlobalExceptionHandler 全局异常处理器
 */
public class TechnicalException extends RuntimeException {
    /** 技术错误码 */
    private final ResultCode code;

    public TechnicalException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.TECHNICAL_ERROR;
    }

    public TechnicalException(String message) {
        super(message);
        this.code = ResultCode.TECHNICAL_ERROR;
    }

    /**
     * 构造函数 - 包含指定的业务错误码
     * 
     * @param code    业务错误码
     * @param message 异常描述信息
     * @param cause   异常原因
     */
    public TechnicalException(ResultCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 构造函数 - 包含指定的业务错误码
     * 
     * @param code    业务错误码
     * @param message 异常描述信息
     */
    public TechnicalException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }

    public ResultCode getCode() {
        return code;
    }
}
