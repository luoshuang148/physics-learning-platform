<template>
  <AppShell :subtitle="`学生答题 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="q-page">
      <!-- 顶部：任务标题 + 圆点导航 -->
      <div class="q-header">
        <span class="q-task-label" v-if="activeTaskTitle">{{ activeTaskTitle }}</span>
        <div class="q-dots">
          <button
            v-for="(q, idx) in questions"
            :key="q.id"
            class="q-dot"
            :class="{
              active: currentQuestion?.id === q.id,
              correct: doneMap[q.id]?.correct === true,
              wrong: doneMap[q.id]?.correct === false,
              locked: needsAnswerCommit && currentQuestion?.id !== q.id,
            }"
            :disabled="needsAnswerCommit && currentQuestion?.id !== q.id"
            @click="selectQuestion(q)"
            :title="needsAnswerCommit && currentQuestion?.id !== q.id ? '请先提交当前题目记录' : `第 ${idx + 1} 题`"
          >{{ idx + 1 }}</button>
        </div>
        <button v-if="isPractice && currentCommittedResult" class="neon-btn primary" @click="restartPractice">再次补练</button>
        <span class="q-progress">{{ doneCount }}/{{ questions.length }}</span>
      </div>

      <!-- 主体：答题区 -->
      <div class="q-body">
        <div v-if="!currentQuestion" class="q-empty muted">题目加载中…</div>
        <template v-else>
          <div class="q-index">第 {{ currentIndex + 1 }} 题 / 共 {{ questions.length }} 题</div>
          <div class="question-text"><LatexText :content="currentQuestion.content" /></div>
          <div v-if="currentQuestion.imageBase64" class="question-image-panel">
            <img :src="currentQuestion.imageBase64" alt="题目配图" class="question-image" />
          </div>

          <template v-if="currentCommittedResult">
            <div class="option-list">
              <div
                v-for="(text, key) in currentQuestion.options"
                :key="key"
                class="option-row"
                :class="{
                  'opt-correct': String(key) === currentCommittedResult.correctAnswer,
                  'opt-wrong': String(key) === currentCommittedResult.userAnswer
                    && !currentCommittedResult.correct,
                }"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </div>
            </div>
            <div class="result-box" :class="currentCommittedResult.correct ? 'ok' : 'fail'">
              <p>{{ currentCommittedResult.correct ? '✓ 回答正确！' : '✗ 回答错误' }}</p>
              <p>你的答案：{{ currentCommittedResult.userAnswer }} · 正确答案：{{ currentCommittedResult.correctAnswer }}</p>
              <p v-if="currentCommittedResult.questionAnalysis">解析：<LatexText :content="currentCommittedResult.questionAnalysis!" /></p>
            </div>
            <div class="q-nav-row q-action-row">
              <button
                v-if="currentIndex < questions.length - 1"
                class="neon-btn primary"
                type="button"
                @click="goNext"
              >下一题</button>
            </div>
            <div v-if="shouldShowSaveSuccess" class="q-save-feedback">
              {{ saveSuccessMessage }}
            </div>
            <WrongAnalysisPanel
              :question-id="currentQuestion.id"
              :record-id="currentCommittedResult?.recordId"
            />
          </template>

          <template v-else-if="currentPendingAnswer">
            <div class="option-list">
              <div
                v-for="(text, key) in currentQuestion.options"
                :key="key"
                class="option-row"
                :class="{ selected: String(key) === currentPendingAnswer.userAnswer }"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </div>
            </div>
            <div class="result-box">
              <p>{{ isPersistingAnswer ? '正在由服务端保存并判分…' : '作答尚未保存' }}</p>
              <p>你的答案：{{ currentPendingAnswer.userAnswer }}</p>
            </div>
            <div class="q-submit-row q-submit-row-pending">
              <button class="neon-btn primary" :disabled="isPersistingAnswer" @click="submitPendingAnswer">
                {{ isPersistingAnswer ? '提交中…' : '重试保存作答' }}
              </button>
            </div>
          </template>

          <!-- 未作答 -->
          <template v-else>
            <div class="option-list">
              <label
                v-for="(text, key) in currentQuestion.options"
                :key="key"
                class="option-row"
                :class="{ selected: selectedOption === String(key) }"
                @click="selectedOption = String(key)"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </label>
            </div>
            <div class="q-submit-row">
              <button class="neon-btn primary" :disabled="!selectedOption || isPersistingAnswer" @click="submitAnswer">提交答案</button>
            </div>
          </template>
        </template>
      </div>
    </div>

    <!-- 底部完成条 -->
    <transition name="finish-pop">
      <div v-if="allDone" class="finish-bar">
        <span class="finish-bar-text">全部题目已完成</span>
        <div class="finish-bar-actions">
          <button class="neon-btn primary" @click="finishAndExit">完成答题，返回首页</button>
        </div>
      </div>
    </transition>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import { useAuthStore } from '@/stores/auth';
