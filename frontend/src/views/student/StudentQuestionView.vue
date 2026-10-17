<template>
  <AppShell :subtitle="`学生答题 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="q-page">
      <transition name="wrong-video-modal">
        <div v-if="showWrongVideoModal" class="q-wrong-video-modal" @click.self="closeWrongVideoModal">
          <div class="q-wrong-video-dialog">
            <button class="q-wrong-video-close" type="button" @click="closeWrongVideoModal">×</button>
            <div class="q-wrong-video-head">
              <span class="q-wrong-video-badge">讲解视频</span>
              <h3>进入对应讲解视频</h3>
              <p class="q-wrong-video-tip">系统会根据你当前选择的选项，跳转到老师为该选项绑定的讲解视频。</p>
            </div>
            <button
              class="q-wrong-video-link"
              type="button"
              @click="openWrongVideo"
            >前往对应讲解视频</button>
          </div>
        </div>
      </transition>

      <!-- 顶部：任务标题 + 圆点导航 -->
      <p v-if="currentQuestion?.snapshotOrigin && currentQuestion.snapshotOrigin !== 'PUBLISHED'" role="note">历史内容在升级时重建；原题缺失的条目无法补练。</p>
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
                v-if="currentCommittedResult"
                class="neon-btn ghost"
                type="button"
                @click="playQuestionVideo(currentQuestion, currentCommittedResult.userAnswer)"
              >查看讲解视频</button>
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
            <div v-if="shouldShowCommittedWrongReason" class="q-reason-saved-tip">
              错因已保存，可继续查看 AI 解惑。
            </div>
            <WrongAnalysisPanel
              :question-id="currentQuestion.id"
              :initial-error-reason="normalizedCommittedErrorReason"
            />
          </template>

          <template v-else-if="currentPendingAnswer">
            <div class="option-list">
              <div
                v-for="(text, key) in currentQuestion.options"
                :key="key"
                class="option-row"
                :class="{
                  'opt-correct': String(key) === currentPendingAnswer.correctAnswer,
                  'opt-wrong': String(key) === currentPendingAnswer.userAnswer
                    && !currentPendingAnswer.correct,
                }"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-content"><LatexText :content="text" /></span>
              </div>
            </div>
            <div class="result-box" :class="currentPendingAnswer.correct ? 'ok' : 'fail'">
              <p>{{ currentPendingAnswer.correct ? '✓ 回答正确！' : '✗ 回答错误' }}</p>
              <p>你的答案：{{ currentPendingAnswer.userAnswer }} · 正确答案：{{ currentPendingAnswer.correctAnswer }}</p>
              <p v-if="currentPendingAnswer.questionAnalysis">解析：<LatexText :content="currentPendingAnswer.questionAnalysis" /></p>
            </div>
            <div class="q-nav-row q-action-row q-action-row-pending">
              <button
                class="neon-btn ghost"
                type="button"
                @click="playQuestionVideo(currentQuestion, currentPendingAnswer.userAnswer)"
              >查看讲解视频</button>
              <WrongAnalysisPanel
                v-if="currentPendingAnswer.correct"
                :question-id="currentQuestion.id"
                :initial-error-reason="''"
              />
            </div>
            <div v-if="shouldShowPendingWrongReason" class="q-reason-box post-submit">
              <div class="q-reason-head">
                <span class="q-reason-title">说说你为什么会选错</span>
                <span class="q-reason-hint">必须先提交或放弃错因，系统才会保存答题记录并开放 AI 分析</span>
              </div>
              <textarea
                v-model="errorReason"
                class="q-reason-input"
                rows="4"
                placeholder="例如：我把受力方向看反了、把位移和路程混了、忽略了题目里的限制条件……"
              />
              <div class="q-reason-actions">
                <button class="neon-btn ghost" :disabled="isPersistingAnswer" @click="skipErrorReasonAndSubmit">放弃填写</button>

              <button class="neon-btn primary" :disabled="isPersistingAnswer" @click="submitPendingAnswer">{{ isPersistingAnswer ? '提交中…' : '提交并保存' }}</button>
              </div>
            </div>
            <div v-else class="q-submit-row q-submit-row-pending">
              <button class="neon-btn primary" :disabled="isPersistingAnswer" @click="goNextQuestionAfterCommit">{{ currentIndex < questions.length - 1 ? '下一题' : '完成本题' }}</button>
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
        <span class="finish-bar-text">🎉 全部题目已完成！</span>
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
import { getAllVideosApi } from '@/api/video';
import { submitAnswerApi, getTaskAnswersApi } from '@/api/answer-record';
import { getQuestionByIdApi } from '@/api/question';
import { getTaskApi, getTaskQuestionsApi, getTeacherEventApi } from '@/api/teacher-event';
import { useToast } from '@/composables/useToast';
import WrongAnalysisPanel from '@/components/WrongAnalysisPanel.vue';
import AppToast from '@/components/AppToast.vue';
import { resetSessionId } from '@/utils/session';
import { resolveApiBaseUrl } from '@/utils/request';
import type { QuestionResponse } from '@/types/question';
import type { AnswerRecordResponse, AnswerRecordSubmitPayload } from '@/types/answer-record';
import type { VideoResponse } from '@/types/video';

