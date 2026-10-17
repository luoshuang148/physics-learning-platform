package com.zhixuewuli.common.client.callback;

/**
 * AI流式回调接口
 * 
 * 定义流式响应处理的通用回调方法，用于处理AI生成内容的实时传输。
 * 所有AI客户端都应该实现此接口以保证统一的流式处理体验。
 * 该接口采用事件驱动的设计模式，支持实时内容推送和状态通知。
 * 
 * <p>
 * 核心功能：
 * </p>
 * <ul>
 * <li><strong>实时内容接收</strong>: {@link #onContent(String, boolean)} -
 * 处理AI逐步生成的内容</li>
 * <li><strong>传输完成通知</strong>: {@link #onComplete(int, int, int)} -
 * 通知整个流式传输完成</li>
 * <li><strong>错误处理机制</strong>: {@link #onError(Exception)} - 处理传输过程中的异常情况</li>
 * <li><strong>进度跟踪支持</strong>: {@link #onProgress(int)} - 可选的进度更新功能</li>
 * </ul>
 * 
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     AIStreamCallback callback = new AIStreamCallback() {
 *         &#64;Override
 *         public void onContent(String content, boolean isLast) {
 *             System.out.print(content);
 *             if (isLast) {
 *                 System.out.println("\n--- 传输完成 ---");
 *             }
 *         }
 * 
 *         &#64;Override
 *         public void onComplete(int promptTokens, int completionTokens, int totalTokens) {
 *             System.out.printf("Token统计: 提示词%d, 生成%d, 总计%d%n",
 *                     promptTokens, completionTokens, totalTokens);
 *         }
 * 
 *         @Override
 *         public void onError(Exception error) {
 *             System.err.println("传输错误: " + error.getMessage());
 *         }
 *     };
 * }
 * </pre>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
public interface AIStreamCallback {

    /**
     * 内容接收回调
     * 
     * 当AI生成新的内容片段时调用此方法。这是流式传输的核心回调，
     * 会在每次接收到新的内容时触发。通过isLast参数可以判断是否为最终内容。
     * 
     * <p>
     * 实现建议：
     * </p>
     * <ul>
     * <li>及时处理接收到的内容，避免阻塞</li>
     * <li>根据isLast参数决定是否进行最终处理</li>
     * <li>注意线程安全性，可能在不同线程中被调用</li>
     * <li>异常处理应该在实现中妥善处理，避免影响主流程</li>
     * </ul>
     * 
     * @param content 接收到的内容片段，通常是文本的一部分
     * @param isLast  是否为最后的内容片段，true表示传输即将结束
     * @implSpec 实现类应该快速处理内容，避免长时间阻塞
     */
    void onContent(String content, boolean isLast);

    /**
     * 传输完成回调
     * 
     * 当整个流式传输过程完全结束后调用此方法。
     * 提供详细的token消耗统计信息，用于成本计算和性能分析。
     * 
     * <p>
     * Token计算说明：
     * </p>
     * <ul>
     * <li><strong>promptTokens</strong>: 用户输入提示词消耗的token数量</li>
     * <li><strong>completionTokens</strong>: AI生成内容消耗的token数量</li>
     * <li><strong>totalTokens</strong>: 总消耗token数，等于前两者之和</li>
     * </ul>
     * 
     * @param promptTokens     提示词消耗的token数
     * @param completionTokens 生成内容消耗的token数
     * @param totalTokens      总消耗token数
     * @implNote 此方法在所有{@link #onContent(String, boolean)}调用之后执行
     */
    void onComplete(int promptTokens, int completionTokens, int totalTokens);

    /**
     * 错误处理回调
     * 
     * 当流式传输过程中发生错误时调用此方法。
     * 包括网络异常、API错误、超时等各种异常情况。
     * 
     * <p>
     * 常见错误类型：
     * </p>
     * <ul>
     * <li><strong>网络异常</strong>: 连接中断、超时等</li>
     * <li><strong>API错误</strong>: 认证失败、配额不足、参数错误等</li>
     * <li><strong>服务异常</strong>: AI服务内部错误</li>
     * <li><strong>解析错误</strong>: 响应数据格式异常</li>
     * </ul>
     * 
     * @param error 发生的具体异常对象
     * @implSpec 建议记录详细的错误信息并进行适当的清理工作
     */
    void onError(Exception error);

    /**
     * 进度更新回调
     * 
     * 可选的进度跟踪方法，默认空实现。
     * 子类可以根据需要重写此方法来实现进度显示。
     * 
     * <p>
     * 进度计算说明：
     * </p>
     * <ul>
     * <li>进度值范围：0-100的整数</li>
     * <li>0表示刚开始传输</li>
     * <li>100表示传输接近完成</li>
     * <li>具体的计算逻辑由调用方实现</li>
     * </ul>
     * 
     * <p>
     * 使用场景：
     * </p>
     * <ul>
     * <li>前端进度条显示</li>
     * <li>命令行进度指示器</li>
     * <li>日志进度报告</li>
     * </ul>
     * 
     * @param progress 当前进度百分比(0-100)
     * @implSpec 默认空实现，可根据需要重写
     */
    default void onProgress(int progress) {
        // 默认空实现
    }

    /**
     * 深度思考内容回调
     * 当启用thinking参数且模型返回reasoning_content时调用
     * 
     * @param reasoning 思考内容片段，建议前端用特殊样式展示
     */
    default void onReasoningContent(String reasoning) {
    }

    /**
     * 检查是否已取消
     * 用于流式生成过程中，检测外部（如 SSE 连接断开）发出的取消信号
     * 
     * @return true 表示已取消，此时应停止生成流
     */
    /** Registers cancellation of a blocking upstream call. Late registration must cancel immediately. */
    default void onCancel(Runnable action) { }

    default boolean isCancelled() {
        return false;
    }
}