import { submitAnswerApi, getTaskAnswersApi } from '@/api/answer-record';
import { getQuestionByIdApi } from '@/api/question';
import { getStudentTasksApi, getTaskApi, getTaskQuestionsApi } from '@/api/teacher-event';
import { useToast } from '@/composables/useToast';
import WrongAnalysisPanel from '@/components/WrongAnalysisPanel.vue';
import AppToast from '@/components/AppToast.vue';
import { resetSessionId } from '@/utils/session';
import type { QuestionResponse } from '@/types/question';
import type { AnswerRecordResponse, AnswerRecordSubmitPayload } from '@/types/answer-record';

const QUESTION_VIEW_STATE_KEY_PREFIX = 'student_question_view_state';
const QUESTION_TASK_EVENT_ID_KEY_PREFIX = 'student_question_task_event_id';
const QUESTION_VIEW_STATE_LEGACY_KEY = 'student_question_view_state';

interface PendingAnswerResult {
  submission?: AnswerRecordSubmitPayload;
  requestId: string;
  questionId: number;
  userAnswer: string;
}

const auth = useAuthStore();
const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';
const getTaskEntryEventIdKey = () => TASK_ENTRY_EVENT_ID_KEY_PREFIX + '_' + (auth.user?.id ?? 'anonymous');
const route = useRoute();
const router = useRouter();
const isPractice = computed(() => route.path === '/student/practice');

