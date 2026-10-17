package com.zhixuewuli.common.exception.question;

import com.zhixuewuli.common.result.ResultCode;

/**
 * 关联视频不存在异常
 * 
 * 当题目关联的视频记录不存在时抛出此异常。
 * 错误码：4003
 * 
 * <p>
 * 触发场景：
 * </p>
 * <ul>
 * <li>创建题目时指定的视频ID不存在</li>
 * <li>更新题目时新的视频ID不存在</li>
 * <li>题目数据完整性验证失败</li>
 * <li>批量操作中发现视频关联异常</li>
 * </ul>
 * 
 * <p>
 * 业务含义：
 * </p>
 * <ul>
 * <li>确保题目与视频的关联关系有效</li>
 * <li>维护数据引用完整性</li>
 * <li>防止孤立的题目记录产生</li>
 * </ul>
 * 
 * <p>
 * 处理建议：
 * </p>
 * <ul>
 * <li>验证视频ID的有效性</li>
 * <li>检查视频是否已被删除</li>
 * <li>提供视频选择界面</li>
 * <li>记录关联关系异常日志</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public class QuestionVideoNotFoundException extends QuestionBusinessException {

    private static final ResultCode ERROR_CODE = ResultCode.QUESTION_VIDEO_NOT_FOUND;

    /**
     * 构造函数 - 根据视频ID创建异常
     * 
     * @param videoId 视频ID
     */
    public QuestionVideoNotFoundException(Long videoId) {
        super(ERROR_CODE, String.format("关联的视频不存在，ID: %d", videoId));
    }

    /**
     * 构造函数 - 使用自定义消息
     * 
     * @param message 自定义异常消息
     */
    public QuestionVideoNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}