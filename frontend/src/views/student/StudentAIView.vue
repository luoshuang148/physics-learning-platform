<template>
  <AppShell :subtitle="`AI 问答 · ${auth.user?.realName ?? ''}`">
    <div class="ai-page student-font">
      <section class="ai-hero">
        <div class="mode-tabs">
          <button
            class="mode-tab"
            :class="{ active: activeMode === 'realtime' }"
            :disabled="state.loading"
            @click="switchMode('realtime')"
          >实时问答</button>
          <button
            class="mode-tab"
            :class="{ active: activeMode === 'wrong' }"
            :disabled="state.loading"
            @click="switchMode('wrong')"
          >题目分析</button>
          <button
            class="mode-tab"
            :class="{ active: activeMode === 'platform' }"
            :disabled="state.loading"
            @click="switchMode('platform')"
          >平台找题</button>
          <button class="mode-tab" :class="{ active: activePanel === 'plan' }" :disabled="state.loading" @click="openPlanPanel">复习计划</button>
        </div>
      </section>

      <section v-if="activePanel === 'chat'" class="ai-layout">
        <div class="ask-panel">
          <div class="panel-head">
            <h3>发起提问</h3>
            <button class="ghost-btn" :disabled="state.loading" @click="clearCurrentMode">清空会话</button>
          </div>

          <label class="field-label">提问内容</label>
          <textarea v-model="question" rows="8" placeholder="输入你想问的物理问题..." />

          <template v-if="activeMode === 'wrong'">
            <label class="field-label">关联题目 ID</label>
            <input v-model.number="questionId" type="number" placeholder="例如 1001" />
          </template>

          <template v-if="activeMode === 'platform'">
            <label class="field-label">
              <input v-model="platformExcludeAttempted" type="checkbox" /> 排除我已做过的题
            </label>
            <div class="panel-actions">
              <button class="submit-btn" :disabled="platformLoading || !question.trim()" @click="runPlatformSearch">
                {{ platformLoading ? '检索中…' : '搜平台题库' }}
              </button>
            </div>
            <div v-if="platformStatus" class="platform-status">{{ platformStatus }}</div>
            <PlatformSources :sources="platformSources" @open="openPlatformSource" />
          </template>

          <div class="panel-actions">
            <button
              v-if="state.loading"
              class="stop-btn"
              @click="stopCurrentAnalysis"
            >{{ activeMode === 'realtime' ? '停止生成' : '停止分析' }}</button>
            <button class="submit-btn" :disabled="state.loading || platformLoading || !question.trim()" @click="submitCurrentMode">
              {{ activeMode === 'realtime' ? '发送提问' : activeMode === 'platform' ? '搜平台题库' : '开始分析' }}
            </button>
          </div>
        </div>

        <div class="history-panel">
          <div class="panel-head">
            <h3>会话记录</h3>
            <span class="session-tip">{{ activeMode === 'realtime' ? '实时问答' : '题目分析' }}</span>
          </div>

          <div class="history-list">
            <div v-if="!currentMessages.length && !state.loading" class="history-empty">
              暂无会话记录
            </div>

            <div v-for="item in currentMessages" :key="item.id" class="history-item" :class="item.role">
              <div class="history-meta">
                <span class="history-role">{{ item.role === 'user' ? '你' : 'AI' }}</span>
                <span v-if="item.meta">{{ item.meta }}</span>
              </div>
              <pre class="history-content">{{ item.content }}</pre>
              <details v-if="item.reasoning" class="history-reasoning">
                <summary>思考过程</summary>
                <pre>{{ item.reasoning }}</pre>
              </details>
              <p v-if="item.error" class="history-error">{{ item.error }}</p>
            </div>

            <div v-if="state.loading || state.content || state.error" class="history-item assistant live-output">
              <div class="history-meta">
                <span class="history-role">AI</span>
                <span>{{ state.loading ? '生成中…' : '最新响应' }}</span>
              </div>
              <StreamOutput
                :loading="state.loading"
                :progress="state.progress"
                :content="state.content"
                :reasoning="state.reasoning"
                :token-usage="state.tokenUsage"
                :error="state.error"
              />
            </div>
          </div>
        </div>
      </section>

      <section v-else class="learning-plan-panel">
        <header class="panel-head"><div><h2>按本次作业安排复习</h2><p>先检查建议和依据，再保存并激活。题目不足时会显示部分建议。</p></div><button class="ghost-btn" @click="activePanel = 'chat'">返回问答</button></header>
        <label class="field-label" for="learning-event">选择作业</label>
        <select id="learning-event" v-model.number="learningEventId" :disabled="learningPlanBusy || loadingTasks">
          <option :value="null" disabled>{{ loadingTasks ? '正在加载作业…' : '请选择一份作业' }}</option>
          <option v-for="task in studentTasks" :key="task.id" :value="task.id">{{ task.eventTitle }}（{{ task.questionCount ?? task.questionIds.length }} 题）</option>
        </select>
        <label class="field-label" for="learning-question">你想重点复习什么？</label>
        <textarea id="learning-question" v-model="learningQuestion" rows="3" maxlength="800" placeholder="例如：帮我针对这次作业的错题安排三天复习" />
        <label class="field-label" for="learning-days">复习天数</label>
        <select id="learning-days" v-model.number="learningDays"><option v-for="day in 7" :key="day" :value="day">{{ day }} 天</option></select>
        <div class="panel-actions">
          <button v-if="learningPlanBusy" class="stop-btn" @click="stopLearningPlan">停止生成</button>
          <button class="submit-btn" :disabled="learningPlanBusy || !learningEventId || !learningQuestion.trim() || !studentTasks.length" @click="generateLearningPlan">生成建议</button>
        </div>
        <p v-if="learningPlanError" class="learning-error">{{ learningPlanError }}</p>
        <section v-if="savedLearningPlans.length" class="plan-history" aria-label="已保存的复习计划">
          <h3>已保存的计划</h3>
          <button v-for="plan in savedLearningPlans" :key="plan.id" class="plan-history-item" @click="openSavedPlan(plan)">
            作业 {{ plan.eventId }} · {{ plan.status }} · {{ plan.executionMode === 'AGENT' ? '智能复习助手' : '复习工作流' }}
          </button>
        </section>
        <AgentExecutionSteps :steps="learningPlan.steps" />
        <LearningPlanCard :preview="learningPlan.preview" :saved-plan="learningPlan.savedPlan" :run-id="learningPlan.runId ?? ''" :busy="learningPlanBusy" @save="savePlan" @activate="activatePlan" @refresh="refreshPlan" @cancel="cancelPlan" @practice="goPractice" />
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { useAuthStore } from '@/stores/auth';
import { clearSessionApi, streamRealtimeQA, streamWrongQuestionAnalysis, streamLearningPlanPreview } from '@/api/ai';
import { getStudentTasksApi } from '@/api/teacher-event';
import { activateLearningPlanApi, cancelLearningPlanApi, getLearningPlanApi, getLearningPlansApi, saveLearningPlanApi } from '@/api/learning-plan';
import AgentExecutionSteps from '@/components/AgentExecutionSteps.vue';
import LearningPlanCard from '@/components/LearningPlanCard.vue';
import PlatformSources from '@/components/assistant/PlatformSources.vue';
import { platformSearchApi, type PlatformSource } from '@/api/study-assistant';
import { acceptLearningPlanEvent, emptyLearningPlanState, startLearningPlanRun } from '@/composables/learningPlanState';
import type { LearningAssistantStep, LearningPlanPreview } from '@/types/learning-plan';
import type { LearningPlanView } from '@/types/learning-plan';
import { useRouter } from 'vue-router';
import { useAIStreamState } from '@/composables/useAIStreamState';

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  reasoning?: string;
  tokenUsage?: string;
  error?: string;
  meta?: string;
}

