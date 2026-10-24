<template>
  <AppShell hide-heading subtitle="作业结果与复盘">
    <header class="sw-header">
      <div>
        <h1>作业结果与复盘</h1>
        <p>回看每一道题，找到下一步练习的方向。</p>
      </div>
      <RouterLink to="/student/task" class="sw-button">我的作业</RouterLink>
    </header>
    <p v-if="loading" class="review-loading" role="status">正在加载作业结果…</p>
    <div v-else-if="loadError" class="review-load-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="review-secondary" @click="loadReview">重试</button>
      <RouterLink to="/student/task">返回我的作业</RouterLink>
    </div>
    <div v-else-if="result" class="assignment-review">
      <section class="review-task">
        <div>
          <span class="review-kicker">本次作业</span>
          <h2>{{ result.task.eventTitle }}</h2>
          <p>{{ result.task.teacherName }} · 共 {{ questionCount }} 题</p>
        </div>
        <button v-if="canResume" class="sw-button primary" @click="resumeTask()">
          继续未完成的作业<AppIcon name="arrow" /></button
        ><RouterLink v-else class="sw-button" to="/student/wrong-book">去复习错题</RouterLink>
      </section>
      <section class="review-facts" aria-label="首次作答结果">
        <div>
          <span>已答题数</span
          ><strong data-testid="assignment-answered"
            >{{ result.answeredQuestions }} / {{ questionCount }}</strong
          >
        </div>
        <div>
          <span>答对题数</span
          ><strong data-testid="assignment-correct">{{ result.correctQuestions }}</strong>
        </div>
        <div>
          <span>首次正确率</span><strong>{{ accuracy }}</strong>
        </div>
      </section>
      <p class="review-facts-note">
        答错 {{ result.answeredQuestions - result.correctQuestions }} 题 · 未答
        {{
          Math.max(0, questionCount - result.answeredQuestions)
        }}
        题。正确率按已答题计算，补练不改变本次成绩。
      </p>
      <section v-if="result.answeredQuestions === 0" class="review-no-answers">
        <h2>这份作业还没有作答记录</h2>
        <p>先完成作答，再查看针对本次作业的复盘建议。</p>
      </section>
      <section class="sw-surface question-review-section" aria-label="逐题结果">
        <div class="sw-section-head">
          <div>
            <h2>逐题结果</h2>
            <p>保留本次作业的首次答案，补练结果另行记录。</p>
          </div>
          <button class="sw-link" :disabled="recordsLoading" @click="loadQuestionResults">
            刷新结果
          </button>
        </div>
        <div class="sw-tabs" role="tablist" aria-label="逐题结果筛选">
          <button
            v-for="(tab, index) in reviewTabs"
            :id="`result-${tab.id}`"
            :key="tab.id"
            role="tab"
            :aria-selected="questionFilter === tab.id"
            :tabindex="questionFilter === tab.id ? 0 : -1"
            aria-controls="question-results-panel"
            @click="questionFilter = tab.id"
            @keydown="navigateReviewTabs($event, index)"
          >
            {{ tab.label
            }}<span v-if="!recordsLoading && !recordsError" class="sw-count">{{
              questionRows.filter((row) => tab.id === 'all' || row.status === tab.id).length
            }}</span>
          </button>
        </div>
        <div
          id="question-results-panel"
          role="tabpanel"
          :aria-labelledby="`result-${questionFilter}`"
        >
          <p v-if="recordsLoading" class="sw-empty" role="status">正在读取逐题作答结果…</p>
          <p v-else-if="recordsError" class="sw-error question-results-error" role="alert">
            {{ recordsError }}
            <button class="sw-link" @click="loadQuestionResults">重新加载</button>
          </p>
          <p v-else-if="!filteredQuestions.length" class="sw-empty">
            {{
              questionFilter === 'wrong'
                ? '这次作业没有答错的题目。'
                : questionFilter === 'unanswered'
                  ? '这次作业没有未答题。'
                  : questionFilter === 'correct'
                    ? '暂时没有答对的题目，继续练习。'
                    : '这次作业没有可查看的题目。'
            }}
          </p>
          <div v-else class="question-results-list">
            <article v-for="row in filteredQuestions" :key="row.id" class="question-result-row">
              <button
                class="question-result-trigger"
                :aria-expanded="expandedQuestion === row.id"
                :aria-controls="`result-question-${row.id}`"
                @click="toggleQuestion(row)"
              >
                <span class="result-question-number">{{
                  String(row.index + 1).padStart(2, '0')
                }}</span
                ><span class="result-question-copy"
                  ><span class="result-question-preview"
                    ><LatexText
                      :content="questionDetail(row)?.content || '尚未作答，展开查看题目'" /></span
                  ><span v-if="row.record" class="result-question-answer"
                    >我的答案 {{ row.record.userAnswer }} · 正确答案
                    {{ row.record.correctAnswer }}</span
                  ></span
                ><span
                  class="sw-badge"
                  :class="{ rose: row.status === 'wrong', neutral: row.status === 'unanswered' }"
                  >{{
                    row.status === 'correct' ? '答对' : row.status === 'wrong' ? '答错' : '未作答'
                  }}</span
                ><span aria-hidden="true" class="result-chevron">{{
                  expandedQuestion === row.id ? '−' : '＋'
                }}</span>
              </button>
              <div
                v-if="expandedQuestion === row.id"
                :id="`result-question-${row.id}`"
                class="question-result-detail"
              >
                <p v-if="!row.record && taskQuestionsLoading" class="sw-empty" role="status">
                  正在读取题目…
                </p>
                <p v-else-if="!row.record && taskQuestionsError" class="sw-error" role="alert">
                  {{ taskQuestionsError }}
                  <button class="sw-link" @click="loadTaskQuestions">重新加载题目</button>
                </p>
                <template v-else-if="questionDetail(row)"
                  ><div class="sw-formula">
                    <LatexText :content="questionDetail(row)!.content" />
                  </div>
                  <img
                    v-if="questionDetail(row)!.imageBase64"
                    class="sw-question-image"
                    :src="questionDetail(row)!.imageBase64!"
                    alt="题目配图" />
                  <div class="sw-result-options">
                    <div
                      v-for="(text, key) in questionDetail(row)!.options"
                      :key="key"
                      class="sw-result-option"
                      :class="{
                        correct: row.record && key === questionDetail(row)!.correctOption,
                        wrong: row.record && key === row.record.userAnswer && !row.record.correct,
                      }"
                    >
                      <strong>{{ key }}</strong
                      ><LatexText :content="text" />
                    </div>
                  </div>
                  <div v-if="row.record && questionDetail(row)!.explanation" class="sw-explanation">
                    <h3>解题解析</h3>
                    <LatexText :content="questionDetail(row)!.explanation!" /></div
                ></template>
                <p v-else class="sw-feedback">当前题目详情暂不可用。</p>
                <div class="question-result-actions">
                  <button
                    v-if="row.status === 'unanswered' && canResume"
                    class="sw-button primary"
                    @click="resumeTask(row.id)"
                  >
                    作答本题<AppIcon name="arrow" /></button
                  ><button
                    v-else-if="row.record"
                    class="sw-button"
                    :class="{ primary: row.status === 'wrong' }"
                    @click="practiceQuestion(row.id)"
                  >
                    {{ row.status === 'wrong' ? '补练这道题' : '再练一次' }}</button
                  ><span v-else class="sw-muted">作业当前不开放，无法补交作答。</span>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>
      <section class="review-ai">
        <div class="review-ai-head">
          <div>
            <h2>AI 作业复盘</h2>
            <p>结合本次作答，总结问题并给出下一步练习建议。</p>
          </div>
          <div class="review-ai-actions">
            <button
              class="review-primary"
              :disabled="state.loading || !reportsAvailable || result.answeredQuestions === 0"
              @click="generateReport"
            >
              {{ state.loading ? '生成中…' : state.content ? '重新生成 AI 复盘' : '生成 AI 复盘' }}
            </button>
            <button v-if="state.loading" class="review-secondary" @click="stopCurrentReport">
              停止生成
            </button>
          </div>
        </div>
        <p v-if="capabilityLoading" class="review-notice">正在检查 AI 复盘服务…</p>
        <p v-else-if="!reportsAvailable" class="review-notice">
          AI 复盘暂不可用，答题结果仍可查看。
        </p>
        <p v-if="capabilityNotice" class="review-notice">{{ capabilityNotice }}</p>
        <p v-if="notice" class="review-notice" role="status">{{ notice }}</p>
        <p v-if="state.error" class="review-error" role="alert">{{ state.error }}</p>
        <div v-if="state.content || state.loading" class="review-output">
          <StreamOutput
            :loading="state.loading"
            :progress="state.progress"
            :content="state.content"
            :reasoning="state.reasoning"
            token-usage=""
            error=""
          />
        </div>
        <p v-else-if="reportsAvailable && result.answeredQuestions > 0" class="review-placeholder">
          需要时再生成复盘，作答结果已经保存在上方。
        </p>
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import '@/assets/student-workspace.css';
import AppIcon from '@/components/AppIcon.vue';
import LatexText from '@/components/LatexText.vue';
import { useAuthStore } from '@/stores/auth';
import { studentTaskProgress } from '@/utils/assignmentProgress';
import { getTaskAnswersApi } from '@/api/answer-record';
import { getTaskQuestionsApi } from '@/api/teacher-event';
import type { AnswerRecordResponse } from '@/types/answer-record';
import type { QuestionResponse } from '@/types/question';
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
const auth = useAuthStore();
type QuestionFilter = 'all' | 'wrong' | 'unanswered' | 'correct';
const reviewTabs: Array<{ id: QuestionFilter; label: string }> = [
  { id: 'all', label: '全部题目' },
  { id: 'wrong', label: '答错' },
  { id: 'unanswered', label: '未作答' },
  { id: 'correct', label: '答对' },
];
const questionFilter = ref<QuestionFilter>('all'),
  recordsLoading = ref(false),
  recordsError = ref(''),
  assignmentRecords = ref<Record<number, AnswerRecordResponse>>({}),
  expandedQuestion = ref<number | null>(null);
