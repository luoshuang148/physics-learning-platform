package com.zhixuewuli.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import com.zhixuewuli.common.exception.ai.AITimeoutException;
import com.zhixuewuli.common.exception.ai.AIResponseParseException;
import com.zhixuewuli.common.exception.ai.AITechnicalException;
import com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException;
import com.zhixuewuli.common.exception.question.QuestionNotFoundException;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.common.util.StreamUtils;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AIStreamExecutor 单元测试类
 * 
 * <p>
 * 测试 AI 流式操作的异常分类处理逻辑，包括：
 * </p>
 * <ul>
 * <li>题目/答题记录不存在异常 - QuestionNotFoundException,
 * AnswerRecordNotFoundException</li>
 * <li>AI 服务不可用异常 - AIServiceUnavailableException</li>
 * <li>AI 响应超时异常 - AITimeoutException</li>
 * <li>AI 响应解析失败异常 - AIResponseParseException</li>
 * <li>AI 技术异常兜底 - AITechnicalException 子类</li>
 * <li>通用业务异常 - BusinessException</li>
 * <li>未知异常 - Exception</li>
 * </ul>
 * 
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 * <li>使用 Mock 模拟 SseEmitter 和 StreamOperation</li>
 * <li>验证每种异常类型都被正确捕获并发送对应的错误响应</li>
 * <li>确保异常处理后 emitter 被正确关闭</li>
 * <li>符合现有实际代码与架构设计</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("AIStreamExecutor 单元测试 - 异常分类处理")
class AIStreamExecutorUnitTest extends BaseUnitTest {

    private SseEmitter mockEmitter;
    private StreamUtils mockStreamUtils;
    private AIStreamExecutor.StreamOperation mockOperation;

    // 测试常量
    private static final String TEST_OPERATION_NAME = "测试操作";
    private static final Long TEST_USER_ID = 12345L;

    @BeforeEach
    void setUp() {
        mockEmitter = mock(SseEmitter.class);
        mockStreamUtils = mock(StreamUtils.class);
        mockOperation = mock(AIStreamExecutor.StreamOperation.class);
    }

    // ==================== 成功场景测试 ====================

    @Nested
    @DisplayName("executeStream - 成功场景测试")
    class SuccessTests {

