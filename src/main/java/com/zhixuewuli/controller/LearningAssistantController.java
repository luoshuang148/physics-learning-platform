package com.zhixuewuli.controller;

import com.zhixuewuli.common.config.properties.LearningAssistantProperties;
import com.zhixuewuli.common.security.AuthenticatedUser;
import com.zhixuewuli.dto.agent.LearningToolContext;
import com.zhixuewuli.dto.agent.ValidatedPlanPreview;
import com.zhixuewuli.dto.request.LearningPlanPreviewRequest;
import com.zhixuewuli.service.ai.agent.LearningAgentService;
import com.zhixuewuli.service.ai.agent.LearningWorkflowService;
import com.zhixuewuli.service.ai.agent.LearningPlanService;
import com.zhixuewuli.service.ai.agent.LearningAssistantRunRecorder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/ai/student/learning-plan")
public class LearningAssistantController {
    private final LearningWorkflowService workflow;
    private final LearningAgentService agent;
    private final LearningAssistantProperties properties;
    private final LearningPlanService plans;
    private final Executor executor;
    private final LearningAssistantRunRecorder runs;

    @Autowired
    public LearningAssistantController(LearningWorkflowService workflow, LearningAgentService agent,
                                       LearningAssistantProperties properties,
                                       LearningPlanService plans,
                                       LearningAssistantRunRecorder runs,
                                       @Qualifier("aiTaskExecutor") Executor executor) {
        this.workflow = workflow;
        this.agent = agent;
        this.properties = properties;
        this.plans = plans;
        this.runs = runs;
        this.executor = executor;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('STUDENT')")
    public SseEmitter preview(@Valid @RequestBody LearningPlanPreviewRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.id() == null) throw new IllegalStateException("需要学生身份认证");
        String runId = UUID.randomUUID().toString();
        String mode = properties.getMode().name();
        runs.start(runId, user.id(), request.eventId(), mode);
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        SseEmitter emitter = new SseEmitter((properties.getTotalDeadlineSeconds() + 5) * 1000);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CopyOnWriteArrayList<Runnable> cancellationActions = new CopyOnWriteArrayList<>();
        Runnable cancel = () -> {
            if (cancelled.compareAndSet(false, true)) cancellationActions.forEach(action -> {
                try { action.run(); } catch (RuntimeException ignored) { }
            });
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> { cancel.run(); emitter.complete(); });
        emitter.onError(error -> cancel.run());
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("progress").data(Map.of("runId", runId, "requestId", request.requestId(), "mode", mode)));
                LearningToolContext context = new LearningToolContext(user.id(), request.eventId());
                long deadline = System.currentTimeMillis() + properties.getTotalDeadlineSeconds() * 1000;
                long planningStarted = System.nanoTime();
                ValidatedPlanPreview plan = properties.getMode() == LearningAssistantProperties.Mode.AGENT
                        ? agent.preview(context, request.question(), request.days(), cancelled::get, cancellationActions::add,
                        step -> sendStep(emitter, runId, step, sequence.incrementAndGet()),
                        response -> runs.addUsage(runId, response.inputTokens(), response.outputTokens(), null, null, null))
                        : workflow.preview(context, request.question(), request.days(), deadline, cancelled::get,
                        step -> sendStep(emitter, runId, step, sequence.incrementAndGet()));
                runs.recordStep(runId, sequence.incrementAndGet(), "plan_generation", "SUCCEEDED",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - planningStarted));
                if (cancelled.get()) { runs.finish(runId, "CANCELLED"); return; }
                plans.storeSuccessfulPreview(user.id(), runId, plan);
                emitter.send(SseEmitter.event().name("plan").data(Map.of("runId", runId, "seq", sequence.incrementAndGet(), "planPreview", plan, "evidenceSummary", plan.evidenceSummary())));
                emitter.send(SseEmitter.event().name("complete").data(Map.of("runId", runId, "seq", sequence.incrementAndGet(), "status", "success", "executionMode", plan.executionMode())));
                runs.finish(runId, "SUCCEEDED");
                emitter.complete();
            } catch (Exception error) {
                if (cancelled.get()) { runs.finish(runId, "CANCELLED"); return; }
                runs.finish(runId, "FAILED");
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("runId", runId, "seq", sequence.incrementAndGet(), "code", "LEARNING_PLAN_FAILED", "message", safeMessage(error))));
                } catch (Exception ignored) { }
                emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> run(@PathVariable String runId, @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null || user.id() == null) throw new IllegalStateException("需要学生身份认证");
        return runs.ownedRun(runId, user.id());
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "暂时无法生成复习计划";
        return message.substring(0, Math.min(160, message.length()));
    }

    private void sendStep(SseEmitter emitter, String runId, com.zhixuewuli.dto.agent.LearningAssistantStep step, int sequence) {
        runs.recordStep(runId, step.step(), step.toolName(), step.status(), step.durationMs());
        try {
            emitter.send(SseEmitter.event().name("tool-step").data(Map.of("runId", runId, "seq", sequence,
                    "toolName", step.toolName(), "status", step.status(), "summary", step.summary())));
        } catch (Exception ignored) { }
    }
}