const taskQuestions = ref<Record<number, QuestionResponse>>({}),
  taskQuestionsLoading = ref(false),
  taskQuestionsError = ref('');
let pageVersion = 0,
  recordsSequence = 0,
  taskQuestionsRequest: Promise<void> | null = null;
const canResume = computed(() =>
  result.value ? studentTaskProgress(result.value).canResume : false,
);
const questionRows = computed(() =>
  (result.value?.task.questionIds ?? []).map((id, index) => ({
    id,
    index,
    record: assignmentRecords.value[id],
    status: assignmentRecords.value[id]
      ? assignmentRecords.value[id]!.correct
        ? 'correct'
        : 'wrong'
      : 'unanswered',
  })),
);
type QuestionRow = (typeof questionRows.value)[number];
const filteredQuestions = computed(() =>
  questionRows.value.filter(
    (row) => questionFilter.value === 'all' || row.status === questionFilter.value,
  ),
);
function questionDetail(row: QuestionRow) {
  if (row.record) {
    const answer = row.record;
    return {
      content: answer.questionContent,
      options: answer.questionOptions,
      correctOption: answer.correctAnswer,
      explanation: answer.questionAnalysis,
      imageBase64: answer.imageBase64,
    };
  }
  return taskQuestions.value[row.id] ?? null;
}
async function loadQuestionResults() {
  const task = result.value?.task;
  if (!task) return;
  const version = pageVersion,
    owner = ++recordsSequence;
  recordsLoading.value = true;
  recordsError.value = '';
  try {
    const data = await getTaskAnswersApi(task.id);
    if (version !== pageVersion || owner !== recordsSequence) return;
    const byId: Record<number, AnswerRecordResponse> = {};
    for (const record of data.records ?? [])
      if (!record.practice && record.eventId === task.id && !byId[record.questionId])
        byId[record.questionId] = record;
    assignmentRecords.value = byId;
  } catch {
    if (version === pageVersion && owner === recordsSequence)
      recordsError.value = '逐题结果加载失败，请重试。';
  } finally {
    if (version === pageVersion && owner === recordsSequence) recordsLoading.value = false;
  }
}
async function loadTaskQuestions() {
  const task = result.value?.task;
  if (!task) return;
  if (taskQuestionsRequest) return taskQuestionsRequest;
  const version = pageVersion;
  taskQuestionsLoading.value = true;
  taskQuestionsError.value = '';
  taskQuestionsRequest = (async () => {
    try {
      const data = await getTaskQuestionsApi(task.id);
      if (version === pageVersion)
        taskQuestions.value = Object.fromEntries(data.map((question) => [question.id, question]));
    } catch {
      if (version === pageVersion) taskQuestionsError.value = '完整题目加载失败，请重试。';
    } finally {
      if (version === pageVersion) {
        taskQuestionsLoading.value = false;
        taskQuestionsRequest = null;
      }
    }
  })();
  return taskQuestionsRequest;
}
function toggleQuestion(row: QuestionRow) {
  if (expandedQuestion.value === row.id) {
    expandedQuestion.value = null;
    return;
  }
  expandedQuestion.value = row.id;
  if (!row.record && !taskQuestions.value[row.id]) void loadTaskQuestions();
}
function resumeTask(questionId?: number) {
  if (!result.value || !canResume.value) return;
  sessionStorage.setItem(
    'student_task_entry_event_id_' + (auth.user?.id ?? 'anonymous'),
    String(result.value.task.id),
  );
  void router.push({
    path: '/student/question',
    query: { eventId: result.value.task.id, resume: '1', questionId },
  });
}
function practiceQuestion(questionId: number) {
  if (result.value)
    void router.push({
      path: '/student/practice',
      query: { eventId: result.value.task.id, questionId },
    });
}
async function navigateReviewTabs(event: KeyboardEvent, index: number) {
  let next: number;
  if (event.key === 'ArrowRight') next = (index + 1) % reviewTabs.length;
  else if (event.key === 'ArrowLeft') next = (index + reviewTabs.length - 1) % reviewTabs.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = reviewTabs.length - 1;
  else return;
  event.preventDefault();
  questionFilter.value = reviewTabs[next]!.id;
  await nextTick();
  document.getElementById(`result-${questionFilter.value}`)?.focus();
}