type AIMode = 'realtime' | 'wrong' | 'platform';

const SESSION_TYPE_MAP: Record<AIMode, string> = {
  realtime: 'student_realtime',
  wrong: 'student_wrong_analysis',
  platform: 'student_platform',
};

const auth = useAuthStore();
const router = useRouter();
const { state, reset } = useAIStreamState();


const question = ref('');
const questionId = ref<number | undefined>(undefined);
const activeMode = ref<AIMode>('realtime');
const activePanel = ref<'chat' | 'plan'>('chat');
const histories = ref<Record<AIMode, ChatMessage[]>>({ realtime: [], wrong: [], platform: [] });
const activeAbortController = ref<AbortController | null>(null);
const learningPlanAbort = ref<AbortController | null>(null);
const learningPlan = ref(emptyLearningPlanState());
const learningPlanBusy = ref(false);
const learningPlanError = ref('');
const learningQuestion = ref('');
const learningDays = ref(3);
const learningEventId = ref<number | null>(null);
const learningSaveRequestId = ref('');
const studentTasks = ref<Awaited<ReturnType<typeof getStudentTasksApi>>>([]);
const loadingTasks = ref(false);
const savedLearningPlans = ref<LearningPlanView[]>([]);

const currentMessages = computed(() => histories.value[activeMode.value]);

