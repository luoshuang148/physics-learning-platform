package com.zhixuewuli.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.exception.*;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.answerrecord.*;
import com.zhixuewuli.common.exception.auth.*;
import com.zhixuewuli.common.exception.handler.GlobalExceptionHandler;
import com.zhixuewuli.common.exception.question.*;
import com.zhixuewuli.common.exception.teacherevent.*;
import com.zhixuewuli.common.result.ResultCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
class ExceptionResponseContractTest {
    // Exercises HTTP exception resolution so subclass precedence and serialized fields stay observable.
    @ParameterizedTest
    @MethodSource("responses")
    void preservesStatusCodeMessageAndReason(RuntimeException failure, int httpStatus, String message, String reason)
            throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailureController(failure))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String response = mvc.perform(get("/failure")).andExpect(status().is(httpStatus))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var body = new ObjectMapper().readTree(response);
        int code = failure instanceof BusinessException business ? business.getCode().getCode()
                : failure instanceof TechnicalException technical ? technical.getCode().getCode()
                : ResultCode.TECHNICAL_ERROR.getCode();
        assertThat(body.path("code").asInt()).isEqualTo(code);
        assertThat(body.path("message").isNull() ? null : body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("reason").isNull() ? null : body.path("reason").asText()).isEqualTo(reason);
    }

    static Stream<Arguments> responses() {
        return Stream.of(
                same(new InvalidCredentialsException(), 401), same(new InvalidTokenException(), 401),
                same(new AccountDisabledException(), 403), same(new UserNotFoundException(), 404),
                same(new UserNameDuplicateException("student"), 409), same(new RealNameDuplicateException("学生"), 409),
                same(new AuthenticationException("auth"), 400), same(new BusinessException("business"), 400),
                same(new QuestionNotFoundException(1L), 404),
                same(new QuestionContentDuplicateException("duplicate"), 409),
                same(new EventNotFoundException(), 404), same(new EventAlreadyExistsException(), 409),
                custom(new QuestionBusinessException("hidden"), 500, null, null),
                custom(new AnswerRecordBusinessException("hidden"), 500, null, null),
                custom(new TeacherEventBusinessException("hidden"), 500, null, null),
                withReason(new SessionNotFoundException("session"), 404, "会话ID: session"),
                withReason(new InsufficientPermissionException(7L, "denied"), 403, "用户ID: 7"),
                same(new InsufficientPermissionException("denied"), 403),
                withReason(new AnswerRecordNotFoundException("record"), 404, "记录ID: record"),
                withReason(new AnswerRecordProcessingException("submit", "invalid"), 400, "操作类型: submit"),
                withReason(new InvalidQuestionIdException("question"), 400, "题目ID: question"),
                withReason(new SessionExpiredException("session"), 408, "会话ID: session"),
                custom(new InvalidQuestionStatisticsException("invalid"), 400, "题目统计查询异常: 题目统计查询异常：invalid", null),
                custom(new AIServiceUnavailableException("upstream"), 503, "AI服务暂时不可用，请稍后重试", "upstream"),
                custom(new AITimeoutException("upstream"), 408, "AI响应超时，请稍后重试", "upstream"),
                custom(new AIResponseParseException("hidden"), 500, "AI响应解析失败", null),
                custom(new SessionOperationException("clear", "session", "hidden"), 500, "会话操作失败", null),
                custom(new SessionManagementException(ResultCode.TECHNICAL_ERROR, "hidden") {}, 500, "会话管理失败", null),
                custom(new TokenCalculationException("hidden"), 500, "令牌计算失败", null),
                custom(new TokenQuotaException(ResultCode.AI_QUOTA_EXCEEDED, "upstream") {}, 429, "令牌配额不足", "upstream"),
                custom(new AITechnicalException(ResultCode.TECHNICAL_ERROR, "hidden") {}, 500, "AI技术服务异常，请稍后重试", null),
                custom(new TechnicalException("hidden"), 500, "系统繁忙，请稍后重试", null),
                custom(new RuntimeException("hidden"), 500, "系统繁忙，请稍后重试", null));
    }

    private static Arguments same(BusinessException error, int status) {
        return withReason(error, status, null);
    }

    private static Arguments withReason(BusinessException error, int status, String reason) {
        return custom(error, status, error.getMessage(), reason);
    }

    private static Arguments withReason(TechnicalException error, int status, String reason) {
        return custom(error, status, error.getMessage(), reason);
    }

    private static Arguments custom(RuntimeException error, int status, String message, String reason) {
        return Arguments.of(error, status, message, reason);
    }

    @RestController
    static class FailureController {
        private final RuntimeException failure;
        FailureController(RuntimeException failure) { this.failure = failure; }
        @GetMapping("/failure") public void fail() { throw failure; }
    }
}
