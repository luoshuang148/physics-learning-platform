package com.zhixuewuli.common.exception.experiment;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 实验不存在异常
 * 
 * <p>
 * 当尝试访问、操作或引用一个不存在的实验记录时抛出此异常。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class ExperimentNotFoundException extends ExperimentBusinessException {
    
    private static final ResultCode ERROR_CODE = ResultCode.EXPERIMENT_NOT_FOUND;
    
    /** 实验ID */
    private final Long experimentId;
    
    /**
     * 构造函数 - 根据实验ID 创建异常
     * 
     * @param experimentId 实验ID
     */
    public ExperimentNotFoundException(Long experimentId) {
        super(ERROR_CODE, String.format("实验不存在，ID: %d", experimentId));
        this.experimentId = experimentId;
    }
    
    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param message 自定义异常消息
     */
    public ExperimentNotFoundException(String message) {
        super(ERROR_CODE, message);
        this.experimentId = null;
    }
    
    /**
     * 获取实验ID
     * 
     * @return Long 实验ID，如果未设置则返回 null
     */
    public Long getExperimentId() {
        return experimentId;
    }
}
