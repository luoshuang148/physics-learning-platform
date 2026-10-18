<template>
  <AppShell :subtitle="`题目分析 · ${auth.user?.realName ?? ''}`" :links="links">

    <div v-if="loading" class="page-loading">加载数据中…</div>
    <div v-else-if="summary.length === 0" class="page-empty">
      <p>暂无题目分析数据</p>
      <p class="empty-sub">学生答题后将在此显示错误统计</p>
    </div>

    <div v-else class="error-layout">

      <div class="left-rail">
        <div class="rail-head">
          <span class="rail-title">题目列表</span>
          <span class="rail-count">{{ summary.length }} 题</span>
        </div>
        <div class="rail-list">
          <button
            v-for="item in sortedSummary"
            :key="item.questionId"
            class="rail-item"
            :class="{ active: selected?.questionId === item.questionId }"
            @click="pick(item.questionId)"
          >
            <div class="rail-item-top">
              <span class="rail-badge">#{{ item.questionId }}</span>
              <div class="rail-item-actions" @click.stop>
                <button class="rail-ai-btn" title="AI分析" @click="openAIAnalysis(item)">AI 分析</button>
                <span class="rail-rate" :class="rateClass(item.errorRate)">{{ item.errorRate?.toFixed(0) }}%</span>
              </div>
            </div>
            <div class="rail-item-preview"><LatexText :content="`${item.content?.slice(0, 36) ?? ''}…`" /></div>
            <div class="rail-item-meta">点击查看详情</div>
          </button>
        </div>
      </div>

      <div class="detail-area">
        <div v-if="!selected" class="detail-empty">← 选择左侧题目查看详情</div>
        <template v-else>

          <div class="question-card">
            <div class="qcard-head">
              <span class="qcard-id">#{{ selected.questionId }}</span>
              <span class="qcard-diff" v-if="selected.difficulty">难度 {{ selected.difficulty }}</span>
              <span class="qcard-count">错 {{ selected.errorCount }} 人 · 对 {{ selected.correctCount }} 人</span>
              <button class="qcard-ai-btn" @click="openAIAnalysis(selected)">AI 分析</button>
              <span class="qcard-rate" :class="rateClass(selected.errorRate)">错误率 {{ selected.errorRate?.toFixed(1) }}%</span>
            </div>
            <div class="qcard-content"><LatexText :content="selected.content" /></div>
            <div v-if="selected.imageBase64" class="qcard-image-wrap">
              <img :src="selected.imageBase64" alt="题目图片预览" class="qcard-image" />
            </div>
            <div class="qcard-options">
              <div
                v-for="(text, key) in selected.options" :key="key"
                class="qcard-opt"
                :class="{ correct: key === selected.correctOption, wrong: isWrongOpt(key) }"
              >
                <span class="qcard-opt-key">{{ key }}</span>
                <span class="qcard-opt-text"><LatexText :content="text" /></span>
                <span class="qcard-opt-count" v-if="answerDist[key]">{{ answerDist[key] }} 人</span>
              </div>
            </div>
            <div class="qcard-explanation" v-if="selected.explanation">
              <span class="qcard-exp-label">解析</span>
              <LatexText :content="selected.explanation" />
            </div>
          </div>

          <div class="dist-card">
            <div class="dist-head">
              <span>答案分布</span>
              <span class="dist-summary">答错 {{ selected.errorCount }} 人 · 答对 {{ selected.correctCount }} 人 · 总作答 {{ selected.errorCount + selected.correctCount }} 人</span>
            </div>
            <div class="dist-bars">
              <div v-for="(_, key) in selected.options" :key="key" class="dist-row">
                <span class="dist-key" :class="{ correct: key === selected.correctOption }">{{ key }}</span>
                <div class="dist-bar-wrap">
                  <div
                    class="dist-bar"
                    :class="{ correct: key === selected.correctOption, wrong: key !== selected.correctOption }"
                    :style="{ width: distPct(key) + '%' }"
                  />
                </div>
                <span class="dist-pct">{{ distPct(key) }}%</span>
                <span class="dist-n">({{ answerDist[key] ?? 0 }})</span>
              </div>
            </div>
          </div>

          <div class="student-card">
            <div class="student-head">
              <span class="student-title">学生作答记录</span>
              <div class="student-filters">
                <button
                  v-for="cls in classOptions"
                  :key="cls"
                  class="filter-btn"
                  :class="{ active: classFilter === cls }"
                  @click="classFilter = cls"
                >{{ cls || '全部' }}</button>
              </div>
            </div>
            <div class="student-table">
              <div class="student-table-head">
                <span>姓名</span><span>班级</span><span>选项</span><span>作答时间</span>
              </div>
              <div v-if="loadingDetail" class="student-loading">加载中…</div>
              <template v-else>
                <div
                  v-for="s in filteredStudents"
                  :key="`${s.userId}-${s.answerTime}`"
                  class="student-row"
                >
                  <span class="s-name">{{ s.studentName }}</span>
                  <span class="s-class">{{ s.className || '—' }}</span>
                  <span class="s-ans wrong-ans">{{ s.userAnswer }}</span>
                  <span class="s-time">{{ formatTime(s.answerTime) }}</span>
                </div>
                <div v-if="filteredStudents.length === 0" class="student-empty">无匹配记录</div>
              </template>
            </div>
          </div>

        </template>
      </div>
    </div>

    <div v-if="aiPanelOpen" class="dialog-mask ai-mask" @click.self="closeAIAnalysis">
      <div class="ai-dialog">
        <div class="ai-dialog-head">
          <div>
            <div class="ai-dialog-title">题目 AI 分析</div>
            <div class="ai-dialog-sub">题目 #{{ aiTarget?.questionId }} · 基于错误统计、选项分布与学生作答情况继续追问</div>
          </div>
          <button class="close-btn" @click="closeAIAnalysis">✕</button>
        </div>

        <div class="ai-dialog-body">
          <div class="ai-question-card" v-if="aiTarget">
            <div class="ai-question-meta">
              <span class="qcard-id">#{{ aiTarget.questionId }}</span>
              <span class="qcard-diff" v-if="aiTarget.difficulty">难度 {{ aiTarget.difficulty }}</span>
              <span class="qcard-count">错 {{ aiTarget.errorCount }} 人 · 对 {{ aiTarget.correctCount }} 人</span>
            </div>
            <div class="ai-question-content"><LatexText :content="aiTarget.content" /></div>
            <div v-if="aiTarget.imageBase64" class="ai-question-image-wrap">
              <img :src="aiTarget.imageBase64" alt="题目图片预览" class="ai-question-image" />
            </div>
            <div v-if="previewOptions(aiTarget.options).length" class="ai-question-options">
              <div
                v-for="option in previewOptions(aiTarget.options)"
                :key="`${aiTarget.questionId}-${option.key}`"
                class="ai-question-option"
                :class="{ correct: option.key === aiTarget.correctOption }"
              >
                <span class="ai-question-option-key">{{ option.key }}</span>
                <span class="ai-question-option-text"><LatexText :content="option.text" /></span>
              </div>
            </div>
            <div class="ai-quick-stats">
              <span class="ai-stat-pill">错误率 {{ aiTarget.errorRate?.toFixed(1) }}%</span>
              <span class="ai-stat-pill">总作答 {{ aiTarget.errorCount + aiTarget.correctCount }} 人</span>
            </div>
          </div>

          <div class="ai-main">
            <div class="ai-chat-box">
              <div v-if="!aiMessages.length && !aiState.loading" class="ai-empty-state">
                首次不会采纳教师输入的问题，点击下方 `AI分析` 后会按系统预设角度自动生成本题分析；报告生成完成后，才能继续追问。
              </div>

              <div v-for="msg in aiMessages" :key="msg.id" class="ai-msg" :class="msg.role">
                <div class="ai-msg-head">
                  <span class="ai-role">{{ msg.role === 'user' ? '教师' : 'AI' }}</span>
                  <span v-if="msg.meta" class="ai-meta">{{ msg.meta }}</span>
                </div>
                <pre v-if="msg.role === 'user'" class="ai-msg-user">{{ msg.content }}</pre>
                <StreamOutput
                  v-else
                  :loading="false"
                  :progress="0"
                  :content="msg.content"
                  :reasoning="msg.reasoning ?? ''"
                  :token-usage="msg.tokenUsage ?? ''"
                  :error="msg.error ?? ''"
                />
              </div>

              <div v-if="aiState.loading" class="ai-msg assistant">
                <div class="ai-msg-head">
                  <span class="ai-role">AI</span>
                  <span class="ai-meta">正在分析… {{ aiState.progress }}%</span>
                </div>
                <StreamOutput
                  :loading="aiState.loading"
                  :progress="aiState.progress"
                  :content="aiState.content"
                  :reasoning="aiState.reasoning"
                  :token-usage="aiState.tokenUsage"
                  :error="aiState.error"
                />
              </div>
            </div>

            <div class="ai-composer">
              <textarea
                v-if="aiMessages.length"
                v-model="aiQuestion"
                ref="aiQuestionInputRef"
                class="ai-textarea"
                rows="4"
                placeholder="例如：请结合当前错误率和选项分布，分析学生最可能的知识误区，并给出课堂纠错建议"
              />
              <div class="ai-actions">
                <div class="ai-tool-row" v-if="aiMessages.length">
                  <button
                    class="ghost-icon-btn"
                    type="button"
                    :class="{ active: speechSupported && speechListening }"
                    :disabled="aiState.loading || !speechSupported"
                    :title="speechSupported ? (speechListening ? '停止语音输入' : '语音输入') : '浏览器不支持语音输入'"
                    @click="toggleSpeechInput"
                  >语音</button>
                </div>
                <button
                  v-if="aiState.loading"
                  class="stop-btn"
                  @click="stopCurrentAnalysis"
                >停止分析</button>
                <button class="primary-btn" :disabled="aiState.loading || !aiTarget" @click="submitAIAnalysis">
                  {{ aiState.loading ? '分析中…' : (aiMessages.length ? '发送追问' : 'AI分析') }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { useAuthStore } from '@/stores/auth';
import { getQuestionErrorDetailsApi, getQuestionErrorStatisticsApi } from '@/api/question-error';
import { streamTeacherQuestionAnalysis } from '@/api/ai';
import type { TeacherQuestionErrorDetails, TeacherQuestionErrorSummary } from '@/types/ai';
import { useAIStreamState } from '@/composables/useAIStreamState';
import { teacherNavLinks } from '@/constants/navigation';

interface AIChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  reasoning?: string;
  tokenUsage?: string;
  error?: string;
  meta?: string;
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognition;
    webkitSpeechRecognition?: new () => SpeechRecognition;
  }
}

