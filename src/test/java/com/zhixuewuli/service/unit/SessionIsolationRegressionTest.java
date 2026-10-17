package com.zhixuewuli.service.unit;

import com.zhixuewuli.common.base.BaseUnitTest;
import com.zhixuewuli.common.config.properties.AIBusinessProperties;
import com.zhixuewuli.dto.internal.ConversationType;
import com.zhixuewuli.dto.internal.SessionContextInternalDTO;
import com.zhixuewuli.repository.AnswerRecordRepository;
import com.zhixuewuli.repository.ConversationHistoryRepository;
import com.zhixuewuli.service.session.SessionManager;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDateTime;
import java.lang.reflect.Field;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionIsolationRegressionTest extends BaseUnitTest {
    private final SessionManager manager = new SessionManager(mock(ConversationHistoryRepository.class),
            mock(AnswerRecordRepository.class), new AIBusinessProperties(), mock(com.zhixuewuli.repository.TeacherEventRepository.class));

    @Test
    void identicalClientSessionIdsMustNotShareUserContext() {
        var alice = session(1L);
        var bob = session(2L);
        assertThat(bob).isNotSameAs(alice);
        assertThat(bob.getUserId()).isEqualTo(2L);
        assertThat(session(1L)).isSameAs(alice);
    }

    @Test
    void expiredSessionsAreActuallyReclaimedWithoutMovingTimeBackwards() throws Exception {
        var old = session(1L);
        old.setLastActiveTime(LocalDateTime.now().minusHours(2));
        manager.cleanupExpiredSessions(Duration.ofMinutes(10));
        manager.cleanupExpiredSessions(Duration.ofMinutes(10));
        Field field = SessionManager.class.getDeclaredField("sessionStore");
        field.setAccessible(true);
        assertThat((Map<?, ?>) field.get(manager)).isEmpty();
    }

    private SessionContextInternalDTO session(Long userId) {
        return manager.getOrCreateSession("shared-client-id", userId, ConversationType.REAL_TIME_QA,
                null, () -> "physics");
    }
}
