<template>
  <AppShell :subtitle="`AI 问答 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="ai-page student-font">
      <section class="ai-hero">
        <div>
          <span class="ai-kicker">AI study assistant</span>
          <h1 class="ai-title">AI 问答</h1>
          <p class="ai-summary">在同一个页面中完成提问、查看回答与切换问答模式。</p>
        </div>
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
        </div>
      </section>

      <section class="ai-layout">
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

          <div class="panel-actions">
            <button
              v-if="state.loading"
              class="stop-btn"
              @click="stopCurrentAnalysis"
            >{{ activeMode === 'realtime' ? '停止生成' : '停止分析' }}</button>
            <button class="submit-btn" :disabled="state.loading || !question.trim()" @click="submitCurrentMode">
              {{ activeMode === 'realtime' ? '发送提问' : '开始分析' }}
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
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import { useAuthStore } from '@/stores/auth';
import { clearSessionApi, streamRealtimeQA, streamWrongQuestionAnalysis } from '@/api/ai';
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

type AIMode = 'realtime' | 'wrong';

const SESSION_TYPE_MAP: Record<AIMode, string> = {
  realtime: 'student_realtime',
  wrong: 'student_wrong_analysis',
};

const auth = useAuthStore();
const { state, reset } = useAIStreamState();

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: 'AI 问答', to: '/student/ai' },
  { label: '学习报告', to: '/student/report' },
];

const question = ref('');
const questionId = ref<number | undefined>(undefined);
const activeMode = ref<AIMode>('realtime');
const histories = ref<Record<AIMode, ChatMessage[]>>({ realtime: [], wrong: [] });
const activeAbortController = ref<AbortController | null>(null);

const currentMessages = computed(() => histories.value[activeMode.value]);

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
  color: rgba(255, 107, 145, 0.92);
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