interface SpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}

interface SpeechRecognitionEvent {
  resultIndex: number;
  results: SpeechRecognitionResultList;
}

interface SpeechRecognitionErrorEvent {
  error: string;
}

interface SpeechRecognitionResultList {
  length: number;
  [index: number]: SpeechRecognitionResult;
}

interface SpeechRecognitionResult {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternative;
}

interface SpeechRecognitionAlternative {
  transcript: string;
}

const auth = useAuthStore();
const { state: aiState, reset: resetAIState } = useAIStreamState();

const links = teacherNavLinks;

const summary = ref<TeacherQuestionErrorSummary[]>([]);
const selected = ref<TeacherQuestionErrorSummary | null>(null);
const details = ref<TeacherQuestionErrorDetails | null>(null);
const loading = ref(true);
const loadingDetail = ref(false);
const classFilter = ref('');

const aiPanelOpen = ref(false);
const aiTarget = ref<TeacherQuestionErrorSummary | null>(null);
const aiQuestion = ref('');
const aiMessages = ref<AIChatMessage[]>([]);
const aiQuestionInputRef = ref<HTMLTextAreaElement | null>(null);
const activeAbortController = ref<AbortController | null>(null);
const speechSupported = typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
const speechListening = ref(false);
let speechRecognition: SpeechRecognition | null = null;

