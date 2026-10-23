package com.zhixuewuli.service.ai.agent;

import com.zhixuewuli.common.metrics.AIMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class LearningAssistantRunRecorderTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LearningAssistantRunRecorder recorder = new LearningAssistantRunRecorder(jdbc, new AiUsageAccounting(), new AIMetrics(registry));

    @Test void cancelledOrRepeatedTerminalUpdateIsCountedOnlyOnce() {
        doReturn(1, 1, 0).when(jdbc).update(anyString(), any(Object[].class));
        recorder.start("run", 7L, 9L, "AGENT");
        recorder.finish("run", "CANCELLED");
        recorder.finish("run", "SUCCEEDED");
        assertEquals(1.0, registry.get("ai.learning.run.finished").tag("outcome", "cancelled").counter().count());
        verify(jdbc, times(2)).update(contains("WHERE run_id=? AND status='RUNNING'"), any(Object[].class));
    }

    @Test void differentStudentCannotReadRunDetails() {
        when(jdbc.queryForList(contains("WHERE run_id=? AND user_id=?"), eq("run"), eq(8L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> recorder.ownedRun("run", 8L));
        verify(jdbc, never()).queryForList(contains("FROM learning_assistant_run_steps"), any(Object[].class));
    }
}
