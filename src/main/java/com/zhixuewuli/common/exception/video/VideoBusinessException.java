package com.zhixuewuli.common.exception.video;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.result.ResultCode;

/**
 * 视频业务异常基类
 * 
 * 继承BusinessException，为视频相关业务异常提供统一基类
 * 使用特定的错误码范围（4300+系列），便于异常分类和管理
 * 
 * <p>
 * 设计特点：
 * </p>
 * <ul>
 * <li>使用特定的错误码范围（4300+系列）</li>
 * <li>提供视频领域专用的构造方法</li>
 * <li>保持与业务异常体系的一致性</li>
 * <li>支持灵活的错误码和消息组合</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see BusinessException 业务异常基类
 */
public class VideoBusinessException extends BusinessException {

    /**
     * 构造函数 - 使用默认视频状态不正确错误码
     * 
     * @param message 异常描述信息
     */
    public VideoBusinessException(String message) {
        super(com.zhixuewuli.common.result.ResultCode.VIDEO_STATUS_INVALID, message);
    }

    /**
     * 构造函数 - 指定错误码和消息（基类推荐明确传入）
     * 
     * @param code    自定义视频错误码
     * @param message 异常描述信息
     */
    public VideoBusinessException(ResultCode code, String message) {
        super(code, message);
    }

    /**
     * 构造函数 - 指定消息和原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public VideoBusinessException(String message, Throwable cause) {
        super(com.zhixuewuli.common.result.ResultCode.VIDEO_STATUS_INVALID, message);
        initCause(cause);
    }

    /**
     * 构造函数 - 包含原因异常
     * 
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public VideoBusinessException(ResultCode code, Throwable cause) {
        super(code, cause.getMessage());
        initCause(cause);
    }

    /**
     * 构造函数 - 指定错误码、消息和原因异常
     * 
     * @param code    自定义视频错误码
     * @param message 异常描述信息
     * @param cause   原因异常
     */
    public VideoBusinessException(ResultCode code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }

}