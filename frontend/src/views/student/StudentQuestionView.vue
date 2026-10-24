<template>
  <AppShell hide-heading :subtitle="`学生答题 · ${auth.user?.realName ?? ''}`">
    <div class="q-page">
      <header class="sw-header">
        <div>
          <h1>{{ isPractice ? '巩固练习' : '作业练习' }}</h1>
          <p>{{ activeTaskTitle || '准备好，一次专注解决一道题。' }}</p>
        </div>
        <button class="sw-button" :disabled="!questions.length" @click="paletteOpen = true">
          <AppIcon name="questions" />答题卡
          <span class="sw-count">{{ doneCount }} / {{ questions.length }}</span>
        </button>
      </header>
      <section class="question-session-progress" aria-label="答题进度">
        <div>
          <span
            >第 {{ currentQuestion ? currentIndex + 1 : '—' }} 题 / 共
            {{ questions.length }} 题</span
          ><strong>已完成 {{ doneCount }} 题</strong>
        </div>
        <progress
          class="sw-progress"
          :value="doneCount"
          :max="questions.length || 1"
          aria-label="本次练习完成进度"
        />
      </section>
      <AppDialog :open="paletteOpen" title="答题卡" @close="paletteOpen = false"
        ><p class="palette-copy">点击题号跳转。每题提交后保存作答进度。</p>
        <div class="palette-legend">
          <span><i class="current"></i>当前题</span><span><i class="correct"></i>已答对</span
          ><span><i class="wrong"></i>已答错</span><span><i></i>未作答</span>
        </div>
        <p v-if="needsAnswerCommit" class="sw-error">请先保存当前题目的作答记录，再切换题目。</p>
        <nav class="question-palette" aria-label="题号导航">
          <button
            v-for="(q, index) in questions"
            :key="q.id"
            type="button"
            class="palette-number"
            :class="{
              current: currentQuestion?.id === q.id,
              correct: doneMap[q.id]?.correct === true,
              wrong: doneMap[q.id]?.correct === false,
            }"
            :aria-label="`第 ${index + 1} 题，${doneMap[q.id] ? (doneMap[q.id]!.correct ? '已答对' : '已答错') : '未作答'}`"
            :aria-current="currentQuestion?.id === q.id ? 'step' : undefined"
            :disabled="(needsAnswerCommit || isPersistingAnswer) && currentQuestion?.id !== q.id"
            @click="selectFromPalette(q)"
          >
            {{ index + 1 }}
          </button>
        </nav>
        <div class="sw-dialog-footer">
          <button class="sw-button" @click="paletteOpen = false">返回当前题目</button>
        </div></AppDialog
      >
      <!-- 主体：答题区 -->
      <div class="q-body">
        <div v-if="questionsLoading" class="sw-empty" role="status">正在加载题目与已保存进度…</div>
        <div v-else-if="questionError" class="sw-empty" role="alert">
          <strong>暂时无法打开练习</strong>{{ questionError }}
          <div class="sw-actions">
            <button class="sw-button" @click="loadQuestions">重新加载</button
            ><RouterLink to="/student/task" class="sw-button">返回我的作业</RouterLink>
          </div>
        </div>
        <div v-else-if="!currentQuestion" class="sw-empty">
          暂无可作答题目，可返回作业列表查看其他安排。
        </div>
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
                  'opt-wrong':
                    String(key) === currentCommittedResult.userAnswer &&
                    !currentCommittedResult.correct,
                }"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </div>
            </div>
            <div class="result-box" :class="currentCommittedResult.correct ? 'ok' : 'fail'">
              <p>{{ currentCommittedResult.correct ? '✓ 回答正确！' : '✗ 回答错误' }}</p>
              <p>
                你的答案：{{ currentCommittedResult.userAnswer }} · 正确答案：{{
                  currentCommittedResult.correctAnswer
                }}
              </p>
              <p v-if="currentCommittedResult.questionAnalysis">
                解析：<LatexText :content="currentCommittedResult.questionAnalysis!" />
              </p>
            </div>
            <div v-if="!allDone || isPractice" class="q-nav-row q-action-row">
              <button v-if="isPractice" type="button" class="sw-button" @click="restartPractice">
                再次补练</button
              ><button
                v-if="!isPractice && !allDone && currentIndex < questions.length - 1"
                class="sw-button primary"
                type="button"
                @click="goNext"
              >
                下一题 <AppIcon name="arrow" /></button
              ><button
                v-else-if="!isPractice && !allDone"
                class="sw-button primary"
                type="button"
                @click="goUnanswered"
              >
                继续未答题 <AppIcon name="arrow" />
              </button>
              <button
                v-if="isPractice"
                type="button"
                class="sw-button primary"
                @click="finishAndExit"
              >
                完成本题，返回错题本
              </button>
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
              <button
                class="sw-button primary"
                :disabled="isPersistingAnswer"
                @click="submitPendingAnswer"
              >
                {{ isPersistingAnswer ? '提交中…' : '重试保存作答' }}
              </button>
            </div>
          </template>

          <!-- 未作答 -->
          <template v-else>
            <fieldset class="option-list question-options">
              <legend class="sr-only">选择本题答案</legend>
              <label
                v-for="(text, key) in currentQuestion.options"
                :key="key"
                class="option-row"
                :class="{ selected: selectedOption === String(key) }"
              >
                <input
                  v-model="selectedOption"
                  class="question-radio"
                  type="radio"
                  :name="`answer-${currentQuestion.id}`"
                  :value="String(key)"
                />
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </label>
            </fieldset>
            <div class="q-submit-row">
              <button
                class="sw-button primary"
                :disabled="!selectedOption || isPersistingAnswer"
                @click="submitAnswer"
              >
                提交答案
              </button>
            </div>
          </template>
        </template>
      </div>
    </div>

    <!-- 底部完成条 -->
    <transition name="finish-pop">
      <div v-if="allDone && !isPractice" class="finish-bar">
        <span class="finish-bar-text">全部题目已完成</span>
        <div class="finish-bar-actions">
          <button
            v-if="!isPractice && activeTeachingEventId"
            class="sw-button primary"
            @click="openReview"
          >
            查看本次结果与复盘
          </button>
          <button class="sw-button" :class="{ primary: isPractice }" @click="finishAndExit">
            {{ isPractice ? '完成补练，返回错题本' : '返回首页' }}
          </button>
        </div>
      </div>
    </transition>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import '@/assets/student-workspace.css';
