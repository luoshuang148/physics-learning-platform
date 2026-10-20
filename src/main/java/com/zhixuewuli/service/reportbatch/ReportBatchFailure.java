package com.zhixuewuli.service.reportbatch;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.*;

import java.time.Duration;

/**
 * 失败分类：临时失败（可有限重试）与永久失败（不重试）。
 *
 * <p>分类只依据异常类型与安全的错误摘要，绝不携带提示词或学生数据。</p>
 */
public record ReportBatchFailure(String code, String message, boolean retryable) {

    public static final String TIMEOUT = "TIMEOUT";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String AUTH = "AUTH";
    public static final String INPUT_INVALID = "INPUT_INVALID";
    public static final String DATA_MISSING = "DATA_MISSING";
    public static final String AI_DISABLED = "AI_DISABLED";
    public static final String UNKNOWN = "UNKNOWN";

    public static ReportBatchFailure classify(Throwable error) {
        String message = summarize(error);
        if (error instanceof ReportBatchAiDisabledException) {
            return new ReportBatchFailure(AI_DISABLED, message, false);
        }
        if (error instanceof AITimeoutException || error instanceof java.net.SocketTimeoutException) {
            return new ReportBatchFailure(TIMEOUT, message, true);
        }
        if (error instanceof AIUpstreamException upstream) {
            int status = upstream.getStatus();
            if (status == 429) return new ReportBatchFailure(RATE_LIMIT, message, true);
            if (status == 401 || status == 403) return new ReportBatchFailure(AUTH, message, false);
            if (status >= 500) return new ReportBatchFailure(UNAVAILABLE, message, true);
            if (status >= 400) return new ReportBatchFailure(INPUT_INVALID, message, false);
            return new ReportBatchFailure(UNAVAILABLE, message, true);
        }
        if (error instanceof AIServiceUnavailableException || error instanceof AIResponseParseException) {
            return new ReportBatchFailure(UNAVAILABLE, message, true);
        }
        if (error instanceof com.zhixuewuli.common.exception.answerrecord.AnswerRecordNotFoundException) {
            return new ReportBatchFailure(DATA_MISSING, message, false);
        }
        if (error instanceof BusinessException) {
            return new ReportBatchFailure(INPUT_INVALID, message, false);
        }
        return new ReportBatchFailure(UNKNOWN, message, true);
    }

    /** 指数退避：base * 2^(attempts-1)，封顶 retry-max-delay。 */
    public static Duration backoff(int attempts, Duration base, Duration max) {
        int exponent = Math.max(0, Math.min(attempts - 1, 16));
        long millis;
        try {
            millis = Math.multiplyExact(base.toMillis(), 1L << exponent);
        } catch (ArithmeticException overflow) {
            millis = Long.MAX_VALUE;
        }
        return Duration.ofMillis(Math.min(millis, max.toMillis()));
    }

    /** 长度受限的安全错误摘要：不包含堆栈、提示词或学生数据。 */
    public static String summarize(Throwable error) {
        String raw = error.getMessage();
        String fallback = error.getClass().getSimpleName();
        String text = raw == null || raw.isBlank() ? fallback : raw.strip();
        text = text.replaceAll("\\s+", " ");
        return text.length() > 400 ? text.substring(0, 400) : text;
    }
}