const sortedSummary = computed(() =>
  [...summary.value].sort((a, b) => (b.errorRate ?? 0) - (a.errorRate ?? 0))
);

const previewOptions = (options?: Record<string, string>) => {
  if (!options) return [] as Array<{ key: string; text: string }>;
  return Object.entries(options).map(([key, text]) => ({ key, text: text ?? '' }));
};

const answerDist = computed<Record<string, number>>(() => {
  if (!details.value || !selected.value) return {};
  const dist: Record<string, number> = {};
  // 从学生列表统计各选项作答人数
  for (const s of details.value.studentList) {
    dist[s.userAnswer] = (dist[s.userAnswer] ?? 0) + 1;
  }
  // 把正确选项的人数也加进去（答对的学生可能不在 studentList 里）
  if (selected.value.correctOption) {
    dist[selected.value.correctOption] = (dist[selected.value.correctOption] ?? 0) + (selected.value.correctCount ?? 0);
  }
  return dist;
});

const totalAnswered = computed(() => {
  if (!selected.value) return 0;
  return (selected.value.correctCount ?? 0) + (selected.value.errorCount ?? 0);
});

const distPct = (key: string) => {
  if (!totalAnswered.value) return 0;
  return Math.round(((answerDist.value[key] ?? 0) / totalAnswered.value) * 100);
};