import AppDialog from '@/components/AppDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router';
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
import { createRequestId } from '@/utils/requestId';
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
const getTaskEntryEventIdKey = () =>
  TASK_ENTRY_EVENT_ID_KEY_PREFIX + '_' + (auth.user?.id ?? 'anonymous');
const route = useRoute();
const router = useRouter();
const isPractice = computed(() => route.path === '/student/practice');

const getQuestionViewStateKey = () =>
  `${QUESTION_VIEW_STATE_KEY_PREFIX}_v2_${auth.user?.id ?? 'anonymous'}_${activeTeachingEventId.value ?? 'free'}_${isPractice.value ? `practice_${route.query.questionId ?? 'first'}` : 'assignment'}`;
const getQuestionTaskEventIdKey = () =>
  `${QUESTION_TASK_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}_${isPractice.value ? 'practice' : 'assignment'}`;

const questions = ref<QuestionResponse[]>([]);
const currentQuestion = ref<QuestionResponse | null>(null);
const paletteOpen = ref(false),
  questionsLoading = ref(true),
  questionError = ref('');
let alive = true,
  loadSequence = 0;
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
const allDone = computed(
  () => questions.value.length > 0 && doneCount.value === questions.value.length,
);
const currentIndex = computed(() =>
  questions.value.findIndex((q) => q.id === currentQuestion.value?.id),
);
const currentPendingAnswer = computed(() =>
  currentQuestion.value
    ? pendingAnswer.value?.questionId === currentQuestion.value.id
      ? pendingAnswer.value
      : null
    : null,
);
const currentCommittedResult = computed(() =>
  currentQuestion.value ? (doneMap.value[currentQuestion.value.id] ?? null) : null,
);
const shouldShowSaveSuccess = computed(() =>
  Boolean(
    currentQuestion.value &&
      saveSuccessQuestionId.value === currentQuestion.value.id &&
      saveSuccessMessage.value,
  ),
);
const needsAnswerCommit = computed(() => Boolean(currentPendingAnswer.value));

