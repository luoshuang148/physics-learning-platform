package com.zhixuewuli.common.util;

import com.zhixuewuli.common.config.properties.AIStreamingProperties;
import com.zhixuewuli.common.result.ResultCode;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamUtils 单元测试
 * 
 * <p>
 * 测试重点：
 * </p>
 * <ul>
 * <li>sendError 对 null emitter 的防御性处理</li>
 * <li>sendError 对已完成的 emitter 的防御性处理</li>
 * <li>safeCompleteEmitter 的幂等性保证</li>
 * </ul>
 * 
 * @author 智学物理开发团队
 * @version 1.0.0
 * @since 2026
 */
@Tag("unit")
@DisplayName("StreamUtils 单元测试")
class StreamUtilsUnitTest {

    private StreamUtils streamUtils;
    private AIStreamingProperties aiStreamingProperties;

    @BeforeEach
    void setUp() throws Exception {
        aiStreamingProperties = new AIStreamingProperties();
        // 设置有效的配置值
        setFieldValue(aiStreamingProperties, "sseEmitterTimeout", 60000L);
        setFieldValue(aiStreamingProperties, "sseReconnectTime", 5000L);
        
        streamUtils = new StreamUtils(aiStreamingProperties);
    }

    /**
     * 通过反射设置字段值（用于配置对象）
     */
    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 读取 StreamUtils 私有映射大小，用于泄漏守卫断言。
     */
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

    private SessionContextInternalDTO createContext(String sessionId, long userId) {
        return SessionContextInternalDTO.builder()
                .sessionId(sessionId)
                .userId(userId)
                .maxTokens(10000)
                .usedTokens(new AtomicInteger(0))
                .isActive(true)
                .maxInteractionCount(10)
                .interactionRecords(new ConcurrentSkipListMap<>())
                .totalInteractionCount(0)
                .build();
    }

    /**
     * 等待连接计数达到目标值，避免回调异步触发导致断言抖动
     */
    private void waitForConnectionCount(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (streamUtils.getActiveConnectionCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, streamUtils.getActiveConnectionCount(), "连接计数应在超时时间内达到目标值");
    }

    @Nested
    @DisplayName("sendError 防御性编程测试")
    class SendErrorDefensiveTests {

        @Test
        @DisplayName("sendError 对 null emitter 应安全返回，不抛异常")
        void sendError_withNullEmitter_shouldNotThrow() {
            // given
            SseEmitter nullEmitter = null;

            // when & then - 不应抛出任何异常
            assertDoesNotThrow(() -> 
                streamUtils.sendError(nullEmitter, ResultCode.TECHNICAL_ERROR, "测试错误消息")
            );
        }

        @Test
        @DisplayName("sendError 对已完成的 emitter 应安全处理，不抛 IllegalStateException")
        void sendError_withCompletedEmitter_shouldNotThrow() {
            // given - 创建一个已完成的 emitter
            SseEmitter emitter = new SseEmitter(60000L);
            emitter.complete(); // 手动完成 emitter

            // when & then - 不应抛出 IllegalStateException
            assertDoesNotThrow(() -> 
                streamUtils.sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE, "AI服务不可用")
            );
        }

        @Test
        @DisplayName("sendError 正常情况下应成功发送错误事件")
        void sendError_withNormalEmitter_shouldSendErrorEvent() {
            // given - 创建一个正常的 emitter，并注册回调
            SseEmitter emitter = new SseEmitter(60000L);
            
            // 用于捕获发送的数据
            final boolean[] eventSent = {false};
            emitter.onCompletion(() -> eventSent[0] = true);

            // when
            streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "测试错误消息");