const isWrongOpt = (key: string) => {
  if (!selected.value || !answerDist.value[key]) return false;
  return key !== selected.value.correctOption && (answerDist.value[key] ?? 0) > 0;
};

const classOptions = computed(() => {
  if (!details.value) return [''];
  const set = new Set(details.value.studentList.map(s => s.className ?? ''));
  return ['', ...Array.from(set).filter(Boolean).sort()];
});

const filteredStudents = computed(() => {
  if (!details.value) return [];
  if (!classFilter.value) return details.value.studentList;
  return details.value.studentList.filter(s => s.className === classFilter.value);
});

const ensureSpeechRecognition = () => {
  if (!speechSupported || speechRecognition) return speechRecognition;
  const RecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!RecognitionCtor) return null;
  speechRecognition = new RecognitionCtor();
  speechRecognition.continuous = false;
  speechRecognition.interimResults = false;
  speechRecognition.lang = 'zh-CN';
  speechRecognition.onresult = (event: SpeechRecognitionEvent) => {
    let transcript = '';
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      transcript += event.results[i][0]?.transcript ?? '';
    }
    const normalized = transcript.trim();
    if (!normalized) return;
    aiQuestion.value = aiQuestion.value.trim() ? `${aiQuestion.value.trim()} ${normalized}` : normalized;
    aiQuestionInputRef.value?.focus();
  };
  speechRecognition.onerror = () => {
    speechListening.value = false;
  };
  speechRecognition.onend = () => {
    speechListening.value = false;
  };
  return speechRecognition;
};

const toggleSpeechInput = () => {
  const recognition = ensureSpeechRecognition();
  if (!recognition || aiState.value.loading) return;
  if (speechListening.value) {
    recognition.stop();
    speechListening.value = false;
    return;
  }
  speechListening.value = true;
  recognition.start();
};

const rateClass = (rate: number | null | undefined) => {
  if (!rate) return 'rate-low';
  if (rate >= 60) return 'rate-high';
  if (rate >= 30) return 'rate-mid';
  return 'rate-low';
};

const formatTime = (t: string) =>
  new Date(t).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const formatNow = () => new Date().toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });

const normalizeAIError = (raw: string | undefined) => {
  const text = raw ?? '';
  if (!text) return 'AI 分析暂时不可用，请稍后重试。';
  if (text.includes('问题不能为空')) return '请先点击 AI分析 生成固定分析报告，再继续追问。';
  return text;
};

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const openAIAnalysis = (item: TeacherQuestionErrorSummary) => {
  aiTarget.value = item;
  aiPanelOpen.value = true;
  aiMessages.value = [];
  speechRecognition?.stop();
  speechListening.value = false;
  resetAIState();
};

const closeAIAnalysis = () => {
  aiPanelOpen.value = false;
  aiTarget.value = null;
  aiQuestion.value = '';
  aiMessages.value = [];
  activeAbortController.value?.abort();
  speechRecognition?.stop();
  speechListening.value = false;
  resetAIState();
};

const stopCurrentAnalysis = () => {
  if (!aiState.value.loading) return;
  activeAbortController.value?.abort();
};

