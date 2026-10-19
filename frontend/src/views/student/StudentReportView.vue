<template>
  <AppShell subtitle="作业结果与复盘">
    <p v-if="loading" class="review-loading" role="status">正在加载作业结果…</p>
    <div v-else-if="loadError" class="review-load-error" role="alert">
      <p>{{ loadError }}</p><button class="review-secondary" @click="loadReview">重试</button>
      <RouterLink to="/student/task">返回我的作业</RouterLink>
    </div>
    <div v-else-if="result" class="assignment-review">
      <section class="review-task">
        <div><span class="review-kicker">本次作业</span><h2>{{ result.task.eventTitle }}</h2>
          <p>{{ result.task.teacherName }} · 共 {{ questionCount }} 题</p>
        </div>
        <RouterLink class="review-secondary" to="/student/task">返回我的作业</RouterLink>
      </section>
      <section class="review-facts" aria-label="首次作答结果">
        <div><span>已答题数</span><strong data-testid="assignment-answered">{{ result.answeredQuestions }} / {{ questionCount }}</strong></div>
        <div><span>答对题数</span><strong data-testid="assignment-correct">{{ result.correctQuestions }}</strong></div>
        <div><span>首次正确率</span><strong>{{ accuracy }}</strong></div>
      </section>
      <p class="review-facts-note">答错 {{ result.answeredQuestions - result.correctQuestions }} 题 · 未答 {{ Math.max(0, questionCount - result.answeredQuestions) }} 题。正确率按已答题计算，补练不改变本次成绩。</p>
      <section v-if="result.answeredQuestions === 0" class="review-no-answers">
        <h2>这份作业还没有作答记录</h2><p>先完成作答，再查看针对本次作业的复盘建议。</p>
      </section>
      <section class="review-ai">
        <div class="review-ai-head"><div><h2>AI 作业复盘</h2><p>结合本次作答，总结问题并给出下一步练习建议。</p></div>
          <div class="review-ai-actions">
            <button class="review-primary" :disabled="state.loading || !reportsAvailable || result.answeredQuestions === 0" @click="generateReport">
              {{ state.loading ? '生成中…' : state.content ? '重新生成 AI 复盘' : '生成 AI 复盘' }}
            </button>
            <button v-if="state.loading" class="review-secondary" @click="stopCurrentReport">停止生成</button>
          </div>
        </div>
        <p v-if="capabilityLoading" class="review-notice">正在检查 AI 复盘服务…</p>
        <p v-else-if="!reportsAvailable" class="review-notice">AI 复盘暂不可用，答题结果仍可查看。</p>
        <p v-if="capabilityNotice" class="review-notice">{{ capabilityNotice }}</p>
        <p v-if="notice" class="review-notice" role="status">{{ notice }}</p>
        <p v-if="state.error" class="review-error" role="alert">{{ state.error }}</p>
        <div v-if="state.content || state.loading" class="review-output">
          <StreamOutput :loading="state.loading" :progress="state.progress" :content="state.content" :reasoning="state.reasoning" token-usage="" error="" />
        </div>
        <p v-else-if="reportsAvailable && result.answeredQuestions > 0" class="review-placeholder">需要时再生成复盘，作答结果已经保存在上方。</p>
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { getStudentTaskResultApi, type StudentTaskResult } from '@/api/teacher-event';
import { getAICapabilitiesApi, streamStudentReport } from '@/api/ai';
import { useAIStreamState } from '@/composables/useAIStreamState';
import { useStudentReportStore } from '@/stores/studentReport';
import { createReportRequestGuard } from '@/utils/reportRequestGuard';

const route = useRoute();
const router = useRouter();
const result = ref<StudentTaskResult | null>(null);
const loading = ref(true);
const loadError = ref('');
const reportsAvailable = ref(false);
const capabilityNotice = ref('');
const capabilityLoading = ref(true);
const notice = ref('');
const { state, reset } = useAIStreamState();
const reportStore = useStudentReportStore();
const requestGuard = createReportRequestGuard();
let activeController: AbortController | null = null;

