<template>
  <AppShell :subtitle="`错题本 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="wb-page student-font">
      <section class="wb-hero">
        <div>
          <span class="wb-kicker">Wrong answer book</span>
          <h1 class="wb-title">错题本</h1>
          <p class="wb-summary">先定位问题，再决定是否继续追问 AI。</p>
        </div>
        <div class="wb-tools">
          <input v-model="searchText" class="wb-search" placeholder="搜索题目内容…" />
          <span v-if="!loading" class="wb-count">{{ filteredRecords.length }} 道错题</span>
        </div>
      </section>

      <div v-if="loading" class="wb-loading">
        <div class="loading-ring" />
        <p>加载错题记录…</p>
      </div>

      <div v-else-if="records.length === 0" class="wb-empty">
        <div class="wb-empty-icon">🎉</div>
        <p class="wb-empty-title">暂无错题记录</p>
      </div>

      <div v-else-if="filteredRecords.length === 0" class="wb-empty">
        <div class="wb-empty-icon">🔍</div>
        <p class="wb-empty-title">无匹配题目</p>
      </div>

      <div v-else class="wb-list">
        <div
          v-for="(item, idx) in filteredRecords"
          :key="item.recordId"
          class="wb-card"
          :class="{ expanded: expandedId === item.recordId }"
        >
          <div class="wb-card-head" @click="toggleExpand(item.recordId)">
            <span class="wb-idx">{{ idx + 1 }}</span>
            <div class="wb-card-preview">
              <LatexText :content="item.questionContent?.slice(0, 80) ?? ''" />
              <span v-if="item.questionContent?.length > 80">…</span>
            </div>
            <div class="wb-card-chips">
              <span class="wb-chip wrong">我选 {{ item.userAnswer }}</span>
              <span class="wb-chip correct">✓ {{ item.correctAnswer }}</span>
            </div>
            <span class="wb-time">{{ formatTime(item.answeredAt) }}</span>
            <span class="wb-expand-icon">{{ expandedId === item.recordId ? '▲' : '▼' }}</span>
          </div>

          <div v-if="expandedId === item.recordId" class="wb-card-detail">
            <section class="wb-detail-block">
              <h3 class="wb-section-title">题目内容</h3>
              <div class="wb-detail-q"><LatexText :content="item.questionContent" /></div>
              <div v-if="item.imageBase64" class="wb-question-image-wrap">
                <img :src="item.imageBase64" alt="题目配图" class="wb-question-image" />
              </div>
            </section>

            <section class="wb-detail-block">
              <h3 class="wb-section-title">选项与答案</h3>
              <div class="wb-detail-opts">
                <div
                  v-for="(text, key) in item.questionOptions"
                  :key="key"
                  class="wb-opt"
                  :class="{
                    'wb-opt-correct': key === item.correctAnswer,
                    'wb-opt-wrong': key === item.userAnswer && key !== item.correctAnswer
                  }"
                >
                  <span class="wb-opt-key">{{ key }}</span>
                  <span><LatexText :content="text" /></span>
                </div>
              </div>
            </section>

            <section v-if="item.questionAnalysis || item.errorReason" class="wb-detail-block wb-analysis-grid">
              <div v-if="item.questionAnalysis" class="wb-analysis">
                <span class="wb-analysis-label">解析</span>
                <LatexText :content="item.questionAnalysis" />
              </div>
              <div v-if="item.errorReason" class="wb-reason">
                <span class="wb-reason-label">错误原因</span>
                <span>{{ item.errorReason }}</span>
              </div>
            </section>

            <section class="wb-ai-panel">
              <div class="wb-ai-head">
                <div>
                  <div class="wb-ai-title">AI 追问</div>
                  <div class="wb-ai-sub">首次分析会使用系统预设提示词，生成完成后才能继续追问。</div>
                </div>
                <button
                  v-if="hasInitialAnalysis(item.recordId)"
                  class="wb-ai-fill-btn"
                  :disabled="aiState.loading"
                  @click="fillSuggestedQuestion(item)"
                >推荐问题</button>
              </div>

              <div class="wb-ai-history">
                <div v-if="!getMessages(item.recordId).length && !isCurrentLoading(item.recordId)" class="wb-ai-empty">
                  暂无追问记录
                </div>

                <div v-for="msg in getMessages(item.recordId)" :key="msg.id" class="wb-ai-msg" :class="msg.role">
                  <div class="wb-ai-msg-head">
                    <span class="wb-ai-role">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
                    <span v-if="msg.meta" class="wb-ai-meta">{{ msg.meta }}</span>
                  </div>
                  <pre v-if="msg.role === 'user'" class="wb-ai-user">{{ msg.content }}</pre>
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

                <div v-if="isCurrentLoading(item.recordId)" class="wb-ai-msg assistant">
                  <div class="wb-ai-msg-head">
                    <span class="wb-ai-role">AI</span>
                    <span class="wb-ai-meta">正在分析… {{ aiState.progress }}%</span>
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

              <div v-if="hasInitialAnalysis(item.recordId)" class="wb-ai-composer">
                <textarea
                  v-model="aiQuestions[item.recordId]"
                  :ref="(el) => setQuestionInputRef(item.recordId, el as Element | null)"
                  class="wb-ai-textarea"
                  rows="4"
                  placeholder="输入你的追问"
                />

                <div class="wb-ai-actions">
                  <div class="wb-ai-tools">
                    <button
                      class="wb-ai-icon-btn"
                      type="button"
                      :class="{ active: speechSupported && speechListeningRecordId === item.recordId }"
                      :disabled="aiState.loading || !speechSupported"
                      @click="toggleSpeechInput(item.recordId)"
                      aria-label="语音输入"
                    >
                      <span aria-hidden="true">🎤</span>
                    </button>
                    <button class="wb-ai-clear-btn" :disabled="aiState.loading" @click="clearMessages(item.recordId)">清空</button>
                  </div>
                  <button
                    v-if="isCurrentLoading(item.recordId)"
                    class="wb-ai-stop-btn"
                    @click="stopCurrentAnalysis(item.recordId)"
                  >停止分析</button>
                  <button
                    class="wb-ai-send-btn"
                    :disabled="aiState.loading || !aiQuestions[item.recordId]?.trim()"
                    @click="submitQuestionAsk(item)"
                  >{{ aiState.loading && currentRecordId === item.recordId ? '分析中…' : '发送追问' }}</button>
                </div>
              </div>

              <div v-else class="wb-ai-initial-actions">
                <button
                  v-if="isCurrentLoading(item.recordId)"
                  class="wb-ai-stop-btn"
                  @click="stopCurrentAnalysis(item.recordId)"
                >停止分析</button>
                <button
                  class="wb-ai-send-btn"
                  :disabled="aiState.loading"
                  @click="startInitialAnalysis(item)"
                >{{ aiState.loading && currentRecordId === item.recordId ? '分析中…' : 'AI分析' }}</button>
              </div>
            </section>
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
import { getMyWrongAnswersApi } from '@/api/answer-record';
import { streamWrongQuestionAnalysis } from '@/api/ai';
import { useAIStreamState } from '@/composables/useAIStreamState';
import type { AnswerRecordResponse } from '@/types/answer-record';

