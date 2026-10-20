package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.common.exception.ai.AIResponseParseException;
import com.zhixuewuli.common.exception.ai.AIServiceUnavailableException;
import com.zhixuewuli.common.exception.ai.AITimeoutException;
import com.zhixuewuli.common.exception.ai.AIUpstreamException;
import com.zhixuewuli.service.reportbatch.ReportBatchAiDisabledException;
import com.zhixuewuli.service.reportbatch.ReportBatchFailure;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/** 失败分类与退避：临时失败有限重试，配置/凭据/数据问题不自动重试。 */
@Tag("unit")
class ReportBatchFailureTest {

    @Test
    void classifiesTransientFailures() {
        assertThat(ReportBatchFailure.classify(new AITimeoutException("timeout")).code())
                .isEqualTo(ReportBatchFailure.TIMEOUT);
        assertThat(ReportBatchFailure.classify(new AITimeoutException("timeout")).retryable()).isTrue();
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(429)).code())
                .isEqualTo(ReportBatchFailure.RATE_LIMIT);
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(503)).code())
                .isEqualTo(ReportBatchFailure.UNAVAILABLE);
        assertThat(ReportBatchFailure.classify(new AIServiceUnavailableException("io")).retryable()).isTrue();
        assertThat(ReportBatchFailure.classify(new AIResponseParseException("bad chunk", null)).retryable()).isTrue();
    }

    @Test
    void classifiesPermanentFailures() {
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(401)).code())
                .isEqualTo(ReportBatchFailure.AUTH);
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(401)).retryable()).isFalse();
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(403)).retryable()).isFalse();
        assertThat(ReportBatchFailure.classify(new AIUpstreamException(400)).code())
                .isEqualTo(ReportBatchFailure.INPUT_INVALID);
        assertThat(ReportBatchFailure.classify(new BusinessException("报告输入超过上限")).retryable()).isFalse();
        assertThat(ReportBatchFailure.classify(new BusinessException("报告输入超过上限")).code())
                .isEqualTo(ReportBatchFailure.INPUT_INVALID);
        assertThat(ReportBatchFailure.classify(new ReportBatchAiDisabledException("未启用")).code())
                .isEqualTo(ReportBatchFailure.AI_DISABLED);
        assertThat(ReportBatchFailure.classify(new ReportBatchAiDisabledException("未启用")).retryable()).isFalse();
    }

    @Test
    void backoffGrowsExponentiallyAndCaps() {
        Duration base = Duration.ofSeconds(30);
        Duration max = Duration.ofMinutes(10);
        assertThat(ReportBatchFailure.backoff(1, base, max)).isEqualTo(Duration.ofSeconds(30));
        assertThat(ReportBatchFailure.backoff(2, base, max)).isEqualTo(Duration.ofSeconds(60));
        assertThat(ReportBatchFailure.backoff(3, base, max)).isEqualTo(Duration.ofSeconds(120));
        assertThat(ReportBatchFailure.backoff(20, base, max)).isEqualTo(max);
        assertThat(ReportBatchFailure.backoff(60, base, max)).isEqualTo(max);
    }

    @Test
    void errorSummaryIsBoundedAndSingleLine() {
        String summary = ReportBatchFailure.summarize(new RuntimeException("line1\nline2 " + "x".repeat(600)));
        assertThat(summary).doesNotContain("\n").hasSizeLessThanOrEqualTo(400);
    }
}
