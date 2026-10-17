package com.zhixuewuli.common.exception;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 业务异常基类
 * 
 * <p>
 * 用于表示业务逻辑层面的异常情况，是所有业务相关异常的基类。
 * 区别于系统技术异常({@link TechnicalException})，主要用于处理业务规则违反、
 * 数据状态不一致等业务相关问题。
 * </p>
 * 
 * <h3>核心特性：</h3>
 * <ul>
 * <li><strong>继承RuntimeException</strong>：支持运行时抛出，无需显式声明</li>
 * <li><strong>包含业务错误码</strong>：通过{@link ResultCode}提供标准化错误标识</li>
 * <li><strong>灵活构造方式</strong>：支持多种参数组合的构造函数</li>
 * <li><strong>统一异常处理</strong>：与{@link GlobalExceptionHandler}无缝集成</li>
 * </ul>
 * 
 * <h3>典型使用场景：</h3>
 * <ul>
 * <li>用户操作不符合业务规则（如重复提交、非法操作）</li>
 * <li>数据状态转换不合法（如状态机违规转换）</li>
 * <li>业务流程执行条件不满足（如前置条件缺失）</li>
 * <li>资源配置或权限不足（如配额耗尽、权限拒绝）</li>
 * </ul>
 * 
 * <h3>设计原则：</h3>
 * <ul>
 * <li><strong>语义清晰</strong>：异常名称准确反映业务含义</li>
 * <li><strong>层次分明</strong>：通过继承关系组织异常类型</li>
 * <li><strong>信息完整</strong>：提供足够的上下文信息便于调试</li>
 * <li><strong>处理统一</strong>：通过全局异常处理器标准化响应格式</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see ResultCode 业务状态码枚举定义
 * @see TechnicalException 技术异常基类
 * @see GlobalExceptionHandler 全局异常处理器
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final ResultCode code;

    /**
     * 构造函数 - 使用默认业务错误码
     * 
     * @param message 异常描述信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR;
    }

    /**
     * 构造函数 - 指定错误码和消息
     * 
     * @param code    自定义业务错误码
     * @param message 异常描述信息
     */
    public BusinessException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码
     * 
     * @return Integer 业务错误码
     */
    public ResultCode getCode() {
        return code;
    }
}