interface WrongBookChatMessage {
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

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const loading = ref(false);
const records = ref<AnswerRecordResponse[]>([]);
const searchText = ref('');
const expandedId = ref<string | null>(null);
const currentRecordId = ref<string | null>(null);
const activeAbortController = ref<AbortController | null>(null);
const aiQuestions = ref<Record<string, string>>({});
const aiMessages = ref<Record<string, WrongBookChatMessage[]>>({});
const questionInputRefs = new Map<string, HTMLTextAreaElement>();
// 检测语音输入是否可用：需要安全上下文 + 浏览器支持
const isSecureContext = typeof window !== 'undefined' && window.isSecureContext;
const speechSupported = typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition) && isSecureContext;
const speechListeningRecordId = ref<string | null>(null);
let speechRecognition: SpeechRecognition | null = null;

const ensureSpeechRecognition = () => {
  if (!speechSupported || speechRecognition) return speechRecognition;
  const RecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition;
  if (!RecognitionCtor) return null;
  speechRecognition = new RecognitionCtor();
  speechRecognition.continuous = false;
  speechRecognition.interimResults = false;
  speechRecognition.lang = 'zh-CN';
  speechRecognition.onresult = (event: SpeechRecognitionEvent) => {
    const recordId = speechListeningRecordId.value;
    if (!recordId) return;
    let transcript = '';
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      transcript += event.results[i][0]?.transcript ?? '';
    }
    const normalized = transcript.trim();
    if (!normalized) return;
    const current = aiQuestions.value[recordId]?.trim() ?? '';
    aiQuestions.value[recordId] = current ? `${current} ${normalized}` : normalized;
    questionInputRefs.get(recordId)?.focus();
  };
  speechRecognition.onerror = (event: SpeechRecognitionErrorEvent) => {
    speechListeningRecordId.value = null;
    // 处理常见错误：权限被拒绝、静默失败
    if (event.error === 'not-allowed' || event.error === 'denied') {
      console.warn('语音输入权限被拒绝，请检查浏览器麦克风权限设置');
    }
  };
  speechRecognition.onend = () => {
    speechListeningRecordId.value = null;
  };
  return speechRecognition;
};

