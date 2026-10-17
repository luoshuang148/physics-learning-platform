package com.zhixuewuli.common.util;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 流式操作异常处理模板
 * 统一处理各类 AI 相关异常，并发送标准错误响应
 */
@Slf4j
public class AIStreamExecutor {
    public static void submit(java.util.concurrent.Executor executor, Long userId, SseEmitter emitter,
            StreamUtils streams, Runnable task) {
        try {
            streams.claimUser(emitter, userId);
            executor.execute(() -> {
                if (streams.isCancelled(emitter)) { streams.safeCompleteEmitter(emitter); return; }
                try { task.run(); }
                catch (Exception error) {
                    log.error("AI 工作任务失败", error);
                    streams.sendError(emitter, ResultCode.TECHNICAL_ERROR, "AI处理失败，请稍后重试");
                } finally { streams.safeCompleteEmitter(emitter); }
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            streams.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI任务队列已满，请稍后重试");
        } catch (BusinessException error) {
            streams.sendError(emitter, error.getCode(), error.getMessage());
        }
    }


    @FunctionalInterface
    public interface StreamOperation {
        void execute() throws Exception;
    }

    /**
     * 执行流式操作，并统一处理异常
     *
     * @param operationName 操作名称（用于日志）
     * @param userId        当前用户 ID
     * @param emitter       SSE 发射器
     * @param streamUtils   StreamUtils 实例
     * @param operation     实际业务操作
     */
    public static void executeStream(String operationName, Long userId,
            SseEmitter emitter, StreamUtils streamUtils, StreamOperation operation) {
        try {
            log.info("{}开始 - 用户ID: {}", operationName, userId);
            operation.execute();
        } catch (QuestionNotFoundException | AnswerRecordNotFoundException e) {
            log.warn("AI业务异常 - 用户ID: {}, 原因: {}", userId, e.getMessage());
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AIServiceUnavailableException e) {
            log.error("AI服务不可用 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AITimeoutException e) {
            log.error("AI响应超时 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AIResponseParseException e) {
            log.error("AI响应解析失败 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AITechnicalException e) {
            log.error("AI技术异常兜底 - 用户ID: {}, 错误码: {}, 异常类型: {}",
                    userId, e.getCode(), e.getClass().getSimpleName(), e);
            streamUtils.sendError(emitter, e.getCode(), "AI技术服务异常，请稍后重试");
        } catch (BusinessException e) {
            log.warn("AI业务异常 - 用户ID: {}, 错误码: {}, 原因: {}", userId, e.getCode(), e.getMessage());
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("AI未知异常 - 用户ID: {}", userId, e);
            streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "AI服务出现未知问题，请稍后重试");
        }
    }
}