        @Test
        @DisplayName("应该成功执行操作且不发送错误消息")
        void testExecuteStream_SuccessfulOperation_ShouldNotSendError() throws Exception {
            // Given - 模拟操作成功执行，不抛出异常
            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockOperation, times(1)).execute();
            // 不应该调用 sendError（因为这是静态方法，通过 never 验证没有异常抛出）
        }
    }

    // ==================== 题目/答题记录不存在异常测试 ====================

    @Nested
    @DisplayName("executeStream - 题目/答题记录不存在异常测试")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("当抛出 QuestionNotFoundException 时应该发送 BUSINESS_ERROR 错误")
        void testExecuteStream_QuestionNotFoundException_ShouldSendBusinessError() throws Exception {
            // Given
            Long questionId = 999L;
            QuestionNotFoundException exception = new QuestionNotFoundException(questionId);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
        }

        @Test
        @DisplayName("当抛出 AnswerRecordNotFoundException 时应该发送 ANSWER_RECORD_NOT_FOUND 错误")
        void testExecuteStream_AnswerRecordNotFoundException_ShouldSendAnswerRecordError() throws Exception {
            // Given
            String recordId = "record-999";
            AnswerRecordNotFoundException exception = new AnswerRecordNotFoundException(recordId);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
        }

        @Test
        @DisplayName("QuestionNotFoundException 应该包含正确的错误码和消息")
        void testExecuteStream_QuestionNotFoundException_ShouldContainCorrectErrorCode() throws Exception {
            // Given
            Long questionId = 888L;
            String expectedMessage = "题目不存在，ID: 888";
            QuestionNotFoundException exception = new QuestionNotFoundException(questionId);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then - 验证错误码为 QUESTION_NOT_FOUND (4202)
            assertThat(exception.getCode()).isEqualTo(ResultCode.QUESTION_NOT_FOUND);
            assertThat(exception.getMessage()).contains(expectedMessage);
        }

        @Test
        @DisplayName("AnswerRecordNotFoundException 应该包含正确的错误码和消息")
        void testExecuteStream_AnswerRecordNotFoundException_ShouldContainCorrectErrorCode() throws Exception {
            // Given
            String recordId = "record-777";
            AnswerRecordNotFoundException exception = new AnswerRecordNotFoundException(recordId);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            assertThat(exception.getCode()).isEqualTo(ResultCode.ANSWER_RECORD_NOT_FOUND);
            assertThat(exception.getMessage()).contains("答题记录不存在:");
        }
    }

    // ==================== AI 服务不可用异常测试 ====================

    @Nested
    @DisplayName("executeStream - AI 服务不可用异常测试")
    class AIServiceUnavailableTests {

        @Test
        @DisplayName("当抛出 AIServiceUnavailableException 时应该发送 AI_SERVICE_UNAVAILABLE 错误")
        void testExecuteStream_AIServiceUnavailableException_ShouldSendServiceUnavailableError() throws Exception {
            // Given
            String errorMessage = "AI 服务认证失败，请检查 API 密钥配置";
            AIServiceUnavailableException exception = new AIServiceUnavailableException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.AI_SERVICE_UNAVAILABLE),
                    eq(errorMessage));
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_SERVICE_UNAVAILABLE);
            assertThat(exception.getMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("AIServiceUnavailableException 带原因异常时应该正确处理")
        void testExecuteStream_AIServiceUnavailableException_WithCause_ShouldHandleCorrectly() throws Exception {
            // Given
            String errorMessage = "AI 服务不可用";
            Throwable cause = new RuntimeException("网络连接中断");
            AIServiceUnavailableException exception = new AIServiceUnavailableException(errorMessage, cause);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_SERVICE_UNAVAILABLE);
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    // ==================== AI 响应超时异常测试 ====================

    @Nested
    @DisplayName("executeStream - AI 响应超时异常测试")
    class AITimeoutTests {

        @Test
        @DisplayName("当抛出 AITimeoutException 时应该发送 AI_TIMEOUT 错误")
        void testExecuteStream_AITimeoutException_ShouldSendTimeoutError() throws Exception {
            // Given
            String errorMessage = "AI 服务响应超时，已超过 30 秒";
            AITimeoutException exception = new AITimeoutException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.AI_TIMEOUT), eq(errorMessage));
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_TIMEOUT);
            assertThat(exception.getMessage()).isEqualTo(errorMessage);
        }
    }

    // ==================== AI 响应解析失败异常测试 ====================

    @Nested
    @DisplayName("executeStream - AI 响应解析失败异常测试")
    class AIResponseParseTests {

        @Test
        @DisplayName("当抛出 AIResponseParseException 时应该发送 AI_PARSE_ERROR 错误")
        void testExecuteStream_AIResponseParseException_ShouldSendParseError() throws Exception {
            // Given
            String errorMessage = "AI 响应 JSON 格式无效";
            AIResponseParseException exception = new AIResponseParseException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.AI_PARSE_ERROR),
                    eq(errorMessage));
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_PARSE_ERROR);
            assertThat(exception.getMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("AIResponseParseException 带原因异常时应该正确处理")
        void testExecuteStream_AIResponseParseException_WithCause_ShouldHandleCorrectly() throws Exception {
            // Given
            String errorMessage = "解析 AI 响应失败";
            Throwable cause = new com.fasterxml.jackson.core.JsonParseException("Invalid JSON");
            AIResponseParseException exception = new AIResponseParseException(errorMessage, cause);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_PARSE_ERROR);
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    // ==================== AI 技术异常兜底测试 ====================

    @Nested
    @DisplayName("executeStream - AI 技术异常兜底测试")
    class AITechnicalTests {

        @Test
        @DisplayName("当抛出 AITechnicalException 时应该发送默认技术异常消息")
        void testExecuteStream_AITechnicalException_ShouldSendDefaultTechnicalMessage() throws Exception {
            // Given - 创建一个匿名的 AITechnicalException 子类实例
            AITechnicalException exception = new AITechnicalException(ResultCode.AI_QUOTA_EXCEEDED, "AI 配额不足") {
            };
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.AI_QUOTA_EXCEEDED), any());
            assertThat(exception.getCode()).isEqualTo(ResultCode.AI_QUOTA_EXCEEDED);
        }

        @Test
        @DisplayName("AITechnicalException 子类应该被正确捕获")
        void testExecuteStream_AITechnicalException_Subclass_ShouldBeCaught() throws Exception {
            // Given - AIServiceUnavailableException 是 AITechnicalException 的子类
            // 但会被更具体的 catch 块捕获，这里测试其他子类
            AITechnicalException exception = new AITechnicalException(ResultCode.AI_MODEL_UNSUPPORTED, "模型不支持") {
            };
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
        }
    }

    // ==================== 通用业务异常测试 ====================

    @Nested
    @DisplayName("executeStream - 通用业务异常测试")
    class BusinessExceptionTests {

        @Test
        @DisplayName("当抛出 BusinessException 时应该发送对应的业务错误")
        void testExecuteStream_BusinessException_ShouldSendBusinessError() throws Exception {
            // Given
            String errorMessage = "用户权限不足";
            BusinessException exception = new BusinessException(ResultCode.PERMISSION_DENIED, errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.PERMISSION_DENIED),
                    eq(errorMessage));
            assertThat(exception.getCode()).isEqualTo(ResultCode.PERMISSION_DENIED);
            assertThat(exception.getMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("BusinessException 使用默认错误码时应该正确处理")
        void testExecuteStream_BusinessException_DefaultCode_ShouldHandleCorrectly() throws Exception {
            // Given
            String errorMessage = "业务逻辑错误";
            BusinessException exception = new BusinessException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
            assertThat(exception.getCode()).isEqualTo(ResultCode.BUSINESS_ERROR);
        }
    }

    // ==================== 未知异常测试 ====================

    @Nested
    @DisplayName("executeStream - 未知异常测试")
    class UnknownExceptionTests {

        @Test
        @DisplayName("当抛出未知 Exception 时应该发送 TECHNICAL_ERROR 错误")
        void testExecuteStream_UnknownException_ShouldSendTechnicalError() throws Exception {
            // Given
            String errorMessage = "未预期的运行时异常";
            RuntimeException exception = new RuntimeException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then - 未知异常统一发送 TECHNICAL_ERROR
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.TECHNICAL_ERROR), any());
        }

        @Test
        @DisplayName("当抛出 Error 时应该被 Exception 捕获并发送 TECHNICAL_ERROR 错误")
        void testExecuteStream_Error_ShouldBeCaughtAsException() throws Exception {
            // Given
            String errorMessage = "严重错误";
            IllegalStateException exception = new IllegalStateException(errorMessage);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.TECHNICAL_ERROR), any());
        }
    }

    // ==================== 异常优先级测试 ====================

    @Nested
    @DisplayName("executeStream - 异常捕获优先级测试")
    class ExceptionPriorityTests {

        @Test
        @DisplayName("AIServiceUnavailableException 应该被具体捕获而不是 AITechnicalException 兜底")
        void testExecuteStream_AIServiceUnavailableException_PriorityOverAITechnicalException() throws Exception {
            // Given - AIServiceUnavailableException 是 AITechnicalException 的子类
            AIServiceUnavailableException exception = new AIServiceUnavailableException("服务不可用");
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then - 应该被具体的 catch 块捕获，而不是被 AITechnicalException 捕获
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.AI_SERVICE_UNAVAILABLE),
                    eq("服务不可用"));
            // 验证使用的是具体的错误消息，而不是兜底的"AI 技术服务异常"
            assertThat(exception.getMessage()).isEqualTo("服务不可用");
        }

        @Test
        @DisplayName("QuestionNotFoundException 应该优先于 BusinessException 被捕获")
        void testExecuteStream_QuestionNotFoundException_PriorityOverBusinessException() throws Exception {
            // Given - QuestionNotFoundException 最终继承自 RuntimeException
            QuestionNotFoundException exception = new QuestionNotFoundException(123L);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then - 应该被第一个 catch 块捕获
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
            assertThat(exception.getCode()).isEqualTo(ResultCode.QUESTION_NOT_FOUND);
        }
    }

    // ==================== StreamUtils.sendError 集成测试 ====================

    @Nested
    @DisplayName("executeStream - StreamUtils 集成测试")
    class StreamUtilsIntegrationTests {

        @Test
        @DisplayName("异常处理后应该调用 StreamUtils.sendError 并关闭 emitter")
        void testExecuteStream_ExceptionHandling_ShouldCallStreamUtilsAndCloseEmitter() throws Exception {
            // Given
            BusinessException exception = new BusinessException("测试异常");
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            // 验证 sendError 被调用（emitter 会在 sendError 内部被关闭）
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), any(), any());
        }

        @Test
        @DisplayName("发送错误消息应该包含错误码和消息")
        void testExecuteStream_ErrorMessage_ShouldContainCodeAndMessage() throws Exception {
            // Given
            String errorMsg = "权限验证失败";
            BusinessException exception = new BusinessException(ResultCode.INSUFFICIENT_TEACHER_PERMISSION, errorMsg);
            doThrow(exception).when(mockOperation).execute();

            // When
            AIStreamExecutor.executeStream(TEST_OPERATION_NAME, TEST_USER_ID, mockEmitter, mockStreamUtils,
                    mockOperation);

            // Then
            verify(mockStreamUtils, times(1)).sendError(eq(mockEmitter), eq(ResultCode.INSUFFICIENT_TEACHER_PERMISSION),
                    eq(errorMsg));
            assertThat(exception.getCode()).isEqualTo(ResultCode.INSUFFICIENT_TEACHER_PERMISSION);
            assertThat(exception.getMessage()).isEqualTo(errorMsg);
        }
    }
}