const platformLoading = ref(false);
const platformStatus = ref('');
const platformSources = ref<PlatformSource[]>([]);
const platformExcludeAttempted = ref(false);

const runPlatformSearch = async () => {
  if (!question.value.trim() || platformLoading.value) return;
  platformLoading.value = true;
  platformStatus.value = '检索中…';
  platformSources.value = [];
  try {
    const result = await platformSearchApi({
      query: question.value.trim(),
      excludeAttempted: platformExcludeAttempted.value,
      limit: 5,
    });
    platformSources.value = result.sources ?? [];
    if (result.status === 'UNAVAILABLE') {
      platformStatus.value = '检索暂不可用，请稍后重试。';
    } else if (result.status === 'INSUFFICIENT') {
      platformStatus.value = '平台暂无足够相关内容。可切换通用讲解或换一个更具体的提问。';
    } else {
      const modeText = result.actualMode === 'lexical_only' ? '（关键词模式）' : '';
      platformStatus.value = `找到 ${result.questions.length} 道相关题${modeText}，来源 ${result.sources.length} 条，耗时 ${result.durationMs}ms`;
      histories.value.platform.push({
        id: String(Date.now()),
        role: 'assistant',
        content: result.questions.map((q) => `【题 ${q.questionId}】${q.content}`).join('\n\n'),
        meta: `平台检索 · ${result.status}`,
      });
    }
  } catch {
    platformStatus.value = '检索失败，请稍后重试。';
  } finally {
    platformLoading.value = false;
  }
};

const openPlatformSource = (source: PlatformSource) => {
  histories.value.platform.push({
    id: String(Date.now()),
    role: 'assistant',
    content: `【来源 ${source.sourceId}】\n${source.text}`,
    meta: '平台来源',
  });
};

onMounted(async () => {
  loadingTasks.value = true;
  try {
    studentTasks.value = await getStudentTasksApi();
    if (studentTasks.value.length === 1) learningEventId.value = studentTasks.value[0]!.id;
  } catch {
    learningPlanError.value = '暂时无法加载可用作业，请稍后重试。';
  } finally { loadingTasks.value = false; }
  try { savedLearningPlans.value = await getLearningPlansApi(); } catch { /* unavailable history remains empty */ }
});

const openPlanPanel = () => { if (!state.value.loading && !learningPlanBusy.value) activePanel.value = 'plan'; };

