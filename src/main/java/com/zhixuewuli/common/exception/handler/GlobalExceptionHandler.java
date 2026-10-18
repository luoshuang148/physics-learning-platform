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

import com.zhixuewuli.common.exception.video.*;
import com.zhixuewuli.common.exception.teacherevent.*;
import com.zhixuewuli.common.exception.experiment.*;
import com.zhixuewuli.common.util.DatabaseExceptionParserUtil;
import org.springframework.dao.DataIntegrityViolationException;
import com.zhixuewuli.common.result.Result;
import com.zhixuewuli.common.result.ResultCode;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用程序中抛出的各种异常，提供标准化的错误响应格式，确保API的一致性。
 *
 * <h3>处理的异常类型：</h3>
 * <ul>
 * <li><strong>BusinessException</strong>: 业务逻辑异常</li>
 * <li><strong>TechnicalException</strong>: 技术异常</li>
 * <li><strong>各模块业务异常基类</strong>: QuestionBusinessException,
 * AnswerRecordBusinessException等</li>
 * <li><strong>MethodArgumentNotValidException</strong>: 参数验证异常</li>
 * <li><strong>Exception</strong>: 通用异常兜底处理</li>
 * </ul>
 *
 * <h3>AI模块异常分类：</h3>
 * <strong>AI业务异常：</strong>
 * <ul>
 * <li>AIBusinessException: AI业务异常基类</li>
 * <li>QuestionNotFoundException: 题目不存在</li>
 * <li>AnswerRecordNotFoundException: 答题记录不存在</li>
 * <li>SessionExpiredException: 会话过期</li>
 * <li>InvalidQuestionIdException: 无效题目ID</li>
 * <li>PromptTooLongException: 提示词过长</li>
 * </ul>
 * <strong>AI技术异常：</strong>
 * <ul>
 * <li>AITechnicalException: AI技术异常基类</li>
 * <li>AIServiceUnavailableException: AI服务不可用</li>
 * <li>AITimeoutException: AI响应超时</li>
 * <li>AIResponseParseException: AI响应解析失败</li>
 * </ul>
 *
 * <h3>设计原则：</h3>
 * <ul>
 * <li>统一异常处理机制</li>
 * <li>友好的错误信息展示</li>
 * <li>完整的日志记录</li>
 * <li>安全的异常信息暴露</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 * @see RestControllerAdvice Spring MVC全局异常处理注解
 * @see BusinessException 业务异常类
 * @see TechnicalException 技术异常类
 */