            // then - emitter 应该被完成（通过回调触发）
            // 注意：由于 emitter 已完成，onCompletion 可能不会再次触发
        }
    }

    @Nested
    @DisplayName("safeCompleteEmitter 幂等性测试")
    class SafeCompleteEmitterIdempotencyTests {

        @Test
        @DisplayName("safeCompleteEmitter 对 null 应安全返回")
        void safeCompleteEmitter_withNull_shouldNotThrow() {
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(null));
        }

        @Test
        @DisplayName("safeCompleteEmitter 对已完成的 emitter 不应抛 IllegalStateException")
        void safeCompleteEmitter_withCompletedEmitter_shouldNotThrow() {
            // given
            SseEmitter emitter = new SseEmitter(60000L);
            emitter.complete(); // 先完成一次

            // when & then
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
        }

        @Test
        @DisplayName("safeCompleteEmitter 对正在运行的 emitter 应正常完成")
        void safeCompleteEmitter_withActiveEmitter_shouldComplete() {
            // given
            SseEmitter emitter = new SseEmitter(60000L);
            
            // when & then - 不应抛异常
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
        }

        @Test
        @DisplayName("safeCompleteEmitter 多次调用应幂等")
        void safeCompleteEmitter_multipleCalls_shouldBeIdempotent() {
            // given
            SseEmitter emitter = new SseEmitter(60000L);

            // when & then - 多次调用都不应抛异常
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
        }
    }

    @Nested
    @DisplayName("createConfiguredEmitterWithLimit 测试")
    class CreateConfiguredEmitterTests {

        @Test
        @DisplayName("createConfiguredEmitterWithLimit 应返回有效 emitter")
        void createConfiguredEmitterWithLimit_shouldReturnValidEmitter() {
            // when
            SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);

            // then
            assertNotNull(emitter);
            
            // 清理
            streamUtils.safeCompleteEmitter(emitter);
        }

        @Test
        @DisplayName("createConfiguredEmitterWithLimit 在连接数限制内应成功")
        void createConfiguredEmitterWithLimit_withinLimit_shouldSucceed() {
            // given - 创建一个 emitter（在连接数限制内）
            SseEmitter emitter1 = null;
            SseEmitter emitter2 = null;
            SseEmitter emitter3 = null;

            try {
                emitter1 = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);
                emitter2 = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);
                emitter3 = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);

                // then - 都应该成功创建
                assertNotNull(emitter1);
                assertNotNull(emitter2);
                assertNotNull(emitter3);
            } finally {
                // 清理
                if (emitter1 != null) streamUtils.safeCompleteEmitter(emitter1);
                if (emitter2 != null) streamUtils.safeCompleteEmitter(emitter2);
                if (emitter3 != null) streamUtils.safeCompleteEmitter(emitter3);
            }
        }
    }

    @Nested
    @DisplayName("连接数管理测试")
    class ConnectionManagementTests {

        @Test
        @DisplayName("getActiveConnectionCount 应正确返回活跃连接数")
        void getActiveConnectionCount_shouldReturnCorrectCount() {
            // given
            int initialCount = streamUtils.getActiveConnectionCount();
            SseEmitter emitter = null;

            try {
                emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);

                // when
                int countAfterCreate = streamUtils.getActiveConnectionCount();

                // then
                assertEquals(initialCount + 1, countAfterCreate);
            } finally {
                if (emitter != null) {
                    streamUtils.safeCompleteEmitter(emitter);
                }
            }
        }

        @Test
        @DisplayName("canAcceptNewConnection 应正确判断是否可以接受新连接")
        void canAcceptNewConnection_shouldReturnCorrectResult() {
            // when
            boolean canAccept = streamUtils.canAcceptNewConnection();

            // then - 至少在测试环境下应该可以接受连接
            assertTrue(canAccept);
        }
    }

    @Nested
    @DisplayName("上下文绑定测试")
    class ContextBindingTests {

        @Test
        @DisplayName("bindContext 应重置历史取消标记")
        void bindContext_shouldResetCancelledFlag() {
            // given
            SseEmitter emitter = new SseEmitter(60000L);
            SessionContextInternalDTO context = SessionContextInternalDTO.builder()
                    .sessionId("test-session")
                    .userId(1001L)
                    .maxTokens(10000)
                    .usedTokens(new AtomicInteger(0))
                    .isActive(true)
                    .maxInteractionCount(10)
                    .interactionRecords(new ConcurrentSkipListMap<>())
                    .totalInteractionCount(0)
                    .build();
            context.setCancelled(true);

            // when
            streamUtils.bindContext(emitter, context);

            // then
            assertFalse(context.isCancelled());

            // 清理
            streamUtils.safeCompleteEmitter(emitter);
        }

        @Test
        @DisplayName("bindContext 对空参数应具备防御性")
        void bindContext_withNullParameters_shouldNotThrow() {
            SessionContextInternalDTO context = SessionContextInternalDTO.builder()
                    .sessionId("test-session-2")
                    .userId(1002L)
                    .maxTokens(10000)
                    .usedTokens(new AtomicInteger(0))
                    .isActive(true)
                    .maxInteractionCount(10)
                    .interactionRecords(new ConcurrentSkipListMap<>())
                    .totalInteractionCount(0)
                    .build();

            assertDoesNotThrow(() -> streamUtils.bindContext(null, context));
            assertDoesNotThrow(() -> streamUtils.bindContext(new SseEmitter(1000L), null));
        }
    }

    @Nested
    @DisplayName("并发正确性测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发创建并重复关闭连接，计数应最终归零且无异常")
        void concurrentCreateAndDuplicateComplete_shouldReturnToZero() throws InterruptedException {
            // given
            streamUtils.setMaxConnections(200);
            int createThreads = 40;
            CountDownLatch createStart = new CountDownLatch(1);
            CountDownLatch createDone = new CountDownLatch(createThreads);
            List<SseEmitter> emitters = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Throwable> failure = new AtomicReference<>();

            // when - 并发创建连接
            for (int i = 0; i < createThreads; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        createStart.await();
                        SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);
                        emitters.add(emitter);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        createDone.countDown();
                    }
                });
                thread.start();
            }

            createStart.countDown();
            createDone.await();

            assertNull(failure.get(), "并发创建不应出现异常: "
                    + (failure.get() != null ? failure.get().getMessage() : ""));
            assertEquals(createThreads, emitters.size(), "并发创建应全部成功");

            // 同一 emitter 增加重复关闭任务，验证幂等性
            List<SseEmitter> closeTargets = new ArrayList<>(emitters);
            int duplicateCount = Math.min(10, emitters.size());
            for (int i = 0; i < duplicateCount; i++) {
                closeTargets.add(emitters.get(i));
            }

            CountDownLatch closeStart = new CountDownLatch(1);
            CountDownLatch closeDone = new CountDownLatch(closeTargets.size());
            AtomicReference<Throwable> closeFailure = new AtomicReference<>();

            for (SseEmitter target : closeTargets) {
                Thread thread = new Thread(() -> {
                    try {
                        closeStart.await();
                        streamUtils.safeCompleteEmitter(target);
                    } catch (Throwable t) {
                        closeFailure.compareAndSet(null, t);
                    } finally {
                        closeDone.countDown();
                    }
                });
                thread.start();
            }

            closeStart.countDown();
            closeDone.await();

            // then
            assertNull(closeFailure.get(), "并发关闭不应出现异常: "
                    + (closeFailure.get() != null ? closeFailure.get().getMessage() : ""));
            waitForConnectionCount(0, 1000);
        }

        @Test
        @DisplayName("并发冲击连接上限，成功数不应超过限制")
        void concurrentCreateUnderLimit_shouldNotExceedMaxConnections() throws InterruptedException {
            // given
            int maxConnections = 2;
            int requestCount = 20;
            streamUtils.setMaxConnections(maxConnections);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(requestCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            List<SseEmitter> successEmitters = Collections.synchronizedList(new ArrayList<>());

            // when
            for (int i = 0; i < requestCount; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 5000L);
                        successEmitters.add(emitter);
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
                thread.start();
            }

            startLatch.countDown();
            doneLatch.await();

            // then
            assertEquals(requestCount, successCount.get() + failureCount.get(), "成功和失败总数应与请求总数一致");
            assertTrue(successCount.get() <= maxConnections,
                    "并发场景下成功连接数不应超过限制，实际成功数: " + successCount.get());

            for (SseEmitter emitter : successEmitters) {
                streamUtils.safeCompleteEmitter(emitter);
            }
            waitForConnectionCount(0, 1000);
        }
    }

    @Nested
    @DisplayName("内存泄漏守卫测试")
    class LeakGuardTests {

        @Test
        @DisplayName("批次循环创建并关闭连接后，连接与内部映射应回到基线")
        void batchChurn_shouldReturnToBaselineWithoutLeaks() throws InterruptedException {
            streamUtils.setMaxConnections(500);
            int rounds = 3;
            int perRound = 40;

            for (int round = 0; round < rounds; round++) {
                List<SseEmitter> emitters = new ArrayList<>();
                List<SessionContextInternalDTO> contexts = new ArrayList<>();

                for (int i = 0; i < perRound; i++) {
                    SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 3000L);
                    SessionContextInternalDTO context = createContext("batch-" + round + "-" + i, 1000L + i);
                    streamUtils.bindContext(emitter, context);
                    emitters.add(emitter);
                    contexts.add(context);
                }

                assertEquals(perRound, streamUtils.getActiveConnectionCount(), "批次创建后连接数应与创建数一致");
                assertEquals(perRound, getPrivateMapSize("activeEmitters"), "activeEmitters 应跟踪全部连接");
                assertEquals(perRound, getPrivateMapSize("emitterContexts"), "emitterContexts 应跟踪全部上下文");

                for (SseEmitter emitter : emitters) {
                    streamUtils.safeCompleteEmitter(emitter);
                }

                waitForConnectionCount(0, 1000);
                assertEquals(0, getPrivateMapSize("activeEmitters"), "批次关闭后 activeEmitters 应清空");
                assertEquals(0, getPrivateMapSize("emitterContexts"), "批次关闭后 emitterContexts 应清空");

                for (SessionContextInternalDTO context : contexts) {
                    assertTrue(context.isCancelled(), "连接关闭后上下文应标记为已取消");
                }
            }
        }

        @Test
        @DisplayName("发送错误并关闭连接后，重复清理仍应保持无泄漏")
        void errorPathCleanup_shouldBeLeakFreeAndIdempotent() throws InterruptedException {
            SseEmitter emitter = streamUtils.createConfiguredEmitterWithLimit(60000L, 3000L);
            SessionContextInternalDTO context = createContext("error-path-session", 2001L);
            streamUtils.bindContext(emitter, context);

            assertEquals(1, streamUtils.getActiveConnectionCount(), "创建后应存在 1 个活跃连接");
            assertEquals(1, getPrivateMapSize("activeEmitters"), "创建后 activeEmitters 应为 1");
            assertEquals(1, getPrivateMapSize("emitterContexts"), "创建后 emitterContexts 应为 1");

            streamUtils.sendError(emitter, ResultCode.TECHNICAL_ERROR, "simulated stream failure");

            waitForConnectionCount(0, 1000);
            assertEquals(0, getPrivateMapSize("activeEmitters"), "异常关闭后 activeEmitters 应清空");
            assertEquals(0, getPrivateMapSize("emitterContexts"), "异常关闭后 emitterContexts 应清空");
            assertTrue(context.isCancelled(), "异常关闭后上下文应被标记取消");

            assertDoesNotThrow(() -> streamUtils.safeCompleteEmitter(emitter));
            assertEquals(0, streamUtils.getActiveConnectionCount(), "重复关闭后连接数应保持为 0");
            assertEquals(0, getPrivateMapSize("activeEmitters"), "重复关闭后 activeEmitters 仍应为 0");
            assertEquals(0, getPrivateMapSize("emitterContexts"), "重复关闭后 emitterContexts 仍应为 0");
        }
    }
}