const questionCount = computed(() => result.value?.task.questionCount ?? result.value?.task.questionIds.length ?? 0);
const accuracy = computed(() => result.value?.answeredQuestions
  ? Math.round(result.value.correctQuestions / result.value.answeredQuestions * 100) + '%' : '—');

const cancelRequest = () => {
  requestGuard.invalidate();
  activeController?.abort();
  activeController = null;
};
const loadReview = async () => {
  cancelRequest();
  result.value = null;
  reset();
  notice.value = '';
  loadError.value = '';
  reportsAvailable.value = false;
  capabilityLoading.value = true;
  capabilityNotice.value = '';
  loading.value = true;
  const id = typeof route.query.eventId === 'string' ? Number(route.query.eventId) : NaN;
  if (!Number.isSafeInteger(id) || id <= 0) {
    await router.replace('/student/task');
    return;
  }
  const owner = requestGuard.begin(id, '');
  void getAICapabilitiesApi().then(capabilities => {
    if (!owner.isCurrent()) return;
    reportsAvailable.value = capabilities.reports;
    const labels: Record<string,string> = { UNKNOWN: 'AI 已配置，尚无实际调用结果。', BUSY: '最近一次调用：模型繁忙，可稍后重试。', AUTH_ERROR: '最近一次调用：认证失败，请联系教师检查配置。', TIMEOUT: '最近一次调用：响应超时，可稍后重试。', UNAVAILABLE: '最近一次调用：服务异常，可稍后重试。' };
    if (capabilities.reports && capabilities.reportStatus) capabilityNotice.value = labels[capabilities.reportStatus.state] ?? '';
  }).catch(() => {}).finally(() => {
    if (owner.isCurrent()) capabilityLoading.value = false;
  });
  try {
    const data = await getStudentTaskResultApi(id);
    if (!owner.isCurrent()) return;
    result.value = data;
    reportStore.hydrateFromStorage(id, data.answerRevision);
    const saved = data.savedReport;
    if (saved?.answerRevision === data.answerRevision && data.answeredQuestions > 0) {
      reportStore.setCachedReport({ content: saved.content, reasoning: '', tokenUsage: '' }, id, data.answerRevision);
    }
    const cached = reportStore.cachedReport;
    if (cached && data.answeredQuestions > 0) {
      state.value.content = cached.content;
      state.value.reasoning = cached.reasoning;
    }
  } catch (error: any) {
    if (owner.isCurrent()) loadError.value = error?.response?.data?.message ?? '作业结果加载失败，请稍后重试。';
  } finally {
    if (owner.isCurrent()) loading.value = false;
  }
};
const generateReport = async () => {
  const selected = result.value;
  if (!selected || state.value.loading || !reportsAvailable.value || selected.answeredQuestions === 0) return;
  const owner = requestGuard.begin(selected.task.id, selected.task.eventTitle);
  const controller = new AbortController();
  activeController = controller;
  reset();
  notice.value = '';
  state.value.loading = true;
  try {
    await streamStudentReport({
      eventId: selected.task.id,
      signal: controller.signal,
      onMessage: payload => { if (owner.isCurrent()) state.value.content += payload.content; },
      onProgress: payload => { if (owner.isCurrent()) state.value.progress = payload.progress ?? 0; },
      onReasoning: payload => { if (owner.isCurrent()) state.value.reasoning += payload.reasoning ?? ''; },
      onComplete: () => {
        if (!owner.isCurrent()) return;
        state.value.loading = false;
        reportStore.setCachedReport({
          content: state.value.content, reasoning: state.value.reasoning, tokenUsage: '',
        }, selected.task.id, selected.answerRevision);
      },
      onError: payload => {
        if (!owner.isCurrent()) return;
        state.value.loading = false;
        state.value.error = payload.message || '复盘生成失败，请稍后重试。';
      },
    });
  } catch (error: any) {
    if (owner.isCurrent() && error?.name !== 'AbortError') state.value.error = error?.message ?? '复盘生成失败，请稍后重试。';
  } finally {
    if (owner.isCurrent()) state.value.loading = false;
    if (activeController === controller) activeController = null;
  }
};
const stopCurrentReport = () => {
  cancelRequest();
  state.value.loading = false;
  notice.value = '已停止生成，当前内容不完整，可以重新生成。';
};
watch(() => route.query.eventId, loadReview, { immediate: true });
onUnmounted(cancelRequest);
</script>

