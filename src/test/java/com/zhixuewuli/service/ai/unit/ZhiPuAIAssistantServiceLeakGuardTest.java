package com.zhixuewuli.service.ai.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.client.callback.AIStreamCallback;
import com.zhixuewuli.common.client.vendor.ZhipuAIClient;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.util.OptionMappingUtil;
import com.zhixuewuli.common.util.StreamUtils;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.dto.request.AIAskRequest;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.repository.QuestionRepository;
import com.zhixuewuli.service.ai.impl.ZhiPuAIAssistantServiceImpl;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("ai-async")
@DisplayName("智谱AI助手异步泄漏守卫测试")
class ZhiPuAIAssistantServiceLeakGuardTest extends BaseUnitTest {

    private static final Long TEST_USER_ID = 1001L;
    private static final String TEST_SESSION_ID = "leak-guard-session";

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private ConversationHistoryRepository conversationHistoryRepository;

    @Mock
    private OptionMappingUtil optionMappingUtil;

    @Mock
    private ZhipuAIClient zhipuAIClient;

    @Mock
    private AIBusinessProperties aiBusinessProperties;

    @Mock
    private AIStreamingProperties aiStreamingProperties;

    @Mock
    private SessionManager sessionManager;

    private StreamUtils streamUtils;
    private TrackingExecutor trackingExecutor;
    private ZhiPuAIAssistantServiceImpl service;

    @BeforeEach
    void setUp() {
        when(aiBusinessProperties.getRealTimeQaModel()).thenReturn("glm-test");

        streamUtils = new StreamUtils(aiStreamingProperties, com.zhixuewuli.common.redis.LeaseMocks.create());
        streamUtils.setMaxConnections(32);
        trackingExecutor = new TrackingExecutor();

        service = new ZhiPuAIAssistantServiceImpl(
                questionRepository,
                answerRecordRepository,
                conversationHistoryRepository,
                optionMappingUtil,
                new ObjectMapper(),
                zhipuAIClient,
                aiBusinessProperties,
                aiStreamingProperties,
                trackingExecutor,
                streamUtils,
                sessionManager);
    }

    @AfterEach
    void tearDown() {
        if (streamUtils != null) {
            streamUtils.destroy();
        }
    }

    @Test
    @DisplayName("streamQAResponse 正常完成后，异步计数与连接计数应回落到基线")
    void streamQAResponse_normalCompletion_shouldReturnCountersToBaseline() throws Exception {
        AIAskRequest request = new AIAskRequest();
        request.setQuestion("什么是牛顿第二定律？");

        SessionContextInternalDTO context = createSessionContext(TEST_SESSION_ID, TEST_USER_ID);
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(context);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onContent("F=ma", true);
            callback.onComplete(12, 18, 30);
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

        SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 3000L);
        assertThat(streamUtils.getActiveConnectionCount()).isEqualTo(1);

        service.streamQAResponse(request, emitter, TEST_USER_ID, TEST_SESSION_ID);

        waitForConnectionCount(0, 1000);
        assertThat(trackingExecutor.getInFlightCount()).isZero();
        assertThat(trackingExecutor.getCompletedTaskCount()).isEqualTo(1);
        assertThat(getPrivateMapSize("activeEmitters")).isZero();
        assertThat(getPrivateMapSize("emitterContexts")).isZero();

        verify(sessionManager).addInteraction(any(), anyString(), anyString(), anyInt());
        verify(sessionManager).updateTokenUsage(any(), anyInt());
    }

    @Test
    @DisplayName("streamQAResponse 异常中断后，异步计数与连接计数应回落到基线")
    void streamQAResponse_errorInterrupted_shouldReturnCountersToBaseline() throws Exception {
        AIAskRequest request = new AIAskRequest();
        request.setQuestion("解释下这道题");

        SessionContextInternalDTO context = createSessionContext(TEST_SESSION_ID + "-error", TEST_USER_ID);
        when(sessionManager.getOrCreateSession(anyString(), anyLong(), any(ConversationType.class),
                any(), any()))
                .thenReturn(context);

        doAnswer(invocation -> {
            AIStreamCallback callback = invocation.getArgument(2);
            callback.onError(new RuntimeException("simulated stream interruption"));
            return null;
        }).when(zhipuAIClient).streamChatWithMessages(any(), anyString(), any(AIStreamCallback.class));

        SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 3000L);
        assertThat(streamUtils.getActiveConnectionCount()).isEqualTo(1);

        service.streamQAResponse(request, emitter, TEST_USER_ID, TEST_SESSION_ID + "-error");

        waitForConnectionCount(0, 1000);
        assertThat(trackingExecutor.getInFlightCount()).isZero();
        assertThat(trackingExecutor.getCompletedTaskCount()).isEqualTo(1);
        assertThat(getPrivateMapSize("activeEmitters")).isZero();
        assertThat(getPrivateMapSize("emitterContexts")).isZero();

        verify(sessionManager, never()).addInteraction(any(), anyString(), anyString(), anyInt());
    }

    private SessionContextInternalDTO createSessionContext(String sessionId, Long userId) throws Exception {
        SessionContextInternalDTO context = SessionContextInternalDTO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .context("测试上下文")
                .maxTokens(10000)
                .usedTokens(new AtomicInteger(0))
                .isActive(true)
                .maxInteractionCount(15)
                .interactionRecords(new ConcurrentSkipListMap<>())
                .totalInteractionCount(0)
                .build();

        Field lockField = SessionContextInternalDTO.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        if (lockField.get(context) == null) {
            lockField.set(context, new ReentrantReadWriteLock());
        }
        return context;
    }

    private int getPrivateMapSize(String fieldName) {
        try {
            Field field = StreamUtils.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<?, ?> map = (Map<?, ?>) field.get(streamUtils);
            return map.size();
        } catch (Exception e) {
            throw new RuntimeException("读取 StreamUtils 私有字段失败: " + fieldName, e);
        }
    }

    private void waitForConnectionCount(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (streamUtils.getActiveConnectionCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(streamUtils.getActiveConnectionCount()).isEqualTo(expected);
    }

    private static class TrackingExecutor implements Executor {
        private final AtomicInteger inFlightCount = new AtomicInteger(0);
        private final AtomicInteger completedTaskCount = new AtomicInteger(0);

        @Override
        public void execute(Runnable command) {
            inFlightCount.incrementAndGet();
            try {
                command.run();
            } finally {
                inFlightCount.decrementAndGet();
                completedTaskCount.incrementAndGet();
            }
        }

        int getInFlightCount() {
            return inFlightCount.get();
        }

        int getCompletedTaskCount() {
            return completedTaskCount.get();
        }
    }
}