const generateLearningPlan = async () => {
  if (!learningEventId.value || !learningQuestion.value.trim() || learningPlanBusy.value) return;
  const requestId = crypto.randomUUID();
  learningSaveRequestId.value = requestId;
  learningPlan.value = emptyLearningPlanState();
  learningPlanError.value = '';
  learningPlanBusy.value = true;
  const controller = new AbortController();
  learningPlanAbort.value = controller;
  try {
    await streamLearningPlanPreview({ eventId: learningEventId.value, days: learningDays.value, question: learningQuestion.value.trim(), requestId }, {
      signal: controller.signal,
      onProgress: (payload) => {
        const event = payload as typeof payload & { runId?: string; requestId?: string };
        if (learningSaveRequestId.value !== requestId || !event.runId || event.requestId !== requestId) return;
        learningPlan.value = startLearningPlanRun(event.runId);
      },
      onToolStep: (payload) => {
        const event = payload as unknown as LearningAssistantStep;
        learningPlan.value = acceptLearningPlanEvent(learningPlan.value, event, current => ({
          ...current, steps: [...current.steps, event],
        }));
      },
      onPlan: (payload) => {
        const event = payload as Record<string, unknown> & { runId: string; seq: number; planPreview: LearningPlanPreview };
        learningPlan.value = acceptLearningPlanEvent(learningPlan.value, event, current => ({ ...current, preview: event.planPreview }));
      },
      onComplete: () => { if (learningSaveRequestId.value === requestId) learningPlanBusy.value = false; },
      onError: (payload) => {
        if (learningSaveRequestId.value !== requestId) return;
        learningPlanError.value = payload.message || '暂时无法生成复习计划。';
        learningPlanBusy.value = false;
      },
    });
  } catch (error: unknown) {
    if (learningSaveRequestId.value === requestId) {
      if (!isAbortError(error)) learningPlanError.value = error instanceof Error ? error.message : '暂时无法生成复习计划。';
      learningPlanBusy.value = false;
    }
  } finally {
    if (learningPlanAbort.value === controller) learningPlanAbort.value = null;
  }
};

const stopLearningPlan = () => learningPlanAbort.value?.abort();

const openSavedPlan = (plan: LearningPlanView) => {
  const preview: LearningPlanPreview = { days: plan.days, sourceEventId: plan.eventId,
    sourceAnswerRevision: plan.sourceAnswerRevision, executionMode: plan.executionMode,
    items: plan.items, evidenceSummary: plan.evidenceSummary, partial: false };
  learningPlan.value = { ...startLearningPlanRun(`saved-${plan.id}`), preview, savedPlan: plan };
  learningPlanError.value = '';
};

const savePlan = async () => {
  if (!learningPlan.value.runId || !learningSaveRequestId.value || learningPlanBusy.value) return;
  learningPlanBusy.value = true;
  try {
    const savedPlan = await saveLearningPlanApi(learningPlan.value.runId, learningSaveRequestId.value);
    learningPlan.value = { ...learningPlan.value, savedPlan };
    savedLearningPlans.value = [savedPlan, ...savedLearningPlans.value.filter(item => item.id !== savedPlan.id)];
  } catch (error: unknown) {
    learningPlanError.value = error instanceof Error ? error.message : '无法保存草稿。';
  } finally { learningPlanBusy.value = false; }
};

const activatePlan = async () => {
  const plan = learningPlan.value.savedPlan;
  if (!plan) return;
  learningPlanBusy.value = true;
  try {
    const savedPlan = await activateLearningPlanApi(plan.id, plan.version);
    learningPlan.value = { ...learningPlan.value, savedPlan };
    savedLearningPlans.value = savedLearningPlans.value.map(item => item.id === savedPlan.id ? savedPlan : item);
  } catch (error: unknown) {
    learningPlanError.value = error instanceof Error ? error.message : '无法激活计划。';
  } finally { learningPlanBusy.value = false; }
};

const refreshPlan = async () => {
  const plan = learningPlan.value.savedPlan;
  if (!plan) return;
  learningPlanBusy.value = true;
  try {
    const savedPlan = await getLearningPlanApi(plan.id);
    learningPlan.value = { ...learningPlan.value, savedPlan };
    savedLearningPlans.value = savedLearningPlans.value.map(item => item.id === savedPlan.id ? savedPlan : item);
  }
  catch (error: unknown) { learningPlanError.value = error instanceof Error ? error.message : '无法刷新进度。'; }
  finally { learningPlanBusy.value = false; }
};

