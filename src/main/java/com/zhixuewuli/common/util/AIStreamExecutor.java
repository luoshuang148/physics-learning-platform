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
            System.out.println("DEBUG AIStreamExecutor: Starting operation " + operationName + " for user " + userId);
            operation.execute();
            System.out.println("DEBUG AIStreamExecutor: Operation completed successfully");
        } catch (QuestionNotFoundException | AnswerRecordNotFoundException e) {
            log.warn("AI业务异常 - 用户ID: {}, 原因: {}", userId, e.getMessage());
            System.out.println(
                    "DEBUG AIStreamExecutor: Caught QuestionNotFoundException/AnswerRecordNotFoundException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AIServiceUnavailableException e) {
            log.error("AI服务不可用 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            System.out.println("DEBUG AIStreamExecutor: Caught AIServiceUnavailableException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AITimeoutException e) {
            log.error("AI响应超时 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            System.out.println("DEBUG AIStreamExecutor: Caught AITimeoutException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AIResponseParseException e) {
            log.error("AI响应解析失败 - 用户ID: {}, 错误码: {}", userId, e.getCode(), e);
            System.out.println("DEBUG AIStreamExecutor: Caught AIResponseParseException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (AITechnicalException e) {
            log.error("AI技术异常兜底 - 用户ID: {}, 错误码: {}, 异常类型: {}",
                    userId, e.getCode(), e.getClass().getSimpleName(), e);
            System.out.println("DEBUG AIStreamExecutor: Caught AITechnicalException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), "AI技术服务异常，请稍后重试");
        } catch (BusinessException e) {
            log.warn("AI业务异常 - 用户ID: {}, 错误码: {}, 原因: {}", userId, e.getCode(), e.getMessage());
            System.out.println("DEBUG AIStreamExecutor: Caught BusinessException: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("AI未知异常 - 用户ID: {}", userId, e);
            System.out.println("DEBUG AIStreamExecutor: Caught generic Exception: " + e);
            e.printStackTrace();
            streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "AI服务出现未知问题，请稍后重试");
        }
    }
}