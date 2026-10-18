<template>
  <AppShell :subtitle="`学习报告 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="report-page student-font">
      <section class="report-hero">
        <div class="report-hero-main">
          <span class="report-kicker">学习报告</span>
          <h1 class="report-title">学习报告</h1>
          <p class="report-summary">查看当前任务的原始作答与补练情况。AI 建议需教师核实。</p>
        </div>
        <div class="report-actions">
          <button
            class="generate-btn"
            :class="{ loading: state.loading }"
            :disabled="state.loading || !reportsAvailable || !scopeReady"
            @click="generateReport"
          >
            <span v-if="!state.loading">生成报告</span>
            <span v-else class="btn-loading"><span class="btn-ring" />生成中…</span>
          </button>
          <button
            v-if="state.loading"
            class="stop-btn"
            @click="stopCurrentReport"
          >停止生成</button>
        </div>
      </section>

      <p v-if="!reportsAvailable">AI 报告暂不可用。</p>
      <p v-if="evidence.model">模型：{{ evidence.model }} · 生成时间：{{ evidence.generatedAt }} · 输入摘要：{{ String(evidence.inputSha256).slice(0,12) }}</p>
      <section class="report-stats">
        <div class="stat-card">
          <span class="stat-label">当前状态</span>
          <strong class="stat-value">{{ state.content ? '已生成' : state.loading ? '生成中' : '待生成' }}</strong>
        </div>
        <div class="stat-card">
          <span class="stat-label">报告来源</span>
          <strong class="stat-value">题目信息+本轮答题记录</strong>
        </div>
      </section>

      <div v-if="state.error" class="report-error">
        <span>{{ state.error }}</span>
      </div>

      <section v-if="state.content || state.loading" class="report-output">
        <div class="output-head">
          <span class="output-title">报告内容</span>
        </div>
        <div class="output-body">
          <StreamOutput
            :loading="state.loading"
            :progress="state.progress"
            :content="state.content"
            :reasoning="state.reasoning"
            :token-usage="state.tokenUsage"
            :error="state.error"
          />
        </div>
      </section>

      <section v-else class="report-empty">
        <div>
          <h3>还没有生成学习报告</h3>
          <p>点击上方按钮后，系统会基于当前学习数据生成总结。</p>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { useAuthStore } from '@/stores/auth';
import { useStudentReportStore } from '@/stores/studentReport';
import { streamStudentReport } from '@/api/ai';
import { useRoute } from 'vue-router';
import { getStudentTasksApi } from '@/api/teacher-event';
import { getAICapabilitiesApi } from '@/api/ai';
import { useAIStreamState } from '@/composables/useAIStreamState';

const auth = useAuthStore();
const route=useRoute();
const eventId=ref<number>();
const reportsAvailable=ref(false);
const scopeReady=ref(false);
const evidence=ref<Record<string,unknown>>({});
const reportStore = useStudentReportStore();
const { state, reset } = useAIStreamState();
const activeAbortController = ref<AbortController | null>(null);