const questionCount = computed(
  () => result.value?.task.questionCount ?? result.value?.task.questionIds.length ?? 0,
);
const accuracy = computed(() =>
  result.value?.answeredQuestions
    ? Math.round((result.value.correctQuestions / result.value.answeredQuestions) * 100) + '%'
    : '—',
);

const cancelRequest = () => {
  requestGuard.invalidate();
  activeController?.abort();
  activeController = null;
};
const loadReview = async () => {
  cancelRequest();
  ++pageVersion;
  ++recordsSequence;
  assignmentRecords.value = {};
  recordsLoading.value = false;
  recordsError.value = '';
  expandedQuestion.value = null;
  taskQuestions.value = {};
  taskQuestionsLoading.value = false;
  taskQuestionsError.value = '';
  taskQuestionsRequest = null;
  questionFilter.value = 'all';
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
  void getAICapabilitiesApi()
    .then((capabilities) => {
      if (!owner.isCurrent()) return;
      reportsAvailable.value = capabilities.reports;
      const labels: Record<string, string> = {
        UNKNOWN: 'AI 已配置，尚无实际调用结果。',
        BUSY: '最近一次调用：模型繁忙，可稍后重试。',
        AUTH_ERROR: '最近一次调用：认证失败，请联系教师检查配置。',
        TIMEOUT: '最近一次调用：响应超时，可稍后重试。',
        UNAVAILABLE: '最近一次调用：服务异常，可稍后重试。',
      };
      if (capabilities.reports && capabilities.reportStatus)
        capabilityNotice.value = labels[capabilities.reportStatus.state] ?? '';
    })
    .catch(() => {})
    .finally(() => {
      if (owner.isCurrent()) capabilityLoading.value = false;
    });
  try {
    const data = await getStudentTaskResultApi(id);
    if (!owner.isCurrent()) return;
    result.value = data;
    void loadQuestionResults();
    reportStore.hydrateFromStorage(id, data.answerRevision);
    const saved = data.savedReport;
    if (saved?.answerRevision === data.answerRevision && data.answeredQuestions > 0) {
      reportStore.setCachedReport(
        { content: saved.content, reasoning: '', tokenUsage: '' },
        id,
        data.answerRevision,
      );
    }
    const cached = reportStore.cachedReport;
    if (cached && data.answeredQuestions > 0) {
      state.value.content = cached.content;
      state.value.reasoning = cached.reasoning;
    }
  } catch (error: any) {
    if (owner.isCurrent())
      loadError.value = error?.response?.data?.message ?? '作业结果加载失败，请稍后重试。';
  } finally {
    if (owner.isCurrent()) loading.value = false;
  }
};
const generateReport = async () => {
  const selected = result.value;
  if (
    !selected ||
    state.value.loading ||
    !reportsAvailable.value ||
    selected.answeredQuestions === 0
  )
    return;
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
      onMessage: (payload) => {
        if (owner.isCurrent()) state.value.content += payload.content;
      },
      onProgress: (payload) => {
        if (owner.isCurrent()) state.value.progress = payload.progress ?? 0;
      },
      onReasoning: (payload) => {
        if (owner.isCurrent()) state.value.reasoning += payload.reasoning ?? '';
      },
      onComplete: () => {
        if (!owner.isCurrent()) return;
        state.value.loading = false;
        reportStore.setCachedReport(
          {
            content: state.value.content,
            reasoning: state.value.reasoning,
            tokenUsage: '',
          },
          selected.task.id,
          selected.answerRevision,
        );
      },
      onError: (payload) => {
        if (!owner.isCurrent()) return;
        state.value.loading = false;
        state.value.error = payload.message || '复盘生成失败，请稍后重试。';
      },
    });
  } catch (error: any) {
    if (owner.isCurrent() && error?.name !== 'AbortError')
      state.value.error = error?.message ?? '复盘生成失败，请稍后重试。';
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
onUnmounted(() => {
  ++pageVersion;
  ++recordsSequence;
  cancelRequest();
});
</script>

<style scoped>
.assignment-review {
  display: grid;
  gap: 20px;
}
.review-task,
.review-ai,
.review-no-answers,
.review-load-error {
  padding: 24px 28px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
}
.review-task {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.review-kicker {
  color: #7c8c9e;
  font-size: 0.75rem;
}
.review-task h2 {
  font-size: 1.5rem;
  font-weight: 600;
  margin-top: 8px;
  overflow-wrap: anywhere;
}
.review-task p {
  color: #64748b;
  font-size: 0.8125rem;
  margin-top: 8px;
}
.review-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.review-facts > div {
  display: grid;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 22px 25px;
  background: #fff;
}
.review-facts span {
  font-size: 0.8125rem;
  color: #64748b;
}
.review-facts strong {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1d715b;
}
.review-facts-note {
  color: #64748b;
  font-size: 0.75rem;
  line-height: 1.8;
  margin-top: -8px;
}
.review-ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.review-ai h2,
.review-no-answers h2 {
  font-size: 1.125rem;
  font-weight: 600;
}
.review-ai-head p,
.review-no-answers p {
  color: #64748b;
  font-size: 0.8125rem;
  margin-top: 8px;
  line-height: 1.8;
}
.review-ai-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  flex-shrink: 0;
}
.review-primary,
.review-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 15px;
  border-radius: 6px;
  font: inherit;
  font-size: 0.8125rem;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
}
.review-primary {
  background: #1d715b;
  color: #fff;
  border: 1px solid #1d715b;
}
.review-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.review-secondary {
  background: #fff;
  border: 1px solid #dbe6e0;
  color: #4e7568;
}
.review-output {
  margin-top: 22px;
  border-top: 1px solid #eef2f7;
  padding-top: 20px;
}
.review-output :deep(.stream-box) {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.review-output :deep(.stream-content) {
  padding: 0;
  min-height: 0;
  border: 0;
  background: transparent;
  color: #334155;
}
.review-output :deep(.markdown-body :is(h1, h2, h3, h4, strong, em)) {
  color: #172033;
}
.review-output :deep(.reasoning-box) {
  background: #f8fafc;
  border-color: #e2e8f0;
}
.review-output :deep(.reasoning-box summary),
.review-output :deep(.reasoning-content) {
  color: #475569;
}
.review-notice,
.review-placeholder {
  margin-top: 20px;
  color: #64748b;
  font-size: 0.8125rem;
  line-height: 1.8;
}
.review-error {
  margin-top: 20px;
  color: #b91c1c;
  font-size: 0.8125rem;
}
.review-loading {
  text-align: center;
  padding: 60px 0;
  color: #64748b;
}
.review-load-error {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  color: #b91c1c;
}
@media (max-width: 760px) {
  .review-task,
  .review-ai,
  .review-no-answers,
  .review-load-error {
    padding: 20px;
  }
  .review-task,
  .review-ai-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
  }
  .review-task h2 {
    font-size: 1.25rem;
  }
  .review-facts {
    gap: 10px;
  }
  .review-facts > div {
    padding: 16px 12px;
  }
  .review-facts span {
    font-size: 0.6875rem;
  }
  .review-facts strong {
    font-size: 1.25rem;
  }
}
</style>