const submitAIAnalysis = async () => {
  if (!aiTarget.value || aiState.value.loading) return;

  const isFollowup = aiMessages.value.length > 0;
  const prompt = isFollowup ? aiQuestion.value.trim() : '开始分析';

  if (!prompt) return;

  if (isFollowup) {
    aiMessages.value.push({
      id: `user-${Date.now()}`,
      role: 'user',
      content: prompt,
      meta: formatNow(),
    });
  }

  resetAIState();
  aiState.value.loading = true;
  aiQuestion.value = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;

  try {
    await streamTeacherQuestionAnalysis(
      { question: prompt, questionId: aiTarget.value.questionId },
      {
        signal: abortController.signal,
        onMessage: (payload) => {
          aiState.value.content += payload.content;
        },
        onProgress: (payload) => {
          aiState.value.progress = payload.progress ?? aiState.value.progress;
        },
        onReasoning: (payload) => {
          aiState.value.reasoning += payload.reasoning;
        },
        onComplete: (payload) => {
          aiState.value.loading = false;
          aiState.value.tokenUsage = `Token: ${payload.totalTokens ?? 0} (in ${payload.promptTokens ?? 0} / out ${payload.completionTokens ?? 0})`;
          aiMessages.value.push({
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            tokenUsage: aiState.value.tokenUsage,
            meta: formatNow(),
          });
          resetAIState();
        },
        onError: (payload) => {
          aiState.value.loading = false;
          aiState.value.error = normalizeAIError(payload.message);
          aiMessages.value.push({
            id: `assistant-error-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            error: aiState.value.error,
            meta: formatNow(),
          });
          resetAIState();
        },
      }
    );
  } catch (error: any) {
    if (isAbortError(error)) {
      aiState.value.loading = false;
      if (aiState.value.content || aiState.value.reasoning) {
        aiMessages.value.push({
          id: `assistant-abort-${Date.now()}`,
          role: 'assistant',
          content: aiState.value.content,
          reasoning: aiState.value.reasoning,
          tokenUsage: '已手动停止',
          meta: formatNow(),
        });
      }
      resetAIState();
      return;
    }

    aiState.value.loading = false;
    aiState.value.error = normalizeAIError(error?.message);
    aiMessages.value.push({
      id: `assistant-catch-${Date.now()}`,
      role: 'assistant',
      content: aiState.value.content,
      reasoning: aiState.value.reasoning,
      error: aiState.value.error,
      meta: formatNow(),
    });
    resetAIState();
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const pick = async (id: number) => {
  selected.value = summary.value.find(item => item.questionId === id) ?? null;
  details.value = null;
  classFilter.value = '';
  loadingDetail.value = true;
  try {
    details.value = await getQuestionErrorDetailsApi(id);
  } finally {
    loadingDetail.value = false;
  }
};

onMounted(async () => {
  loading.value = true;
  try {
    summary.value = await getQuestionErrorStatisticsApi();
    if (summary.value.length) {
      const first = [...summary.value].sort((a, b) => (b.errorRate ?? 0) - (a.errorRate ?? 0))[0];
      await pick(first.questionId);
    }
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => {
  activeAbortController.value?.abort();
  speechRecognition?.stop();
});
</script>

<style scoped>
.page-loading, .page-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: var(--text-muted);
  font-size: var(--font-size-md);
  gap: 8px;
}
.empty-icon { font-size: 40px; opacity: 0.4; }
.empty-sub { font-size: var(--font-size-sm); opacity: 0.5; }

.error-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  align-items: start;
}

.left-rail {
  border: 1px solid rgba(151,172,249,0.15);
  border-radius: 14px;
  overflow: hidden;
  position: sticky;
  top: 16px;
}
.rail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(151,172,249,0.1);
  background: rgba(151,172,249,0.04);
}
.rail-title { font-size: var(--font-size-sm); font-weight: 600; color: var(--text-primary); }
.rail-count { font-size: var(--font-size-xs); color: var(--accent); background: rgba(151,172,249,0.1); padding: 2px 8px; border-radius: 10px; }
.rail-list { max-height: calc(100vh - 200px); overflow-y: auto; }
.rail-item {
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  border-bottom: 1px solid rgba(255,255,255,0.03);
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rail-item:hover { background: rgba(151,172,249,0.05); }
.rail-item.active { background: rgba(151,172,249,0.1); border-left: 2px solid var(--accent); }
.rail-item-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.rail-badge { font-family: monospace; font-size: var(--font-size-xs); color: var(--accent); background: rgba(151,172,249,0.12); padding: 1px 6px; border-radius: 3px; }
.rail-item-actions { display: flex; align-items: center; gap: 8px; }
.rail-ai-btn,
.qcard-ai-btn,
.ghost-btn,
.ghost-icon-btn,
.primary-btn,
.close-btn {
  border: 1px solid rgba(151,172,249,0.16);
  border-radius: 10px;
  cursor: pointer;
}
.rail-ai-btn,
.qcard-ai-btn {
  padding: 5px 10px;
  background: rgba(90,243,211,0.08);
  border-color: rgba(90,243,211,0.28);
  color: var(--brand);
  font-size: var(--font-size-xs);
  font-weight: 700;
}
.rail-rate { font-size: var(--font-size-sm); font-weight: 700; }
.rate-high { color: rgba(255,100,100,0.9); }
.rate-mid { color: rgba(255,180,60,0.9); }
.rate-low { color: rgba(120,220,150,0.9); }
.rail-item-preview { font-size: var(--font-size-xs); color: var(--text-muted); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.rail-bar-wrap { height: 3px; background: rgba(255,255,255,0.05); border-radius: 2px; overflow: hidden; }
.rail-bar { height: 100%; border-radius: 2px; transition: width 0.4s; }
.rail-bar.rate-high { background: rgba(255,100,100,0.6); }
.rail-bar.rate-mid { background: rgba(255,180,60,0.6); }
.rail-bar.rate-low { background: rgba(120,220,150,0.6); }
.rail-item-meta { font-size: var(--font-size-xs); color: var(--text-muted); opacity: 0.5; }

.detail-area { display: flex; flex-direction: column; gap: 12px; }
.detail-empty { padding: 60px; text-align: center; color: var(--text-muted); font-size: var(--font-size-sm); border: 1px dashed rgba(151,172,249,0.15); border-radius: 14px; }

.question-card {
  border: 1px solid rgba(151,172,249,0.18);
  border-radius: 14px;
  overflow: hidden;
  background: rgba(255,255,255,0.02);
}
.qcard-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(151,172,249,0.08);
  background: rgba(151,172,249,0.04);
  flex-wrap: wrap;
}
.qcard-id { font-family: monospace; font-size: var(--font-size-sm); color: var(--accent); background: rgba(151,172,249,0.12); padding: 2px 8px; border-radius: 4px; }
.qcard-diff { font-size: var(--font-size-xs); color: var(--text-muted); opacity: 0.6; }
.qcard-count { font-size: var(--font-size-sm); color: var(--text-muted); }
.qcard-rate { font-size: var(--font-size-md); font-weight: 700; margin-left: auto; }
.qcard-content { padding: 14px 16px; font-size: 15px; color: var(--text-primary); line-height: 1.7; border-bottom: 1px solid rgba(151,172,249,0.06); }
.qcard-image-wrap {
  margin: 0 16px 12px;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(151,172,249,0.12);
  background: rgba(255,255,255,0.02);
}
.qcard-image {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: contain;
  background: rgba(7,11,24,0.9);
}
.qcard-options { padding: 10px 16px; display: flex; flex-direction: column; gap: 6px; }
.qcard-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid rgba(151,172,249,0.08);
  background: rgba(255,255,255,0.01);
  font-size: 13px;
  transition: background 0.15s;
}
.qcard-opt.correct { border-color: rgba(120,220,150,0.35); background: rgba(120,220,150,0.06); }
.qcard-opt.wrong { border-color: rgba(255,100,100,0.25); background: rgba(255,100,100,0.04); }
.qcard-opt-key {
  width: 22px; height: 22px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
  background: rgba(151,172,249,0.12);
  color: var(--text-muted);
  flex-shrink: 0;
}
.qcard-opt.correct .qcard-opt-key { background: rgba(120,220,150,0.2); color: rgba(120,220,150,0.9); }
.qcard-opt.wrong .qcard-opt-key { background: rgba(255,100,100,0.15); color: rgba(255,120,120,0.9); }
.qcard-opt-text { flex: 1; color: var(--text-muted); }
.qcard-opt.correct .qcard-opt-text { color: var(--text-primary); }
.qcard-opt-count { font-size: 11px; color: var(--text-muted); opacity: 0.6; flex-shrink: 0; }
.qcard-explanation { padding: 10px 16px 14px; border-top: 1px solid rgba(151,172,249,0.06); font-size: 12px; color: var(--text-muted); line-height: 1.6; }
.qcard-exp-label { display: inline-block; font-size: 10px; font-weight: 700; letter-spacing: 0.08em; color: var(--accent); background: rgba(151,172,249,0.1); padding: 1px 6px; border-radius: 3px; margin-right: 8px; }

.dist-card {
  border: 1px solid rgba(151,172,249,0.15);
  border-radius: 12px;
  padding: 14px 16px;
  background: rgba(255,255,255,0.01);
}
.dist-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 12px;
}
.dist-summary {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  letter-spacing: 0;
  text-transform: none;
  opacity: 0.8;
}
.dist-bars { display: flex; flex-direction: column; gap: 8px; }
.dist-row { display: flex; align-items: center; gap: 10px; }
.dist-key { width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; background: rgba(151,172,249,0.1); color: var(--text-muted); flex-shrink: 0; }
.dist-key.correct { background: rgba(120,220,150,0.15); color: rgba(120,220,150,0.9); }
.dist-bar-wrap { flex: 1; height: 8px; background: rgba(255,255,255,0.04); border-radius: 4px; overflow: hidden; }
.dist-bar { height: 100%; border-radius: 4px; transition: width 0.5s ease; }
.dist-bar.correct { background: linear-gradient(90deg, rgba(120,220,150,0.5), rgba(120,220,150,0.8)); }
.dist-bar.wrong { background: linear-gradient(90deg, rgba(255,100,100,0.3), rgba(255,140,100,0.5)); }
.dist-pct { font-size: 12px; font-weight: 600; color: var(--text-muted); width: 36px; text-align: right; flex-shrink: 0; }
.dist-n { font-size: 11px; color: var(--text-muted); opacity: 0.4; flex-shrink: 0; }

.student-card {
  border: 1px solid rgba(151,172,249,0.15);
  border-radius: 14px;
  overflow: hidden;
}
.student-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(151,172,249,0.08);
  background: rgba(151,172,249,0.03);
  gap: 10px;
  flex-wrap: wrap;
}
.student-title { font-size: 12px; font-weight: 600; color: var(--text-primary); }
.student-filters { display: flex; gap: 5px; flex-wrap: wrap; }
.filter-btn {
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid rgba(151,172,249,0.15);
  background: none;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-btn:hover { border-color: rgba(151,172,249,0.4); color: var(--text-primary); }
.filter-btn.active { border-color: var(--accent); background: rgba(151,172,249,0.1); color: var(--accent); }
.student-loading, .student-empty { padding: 24px; text-align: center; color: var(--text-muted); font-size: 13px; }
.student-table { overflow-x: auto; }
.student-table-head {
  display: grid;
  grid-template-columns: 1fr 0.8fr 0.5fr 1.1fr;
  padding: 7px 14px;
  background: rgba(151,172,249,0.04);
  border-bottom: 1px solid rgba(151,172,249,0.06);
  font-size: 11px;
  color: var(--text-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.student-row {
  display: grid;
  grid-template-columns: 1fr 0.8fr 0.5fr 1.1fr;
  padding: 9px 14px;
  border-bottom: 1px solid rgba(255,255,255,0.03);
  font-size: 12px;
  align-items: center;
  transition: background 0.15s;
}
.student-row:last-child { border-bottom: none; }
.student-row:hover { background: rgba(151,172,249,0.03); }
.s-name { color: var(--text-primary); font-weight: 500; }
.s-class { color: var(--text-muted); }
.s-ans { font-family: monospace; font-weight: 700; }
.wrong-ans { color: rgba(255,120,100,0.9); }
.s-time { color: var(--text-muted); font-size: 11px; }

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 140;
  background: rgba(6,8,20,0.7);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
}
.ai-dialog {
  width: min(calc(100vw - 24px), 1600px);
  max-width: 1600px;
  height: min(calc(100vh - 24px), 980px);
  max-height: calc(100vh - 24px);
  display: grid;
  grid-template-rows: auto 1fr;
  border-radius: 18px;
  border: 1px solid rgba(151,172,249,0.2);
  background: linear-gradient(160deg, rgba(20,25,50,0.98), rgba(12,16,34,0.98));
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(4,6,18,0.6);
}
.ai-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(151,172,249,0.1);
}
.ai-dialog-title { font-size: 20px; font-weight: 800; color: var(--text-primary); }
.ai-dialog-sub { margin-top: 4px; font-size: 12px; color: var(--text-muted); }
.close-btn {
  width: 32px;
  height: 32px;
  background: rgba(255,255,255,0.04);
  color: var(--text-muted);
}
.ai-dialog-body {
  display: grid;
  grid-template-columns: 460px minmax(0, 1fr);
  min-height: 0;
}
.ai-question-card {
  padding: 18px;
  border-right: 1px solid rgba(151,172,249,0.1);
  background: rgba(255,255,255,0.02);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-question-meta { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.ai-question-content { font-size: 14px; color: var(--text-primary); line-height: 1.8; }
.ai-question-image-wrap {
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(151,172,249,0.12);
  background: rgba(255,255,255,0.02);
}
.ai-question-image {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: contain;
  background: rgba(7,11,24,0.9);
}
.ai-question-options {
  display: grid;
  gap: 8px;
}
.ai-question-option {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: 10px;
  align-items: start;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(151,172,249,0.1);
  background: rgba(255,255,255,0.02);
}
.ai-question-option.correct {
  border-color: rgba(120,220,150,0.3);
  background: rgba(120,220,150,0.06);
}
.ai-question-option-key {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #edf5ff;
  background: rgba(151,172,249,0.14);
}
.ai-question-option.correct .ai-question-option-key {
  background: rgba(120,220,150,0.18);
  color: rgba(120,220,150,0.95);
}
.ai-question-option-text {
  color: rgba(226, 234, 251, 0.88);
  line-height: 1.7;
}
.ai-quick-stats { display: flex; gap: 8px; flex-wrap: wrap; }
.ai-stat-pill {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--text-muted);
  background: rgba(151,172,249,0.08);
}
.ai-main {
  display: grid;
  grid-template-rows: 1fr auto;
  min-height: 0;
}
.ai-chat-box {
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 560px;
}
.ai-empty-state {
  border: 1px dashed rgba(151,172,249,0.16);
  border-radius: 12px;
  padding: 18px;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.8;
}
.ai-msg { display: flex; flex-direction: column; gap: 8px; }
.ai-msg-head { display: flex; align-items: center; gap: 8px; }
.ai-role {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(151,172,249,0.1);
  color: var(--text-primary);
}
.ai-meta { font-size: 11px; color: var(--text-muted); }
.ai-msg-user {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(90,243,211,0.08);
  border: 1px solid rgba(90,243,211,0.18);
  color: var(--text-primary);
  white-space: pre-wrap;
  line-height: 1.7;
  font-family: inherit;
}
.ai-composer {
  border-top: 1px solid rgba(151,172,249,0.1);
  padding: 16px 18px 18px;
  display: grid;
  gap: 10px;
}
.ai-textarea {
  width: 100%;
  min-height: 104px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(151,172,249,0.16);
  background: rgba(255,255,255,0.03);
  color: var(--text-primary);
  resize: vertical;
  outline: none;
}
.ai-textarea:focus {
  border-color: rgba(90,243,211,0.42);
  box-shadow: 0 0 0 3px rgba(90,243,211,0.1);
}
.ai-actions { display: flex; justify-content: flex-end; gap: 10px; }
.ai-tool-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ghost-btn,
.ghost-icon-btn,
.primary-btn {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 700;
}
.ghost-btn,
.ghost-icon-btn {
  background: rgba(255,255,255,0.03);
  color: var(--text-muted);
}
.ghost-icon-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  line-height: 1;
}
.ghost-icon-btn.active {
  background: rgba(90,243,211,0.12);
  border-color: rgba(90,243,211,0.36);
  color: var(--brand);
}
.primary-btn {
  background: linear-gradient(120deg, rgba(90,243,211,0.9), rgba(124,245,221,0.85));
  border-color: rgba(90,243,211,0.5);
  color: #061016;
}
.stop-btn {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 700;
  border-radius: 10px;
  border: 1px solid rgba(255,107,145,0.45);
  background: rgba(255,107,145,0.12);
  color: #ffd5e2;
  cursor: pointer;
}
.primary-btn:disabled,
.ghost-btn:disabled,
.ghost-icon-btn:disabled { opacity: 0.65; cursor: not-allowed; }

@media (max-width: 980px) {
  .error-layout {
    grid-template-columns: 1fr;
  }
  .left-rail {
    position: static;
  }
  .qcard-rate {
    margin-left: 0;
  }
}

@media (max-width: 900px) {
  .ai-dialog-body {
    grid-template-columns: 1fr;
  }
  .ai-question-card {
    border-right: none;
    border-bottom: 1px solid rgba(151,172,249,0.1);
  }
}
</style>