const cancelPlan = async () => {
  const plan = learningPlan.value.savedPlan;
  if (!plan) return;
  learningPlanBusy.value = true;
  try {
    const savedPlan = await cancelLearningPlanApi(plan.id, plan.version);
    learningPlan.value = { ...learningPlan.value, savedPlan };
    savedLearningPlans.value = savedLearningPlans.value.map(item => item.id === savedPlan.id ? savedPlan : item);
  } catch (error: unknown) { learningPlanError.value = error instanceof Error ? error.message : '无法取消计划。'; }
  finally { learningPlanBusy.value = false; }
};

const goPractice = (questionId: number, eventId: number) => {
  router.push({ path: '/student/practice', query: { eventId, questionId } });
};

const normalizeAIError = (raw: string | undefined) => {
  const message = raw ?? '';
  if (!message) return 'AI 服务暂不可用，请稍后重试。';
  if (message.includes('401') || message.includes('API密钥') || message.includes('认证失败')) {
    return 'AI 服务认证失败，请联系管理员检查配置。';
  }
  return message;
};

const formatNow = () => new Date().toLocaleString('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const switchMode = (mode: AIMode) => {
  if (state.value.loading) return;
  activePanel.value = 'chat';
  activeMode.value = mode;
  reset();
};

const buildCallbacks = (mode: AIMode) => ({
  onMessage: (payload: { content: string }) => {
    state.value.content += payload.content;
  },
  onProgress: (payload: { progress: number }) => {
    state.value.progress = payload.progress ?? state.value.progress;
  },
  onReasoning: (payload: { reasoning: string }) => {
    state.value.reasoning += payload.reasoning;
  },
  onComplete: (payload: { promptTokens?: number; completionTokens?: number; totalTokens?: number }) => {
    state.value.loading = false;
    state.value.tokenUsage = `Token: ${payload.totalTokens ?? 0} (in ${payload.promptTokens ?? 0} / out ${payload.completionTokens ?? 0})`;
    histories.value[mode].push({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: state.value.content,
      reasoning: state.value.reasoning,
      tokenUsage: state.value.tokenUsage,
      meta: formatNow(),
    });
  },
  onError: (payload: { message: string }) => {
    state.value.loading = false;
    state.value.error = normalizeAIError(payload.message);
    histories.value[mode].push({
      id: `assistant-error-${Date.now()}`,
      role: 'assistant',
      content: state.value.content,
      reasoning: state.value.reasoning,
      error: state.value.error,
      meta: formatNow(),
    });
  },
  sessionType: SESSION_TYPE_MAP[mode],
});