const getQuestionViewStateKey = () => `${QUESTION_VIEW_STATE_KEY_PREFIX}_v2_${auth.user?.id ?? 'anonymous'}_${activeTeachingEventId.value ?? 'free'}_${isPractice.value ? `practice_${route.query.questionId ?? 'first'}` : 'assignment'}`;
const getQuestionTaskEventIdKey = () => `${QUESTION_TASK_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}_${isPractice.value ? 'practice' : 'assignment'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '作业', to: '/student/task' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const questions = ref<QuestionResponse[]>([]);
const currentQuestion = ref<QuestionResponse | null>(null);
const selectedOption = ref('');
const submitResult = ref<AnswerRecordResponse | null>(null);
const pendingAnswer = ref<PendingAnswerResult | null>(null);
const isPersistingAnswer = ref(false);
const saveSuccessMessage = ref('');
const saveSuccessQuestionId = ref<number | null>(null);
const { visible, type, message, show } = useToast();
const activeTaskTitle = ref('');

const doneMap = ref<Record<number, AnswerRecordResponse>>({});
const doneCount = computed(() => Object.keys(doneMap.value).length);
const allDone = computed(() => questions.value.length > 0 && doneCount.value === questions.value.length);
const currentIndex = computed(() => questions.value.findIndex(q => q.id === currentQuestion.value?.id));
const currentPendingAnswer = computed(() => currentQuestion.value ? pendingAnswer.value?.questionId === currentQuestion.value.id ? pendingAnswer.value : null : null);
const currentCommittedResult = computed(() => currentQuestion.value ? doneMap.value[currentQuestion.value.id] ?? null : null);
const shouldShowSaveSuccess = computed(() => Boolean(
  currentQuestion.value
  && saveSuccessQuestionId.value === currentQuestion.value.id
  && saveSuccessMessage.value
));
const needsAnswerCommit = computed(() => Boolean(currentPendingAnswer.value));

const persistQuestionViewState = () => {
  const currentQuestionId = currentQuestion.value?.id ?? null;
  const pending = pendingAnswer.value;
  sessionStorage.setItem(getQuestionViewStateKey(), JSON.stringify({
    currentQuestionId,
    selectedOption: selectedOption.value,
    pendingAnswer: pending,
    activeTaskTitle: activeTaskTitle.value,
  }));
};

const restoreQuestionViewState = (serverRecords: AnswerRecordResponse[]) => {
  const stateKey = getQuestionViewStateKey();
  const raw = sessionStorage.getItem(stateKey);
  if (!raw) {
    sessionStorage.removeItem(QUESTION_VIEW_STATE_LEGACY_KEY);
    return;
  }
  try {
    const parsed = JSON.parse(raw) as {
      currentQuestionId?: number | null;
      selectedOption?: string;
      pendingAnswer?: PendingAnswerResult | null;
      doneEntries?: Array<[string, AnswerRecordResponse]>;
      activeTaskTitle?: string;
    };

    selectedOption.value = parsed.selectedOption ?? '';
    pendingAnswer.value = parsed.pendingAnswer ?? null;
    activeTaskTitle.value = parsed.activeTaskTitle ?? activeTaskTitle.value;

    // The database is authoritative. Cached completion can be stale, from practice,
    // or from a response that never reached this tab.
    const pending=pendingAnswer.value;
    if(pending) {
      const recovered=serverRecords.find(r=>r.requestId===pending.requestId
        && r.questionId===pending.questionId && r.eventId===activeTeachingEventId.value
        && Boolean(r.practice)===isPractice.value);
      if(recovered) {
        doneMap.value[recovered.questionId]=recovered;
        pendingAnswer.value=null;selectedOption.value='';
      } else if(!isPractice.value && doneMap.value[pending.questionId]) {
        pendingAnswer.value=null;selectedOption.value='';
      }
    }

    if (parsed.currentQuestionId) {
      const matchedQuestion = questions.value.find((q) => q.id === parsed.currentQuestionId);
      if (matchedQuestion) {
        currentQuestion.value = matchedQuestion;
      }
    }
  } catch {
    sessionStorage.removeItem(stateKey);
  }
};

const clearQuestionViewState = () => {
  sessionStorage.removeItem(getQuestionViewStateKey());
  sessionStorage.removeItem(QUESTION_VIEW_STATE_LEGACY_KEY);
};

const finishAndExit = () => {
  if (needsAnswerCommit.value) {
    show('请先提交本题答题记录后再结束答题', 'info');
    return;
  }
  clearQuestionViewState();
  router.push('/student/home');
};

const goNext = () => {
  if (needsAnswerCommit.value) {
    show('请先提交本题答题记录后再进入下一题', 'info');
    return;
  }
  if (currentIndex.value < questions.value.length - 1) selectQuestion(questions.value[currentIndex.value + 1]);
};

const buildPendingResult = (question: QuestionResponse, userAnswer: string): PendingAnswerResult => ({
  requestId: crypto.randomUUID(),
  questionId: question.id,
  userAnswer,
});

const applyCommittedResult = (result: AnswerRecordResponse) => {
  doneMap.value[result.questionId] = result;
  submitResult.value = result;
  pendingAnswer.value = null;
  selectedOption.value = '';
  saveSuccessQuestionId.value = result.questionId;
  saveSuccessMessage.value = '本题答案已提交成功。';
};

const activeTeachingEventId = ref<number | null>(null);

const persistAnswerRecord = async (payload: AnswerRecordSubmitPayload) => {
  const result = await submitAnswerApi(payload);
  applyCommittedResult(result);
  persistQuestionViewState();
  return result;
};

const submitPendingAnswer = async () => {
  if (!currentQuestion.value || !currentPendingAnswer.value || isPersistingAnswer.value) return;

  isPersistingAnswer.value = true;
  try {
    if(!currentPendingAnswer.value.submission) {
      pendingAnswer.value={...currentPendingAnswer.value,submission:{
        eventId:activeTeachingEventId.value ?? undefined,
        requestId:currentPendingAnswer.value.requestId,
        intent:isPractice.value ? 'PRACTICE' : 'ASSIGNMENT',
        questionId:currentQuestion.value.id,userAnswer:currentPendingAnswer.value.userAnswer,
      }};
      // Persist the exact body before I/O; an unknown network result must retry it unchanged.
      persistQuestionViewState();
    }
    await persistAnswerRecord(pendingAnswer.value!.submission!);
    show('本题答案已提交成功', 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '提交失败，请重试', 'error');
  } finally {
    isPersistingAnswer.value = false;
  }
};

const parseQuestionIds = (description: string | null): number[] => {
  if (!description) return [];
  const normalized = description.trim();
  if (!normalized.startsWith('QIDS:')) return [];
  return normalized
    .slice(5)
    .split(/[，,]/)
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isInteger(id) && id > 0);
};

const selectQuestion = (q: QuestionResponse) => {
  if (needsAnswerCommit.value) {
    show('请先提交本题答题记录后再切换题目', 'info');
    return;
  }
  currentQuestion.value = q;
  selectedOption.value = '';
  submitResult.value = doneMap.value[q.id] ?? null;
  if (saveSuccessQuestionId.value !== q.id) {
    saveSuccessMessage.value = '';
  }
  persistQuestionViewState();
};

onMounted(async () => {
  try {
    const freePractice = isPractice.value && !route.query.eventId;
    let eventData: any;
    if (freePractice) {
      eventData = { id: 0, eventTitle: '自由补练（当前题库）', eventDescription: null, questionIds: [Number(route.query.questionId)] };
    } else if (isPractice.value && route.query.eventId) {
      eventData = await getTaskApi(Number(route.query.eventId));
    } else {
      const available = await getStudentTasksApi();
      const entered = sessionStorage.getItem(getTaskEntryEventIdKey());
      eventData = available.find(task => String(task.id) === entered && task.canAnswer)
        ?? available.find(task => task.canAnswer)
        ?? null;
    }
    const [questionResult, recordResult] = await Promise.all([
      freePractice ? getQuestionByIdApi(Number(route.query.questionId)).then(q => [q]) : eventData ? getTaskQuestionsApi(eventData.id) : Promise.resolve([]),
      eventData && !freePractice ? getTaskAnswersApi(eventData.id) : Promise.resolve({ records: [] }),
    ]);

    if (!eventData) {
      show('教师暂未发布任务，无法进入答题', 'info');
      questions.value = [];
      return;
    }

    activeTeachingEventId.value = freePractice ? null : eventData.id;
    const currentTaskEventId = String(eventData.id ?? '');
    const questionTaskEventIdKey = getQuestionTaskEventIdKey();
    const previousTaskEventId = sessionStorage.getItem(questionTaskEventIdKey);
    if (currentTaskEventId && previousTaskEventId !== currentTaskEventId) {
      resetSessionId();
      doneMap.value = {};
      pendingAnswer.value = null;
      selectedOption.value = '';
      submitResult.value = null;
      sessionStorage.setItem(questionTaskEventIdKey, currentTaskEventId);
    }

    activeTaskTitle.value = eventData.eventTitle;
    const orderedIds: number[] = eventData.questionIds ?? parseQuestionIds(eventData.eventDescription ?? null);
    if (orderedIds.length === 0) {
      show('当前任务未配置题目，请联系教师重新发布', 'error');
      questions.value = [];
      return;
    }

    const allQuestions = Array.isArray(questionResult) ? questionResult : [];
    const questionMap = new Map<number, QuestionResponse>(allQuestions.map((q: QuestionResponse) => [q.id, q]));
    questions.value = orderedIds
      .map((id) => questionMap.get(id))
      .filter((q): q is QuestionResponse => Boolean(q));

    const map: Record<number, AnswerRecordResponse> = {};
    for (const rec of (recordResult.records ?? []).filter(rec => !rec.practice)) {
      const matched = questions.value.find((q) => q.id === rec.questionId && rec.eventId === eventData.id);
      if (matched && !(matched.id in map)) {
        map[matched.id] = rec;
      }
    }
    doneMap.value = isPractice.value ? {} : map;

    restoreQuestionViewState(recordResult.records ?? []);
    if (isPractice.value) currentQuestion.value = questions.value.find(q => q.id === Number(route.query.questionId)) ?? questions.value[0] ?? null;
    currentQuestion.value = currentQuestion.value ?? questions.value[0] ?? null;
    if (currentQuestion.value && doneMap.value[currentQuestion.value.id]) {
      submitResult.value = doneMap.value[currentQuestion.value.id];
    }
    if (questions.value.length === 0) {
      show('当前任务题目不存在，请联系教师调整题目配置', 'error');
    }
  } catch (error: any) {
    show(error?.response?.data?.message ?? '题目加载失败，请刷新重试', 'error');
  }
});

const restartPractice = () => {
  if (!currentQuestion.value) return;
  delete doneMap.value[currentQuestion.value.id];pendingAnswer.value=null;submitResult.value=null;selectedOption.value='';clearQuestionViewState();
};

const submitAnswer = async () => {
  if (!currentQuestion.value || !selectedOption.value) return;
  if (doneMap.value[currentQuestion.value.id]) {
    show('此题已作答，不可重复提交', 'error');
    return;
  }
  if (currentPendingAnswer.value) {
    show('请先提交本题答题记录', 'info');
    return;
  }

  pendingAnswer.value = buildPendingResult(currentQuestion.value, selectedOption.value);
  submitResult.value = null;
  persistQuestionViewState();
  await submitPendingAnswer();
};
</script>

<style scoped>
.q-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.q-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 16px;
  background: rgba(17, 21, 40, 0.7);
  border: 1px solid var(--line);
  border-radius: 14px;
  backdrop-filter: blur(8px);
}

.q-task-label {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
}

.q-dots {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  flex-wrap: wrap;
}

.q-dot {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1.5px solid rgba(151, 172, 249, 0.35);
  background: rgba(255,255,255,0.06);
  cursor: pointer;
  padding: 0;
  transition: all 0.18s ease;
  flex-shrink: 0;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.q-dot:hover {
  border-color: rgba(90, 243, 211, 0.6);
  transform: scale(1.1);
  color: var(--text);
}

.q-dot:disabled {
  cursor: not-allowed;
}

.q-dot.locked {
  opacity: 0.46;
  transform: none;
}

.q-dot.active {
  border-color: var(--brand);
  background: var(--brand);
  color: #061016;
  box-shadow: 0 0 10px rgba(90, 243, 211, 0.5);
  transform: scale(1.15);
}

.q-dot.correct {
  border-color: rgba(68, 232, 153, 0.8);
  background: rgba(68, 232, 153, 0.7);
  color: #061a10;
}

.q-dot.wrong {
  border-color: rgba(255, 107, 145, 0.8);
  background: rgba(255, 107, 145, 0.7);
  color: #1a0610;
}

.q-progress {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.q-body {
  flex: 1;
  background: linear-gradient(160deg, rgba(17, 21, 40, 0.82), rgba(22, 27, 52, 0.78));
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow-y: auto;
  animation: fadeUp 0.28s ease;
}

.q-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.q-index {
  font-size: var(--font-size-sm);
  color: var(--brand);
  font-weight: 700;
  letter-spacing: 0.5px;
  opacity: 0.8;
}

.question-text {
  font-size: 22px;
  line-height: 1.9;
  color: rgba(245, 247, 255, 0.98);
}

.question-image-panel {
  margin-top: -6px;
  padding: 14px;
  border-radius: 20px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(151, 172, 249, 0.05));
}

.question-image {
  display: block;
  width: 100%;
  max-height: 360px;
  object-fit: contain;
  border-radius: 14px;
  background: rgba(4, 8, 20, 0.55);
}

.q-nav-row,
.q-submit-row {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.q-action-row {
  justify-content: flex-end;
}

.q-submit-row-pending {
  justify-content: flex-end;
}

.q-save-feedback {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(90, 243, 211, 0.35);
  background: linear-gradient(180deg, rgba(31, 84, 78, 0.26), rgba(13, 37, 35, 0.34));
  color: #92f5e1;
  font-size: var(--font-size-md);
  font-weight: 700;
  box-shadow: 0 12px 28px rgba(37, 152, 132, 0.14);
}

.q-submit-row .neon-btn.primary {
  flex: 1;
}

.opt-correct {
  border-color: rgba(68, 232, 153, 0.7) !important;
  background: rgba(68, 232, 153, 0.1) !important;
}

.opt-wrong {
  border-color: rgba(255, 107, 145, 0.7) !important;
  background: rgba(255, 107, 145, 0.1) !important;
}

.finish-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  border-radius: 14px;
  border: 1px solid rgba(68, 232, 153, 0.35);
  background: linear-gradient(135deg, rgba(14, 22, 38, 0.97), rgba(10, 18, 32, 0.98));
  box-shadow: 0 -4px 24px rgba(68, 232, 153, 0.1);
  animation: fadeUp 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.finish-bar-text {
  font-size: 13px;
  font-weight: 700;
  color: rgba(68, 232, 153, 0.95);
  white-space: nowrap;
}

.finish-bar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.finish-pop-enter-active {
  transition: opacity 0.3s ease, transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.finish-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.finish-pop-enter-from,
.finish-pop-leave-to {
  opacity: 0;
  transform: translateY(16px) scale(0.92);
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}
</style>
