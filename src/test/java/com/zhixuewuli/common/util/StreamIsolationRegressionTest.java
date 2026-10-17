package com.zhixuewuli.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.exception.BusinessException;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.service.ai.impl.ZhiPuAIAssistantServiceImpl;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.Test;
import java.util.concurrent.RejectedExecutionException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StreamIsolationRegressionTest extends BaseUnitTest {
    @Test
    void aConversationMayOnlyHaveOneGenerationAtATime() {
        var streams = new StreamUtils(new AIStreamingProperties());
        try {
            var first = streams.createConfiguredEmitterWithLimit(10000, 1000);
            var second = streams.createConfiguredEmitterWithLimit(10000, 1000);
            var context = context();
            streams.bindContext(first, context);
            assertThatThrownBy(() -> streams.bindContext(second, context)).isInstanceOf(BusinessException.class);
            streams.safeCompleteEmitter(first);
            streams.safeCompleteEmitter(second);
            assertThat(streams.getActiveConnectionCount()).isZero();
        } finally { streams.destroy(); }
    }

    @Test
    void aDisconnectedEmitterCannotStartAGenerationLater() {
        var streams = new StreamUtils(new AIStreamingProperties());
        try {
            var emitter = streams.createConfiguredEmitterWithLimit(10000, 1000);
            streams.safeCompleteEmitter(emitter);
            assertThatThrownBy(() -> streams.bindContext(emitter, context())).isInstanceOf(BusinessException.class);
        } finally { streams.destroy(); }
    }

    @Test
    void saturatedExecutorReleasesTheEmitterInsteadOfThrowingAndLeaking() {
        var properties = new AIStreamingProperties();
        var streams = new StreamUtils(properties);
        try {
            var service = new ZhiPuAIAssistantServiceImpl(mock(QuestionRepository.class),
                    mock(AnswerRecordRepository.class), mock(ConversationHistoryRepository.class),
                    new OptionMappingUtil(new ObjectMapper()), new ObjectMapper(), mock(ZhipuAIClient.class),
                    new AIBusinessProperties(), properties,
                    task -> { throw new RejectedExecutionException("full"); }, streams, mock(SessionManager.class));
            var emitter = service.createConfiguredEmitter();
            var request = new AIAskRequest();
            request.setQuestion("gravity");
            assertThatCode(() -> service.streamQAResponse(request, emitter, 1L, "session")).doesNotThrowAnyException();
            assertThat(streams.getActiveConnectionCount()).isZero();
        } finally { streams.destroy(); }
    }

    @Test
    void oldCompletionCannotCancelTheNextRequestInTheSameConversation() {
        var streams = new StreamUtils(new AIStreamingProperties());
        try {
            var context = context();
            var old = streams.createConfiguredEmitterWithLimit(10000, 1000);
            streams.bindContext(old, context);
            streams.safeCompleteEmitter(old);
            var next = streams.createConfiguredEmitterWithLimit(10000, 1000);
            streams.bindContext(next, context);
            var called = new java.util.concurrent.atomic.AtomicBoolean();
            streams.onCancel(old, () -> called.set(true));
            streams.safeCompleteEmitter(old);
            assertThat(called.get()).isTrue();
            assertThat(streams.isCancelled(old)).isTrue();
            assertThat(streams.isCancelled(next)).isFalse();
            assertThat(context.isCancelled()).isFalse();
            streams.safeCompleteEmitter(next);
        } finally { streams.destroy(); }
    }

    private SessionContextInternalDTO context() {
        return SessionContextInternalDTO.builder().sessionId("same-session").userId(1L)
                .conversationType(ConversationType.REAL_TIME_QA).isActive(true).build();
    }
}