const submitCurrentMode = async () => {
  if (!question.value.trim()) return;
  if (activeMode.value === 'wrong' && !questionId.value) return;
  if (activeMode.value === 'platform') {
    await runPlatformSearch();
    return;
  }

  const mode = activeMode.value;
  const currentQuestion = question.value.trim();
  histories.value[mode].push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: currentQuestion,
    meta: formatNow(),
  });

  reset();
  state.value.loading = true;
  question.value = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;

  try {
    if (mode === 'realtime') {
      await streamRealtimeQA({ question: currentQuestion }, { ...buildCallbacks(mode), signal: abortController.signal });
    } else {
      await streamWrongQuestionAnalysis(
        { question: currentQuestion, questionId: questionId.value },
        { ...buildCallbacks(mode), signal: abortController.signal }
      );
    }
  } catch (error: any) {
    state.value.loading = false;
    if (isAbortError(error)) {
      const partialContent = state.value.content.trim();
      const partialReasoning = state.value.reasoning.trim();
      if (partialContent || partialReasoning) {
        histories.value[mode].push({
          id: `assistant-abort-${Date.now()}`,
          role: 'assistant',
          content: state.value.content,
          reasoning: state.value.reasoning,
          tokenUsage: '已手动停止',
          meta: formatNow(),
        });
      }
      return;
    }

    state.value.error = normalizeAIError(error?.message);
    histories.value[mode].push({
      id: `assistant-catch-${Date.now()}`,
      role: 'assistant',
      content: state.value.content,
      reasoning: state.value.reasoning,
      error: state.value.error,
      meta: formatNow(),
    });
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const stopCurrentAnalysis = () => {
  if (!state.value.loading) return;
  activeAbortController.value?.abort();
};

onUnmounted(() => {
  activeAbortController.value?.abort();
  learningPlanAbort.value?.abort();
});

const clearCurrentMode = async () => {
  const mode = activeMode.value;
  histories.value[mode] = [];
  reset();
  try {
    await clearSessionApi(SESSION_TYPE_MAP[mode]);
  } catch {
    // ignore
  }
};
</script>

<style scoped>
.ai-page {
  display: grid;
  gap: 18px;
}

.learning-plan-panel { display:grid; gap:12px; padding:22px; border:1px solid rgba(151,172,249,.14); border-radius:22px; background:#f3f6fa; color:#1c2938; }
.learning-plan-panel textarea,.learning-plan-panel select { width:100%; padding:12px; border:1px solid #ccd5e0; border-radius:10px; background:white; color:#1c2938; }
.learning-plan-panel .panel-head p { margin:4px 0 0; color:#59697a; }
.learning-error { padding:12px; border-radius:10px; background:#fff0f0; color:#a22121; }
.plan-history { display:flex; flex-wrap:wrap; gap:8px; align-items:center; }
.plan-history h3 { width:100%; margin:4px 0; }
.plan-history-item { border:1px solid #d4dce7; border-radius:999px; background:white; color:#364152; padding:8px 12px; cursor:pointer; }

.ai-hero,
.ask-panel,
.history-panel {
  border: 1px solid rgba(151, 172, 249, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 36, 0.9), rgba(18, 14, 42, 0.84));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.32);
}

.ai-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  padding: 24px 28px;
}

.ai-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(90, 243, 211, 0.2);
  background: rgba(90, 243, 211, 0.08);
  font-size: var(--font-size-xs);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(183, 248, 235, 0.92);
}

.ai-title {
  margin: 14px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.ai-summary {
  color: var(--text-muted);
  line-height: 1.7;
}

.mode-tabs {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.mode-tab {
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.03);
  color: #dbe6ff;
  cursor: pointer;
  font-weight: 700;
}

.mode-tab.active {
  background: rgba(90, 243, 211, 0.12);
  border-color: rgba(90, 243, 211, 0.45);
  color: #8ef7e0;
}

.ai-layout {
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.2fr);
  gap: 18px;
}

.ask-panel,
.history-panel {
  padding: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.field-label {
  display: block;
  margin: 12px 0 8px;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.panel-actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.submit-btn {
  padding: 12px 18px;
  border-radius: 14px;
  border: 1px solid rgba(90, 243, 211, 0.45);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.88), rgba(255, 199, 122, 0.88));
  color: #061016;
  font-size: var(--font-size-md);
  font-weight: 800;
  cursor: pointer;
}

.stop-btn {
  padding: 12px 18px;
  border-radius: 14px;
  border: 1px solid rgba(255, 107, 145, 0.45);
  background: rgba(255, 107, 145, 0.12);
  color: #ffd5e2;
  font-size: var(--font-size-md);
  font-weight: 700;
  cursor: pointer;
  margin-right: 10px;
}

.history-list {
  display: grid;
  gap: 12px;
  max-height: 720px;
  overflow: auto;
}

.history-empty {
  padding: 24px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.025);
  color: var(--text-muted);
  text-align: center;
}

.history-item {
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(151, 172, 249, 0.08);
}

.history-item.user {
  background: rgba(90, 243, 211, 0.06);
}

.history-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.history-role {
  color: var(--text);
  font-weight: 700;
}

.history-content,
.history-reasoning pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.8;
}

.history-error {
  color: #b91c1c;
}

.session-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.live-output {
  border-color: rgba(90, 243, 211, 0.22);
}

@media (max-width: 980px) {
  .ai-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .ai-hero,
  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
