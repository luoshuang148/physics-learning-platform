package com.zhixuewuli.common.exception.experiment;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 实验名称重复异常
 * 
 * <p>
 * 当尝试创建或更新实验时，如果实验名称已存在则抛出此异常。
 * </p>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class ExperimentNameDuplicateException extends ExperimentBusinessException {
    
    private static final ResultCode ERROR_CODE = ResultCode.EXPERIMENT_NAME_DUPLICATE;
    
    /** 实验名称 */
    private final String name;
    
    /**
     * 构造函数 - 根据实验名称创建异常
     * 
     * @param name 实验名称
     */
    public ExperimentNameDuplicateException(String name) {
        super(ERROR_CODE, String.format("实验名称已存在：%s", name));
        this.name = name;
    }
    
    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param message 自定义异常消息
     * @param name 实验名称（可选）
     */
    public ExperimentNameDuplicateException(String message, String name) {
        super(ERROR_CODE, message);
        this.name = name;
    }
    
    /**
     * 获取实验名称
     * 
     * @return String 实验名称，如果未设置则返回 null
     */
    public String getName() {
        return name;
    }
}