const persistQuestionViewState = () => {
  const currentQuestionId = currentQuestion.value?.id ?? null;
  const pending = pendingAnswer.value;
  sessionStorage.setItem(
    getQuestionViewStateKey(),
    JSON.stringify({
      currentQuestionId,
      selectedOption: selectedOption.value,
      pendingAnswer: pending,
      activeTaskTitle: activeTaskTitle.value,
    }),
  );
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
    const pending = pendingAnswer.value;
    if (pending) {
      const recovered = serverRecords.find(
        (r) =>
          r.requestId === pending.requestId &&
          r.questionId === pending.questionId &&
          r.eventId === activeTeachingEventId.value &&
          Boolean(r.practice) === isPractice.value,
      );
      if (recovered) {
        doneMap.value[recovered.questionId] = recovered;
        pendingAnswer.value = null;
        selectedOption.value = '';
      } else if (!isPractice.value && doneMap.value[pending.questionId]) {
        pendingAnswer.value = null;
        selectedOption.value = '';
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
  router.push(isPractice.value ? '/student/wrong-book' : '/student/home');
};

const openReview = () => {
  if (needsAnswerCommit.value || !activeTeachingEventId.value) return;
  clearQuestionViewState();
  router.push({ path: '/student/report', query: { eventId: activeTeachingEventId.value } });
};

const goNext = () => {
  if (needsAnswerCommit.value) {
    show('请先提交本题答题记录后再进入下一题', 'info');
    return;
  }
  if (currentIndex.value < questions.value.length - 1)
    selectQuestion(questions.value[currentIndex.value + 1]);
};

const buildPendingResult = (
  question: QuestionResponse,
  userAnswer: string,
): PendingAnswerResult => ({
  requestId: createRequestId(),
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
  if (!alive) return result;
  applyCommittedResult(result);
  persistQuestionViewState();
  return result;
};

const submitPendingAnswer = async () => {
  if (!currentQuestion.value || !currentPendingAnswer.value || isPersistingAnswer.value) return;

  isPersistingAnswer.value = true;
  try {
    if (!currentPendingAnswer.value.submission) {
      pendingAnswer.value = {
        ...currentPendingAnswer.value,
        submission: {
          eventId: activeTeachingEventId.value ?? undefined,
          requestId: currentPendingAnswer.value.requestId,
          intent: isPractice.value ? 'PRACTICE' : 'ASSIGNMENT',
          questionId: currentQuestion.value.id,
          userAnswer: currentPendingAnswer.value.userAnswer,
        },
      };
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

async function loadQuestions() {
  if (isPersistingAnswer.value) return;
  const owner = ++loadSequence;
  questionsLoading.value = true;
  questionError.value = '';
  questions.value = [];
  currentQuestion.value = null;
  doneMap.value = {};
  pendingAnswer.value = null;
  selectedOption.value = '';
  activeTaskTitle.value = '';
  paletteOpen.value = false;

  try {
    const freePractice = isPractice.value && !route.query.eventId;
    let eventData: any;
    if (freePractice) {
      eventData = {
        id: 0,
        eventTitle: '自由补练（当前题库）',
        eventDescription: null,
        questionIds: [Number(route.query.questionId)],
      };
    } else if (isPractice.value && route.query.eventId) {
      eventData = await getTaskApi(Number(route.query.eventId));
    } else {
      const available = await getStudentTasksApi();
      const entered = sessionStorage.getItem(getTaskEntryEventIdKey());
      eventData = route.query.eventId
        ? (available.find(
            (task) => String(task.id) === String(route.query.eventId) && task.canAnswer,
          ) ?? null)
        : (available.find((task) => String(task.id) === entered && task.canAnswer) ??
          available.find((task) => task.canAnswer) ??
          null);
    }
    if (!alive || owner !== loadSequence) return;
    const [questionResult, recordResult] = await Promise.all([
      freePractice
        ? getQuestionByIdApi(Number(route.query.questionId)).then((q) => [q])
        : eventData
          ? getTaskQuestionsApi(eventData.id)
          : Promise.resolve([]),
      eventData && !freePractice
        ? getTaskAnswersApi(eventData.id)
        : Promise.resolve({ records: [] }),
    ]);

    if (!alive || owner !== loadSequence) return;
    if (!eventData) {
      questionError.value = '当前没有可作答的教师任务，请返回作业列表。';
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
    const orderedIds: number[] =
      eventData.questionIds ?? parseQuestionIds(eventData.eventDescription ?? null);
    if (orderedIds.length === 0) {
      questionError.value = '当前任务未配置题目，请联系教师。';
      questions.value = [];
      return;
    }

    const allQuestions = Array.isArray(questionResult) ? questionResult : [];
    const questionMap = new Map<number, QuestionResponse>(
      allQuestions.map((q: QuestionResponse) => [q.id, q]),
    );
    questions.value = orderedIds
      .map((id) => questionMap.get(id))
      .filter((q): q is QuestionResponse => Boolean(q));

    const map: Record<number, AnswerRecordResponse> = {};
    for (const rec of (recordResult.records ?? []).filter((rec) => !rec.practice)) {
      const matched = questions.value.find(
        (q) => q.id === rec.questionId && rec.eventId === eventData.id,
      );
      if (matched && !(matched.id in map)) {
        map[matched.id] = rec;
      }
    }
    doneMap.value = isPractice.value ? {} : map;

    restoreQuestionViewState(recordResult.records ?? []);
    if (pendingAnswer.value) {
      const pendingQuestion = questions.value.find((q) => q.id === pendingAnswer.value?.questionId);
      if (!pendingQuestion) {
        questionError.value = '尚未保存的作答对应题目暂不可用，请重试加载；本地作答已保留。';
        return;
      }
      currentQuestion.value = pendingQuestion;
    } else if (isPractice.value) {
      const requested = Number(route.query.questionId);
      if (route.query.questionId && !questions.value.some((q) => q.id === requested)) {
        questionError.value = '这道题不属于当前练习，请返回原入口重新选择。';
        return;
      }
      currentQuestion.value =
        currentQuestion.value ??
        questions.value.find((q) => q.id === requested) ??
        questions.value[0] ??
        null;
    }
    if (!isPractice.value && route.query.resume === '1') {
      // A retry must stay on its original question until the answer is saved.
      currentQuestion.value = pendingAnswer.value
        ? (questions.value.find((q) => q.id === pendingAnswer.value?.questionId) ??
          currentQuestion.value)
        : (questions.value.find((q) => !doneMap.value[q.id]) ?? questions.value[0] ?? null);
      if (!pendingAnswer.value) selectedOption.value = '';
    }
    if (!isPractice.value && !pendingAnswer.value && route.query.questionId) {
      const requested = questions.value.find((q) => q.id === Number(route.query.questionId));
      if (requested) currentQuestion.value = requested;
    }
    currentQuestion.value = currentQuestion.value ?? questions.value[0] ?? null;
    submitResult.value = currentQuestion.value
      ? (doneMap.value[currentQuestion.value.id] ?? null)
      : null;
    if (route.query.resume === '1') {
      persistQuestionViewState();
      const { resume, ...query } = route.query;
      await router.replace({ path: route.path, query });
    }
    if (questions.value.length === 0) {
      questionError.value = '当前任务的题目暂时无法读取，请联系教师。';
    }
  } catch (error: any) {
    if (alive && owner === loadSequence)
      questionError.value = error?.response?.data?.message ?? '题目加载失败，请稍后重试。';
  } finally {
    if (alive && owner === loadSequence) questionsLoading.value = false;
  }
}
onMounted(loadQuestions);
watch(
  [() => route.path, () => route.query.eventId, () => route.query.questionId],
  () => void loadQuestions(),
);
onBeforeRouteUpdate((to, from) => {
  const contextChanged =
    to.path !== from.path ||
    to.query.eventId !== from.query.eventId ||
    to.query.questionId !== from.query.questionId;
  if (contextChanged && (needsAnswerCommit.value || isPersistingAnswer.value)) {
    show('请先保存当前作答，再切换练习。', 'info');
    return false;
  }
});
onBeforeUnmount(() => {
  alive = false;
  ++loadSequence;
});
function selectFromPalette(question: QuestionResponse) {
  if (needsAnswerCommit.value || isPersistingAnswer.value) return;
  selectQuestion(question);
  paletteOpen.value = false;
}
function goUnanswered() {
  const question = questions.value.find((q) => !doneMap.value[q.id]);
  if (question) selectQuestion(question);
}

const restartPractice = () => {
  if (!currentQuestion.value) return;
  delete doneMap.value[currentQuestion.value.id];
  pendingAnswer.value = null;
  submitResult.value = null;
  selectedOption.value = '';
  clearQuestionViewState();
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
  display: grid;
  gap: 22px;
  min-width: 0;
}
.question-session-progress {
  padding: 18px 22px;
  border: 1px solid #e0e9e2;
  border-radius: 10px;
  background: white;
}
.question-session-progress > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 11px;
  font-size: 12px;
  color: #83988b;
}
.question-session-progress strong {
  font-weight: 400;
  color: #5d8468;
}
.q-body {
  padding: 32px;
  background: white;
  border: 1px solid #e0e8e2;
  border-radius: 14px;
  display: grid;
  gap: 22px;
  min-width: 0;
}
.q-index {
  font-size: 12px;
  color: #93a698;
}
.question-text {
  font-size: 18px;
  line-height: 1.95;
  overflow-wrap: anywhere;
  color: #3e5b46;
}
.question-image-panel {
  padding: 16px;
  border: 1px solid #e5ede7;
  border-radius: 10px;
  background: #f9fbf9;
}
.question-image {
  display: block;
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  margin: 0 auto;
}
.option-list {
  display: grid;
  gap: 12px;
}
.option-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid #e0e9e3;
  border-radius: 9px;
  background: white;
  font-size: 14px;
  line-height: 1.9;
  color: #5c7865;
  overflow-wrap: anywhere;
}
.question-options .option-row {
  cursor: pointer;
}
.option-row.selected {
  background: #f3f8f4;
  border-color: #9fc5ac;
}
.option-row:focus-within {
  outline: 2px solid #80a88d;
  outline-offset: 2px;
}
.option-key {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border: 1px solid #dfebe2;
  border-radius: 7px;
  font-size: 12px;
  color: #7d9c86;
}
.option-content {
  min-width: 0;
}
.question-radio {
  position: absolute;
  width: 1px;
  height: 1px;
  clip-path: inset(50%);
  overflow: hidden;
  white-space: nowrap;
}
.question-options {
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}
.opt-correct {
  border-color: #adcfb8;
  background: #f3f9f5;
}
.opt-wrong {
  border-color: #e7cdbd;
  background: #fff8f3;
}
.result-box {
  padding: 18px 20px;
  border-radius: 9px;
  background: #faf8f1;
  border: 1px solid #ece4d3;
  color: #9a865e;
  font-size: 13px;
  line-height: 1.9;
}
.result-box.ok {
  background: #f3f8f4;
  border-color: #d6e6db;
  color: #6d9279;
}
.result-box.fail {
  background: #fcf7f3;
  border-color: #ecdccf;
  color: #a4826b;
}
.result-box p {
  margin: 0 0 8px;
}
.result-box p:last-child {
  margin-bottom: 0;
}
.q-action-row,
.q-submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
.q-save-feedback {
  font-size: 12px;
  color: #7d9f87;
}
.finish-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px;
  background: #f2f8f4;
  border: 1px solid #d9e8dd;
  border-radius: 12px;
}
.finish-bar-text {
  font-size: 14px;
  color: #6f947a;
}
.finish-bar-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.palette-copy {
  font-size: 13px;
  color: #809488;
  line-height: 1.8;
  margin: 0 0 20px;
}
.palette-legend {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 22px;
  font-size: 11px;
  color: #8ea193;
}
.palette-legend > span {
  display: flex;
  gap: 6px;
  align-items: center;
}
.palette-legend i {
  width: 9px;
  height: 9px;
  border: 1px solid #d3e1d7;
  border-radius: 3px;
}
.palette-legend .correct {
  background: #d6eadc;
}
.palette-legend .wrong {
  background: #f2ddca;
}
.palette-legend .current {
  border-color: #48815a;
  background: #edf6ef;
}
.question-palette {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(44px, 1fr));
  gap: 10px;
  max-height: 50dvh;
  overflow: auto;
  padding: 4px;
}
.palette-number {
  height: 42px;
  border: 1px solid #dbe6df;
  border-radius: 7px;
  background: white;
  color: #80998a;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.palette-number.correct {
  background: #edf7f0;
  border-color: #c5dfcd;
  color: #5d896a;
}
.palette-number.wrong {
  background: #fcf4eb;
  border-color: #ead5bc;
  color: #a38a65;
}
.palette-number.current {
  outline: 2px solid #659d76;
  outline-offset: 1px;
}
.palette-number:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
@media (max-width: 640px) {
  .q-page {
    gap: 18px;
  }
  .q-body {
    padding: 22px 16px;
    gap: 19px;
  }
  .question-text {
    font-size: 16px;
  }
  .option-row {
    padding: 13px 12px;
    font-size: 13px;
    gap: 10px;
  }
  .question-image-panel {
    padding: 10px;
  }
  .question-session-progress {
    padding: 16px;
  }
  .question-session-progress > div {
    font-size: 11px;
  }
  .sw-header h1 {
    font-size: 22px;
  }
  .sw-header > .sw-button {
    gap: 4px;
    font-size: 11px;
    padding: 9px;
  }
  .sw-header > .sw-button .app-icon {
    display: none;
  }
  .sw-count {
    margin-left: 3px;
  }
  .q-submit-row > .sw-button {
    width: 100%;
  }
  .finish-bar {
    flex-direction: column;
    align-items: stretch;
    padding: 20px;
  }
  .finish-bar-actions {
    justify-content: flex-end;
  }
  .finish-bar-actions .sw-button {
    font-size: 12px;
  }
}
</style>