const links = [
  { label: '首页', to: '/student/home' },
  { label: '作业', to: '/student/task' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const normalizeReportError = (raw: string | undefined) => {
  const message = raw ?? '';
  if (!message) return '学习报告暂时无法生成，请稍后重试。';
  if (message.includes('该会话无答题记录') || message.includes('无法生成报告')) {
    return '本次学习还没有进行过答题。';
  }
  return message;
};

const restoreCachedReport = () => {
  reportStore.hydrateFromStorage(eventId.value);
  const cached = reportStore.cachedReport;
  if (!cached) return;
  state.value.content = cached.content;
  state.value.reasoning = cached.reasoning;
  state.value.tokenUsage = cached.tokenUsage;
};

const persistCurrentReport = () => {
  if (!state.value.content) return;
  reportStore.setCachedReport({
    content: state.value.content,
    reasoning: state.value.reasoning,
    tokenUsage: state.value.tokenUsage,
  }, eventId.value);
};

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const generateReport = async () => {
  reset();
  state.value.loading = true;
  state.value.error = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;
  try {
    await streamStudentReport({
      eventId: eventId.value,
      onEvidence: (payload) => { evidence.value=payload; },
      signal: abortController.signal,
      onMessage: (payload) => { state.value.content += payload.content; },
      onProgress: (payload) => { state.value.progress = payload.progress ?? state.value.progress; },
      onReasoning: (payload) => { state.value.reasoning += payload.reasoning ?? ''; },
      onComplete: (payload) => {
        state.value.loading = false;
        state.value.tokenUsage = `Token: ${payload.totalTokens ?? 0}`;
        persistCurrentReport();
      },
      onError: (payload) => {
        state.value.loading = false;
        state.value.error = normalizeReportError(payload.message);
      },
    });
  } catch (error: any) {
    if (isAbortError(error)) {
      state.value.loading = false;
      persistCurrentReport();
      return;
    }
    state.value.loading = false;
    state.value.error = normalizeReportError(error?.message);
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const stopCurrentReport = () => {
  if (!state.value.loading) return;
  activeAbortController.value?.abort();
};

onMounted(async () => {
  try {
    const capability=await getAICapabilitiesApi();reportsAvailable.value=capability.reports;
    const available = route.query.eventId ? [] : await getStudentTasksApi();
    eventId.value = route.query.eventId ? Number(route.query.eventId) : (available.find(task => task.canAnswer) ?? available[0])?.id;
    if (!eventId.value) { state.value.error='暂无学习任务，请完成任务后再生成报告。';return; }
    scopeReady.value=true;restoreCachedReport();
  } catch { state.value.error='任务信息加载失败，请刷新重试。'; }
});

onUnmounted(() => {
  activeAbortController.value?.abort();
});
</script>

<style scoped>
.report-page {
  display: grid;
  gap: 18px;
}

.report-hero,
.stat-card,
.report-output,
.report-empty,
.report-error {
  border: 1px solid rgba(151, 172, 249, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 36, 0.9), rgba(18, 14, 42, 0.84));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.32);
}

.report-hero {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 16px;
  padding: 26px 28px;
  background:
    radial-gradient(circle at 18% 16%, rgba(90, 243, 211, 0.14), transparent 24%),
    linear-gradient(140deg, rgba(11, 18, 39, 0.96), rgba(20, 12, 44, 0.92));
}

.report-kicker {
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

.report-title {
  margin: 14px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.report-summary {
  color: var(--text-muted);
  line-height: 1.7;
}

.report-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.report-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  padding: 18px 20px;
  display: grid;
  gap: 8px;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.stat-value {
  font-size: var(--font-size-xl);
  color: var(--text);
}

.generate-btn {
  min-width: 140px;
  padding: 12px 20px;
  border-radius: 14px;
  border: 1px solid rgba(90, 243, 211, 0.45);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.88), rgba(255, 199, 122, 0.88));
  color: #061016;
  font-size: var(--font-size-md);
  font-weight: 800;
  cursor: pointer;
}

.generate-btn.loading {
  background: rgba(151, 172, 249, 0.12);
  border-color: rgba(151, 172, 249, 0.2);
  color: var(--text-muted);
}

.stop-btn {
  min-width: 120px;
  padding: 12px 18px;
  border-radius: 14px;
  border: 1px solid rgba(255, 107, 145, 0.45);
  background: rgba(255, 107, 145, 0.12);
  color: #ffd5e2;
  font-size: var(--font-size-md);
  font-weight: 700;
  cursor: pointer;
}

.btn-loading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.btn-ring {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(151, 172, 249, 0.2);
  border-top-color: var(--brand);
  animation: spin 0.8s linear infinite;
}

.report-error {
  padding: 12px 16px;
  color: rgba(255, 107, 145, 0.92);
  background: rgba(255, 107, 145, 0.08);
}

.report-output {
  overflow: hidden;
}

.output-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}

  .output-title {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.output-body {
  padding: 18px 20px;
  min-height: clamp(640px, 120vh, 1440px);
  line-height: 1.8;
}

.report-empty {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 28px;
}

.empty-icon {
  font-size: 34px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
