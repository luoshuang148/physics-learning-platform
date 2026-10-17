package com.zhixuewuli.common.exception.video;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 视频已存在异常
 * 
 * 当尝试创建重复标题或URL的视频时抛出此异常
 * 用于维护视频数据的唯一性约束
 * 
 * <p>
 * 使用场景：
 * </p>
 * <ul>
 * <li>创建新视频时标题已存在</li>
 * <li>创建新视频时URL已存在</li>
 * <li>更新视频时新标题与其他视频冲突</li>
 * <li>更新视频时新URL与其他视频冲突</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoBusinessException 视频业务异常基类
 */
public class VideoAlreadyExistsException extends VideoBusinessException {

    /** 视频已存在错误码 */
    private static final ResultCode VIDEO_ALREADY_EXISTS_CODE = ResultCode.VIDEO_ALREADY_EXISTS;

    /**
     * 构造函数 - 字段重复异常
     * 
     * @param field 重复的字段名（如"标题"、"播放链接"）
     * @param value 重复的字段值
     */
    public VideoAlreadyExistsException(String field, String value) {
        super(VIDEO_ALREADY_EXISTS_CODE, "视频" + field + " '" + value + "' 已存在");
    }

    /**
     * 构造函数 - 标题重复异常
     * 
     * @param title 重复的标题
     */
    public VideoAlreadyExistsException(String title) {
        super(VIDEO_ALREADY_EXISTS_CODE, "视频标题 '" + title + "' 已存在");
    }

    /**
     * 构造函数 - URL重复异常
     * 
     * @param url 重复的URL
     */
    public VideoAlreadyExistsException(String url, boolean isUrl) {
        super(VIDEO_ALREADY_EXISTS_CODE, "视频播放链接 '" + url + "' 已存在");
    }

    /**
     * 构造函数 - 自定义消息
     * 
     * @param message 自定义异常消息
     * @param cause   原因异常
     */
    public VideoAlreadyExistsException(String message, Throwable cause) {
        super(VIDEO_ALREADY_EXISTS_CODE, message, cause);
    }
}