<style scoped>
.question-results-error {
  margin: 22px 24px;
}
.question-results-list {
  padding: 0 24px;
}
.question-result-row {
  border-bottom: 1px solid #eaf0ec;
}
.question-result-row:last-child {
  border-bottom: 0;
}
.question-result-trigger {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 16px;
  padding: 24px 0;
  border: 0;
  background: none;
  font: inherit;
  text-align: left;
  color: #5e7c67;
  cursor: pointer;
}
.result-question-number {
  color: #9eb1a3;
  font-size: 12px;
  width: 24px;
  flex-shrink: 0;
}
.result-question-copy {
  flex: 1;
  min-width: 0;
}
.result-question-preview {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.result-question-answer {
  display: block;
  font-size: 11px;
  color: #91a597;
  margin-top: 9px;
}
.result-chevron {
  font-size: 17px;
  color: #91ac9c;
}
.question-result-detail {
  padding: 0 0 26px 40px;
  font-size: 14px;
}
.question-result-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 22px;
}
.review-ai {
  border-style: dashed;
}
.review-task > .sw-button {
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .question-results-list {
    padding: 0 16px;
  }
  .question-result-trigger {
    gap: 10px;
    padding: 20px 0;
  }
  .result-question-number {
    width: 20px;
  }
  .question-result-detail {
    padding-left: 0;
  }
  .question-result-trigger > .sw-badge {
    font-size: 10px;
  }
  .result-question-preview {
    font-size: 12px;
  }
  .question-result-detail {
    font-size: 13px;
  }
  .question-result-actions .sw-button {
    font-size: 12px;
  }
}
</style>
