package com.zhixuewuli.common.exception.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.TechnicalException;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import com.zhixuewuli.common.exception.SessionNotFoundException;
import com.zhixuewuli.common.exception.ai.*;
import com.zhixuewuli.common.exception.auth.*;
import com.zhixuewuli.common.exception.question.*;
import com.zhixuewuli.common.exception.questionErrorAnalysis.*;
import com.zhixuewuli.common.exception.answerrecord.*;

import com.zhixuewuli.common.exception.teacherevent.*;
import com.zhixuewuli.common.util.DatabaseExceptionParserUtil;
import org.springframework.dao.DataIntegrityViolationException;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.result.ResultCode;

import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

/** Maps application exceptions to stable HTTP contracts; subclasses precede their parent categories. */
@RestControllerAdvice(basePackages = "com.zhixuewuli")
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(com.zhixuewuli.common.security.LoginAttemptLimiter.LoginThrottledException.class)
    public ResponseEntity<Result<?>> loginThrottled(RuntimeException error){
        return ResponseEntity.status(429).header("Retry-After","60").body(Result.error(ResultCode.BUSINESS_ERROR,"登录尝试过多，请一分钟后重试"));
    }

    /** {@code @PreAuthorize}/{@code @TeacherOnly} 拒绝发生在 MVC 内部，需在这里映射为 403。 */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(org.springframework.security.access.AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return error(FORBIDDEN, ResultCode.PERMISSION_DENIED, "权限不足", null);
    }

    // ==================== 验证相关异常处理（最优先） ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数非法异常：{}", e.getMessage());
        return error(BAD_REQUEST, ResultCode.PARAMETER_CONTENT_INVALID, e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        return error(BAD_REQUEST, ResultCode.VALIDATION_FAILED, "参数校验失败: " + message, null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<?>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("绑定异常: {}", message);
        return error(BAD_REQUEST, ResultCode.BINDING_EXCEPTION, "绑定异常: " + message, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("约束违反: {}", message);
        return error(BAD_REQUEST, ResultCode.CONSTRAINT_VIOLATION, "约束违反: " + message, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数: {}", e.getParameterName());
        return error(BAD_REQUEST, ResultCode.MISSING_PARAMETER, "缺少必需参数: " + e.getParameterName(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName());
        return error(BAD_REQUEST, ResultCode.PARAMETER_TYPE_MISMATCH, "参数类型不匹配: " + e.getName(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误");
        return error(BAD_REQUEST, ResultCode.REQUEST_BODY_PARSE_FAILED, "请求体格式错误，请检查JSON格式", null);
    }

    // ==================== 具体业务异常处理（按模块分组） ====================


    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusiness(BusinessException e) {
        log.warn("业务请求失败: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        return switch (e) {
            case InvalidCredentialsException ex -> business(UNAUTHORIZED, ex, null);
            case InvalidTokenException ex -> business(UNAUTHORIZED, ex, null);
            case AccountDisabledException ex -> business(FORBIDDEN, ex, null);
            case UserNotFoundException ex -> business(NOT_FOUND, ex, null);
            case UserNameDuplicateException ex -> business(CONFLICT, ex, null);
            case RealNameDuplicateException ex -> business(CONFLICT, ex, null);
            case QuestionNotFoundException ex -> business(NOT_FOUND, ex, null);
            case QuestionContentDuplicateException ex -> business(CONFLICT, ex, null);
            case EventNotFoundException ex -> business(NOT_FOUND, ex, null);
            case EventAlreadyExistsException ex -> business(CONFLICT, ex, null);
            case SessionNotFoundException ex -> business(NOT_FOUND, ex, detail("会话ID: ", ex.getSessionId()));
            case InsufficientPermissionException ex -> business(FORBIDDEN, ex, detail("用户ID: ", ex.getUserId()));
            case AnswerRecordNotFoundException ex -> business(NOT_FOUND, ex, detail("记录ID: ", ex.getRecordId()));
            case InvalidQuestionIdException ex -> business(BAD_REQUEST, ex, detail("题目ID: ", ex.getQuestionId()));
            case SessionExpiredException ex -> business(REQUEST_TIMEOUT, ex, detail("会话ID: ", ex.getSessionId()));
            // These module fallbacks historically suppress their message; keep that wire contract.
            case QuestionBusinessException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), null, null);
            case AnswerRecordBusinessException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), null, null);
            case TeacherEventBusinessException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), null, null);
            case QuestionErrorAnalysisException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), null, null);
            default -> business(BAD_REQUEST, e, null);
        };
    }

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<Result<?>> handleTechnical(TechnicalException e) {
        log.error("技术请求失败: {}", e.getClass().getSimpleName(), e);
        return switch (e) {
            case AnswerRecordProcessingException ex -> error(BAD_REQUEST, ex.getCode(), ex.getMessage(),
                    detail("操作类型: ", ex.getOperationType()));
            case InvalidQuestionStatisticsException ex -> error(BAD_REQUEST, ex.getCode(), "题目统计查询异常: " + ex.getMessage(), null);
            case AIServiceUnavailableException ex -> error(SERVICE_UNAVAILABLE, ex.getCode(), "AI服务暂时不可用，请稍后重试", ex.getMessage());
            case AITimeoutException ex -> error(REQUEST_TIMEOUT, ex.getCode(), "AI响应超时，请稍后重试", ex.getMessage());
            case AIResponseParseException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), "AI响应解析失败", null);
            case SessionOperationException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), "会话操作失败", null);
            case SessionManagementException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), "会话管理失败", null);
            case TokenCalculationException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), "令牌计算失败", null);
            case TokenQuotaException ex -> error(TOO_MANY_REQUESTS, ex.getCode(), "令牌配额不足", ex.getMessage());
            case AITechnicalException ex -> error(INTERNAL_SERVER_ERROR, ex.getCode(), "AI技术服务异常，请稍后重试", null);
            default -> error(INTERNAL_SERVER_ERROR, e.getCode(), "系统繁忙，请稍后重试", null);
        };
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<Result<?>> handleLockTimeout(org.springframework.dao.PessimisticLockingFailureException e) {
        log.warn("数据库锁等待超时", e);
        return error(CONFLICT, ResultCode.BUSINESS_ERROR, "提交冲突，请稍后重试", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("数据完整性约束违反", e);
        var info = DatabaseExceptionParserUtil.parseException(e);
        return error(CONFLICT, ResultCode.DATABASE_CONSTRAINT_VIOLATION, info.getFriendlyMessage(), null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<?>> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常", e);
        return error(INTERNAL_SERVER_ERROR, ResultCode.DATABASE_ACCESS_FAILED, "数据库连接失败，请稍后重试", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUnknownException(Exception e) {
        log.error("系统未知异常", e);
        return error(INTERNAL_SERVER_ERROR, ResultCode.TECHNICAL_ERROR, "系统繁忙，请稍后重试", null);
    }

    private ResponseEntity<Result<?>> business(HttpStatus status, BusinessException e, String reason) {
        return error(status, e.getCode(), e.getMessage(), reason);
    }

    private ResponseEntity<Result<?>> error(HttpStatus status, ResultCode code, String message, String reason) {
        return ResponseEntity.status(status).body(Result.errorWithMessageAndReason(code, message, reason));
    }

    private String detail(String label, Object value) {
        return value == null ? null : label + value;
    }
}