@RestControllerAdvice(basePackages = "com.zhixuewuli")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(com.zhixuewuli.common.security.LoginAttemptLimiter.LoginThrottledException.class)
    public ResponseEntity<Result<?>> loginThrottled(RuntimeException error){
        return ResponseEntity.status(429).header("Retry-After","60").body(Result.error(ResultCode.BUSINESS_ERROR,"登录尝试过多，请一分钟后重试"));
    }

    // ==================== 验证相关异常处理（最优先） ====================

    /**
     * 处理非法参数异常
     * 捕获参数校验失败、业务参数不合法等场景的异常。
     *
     * @param e 非法参数异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<?>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数非法异常：{}", e.getMessage());
        Result<?> result = Result.error(ResultCode.PARAMETER_CONTENT_INVALID, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理请求参数校验异常 (@Valid 注解触发)
     * 捕获请求参数验证失败的异常，返回详细的验证错误信息。
     * 将多个验证错误合并为一条友好的提示信息。
     *
     * @param e 参数验证异常实例
     * @return Result<?> 标准化的错误响应
     *
     * @implSpec 合并所有验证错误信息
     * @implSpec 使用中文分号分隔多个错误
     * @implSpec 返回参数验证错误码 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        Result<?> result = Result.error(ResultCode.VALIDATION_FAILED, "参数校验失败: " + message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理绑定异常
     * 捕获对象绑定过程中的验证错误。
     *
     * @param e 绑定异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<?>> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("绑定异常: {}", message);
        Result<?> result = Result.error(ResultCode.BINDING_EXCEPTION, "绑定异常: " + message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理约束违反异常
     * 捕获使用 @Validated 注解时的约束违反异常。
     *
     * @param e 约束违反异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("约束违反: {}", message);
        Result<?> result = Result.error(ResultCode.CONSTRAINT_VIOLATION, "约束违反: " + message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理缺少必需参数异常
     * 捕获缺少必需请求参数的异常。
     *
     * @param e 缺少参数异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少必需参数: {}", e.getParameterName());
        Result<?> result = Result.error(ResultCode.MISSING_PARAMETER, "缺少必需参数: " + e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理参数类型不匹配异常
     * 捕获请求参数类型转换失败的异常。
     *
     * @param e 参数类型不匹配异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getName());
        Result<?> result = Result.error(ResultCode.PARAMETER_TYPE_MISMATCH, "参数类型不匹配: " + e.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理请求体不可读异常
     * 捕获JSON格式错误等导致请求体无法解析的异常。
     *
     * @param e 请求体不可读异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误");
        Result<?> result = Result.error(ResultCode.REQUEST_BODY_PARSE_FAILED, "请求体格式错误，请检查JSON格式");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ==================== 具体业务异常处理（按模块分组） ====================

    /**
     * 处理会话不存在异常
     *
     * @param e 会话不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Result<?>> handleSessionNotFoundException(SessionNotFoundException e) {
        log.warn("会话不存在异常 - SessionId: {}, Message: {}",
                e.getSessionId(), e.getMessage());
        String reason = e.getSessionId() != null ? "会话ID: " + e.getSessionId() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理权限不足异常
     *
     * @param e 权限不足异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<Result<?>> handleInsufficientPermissionException(InsufficientPermissionException e) {
        String userIdInfo = e.getUserId() != null ? "用户ID: " + e.getUserId() : null;
        log.warn("权限不足异常 - Message: {}, UserId: {}",
                e.getMessage(), e.getUserId());
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), userIdInfo);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    // ---- 认证模块具体异常 ----

    /**
     * 处理密码错误异常
     *
     * @param e 密码错误异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Result<?>> handleInvalidCredentialsException(InvalidCredentialsException e) {
        log.info("密码错误异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理账户禁用异常
     *
     * @param e 账户禁用异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<Result<?>> handleAccountDisabledException(AccountDisabledException e) {
        log.warn("账户禁用异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }

    /**
     * 处理无效令牌异常
     *
     * @param e 无效令牌异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Result<?>> handleInvalidTokenException(InvalidTokenException e) {
        log.info("无效令牌异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 处理用户名重复异常
     *
     * @param e 用户名重复异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(UserNameDuplicateException.class)
    public ResponseEntity<Result<?>> handleUserNameDuplicateException(UserNameDuplicateException e) {
        log.warn("用户名重复异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理真实姓名重复异常
     *
     * @param e 真实姓名重复异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(RealNameDuplicateException.class)
    public ResponseEntity<Result<?>> handleRealNameDuplicateException(RealNameDuplicateException e) {
        log.warn("真实姓名重复异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理用户不存在异常
     *
     * @param e 用户不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Result<?>> handleAuthUserNotFoundException(
            com.zhixuewuli.common.exception.auth.UserNotFoundException e) {
        log.warn("认证用户不存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理认证异常
     *
     * @param e 认证异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<?>> handleAuthenticationException(AuthenticationException e) {
        log.info("认证异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ---- 题目模块具体异常 ----

    /**
     * 处理题目不存在异常
     *
     * @param e 题目不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(QuestionNotFoundException.class)
    public ResponseEntity<Result<?>> handleQuestionNotFoundException(QuestionNotFoundException e) {
        log.warn("题目不存在异常：{}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理题目内容重复异常
     *
     * @param e 题目内容重复异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(QuestionContentDuplicateException.class)
    public ResponseEntity<Result<?>> handleQuestionContentDuplicateException(QuestionContentDuplicateException e) {
        log.warn("题目内容重复异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理关联视频不存在异常
     *
     * @param e 关联视频不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(QuestionVideoNotFoundException.class)
    public ResponseEntity<Result<?>> handleQuestionVideoNotFoundException(QuestionVideoNotFoundException e) {
        log.warn("关联视频不存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理无效题目统计异常
     *
     * @param e 无效题目统计异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(InvalidQuestionStatisticsException.class)
    public ResponseEntity<Result<?>> handleInvalidQuestionStatisticsException(InvalidQuestionStatisticsException e) {
        log.warn("无效题目统计异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), "题目统计查询异常: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理题目业务异常基类（兜底处理）
     * 捕获未被专门处理的题目相关业务异常，作为题目模块异常处理的最后一道防线。
     *
     * @param e 题目业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(QuestionBusinessException.class)
    public ResponseEntity<Result<?>> handleQuestionBusinessException(QuestionBusinessException e) {
        log.warn("题目业务异常兜底: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ---- 答题记录模块具体异常 ----

    /**
     * 处理答题记录不存在异常
     *
     * @param e 答题记录不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AnswerRecordNotFoundException.class)
    public ResponseEntity<Result<?>> handleAnswerRecordNotFoundException(AnswerRecordNotFoundException e) {
        log.warn("答题记录不存在异常: {}", e.getMessage());
        String reason = e.getRecordId() != null ? "记录ID: " + e.getRecordId() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理答题记录处理异常
     *
     * @param e 答题记录处理异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AnswerRecordProcessingException.class)
    public ResponseEntity<Result<?>> handleAnswerRecordProcessingException(AnswerRecordProcessingException e) {
        log.warn("答题记录处理异常: {}", e.getMessage());
        String reason = e.getOperationType() != null ? "操作类型: " + e.getOperationType() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理答题记录业务异常基类
     *
     * @param e 答题记录业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AnswerRecordBusinessException.class)
    public ResponseEntity<Result<?>> handleAnswerRecordBusinessException(AnswerRecordBusinessException e) {
        log.warn("答题记录业务异常: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ---- 视频模块具体异常 ----

    /**
     * 处理实验不存在异常
     *
     * @param e 实验不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(ExperimentNotFoundException.class)
    public ResponseEntity<Result<?>> handleExperimentNotFoundException(ExperimentNotFoundException e) {
        log.warn("实验不存在异常：{}", e.getMessage());
        String reason = e.getExperimentId() != null ? "实验 ID: " + e.getExperimentId() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理实验名称重复异常
     *
     * @param e 实验名称重复异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(ExperimentNameDuplicateException.class)
    public ResponseEntity<Result<?>> handleExperimentNameDuplicateException(ExperimentNameDuplicateException e) {
        log.warn("实验名称重复异常：{}", e.getMessage());
        String reason = e.getName() != null ? "实验名称：" + e.getName() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理实验序号重复异常
     *
     * @param e 实验序号重复异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(com.zhixuewuli.common.exception.experiment.ExperimentBusinessException.class)
    public ResponseEntity<Result<?>> handleExperimentBusinessException(com.zhixuewuli.common.exception.experiment.ExperimentBusinessException e) {
        log.warn("实验业务异常：{} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ---- 视频模块具体异常 ----

    /**
     * 处理视频不存在异常
     *
     * @param e 视频不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(VideoNotFoundException.class)
    public ResponseEntity<Result<?>> handleVideoNotFoundException(VideoNotFoundException e) {
        log.warn("视频不存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理视频已存在异常
     *
     * @param e 视频已存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(VideoAlreadyExistsException.class)
    public ResponseEntity<Result<?>> handleVideoAlreadyExistsException(VideoAlreadyExistsException e) {
        log.warn("视频已存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理视频业务异常基类
     *
     * @param e 视频业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(VideoBusinessException.class)
    public ResponseEntity<Result<?>> handleVideoBusinessException(VideoBusinessException e) {
        log.warn("视频业务异常: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ---- 教师事件模块具体异常 ----

    /**
     * 处理事件已存在异常
     *
     * @param e 事件已存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(EventAlreadyExistsException.class)
    public ResponseEntity<Result<?>> handleEventAlreadyExistsException(EventAlreadyExistsException e) {
        log.warn("事件已存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理事件不存在异常
     *
     * @param e 事件不存在异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<Result<?>> handleEventNotFoundException(EventNotFoundException e) {
        log.warn("事件不存在异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    /**
     * 处理教师事件业务异常基类
     *
     * @param e 教师事件业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(TeacherEventBusinessException.class)
    public ResponseEntity<Result<?>> handleTeacherEventBusinessException(TeacherEventBusinessException e) {
        log.warn("教师事件业务异常: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ---- 错题分析模块具体异常 ----

    /**
     * 处理分词异常
     *
     * @param e 分词异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(SegmentationException.class)
    public ResponseEntity<Result<?>> handleSegmentationException(SegmentationException e) {
        log.warn("分词异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理词云分析异常
     *
     * @param e 词云分析异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(WordCloudAnalysisException.class)
    public ResponseEntity<Result<?>> handleWordCloudAnalysisException(WordCloudAnalysisException e) {
        log.warn("词云分析异常: {}", e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理错题分析业务异常基类
     *
     * @param e 错题分析业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(QuestionErrorAnalysisException.class)
    public ResponseEntity<Result<?>> handleQuestionErrorAnalysisException(QuestionErrorAnalysisException e) {
        log.warn("错题分析业务异常: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ---- AI业务异常 ----

    /**
     * 处理无效题目ID异常
     *
     * @param e 无效题目ID异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(InvalidQuestionIdException.class)
    public ResponseEntity<Result<?>> handleInvalidQuestionIdException(InvalidQuestionIdException e) {
        log.warn("无效题目ID异常: {}", e.getMessage());
        String reason = e.getQuestionId() != null ? "题目ID: " + e.getQuestionId() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 处理会话过期异常
     *
     * @param e 会话过期异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<Result<?>> handleSessionExpiredException(SessionExpiredException e) {
        log.warn("会话过期异常: {}", e.getMessage());
        String reason = e.getSessionId() != null ? "会话ID: " + e.getSessionId() : null;
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), e.getMessage(), reason);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
    }

    /**
     * 处理AI业务异常基类
     *
     * @param e AI业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AIBusinessException.class)
    public ResponseEntity<Result<?>> handleAIBusinessException(AIBusinessException e) {
        log.warn("AI业务异常: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ---- AI技术异常 ----

    /**
     * 处理AI服务不可用异常
     *
     * @param e AI服务不可用异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AIServiceUnavailableException.class)
    public ResponseEntity<Result<?>> handleAIServiceUnavailableException(AIServiceUnavailableException e) {
        log.error("AI服务不可用异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "AI服务暂时不可用，请稍后重试",
                e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }

    /**
     * 处理AI响应超时异常
     *
     * @param e AI响应超时异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AITimeoutException.class)
    public ResponseEntity<Result<?>> handleAITimeoutException(AITimeoutException e) {
        log.error("AI响应超时异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "AI响应超时，请稍后重试", e.getMessage());
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(result);
    }

    /**
     * 处理AI响应解析异常
     *
     * @param e AI响应解析异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AIResponseParseException.class)
    public ResponseEntity<Result<?>> handleAIResponseParseException(AIResponseParseException e) {
        log.error("AI响应解析异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "AI响应解析失败", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理会话管理异常
     *
     * @param e 会话管理异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(SessionManagementException.class)
    public ResponseEntity<Result<?>> handleSessionManagementException(SessionManagementException e) {
        log.error("会话管理异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "会话管理失败", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理会话操作异常
     *
     * @param e 会话操作异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(SessionOperationException.class)
    public ResponseEntity<Result<?>> handleSessionOperationException(SessionOperationException e) {
        log.error("会话操作异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "会话操作失败",
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理令牌配额异常
     *
     * @param e 令牌配额异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(TokenQuotaException.class)
    public ResponseEntity<Result<?>> handleTokenQuotaException(TokenQuotaException e) {
        log.error("令牌配额异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "令牌配额不足", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
    }

    /**
     * 处理令牌计算异常
     *
     * @param e 令牌计算异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(TokenCalculationException.class)
    public ResponseEntity<Result<?>> handleTokenCalculationException(TokenCalculationException e) {
        log.error("令牌计算异常: {}", e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "令牌计算失败",
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * 处理AI技术异常基类
     *
     * @param e AI技术异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(AITechnicalException.class)
    public ResponseEntity<Result<?>> handleAITechnicalException(AITechnicalException e) {
        log.error("AI技术异常: {} - {}", e.getCode(), e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "AI技术服务异常，请稍后重试", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ---- 数据库技术异常 ----
    /**
     * 处理数据完整性约束异常
     *
     * @param e 数据完整性异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<?>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("数据完整性约束违反: ", e);
        DatabaseExceptionParserUtil.DatabaseConstraintInfo constraintInfo = DatabaseExceptionParserUtil
                .parseException(e);
        String reason = constraintInfo.getFieldName() != null ? String.format("字段: %s, 值: %s, 类型: %s",
                constraintInfo.getFieldName(),
                constraintInfo.getDuplicateValue(),
                constraintInfo.getConstraintType()) : null;
        Result<?> result = Result.errorWithMessageAndReason(ResultCode.DATABASE_CONSTRAINT_VIOLATION,
                constraintInfo.getFriendlyMessage(), null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理数据库访问异常
     *
     * @param e 数据库访问异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<?>> handleDataAccessException(DataAccessException e) {
        log.error("数据库访问异常: ", e);
        Result<?> result = Result.errorWithMessageAndReason(ResultCode.DATABASE_ACCESS_FAILED, "数据库连接失败，请稍后重试",
                null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ==================== 通用业务异常处理 ====================

    /**
     * 通用业务异常处理（兜底）
     * 捕获未被专门处理的业务异常，作为业务异常处理的最后一道防线。
     *
     * @param e 业务异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleGenericBusinessException(BusinessException e) {
        log.warn("通用业务异常兜底: {} - {}", e.getCode(), e.getMessage());
        Result<?> result = Result.error(e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    // ==================== 通用技术异常处理 ====================

    /**
     * 通用技术异常处理（兜底）
     * 捕获未被专门处理的技术异常，作为技术异常处理的最后一道防线。
     *
     * @param e 技术异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<Result<?>> handleGenericTechnicalException(TechnicalException e) {
        log.error("通用技术异常兜底: {} - {}", e.getCode(), e.getMessage(), e);
        Result<?> result = Result.errorWithMessageAndReason(e.getCode(), "系统繁忙，请稍后重试", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    // ==================== 最终兜底异常处理 ====================

    /**
     * 处理通用异常
     * 作为异常处理的最终兜底，捕获所有未被专门处理的异常。
     * 记录详细错误日志并返回系统错误响应。
     *
     * @param e 通用异常实例
     * @return Result<?> 标准化的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUnknownException(Exception e) {
        log.error("系统未知异常: ", e);
        Result<?> result = Result.errorWithMessageAndReason(ResultCode.TECHNICAL_ERROR, "系统繁忙，请稍后重试", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