<style scoped>
.assignment-review { display: grid; gap: 20px; }
.review-task, .review-ai, .review-no-answers, .review-load-error { padding: 24px 28px; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; }
.review-task { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.review-kicker { color: #7c8c9e; font-size: .75rem; }
.review-task h2 { font-size: 1.5rem; font-weight: 600; margin-top: 8px; overflow-wrap: anywhere; }
.review-task p { color: #64748b; font-size: .8125rem; margin-top: 8px; }
.review-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.review-facts > div { display: grid; gap: 10px; border: 1px solid #e2e8f0; border-radius: 10px; padding: 22px 25px; background: #fff; }
.review-facts span { font-size: .8125rem; color: #64748b; }
.review-facts strong { font-size: 1.75rem; font-weight: 600; color: #1d715b; }
.review-facts-note { color: #64748b; font-size: .75rem; line-height: 1.8; margin-top: -8px; }
.review-ai-head { display: flex; justify-content: space-between; align-items: center; gap: 20px; }
.review-ai h2, .review-no-answers h2 { font-size: 1.125rem; font-weight: 600; }
.review-ai-head p, .review-no-answers p { color: #64748b; font-size: .8125rem; margin-top: 8px; line-height: 1.8; }
.review-ai-actions { display: flex; gap: 10px; flex-wrap: wrap; flex-shrink: 0; }
.review-primary, .review-secondary { display: inline-flex; align-items: center; justify-content: center; padding: 10px 15px; border-radius: 6px; font: inherit; font-size: .8125rem; text-decoration: none; cursor: pointer; white-space: nowrap; }
.review-primary { background: #1d715b; color: #fff; border: 1px solid #1d715b; }
.review-primary:disabled { opacity: .5; cursor: not-allowed; }
.review-secondary { background: #fff; border: 1px solid #dbe6e0; color: #4e7568; }
.review-output { margin-top: 22px; border-top: 1px solid #eef2f7; padding-top: 20px; }
.review-output :deep(.stream-box) { padding: 0; border: 0; border-radius: 0; background: transparent; }
.review-output :deep(.stream-content) { padding: 0; min-height: 0; border: 0; background: transparent; color: #334155; }
.review-output :deep(.markdown-body :is(h1, h2, h3, h4, strong, em)) { color: #172033; }
.review-output :deep(.reasoning-box) { background: #f8fafc; border-color: #e2e8f0; }
.review-output :deep(.reasoning-box summary), .review-output :deep(.reasoning-content) { color: #475569; }
.review-notice, .review-placeholder { margin-top: 20px; color: #64748b; font-size: .8125rem; line-height: 1.8; }
.review-error { margin-top: 20px; color: #b91c1c; font-size: .8125rem; }
.review-loading { text-align: center; padding: 60px 0; color: #64748b; }
.review-load-error { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; color: #b91c1c; }
@media (max-width: 760px) {
  .review-task, .review-ai, .review-no-answers, .review-load-error { padding: 20px; }
  .review-task, .review-ai-head { flex-direction: column; align-items: flex-start; gap: 18px; }
  .review-task h2 { font-size: 1.25rem; }
  .review-facts { gap: 10px; }
  .review-facts > div { padding: 16px 12px; }
  .review-facts span { font-size: .6875rem; }
  .review-facts strong { font-size: 1.25rem; }
}
</style>