const VIDEO_ROUTE_PATH = '/student/question/video';
const QUESTION_VIEW_STATE_KEY_PREFIX = 'student_question_view_state';
const QUESTION_TASK_EVENT_ID_KEY_PREFIX = 'student_question_task_event_id';
const QUESTION_VIEW_STATE_LEGACY_KEY = 'student_question_view_state';

interface PendingAnswerResult {
  requestId: string;
  questionId: number;
  userAnswer: string;
  correct: boolean;
  correctAnswer: string;
  questionAnalysis: string | null;
}

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const isPractice = computed(() => route.path === '/student/practice');

const getQuestionViewStateKey = () => `${QUESTION_VIEW_STATE_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;
const getQuestionTaskEventIdKey = () => `${QUESTION_TASK_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const toPlayableVideoUrl = (url: string) => {
  const value = url.trim();
  if (!value) return '';
  if (/^https?:\/\//i.test(value)) return value;

  const normalizedPath = value.startsWith('/') ? value : `/${value}`;
  const normalizedBase = resolveApiBaseUrl().trim().replace(/\/+$/, '');

  // 已经是带 API 前缀的相对路径时，不再重复拼接
  if (normalizedBase && normalizedPath.startsWith(`${normalizedBase}/`)) {
    return normalizedPath;
  }

  if (normalizedPath.startsWith('/api/')) {
    if (/^https?:\/\//i.test(normalizedBase)) {
      try {
        return `${new URL(normalizedBase).origin}${normalizedPath}`;
      } catch {
        return normalizedPath;
      }
    }
    return normalizedPath;
  }

  return `${normalizedBase}${normalizedPath}`;
};

const questions = ref<QuestionResponse[]>([]);
const videos = ref<VideoResponse[]>([]);
const currentQuestion = ref<QuestionResponse | null>(null);
const wrongVideoLink = ref('');
const showWrongVideoModal = ref(false);
const selectedOption = ref('');
const errorReason = ref('');
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
const shouldShowCommittedWrongReason = computed(() => Boolean(currentCommittedResult.value && !currentCommittedResult.value.correct));
const shouldShowPendingWrongReason = computed(() => Boolean(currentPendingAnswer.value && !currentPendingAnswer.value.correct));
const normalizedCommittedErrorReason = computed(() => {
  const raw = currentCommittedResult.value?.errorReason?.trim() ?? '';
  if (!raw || raw === '未填写') {
    return '';
  }
  return raw;
});
const shouldShowSaveSuccess = computed(() => Boolean(
  currentQuestion.value
  && saveSuccessQuestionId.value === currentQuestion.value.id
  && saveSuccessMessage.value
));
const needsAnswerCommit = computed(() => Boolean(currentPendingAnswer.value));

const persistQuestionViewState = () => {
  const currentQuestionId = currentQuestion.value?.id ?? null;
  const pending = pendingAnswer.value;
  const doneEntries = Object.entries(doneMap.value);
  sessionStorage.setItem(getQuestionViewStateKey(), JSON.stringify({
    currentQuestionId,
    selectedOption: selectedOption.value,
    errorReason: errorReason.value,
    pendingAnswer: pending,
    doneEntries,
    activeTaskTitle: activeTaskTitle.value,
  }));
};

const restoreQuestionViewState = () => {
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
      errorReason?: string;
      pendingAnswer?: PendingAnswerResult | null;
      doneEntries?: Array<[string, AnswerRecordResponse]>;
      activeTaskTitle?: string;
    };

    selectedOption.value = parsed.selectedOption ?? '';
    errorReason.value = parsed.errorReason ?? '';
    pendingAnswer.value = parsed.pendingAnswer ?? null;
    activeTaskTitle.value = parsed.activeTaskTitle ?? activeTaskTitle.value;

    if (Array.isArray(parsed.doneEntries)) {
      doneMap.value = Object.fromEntries(
        parsed.doneEntries.map(([key, value]) => [Number(key), value])
      ) as Record<number, AnswerRecordResponse>;
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

const closeWrongVideoModal = () => {
  showWrongVideoModal.value = false;
};

const openWrongVideo = () => {
  if (!wrongVideoLink.value) {
    show('未找到可跳转的视频链接', 'info');
    return;
  }

  const openPath = route.path.startsWith('/preview/') ? `/preview${VIDEO_ROUTE_PATH}` : VIDEO_ROUTE_PATH;
  persistQuestionViewState();
  router.push({
    path: openPath,
    query: {
      src: wrongVideoLink.value,
    },
  });

  closeWrongVideoModal();
};

const getWrongJumpVideo = (question: QuestionResponse | null, userAnswer: string) => {
  if (!question) return null;
  const videoId = question.explanationVideoMapping?.[userAnswer] ?? null;
  if (!videoId) return null;
  return videos.value.find((video) => video.id === videoId) ?? null;
};

const playQuestionVideo = (question: QuestionResponse | null, userAnswer: string) => {
  const matchedVideo = getWrongJumpVideo(question, userAnswer);
  if (!matchedVideo) {
    show('该选项暂未绑定讲解视频', 'info');
    return;
  }
  wrongVideoLink.value = toPlayableVideoUrl(matchedVideo.url);
  showWrongVideoModal.value = true;
  show(`已定位到选项 ${userAnswer} 对应讲解视频，请点击前往`, 'success');
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
  correct: userAnswer === question.correctOption,
  correctAnswer: question.correctOption,
  questionAnalysis: question.explanation ?? null,
});

const applyCommittedResult = (result: AnswerRecordResponse) => {
  doneMap.value[result.questionId] = result;
  submitResult.value = result;
  pendingAnswer.value = null;
  selectedOption.value = '';
  errorReason.value = result.correct ? '' : ((result.errorReason?.trim() && result.errorReason !== '未填写') ? result.errorReason : '');
  saveSuccessQuestionId.value = result.questionId;
  saveSuccessMessage.value = result.correct
    ? '本题答案已提交成功。'
    : '错因已提交并保存成功。';
};

const activeTeachingEventId = ref<number | null>(null);

const persistAnswerRecord = async (payload: AnswerRecordSubmitPayload) => {
  const result = await submitAnswerApi({ ...payload, eventId: activeTeachingEventId.value ?? undefined,
    requestId: pendingAnswer.value?.requestId ?? crypto.randomUUID(), intent: isPractice.value ? 'PRACTICE' : 'ASSIGNMENT' });
  applyCommittedResult(result);
  persistQuestionViewState();
  return result;
};

const submitPendingAnswer = async () => {
  if (!currentQuestion.value || !currentPendingAnswer.value || isPersistingAnswer.value) return;

  isPersistingAnswer.value = true;
  try {
    const result = await persistAnswerRecord({
      questionId: currentQuestion.value.id,
      userAnswer: currentPendingAnswer.value.userAnswer,
      errorReason: currentPendingAnswer.value.correct ? '' : (errorReason.value.trim() || ''),
    });

    show(result.correct ? '本题答案已提交成功' : '错因已提交并保存成功', 'success');

    if (!result.correct) {
      return;
    }
  } catch (error: any) {
    show(error?.response?.data?.message ?? '提交失败，请重试', 'error');
  } finally {
    isPersistingAnswer.value = false;
  }
};

const skipErrorReasonAndSubmit = async () => {
  errorReason.value = '未填写';
  await submitPendingAnswer();
};

const goNextQuestionAfterCommit = async () => {
  await submitPendingAnswer();
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
  errorReason.value = doneMap.value[q.id]?.correct ? '' : (doneMap.value[q.id]?.errorReason ?? '');
  if (saveSuccessQuestionId.value !== q.id) {
    saveSuccessMessage.value = '';
  }
  persistQuestionViewState();
};

onMounted(async () => {
  try {
    const freePractice = isPractice.value && !route.query.eventId;
    const eventData = freePractice
      ? { id: 0, eventTitle: '自由补练（当前题库）', eventDescription: null, questionIds: [Number(route.query.questionId)] }
      : isPractice.value && route.query.eventId
      ? await getTaskApi(Number(route.query.eventId)) : await getTeacherEventApi();
    const [questionResult, recordResult, videoResult] = await Promise.all([
      freePractice ? getQuestionByIdApi(Number(route.query.questionId)).then(q => [q]) : eventData ? getTaskQuestionsApi(eventData.id) : Promise.resolve([]),
      eventData && !freePractice ? getTaskAnswersApi(eventData.id) : Promise.resolve({ records: [] }),
      getAllVideosApi().catch(() => []),
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
      clearQuestionViewState();
      doneMap.value = {};
      pendingAnswer.value = null;
      selectedOption.value = '';
      errorReason.value = '';
      submitResult.value = null;
      sessionStorage.setItem(questionTaskEventIdKey, currentTaskEventId);
    }

    activeTaskTitle.value = eventData.eventTitle;
    videos.value = Array.isArray(videoResult) ? videoResult : [];
    const orderedIds = eventData.questionIds ?? parseQuestionIds(eventData.eventDescription ?? null);
    if (orderedIds.length === 0) {
      show('当前任务未配置题目，请联系教师重新发布', 'error');
      questions.value = [];
      return;
    }

    const allQuestions = Array.isArray(questionResult) ? questionResult : [];
    const questionMap = new Map(allQuestions.map((q) => [q.id, q]));
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

    if (!isPractice.value) restoreQuestionViewState();
    if (isPractice.value) currentQuestion.value = questions.value.find(q => q.id === Number(route.query.questionId)) ?? questions.value[0] ?? null;
    currentQuestion.value = currentQuestion.value ?? questions.value[0] ?? null;
    if (currentQuestion.value && doneMap.value[currentQuestion.value.id]) {
      submitResult.value = doneMap.value[currentQuestion.value.id];
      errorReason.value = doneMap.value[currentQuestion.value.id]?.correct
        ? ''
        : (doneMap.value[currentQuestion.value.id]?.errorReason ?? '');
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
  delete doneMap.value[currentQuestion.value.id];pendingAnswer.value=null;submitResult.value=null;selectedOption.value='';errorReason.value='';
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

  const localResult = buildPendingResult(currentQuestion.value, selectedOption.value);
  pendingAnswer.value = localResult;
  submitResult.value = null;
  errorReason.value = '';
  persistQuestionViewState();

  playQuestionVideo(currentQuestion.value, selectedOption.value);
  show(localResult.correct
    ? '已完成本地判题，可先查看讲解视频或直接提交本题，并可继续使用 AI 解惑'
    : '已根据当前选项定位讲解视频，观看后请填写错因并提交记录'
  );
};
</script>

<style scoped>
.q-wrong-video-modal {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(3, 8, 20, 0.78);
  backdrop-filter: blur(10px);
}

.q-wrong-video-dialog {
  position: relative;
  width: min(560px, 100%);
  padding: 24px;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: linear-gradient(180deg, rgba(13, 18, 36, 0.98), rgba(7, 11, 24, 0.98));
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.42);
}

.q-wrong-video-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.q-wrong-video-head h3 {
  margin: 0;
  font-size: var(--font-size-2xl);
  color: #f7f9ff;
}

.q-wrong-video-tip {
  margin: 0;
  color: rgba(219, 227, 255, 0.72);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.q-wrong-video-badge {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 107, 145, 0.18);
  color: rgba(255, 182, 198, 0.98);
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.q-wrong-video-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #f7f9ff;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.q-wrong-video-close:hover {
  background: rgba(255, 255, 255, 0.16);
}

.q-wrong-video-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  padding: 0 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(90, 243, 211, 0.92), rgba(118, 160, 255, 0.92));
  color: #071019;
  font-size: var(--font-size-md);
  font-weight: 800;
  text-decoration: none;
  box-shadow: 0 16px 36px rgba(56, 188, 180, 0.26);
  transition: transform 0.18s ease, box-shadow 0.18s ease, filter 0.18s ease;
}

.q-wrong-video-link:hover {
  transform: translateY(-1px);
  box-shadow: 0 20px 40px rgba(56, 188, 180, 0.32);
  filter: brightness(1.03);
}

.q-wrong-video-link:active {
  transform: translateY(0);
}

.wrong-video-modal-enter-active,
.wrong-video-modal-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.wrong-video-modal-enter-from,
.wrong-video-modal-leave-to {
  opacity: 0;
}

.wrong-video-modal-enter-from .q-wrong-video-dialog,
.wrong-video-modal-leave-to .q-wrong-video-dialog {
  transform: translateY(18px) scale(0.96);
}

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

.q-reason-saved-tip {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px dashed rgba(255, 184, 108, 0.28);
  background: rgba(255, 184, 108, 0.08);
  color: #ffd29a;
  font-size: var(--font-size-sm);
}

.q-reason-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(255, 168, 124, 0.18);
  background: linear-gradient(180deg, rgba(255, 168, 124, 0.08), rgba(255, 107, 145, 0.05));
}

.q-reason-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.q-reason-title {
  font-size: 13px;
  font-weight: 800;
  color: rgba(255, 194, 148, 0.96);
}

.q-reason-hint {
  font-size: 11px;
  line-height: 1.5;
  color: rgba(197, 204, 230, 0.68);
}

.q-reason-input {
  width: 100%;
  resize: vertical;
  min-height: 92px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.08);
  background: rgba(10, 13, 28, 0.58);
  color: var(--text);
  font-size: 13px;
  line-height: 1.7;
  outline: none;
}

.q-reason-input:focus {
  border-color: rgba(255, 168, 124, 0.36);
  box-shadow: 0 0 0 3px rgba(255, 168, 124, 0.08);
}

.q-reason-input::placeholder {
  color: rgba(197, 204, 230, 0.45);
}

.q-reason-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
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
