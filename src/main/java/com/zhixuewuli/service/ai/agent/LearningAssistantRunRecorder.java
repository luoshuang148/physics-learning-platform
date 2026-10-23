package com.zhixuewuli.service.ai.agent;

import com.zhixuewuli.common.metrics.AIMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class LearningAssistantRunRecorder {
    private final JdbcTemplate jdbc;
    private final AiUsageAccounting usage;
    private final AIMetrics metrics;
    private final Map<String, AiUsageAccounting.Snapshot> snapshots = new ConcurrentHashMap<>();

    public void start(String runId, Long userId, Long eventId, String mode) {
        jdbc.update("INSERT INTO learning_assistant_runs(run_id,user_id,event_id,execution_mode,status,started_at) VALUES(?,?,?,?,'RUNNING',UTC_TIMESTAMP(6))",
                runId, userId, eventId, mode);
        snapshots.put(runId, usage.empty());
        metrics.learningRunStarted(mode);
    }

    public void recordStep(String runId, int number, String name, String status, long durationMs) {
        long elapsed = Math.max(0, durationMs);
        jdbc.update("INSERT INTO learning_assistant_run_steps(run_id,step_no,step_name,status,duration_ms) VALUES(?,?,?,?,?)",
                runId, number, name, status, elapsed);
        metrics.learningStage(name, status, elapsed);
    }

    public void addUsage(String runId, Integer input, Integer output, Integer estimatedInput, Integer estimatedOutput,
                         AiUsageAccounting.Pricing pricing) {
        snapshots.computeIfPresent(runId, (key, previous) -> usage.addCall(previous, input, output, estimatedInput, estimatedOutput, pricing));
    }

    public void finish(String runId, String status) {
        AiUsageAccounting.Snapshot snapshot = snapshots.getOrDefault(runId, usage.empty());
        int changed = jdbc.update("UPDATE learning_assistant_runs SET status=?,finished_at=UTC_TIMESTAMP(6),duration_ms=TIMESTAMPDIFF(MICROSECOND,started_at,UTC_TIMESTAMP(6))/1000,model_calls=?,input_tokens=?,output_tokens=?,usage_source=?,estimated_cost=?,cost_currency=?,pricing_version=? WHERE run_id=? AND status='RUNNING'",
                status, snapshot.modelCalls(), snapshot.inputTokens(), snapshot.outputTokens(), snapshot.usageSource().name(),
                snapshot.cost(), snapshot.currency(), snapshot.pricingVersion(), runId);
        if (changed == 1) metrics.learningRunFinished(status);
        snapshots.remove(runId);
    }

    public List<Map<String, Object>> ownedSteps(String runId, Long userId) {
        return jdbc.queryForList("SELECT s.step_no,s.step_name,s.status,s.duration_ms,s.created_at FROM learning_assistant_run_steps s JOIN learning_assistant_runs r ON r.run_id=s.run_id WHERE r.run_id=? AND r.user_id=? ORDER BY s.step_no", runId, userId);
    }

    public Map<String, Object> ownedRun(String runId, Long userId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT run_id,execution_mode,status,started_at,finished_at,duration_ms,model_calls,input_tokens,output_tokens,usage_source,estimated_cost,cost_currency,pricing_version FROM learning_assistant_runs WHERE run_id=? AND user_id=?", runId, userId);
        if (rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        Map<String,Object> result = new java.util.LinkedHashMap<>(rows.get(0));
        result.put("steps", ownedSteps(runId, userId));
        return result;
    }
}