const toggleSpeechInput = (recordId: string) => {
  const recognition = ensureSpeechRecognition();
  if (!recognition || aiState.value.loading) return;
  if (speechListeningRecordId.value === recordId) {
    recognition.stop();
    speechListeningRecordId.value = null;
    return;
  }
  // 启动前先聚焦输入框，防止权限弹窗导致焦点丢失
  questionInputRefs.get(recordId)?.focus();
  speechListeningRecordId.value = recordId;
  try {
    recognition.start();
  } catch {
    speechListeningRecordId.value = null;
  }
};

const setQuestionInputRef = (recordId: string, el: Element | null) => {
  if (el instanceof HTMLTextAreaElement) {
    questionInputRefs.set(recordId, el);
    return;
  }
  questionInputRefs.delete(recordId);
};

const toggleExpand = (id: string) => {
  expandedId.value = expandedId.value === id ? null : id;
};

const filteredRecords = computed(() => {
  const s = searchText.value.trim().toLowerCase();
  if (!s) return records.value;
  return records.value.filter(r => r.questionContent?.toLowerCase().includes(s));
});

const formatTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const formatNow = () => new Date().toLocaleString('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const getMessages = (recordId: string) => aiMessages.value[recordId] ?? [];
const hasInitialAnalysis = (recordId: string) => getMessages(recordId).some(message => message.role === 'assistant' && !message.error);
const isCurrentLoading = (recordId: string) => aiState.value.loading && currentRecordId.value === recordId;

const normalizeAIError = (raw: string | undefined) => {
  const message = raw ?? '';
  if (!message) return 'AI 服务暂不可用，请稍后重试。';
  if (message.includes('401') || message.includes('API密钥') || message.includes('认证失败')) {
    return 'AI 服务认证失败，请联系管理员检查配置。';
  }
  return message;
};

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const fillSuggestedQuestion = (item: AnswerRecordResponse) => {
  aiQuestions.value[item.recordId] = '请结合正确答案、题目解析和我的错误原因，分析我为什么会做错，并告诉我以后如何避免类似错误。';
};

const buildInitialQuestion = (item: AnswerRecordResponse) => {
  const reason = item.errorReason?.trim();
  if (reason) {
    return `我这道题做错了，我的自我分析是：${reason}。请结合正确答案、题目解析和我的错误原因，分析我为什么会做错，并告诉我以后如何避免类似错误。`;
  }
  return '请结合正确答案、题目解析和我的错误原因，分析我为什么会做错，并告诉我以后如何避免类似错误。';
};

const clearMessages = (recordId: string) => {
  aiMessages.value[recordId] = [];
  aiQuestions.value[recordId] = '';
  if (currentRecordId.value === recordId) {
    activeAbortController.value?.abort();
    currentRecordId.value = null;
    resetAIState();
  }
};

const stopCurrentAnalysis = (recordId: string) => {
  if (!isCurrentLoading(recordId)) return;
  activeAbortController.value?.abort();
};

const submitQuestionAsk = async (item: AnswerRecordResponse) => {
  const prompt = aiQuestions.value[item.recordId]?.trim() ?? '';
  if (!prompt || aiState.value.loading) return;

  currentRecordId.value = item.recordId;
  if (!aiMessages.value[item.recordId]) aiMessages.value[item.recordId] = [];

  aiMessages.value[item.recordId].push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: prompt,
    meta: formatNow(),
  });

  resetAIState();
  aiState.value.loading = true;
  aiQuestions.value[item.recordId] = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;

  try {
    await streamWrongQuestionAnalysis(
      { question: prompt, questionId: item.questionId },
      {
        signal: abortController.signal,
        sessionType: `student_wrong_book_${item.questionId}`,
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
          aiMessages.value[item.recordId].push({
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            tokenUsage: aiState.value.tokenUsage,
            meta: formatNow(),
          });
          resetAIState();
          currentRecordId.value = null;
        },
        onError: (payload) => {
          aiState.value.loading = false;
          aiState.value.error = normalizeAIError(payload.message);
          aiMessages.value[item.recordId].push({
            id: `assistant-error-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            error: aiState.value.error,
            meta: formatNow(),
          });
          resetAIState();
          currentRecordId.value = null;
        },
      }
    );
  } catch (error: any) {
    if (isAbortError(error)) {
      aiState.value.loading = false;
      if (aiState.value.content || aiState.value.reasoning) {
        aiMessages.value[item.recordId].push({
          id: `assistant-abort-${Date.now()}`,
          role: 'assistant',
          content: aiState.value.content,
          reasoning: aiState.value.reasoning,
          tokenUsage: '已手动停止',
          meta: formatNow(),
        });
      }
      resetAIState();
      currentRecordId.value = null;
      return;
    }

    aiState.value.loading = false;
    aiState.value.error = normalizeAIError(error?.message);
    aiMessages.value[item.recordId].push({
      id: `assistant-catch-${Date.now()}`,
      role: 'assistant',
      content: aiState.value.content,
      reasoning: aiState.value.reasoning,
      error: aiState.value.error,
      meta: formatNow(),
    });
    resetAIState();
    currentRecordId.value = null;
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const startInitialAnalysis = async (item: AnswerRecordResponse) => {
  if (aiState.value.loading) return;

  currentRecordId.value = item.recordId;
  if (!aiMessages.value[item.recordId]) aiMessages.value[item.recordId] = [];

  resetAIState();
  aiState.value.loading = true;
  aiQuestions.value[item.recordId] = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;

  try {
    const question = buildInitialQuestion(item);
    await streamWrongQuestionAnalysis(
      { question, questionId: item.questionId },
      {
        signal: abortController.signal,
        sessionType: `student_wrong_book_${item.questionId}`,
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
          aiMessages.value[item.recordId].push({
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            tokenUsage: aiState.value.tokenUsage,
            meta: formatNow(),
          });
          resetAIState();
          currentRecordId.value = null;
        },
        onError: (payload) => {
          aiState.value.loading = false;
          aiState.value.error = normalizeAIError(payload.message);
          aiMessages.value[item.recordId].push({
            id: `assistant-error-${Date.now()}`,
            role: 'assistant',
            content: aiState.value.content,
            reasoning: aiState.value.reasoning,
            error: aiState.value.error,
            meta: formatNow(),
          });
          resetAIState();
          currentRecordId.value = null;
        },
      }
    );
  } catch (error: any) {
    if (isAbortError(error)) {
      aiState.value.loading = false;
      if (aiState.value.content || aiState.value.reasoning) {
        aiMessages.value[item.recordId].push({
          id: `assistant-abort-${Date.now()}`,
          role: 'assistant',
          content: aiState.value.content,
          reasoning: aiState.value.reasoning,
          tokenUsage: '已手动停止',
          meta: formatNow(),
        });
      }
      resetAIState();
      currentRecordId.value = null;
      return;
    }

    aiState.value.loading = false;
    aiState.value.error = normalizeAIError(error?.message);
    aiMessages.value[item.recordId].push({
      id: `assistant-catch-${Date.now()}`,
      role: 'assistant',
      content: aiState.value.content,
      reasoning: aiState.value.reasoning,
      error: aiState.value.error,
      meta: formatNow(),
    });
    resetAIState();
    currentRecordId.value = null;
  } finally {
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

onMounted(async () => {
  loading.value = true;
  try {
    const res = await getMyWrongAnswersApi();
    const seen = new Set<string>();
    records.value = res.records.filter((r) => {
      if (seen.has(r.questionContent)) return false;
      seen.add(r.questionContent);
      return true;
    });
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
.wb-page {
  display: grid;
  gap: 18px;
}

.wb-hero,
.wb-card,
.wb-empty,
.wb-loading {
  border: 1px solid rgba(151, 172, 249, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 36, 0.9), rgba(18, 14, 42, 0.84));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.32);
}

.wb-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: end;
  padding: 24px 28px;
}

.wb-kicker {
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

.wb-title {
  margin: 14px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.wb-summary {
  color: var(--text-muted);
  line-height: 1.7;
}

.wb-tools {
  display: grid;
  gap: 10px;
  justify-items: end;
}

.wb-search {
  min-width: 280px;
}

.wb-count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.wb-loading,
.wb-empty {
  padding: 40px 28px;
  text-align: center;
  color: var(--text-muted);
}

.wb-empty-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

.wb-list {
  display: grid;
  gap: 14px;
}

.wb-card {
  overflow: hidden;
}

.wb-card-head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto auto auto;
  gap: 12px;
  align-items: center;
  padding: 16px 18px;
  cursor: pointer;
}

.wb-idx {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(90, 243, 211, 0.08);
  color: var(--brand);
  font-weight: 800;
}

.wb-card-preview {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.wb-card-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-chip {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: var(--font-size-sm);
}

.wb-chip.wrong {
  background: rgba(255, 107, 145, 0.12);
  color: rgba(255, 140, 165, 0.92);
}

.wb-chip.correct {
  background: rgba(68, 232, 153, 0.1);
  color: rgba(119, 244, 182, 0.92);
}

.wb-time,
.wb-expand-icon {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.wb-card-detail {
  display: grid;
  gap: 16px;
  padding: 0 18px 18px;
}

.wb-detail-block,
.wb-ai-panel {
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.025);
  border: 1px solid rgba(151, 172, 249, 0.08);
}

.wb-section-title {
  margin: 0 0 12px;
  font-size: 16px;
}

.wb-detail-q {
  line-height: 1.75;
}

.wb-question-image-wrap {
  margin-top: 14px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.02);
}

.wb-question-image {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 0 auto;
  border-radius: 12px;
  object-fit: contain;
  box-shadow: 0 16px 34px rgba(4, 8, 20, 0.28);
}

.wb-detail-opts,
.wb-analysis-grid,
.wb-ai-history {
  display: grid;
  gap: 12px;
}

.wb-opt {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.02);
}

.wb-opt-key {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(151, 172, 249, 0.3);
}

.wb-opt-correct {
  border: 1px solid rgba(68, 232, 153, 0.3);
}

.wb-opt-wrong {
  border: 1px solid rgba(255, 107, 145, 0.3);
}

.wb-analysis,
.wb-reason {
  display: grid;
  gap: 8px;
}

.wb-analysis-label,
.wb-reason-label {
  font-size: 12px;
  color: var(--text-muted);
}

.wb-ai-head,
.wb-ai-actions,
.wb-ai-msg-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.wb-ai-title {
  font-size: 17px;
  font-weight: 700;
}

.wb-ai-sub,
.wb-ai-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.wb-ai-fill-btn,
.wb-ai-send-btn,
.wb-ai-stop-btn,
.wb-ai-clear-btn,
.wb-ai-mini-btn,
.wb-ai-icon-btn {
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text);
  cursor: pointer;
}

.wb-ai-fill-btn,
.wb-ai-send-btn,
.wb-ai-stop-btn,
.wb-ai-clear-btn,
.wb-ai-mini-btn {
  padding: 10px 14px;
}

.wb-ai-stop-btn {
  border-color: rgba(255, 107, 145, 0.45);
  background: rgba(255, 107, 145, 0.12);
  color: #ffd5e2;
  font-weight: 700;
}

.wb-ai-send-btn {
  border-color: rgba(90, 243, 211, 0.45);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.88), rgba(255, 199, 122, 0.88));
  color: #061016;
  font-weight: 800;
}

.wb-ai-composer {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.wb-ai-initial-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.wb-ai-textarea {
  width: 100%;
}

.wb-ai-tools {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-ai-icon-btn {
  width: 42px;
  height: 42px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.wb-ai-file-input {
  display: none;
}

.wb-ai-msg {
  padding: 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
}

.wb-ai-msg.user {
  background: rgba(90, 243, 211, 0.06);
}

.wb-ai-role {
  font-weight: 700;
}

.wb-ai-user {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
}

.wb-ai-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
}

.wb-ai-image-wrap,
.wb-ai-image-preview-card {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.wb-ai-image,
.wb-ai-image-preview {
  max-width: 240px;
  border-radius: 14px;
}

@media (max-width: 980px) {
  .wb-card-head {
    grid-template-columns: 40px 1fr;
  }
}

@media (max-width: 760px) {
  .wb-hero,
  .wb-ai-head,
  .wb-ai-actions,
  .wb-ai-msg-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .wb-tools {
    justify-items: stretch;
    width: 100%;
  }

  .wb-search {
    min-width: 0;
    width: 100%;
  }
}
</style>
