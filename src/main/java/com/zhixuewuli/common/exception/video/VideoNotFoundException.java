package com.zhixuewuli.common.exception.video;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 视频不存在异常
 * 
 * <p>
 * 当尝试访问、操作或引用一个不存在的视频资源时抛出此异常。
 * 支持通过视频ID、标题等多种方式标识目标视频，属于视频模块的核心业务异常。
 * </p>
 * 
 * <h3>触发场景：</h3>
 * <ul>
 * <li><strong>ID查询失败</strong>：根据视频ID查询但数据库中无对应记录</li>
 * <li><strong>标题查询失败</strong>：根据视频标题搜索但找不到匹配项</li>
 * <li><strong>引用失效</strong>：视频已被删除但仍有其他模块引用</li>
 * <li><strong>权限限制</strong>：用户无权访问指定视频资源</li>
 * <li><strong>状态异常</strong>：视频处于上传中、转码中等中间状态</li>
 * </ul>
 * 
 * <h3>处理策略：</h3>
 * <ul>
 * <li><strong>多重验证</strong>：支持ID、标题、URL等多种查询方式</li>
 * <li><strong>缓存管理</strong>：及时同步视频状态变化到缓存层</li>
 * <li><strong>用户体验</strong>：提供替代内容推荐或恢复选项</li>
 * <li><strong>数据一致性</strong>：确保关联数据的完整性</li>
 * </ul>
 * 
 * <h3>性能优化：</h3>
 * <ul>
 * <li>实施合理的索引策略提升查询效率</li>
 * <li>使用缓存减少数据库查询压力</li>
 * <li>支持批量查询降低网络开销</li>
 * <li>提供异步加载改善响应速度</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see VideoBusinessException 视频业务异常基类
 * @see ResultCode.VIDEO_NOT_FOUND 对应的业务状态码
 */
public class VideoNotFoundException extends VideoBusinessException {

    /** 视频不存在错误码 */
    private static final ResultCode VIDEO_NOT_FOUND_CODE = ResultCode.VIDEO_NOT_FOUND;

    /**
     * 构造函数 - 通过视频ID构造异常
     * 
     * @param id 视频ID
     */
    public VideoNotFoundException(Long id) {
        super(VIDEO_NOT_FOUND_CODE, "视频 ID " + id + " 不存在"); // 注意：ID 前后都有空格
    }

    /**
     * 构造函数 - 通过视频标题构造异常
     * 
     * @param title 视频标题
     */
    public VideoNotFoundException(String title) {
        super(VIDEO_NOT_FOUND_CODE, "视频标题 '" + title + "' 不存在");
    }

    /**
     * 构造函数 - 自定义消息
     * 
     * @param message 自定义异常消息
     */
    public VideoNotFoundException(String message, Throwable cause) {
        super(VIDEO_NOT_FOUND_CODE, message, cause);
    }
}