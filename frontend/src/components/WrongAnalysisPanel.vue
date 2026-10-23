<template>
  <div class="wap-wrap">
    <button v-if="!started" class="wap-trigger" @click="start">
      <span>AI 解惑</span>

    </button>

    <div v-else class="wap-panel">
      <div class="wap-header">
        <span class="wap-title">
          AI 解惑
        </span>
        <div class="wap-header-right">
          <span v-if="loading" class="wap-streaming">生成中<span class="dots">...</span></span>
          <button v-if="loading" class="wap-stop" @click="stopAnalysis" title="停止分析">停止</button>
          <span v-else-if="chatHistory.length" class="wap-done">✓ 分析完成</span>
          <button v-if="!loading" class="wap-retry" @click="start" title="重新分析">↺</button>
        </div>
      </div>

      <div v-if="loading && progress > 0" class="wap-progress-wrap">
        <div class="wap-progress-bar" :style="{ width: progress + '%' }" />
      </div>

      <div class="wap-body">
        <div v-if="!chatHistory.length && loading && !reasoning && !content" class="wap-skeleton">
          <div class="sk-line" style="width:80%"></div>
          <div class="sk-line" style="width:65%"></div>
          <div class="sk-line" style="width:72%"></div>
        </div>

        <div v-else class="wap-chat-list">
          <div v-for="item in chatHistory" :key="item.id" class="wap-chat-item" :class="item.role">
            <div class="wap-chat-role">{{ item.role === 'user' ? '我的追问' : 'AI 分析' }}</div>
            <pre v-if="item.role === 'user'" class="wap-user-text">{{ item.content }}</pre>
            <div v-else class="wap-ai-answer">
              <details v-if="item.reasoning" class="wap-reasoning">
                <summary>思考过程</summary>
                <div class="wap-reasoning-text" v-html="renderMixedContent(item.reasoning)"></div>
              </details>
              <div class="wap-content" v-html="renderMixedContent(item.content)" />
              <KnowledgeSources :sources="item.sources" />
            </div>
          </div>

          <div v-if="loading" class="wap-chat-item assistant pending">
            <div class="wap-chat-role">AI 分析</div>
            <div v-if="reasoning || content" class="wap-ai-answer">
              <details v-if="reasoning" class="wap-reasoning" :open="streamingReasoningOpen">
                <summary>{{ content ? '思考过程' : 'AI 正在思考' }}</summary>
                <div class="wap-reasoning-text" v-html="renderMixedContent(reasoning)"></div>
              </details>
              <div v-if="content">
                <div class="wap-content" v-html="renderedContent" />
                <KnowledgeSources :sources="currentSources" />
              </div>
              <div v-else class="wap-thinking">
                <span class="thinking-dot"></span>
                <span>AI 正在生成回复…</span>
              </div>
            </div>
            <div v-else class="wap-skeleton">
              <div class="sk-line" style="width:80%"></div>
              <div class="sk-line" style="width:65%"></div>
              <div class="sk-line" style="width:72%"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="wap-composer" v-if="chatHistory.length">
        <textarea
          v-model="followupQuestion"
          class="wap-input"
          rows="3"
          placeholder="继续追问"
        />
        <div class="wap-composer-actions">

          <button class="wap-send-btn" :disabled="loading || !followupQuestion.trim()" @click="askFollowup">发送追问</button>
        </div>
      </div>

      <div v-else class="wap-initial-actions">
        <label v-if="knowledgeReady" class="wap-knowledge-mode"><input v-model="useKnowledge" type="checkbox" />依据班级讲义辅导</label>
        <button class="wap-send-btn" :disabled="loading" @click="start">
          {{ loading ? '分析中…' : 'AI解惑' }}
        </button>
      </div>

      <div v-if="error" class="wap-error">
        <span>{{ error }}</span>
        <button class="wap-retry" @click="start">重试</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue';
import { renderMarkdown as renderMixedContent } from '@/utils/markdown';

import 'katex/dist/katex.min.css';
import { streamWrongQuestionAnalysis, getSessionHistoryApi, getAICapabilitiesApi } from '@/api/ai';
import KnowledgeSources from '@/components/KnowledgeSources.vue';
import type {AISource} from '@/types/ai';

const props = defineProps<{
  questionId: number;
  recordId?: string;
  autoStart?: boolean;
}>();

const started  = ref(false);
const loading  = ref(false);
const progress = ref(0);
const content  = ref('');
const reasoning = ref('');
const error    = ref('');
const followupQuestion = ref('');
const chatHistory = ref<Array<{ id: string; role: 'user' | 'assistant'; content: string; reasoning?: string; sources?: AISource[] }>>([]);
const currentSources = ref<AISource[]>([]);
const useKnowledge = ref(false);
const knowledgeReady = ref(false);
const activeAbortController = ref<AbortController | null>(null);

const renderedContent = ref('');
const streamingReasoningOpen = computed(() => Boolean(reasoning.value) && !content.value);

const buildInitialPrompt = () => '请结合这道题分析关键思路、知识点和解题路径，并给出学习建议。';
const sessionType = () => `student_question_wrong_${props.recordId ?? props.questionId}`;

const restoreHistory = async () => {
  if (!props.recordId) return false;
  try {
    const rows = await getSessionHistoryApi(sessionType(), props.recordId);
    chatHistory.value = rows.flatMap((row) => [
      {id: `user-${row.id}`, role: 'user' as const, content: row.question},
      {id: `assistant-${row.id}`, role: 'assistant' as const, content: row.answer, sources: row.sources ?? []},
    ]);
    started.value = chatHistory.value.length > 0;
    return started.value;
  } catch {
    return false;
  }
};

const isAbortError = (err: unknown) => {
  if (!err) return false;
  return (err instanceof DOMException && err.name === 'AbortError')
    || (typeof err === 'object' && (err as { name?: string }).name === 'AbortError');
};

// Render Markdown + LaTeX. Supports: \[...\], \(...\), $$...$$, $...$
watch(content, (val) => {
  renderedContent.value = renderMixedContent(val);
});

const runAnalysis = async (question: string, appendUserMessage = false) => {
  started.value = true;
  loading.value = true;
  progress.value = 0;
  content.value = '';
  reasoning.value = '';
  currentSources.value = [];
  error.value = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;

  if (appendUserMessage) {
    chatHistory.value.push({
      id: `user-${Date.now()}`,
      role: 'user',
      content: question,
    });
  }

  try {
    await streamWrongQuestionAnalysis(
      { question, questionId: props.questionId, recordId: props.recordId, knowledgeMode: useKnowledge.value ? 'AUTO' : 'OFF' },
      {
        signal: abortController.signal,
        sessionType: sessionType(),
        onMessage: (payload) => {
          const chunk = typeof payload === 'string' ? payload
            : (payload as any)?.content ?? String(payload ?? '');
          content.value += chunk;
        },
        onProgress: (payload) => {
          const p = typeof payload === 'number' ? payload
            : (payload as any)?.progress ?? 0;
          progress.value = p;
        },
        onReasoning: (payload) => {
          const r = typeof payload === 'string' ? payload
            : (payload as any)?.reasoning ?? String(payload ?? '');
          reasoning.value += r;
        },
        onSources: (sources) => { currentSources.value = sources; },
        onComplete: () => {
          progress.value = 100;
          loading.value = false;
          chatHistory.value.push({
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: content.value,
            reasoning: reasoning.value,
            sources: currentSources.value,
          });
          followupQuestion.value = '';
        },
        onError: (payload) => {
          const msg = typeof payload === 'string' ? payload
            : (payload as any)?.message ?? '分析失败，请重试';
          error.value = msg;
          loading.value = false;
        },
      }
    );
  } catch (e: any) {
    if (isAbortError(e)) {
      if (content.value || reasoning.value) {
        chatHistory.value.push({
          id: `assistant-abort-${Date.now()}`,
          role: 'assistant',
          content: content.value,
          reasoning: reasoning.value,
          sources: currentSources.value,
        });
      }
      return;
    }
    error.value = e?.message ?? '网络错误，请重试';
  } finally {
    loading.value = false;
    if (activeAbortController.value === abortController) {
      activeAbortController.value = null;
    }
  }
};

const stopAnalysis = () => {
  if (!loading.value) return;
  activeAbortController.value?.abort();
};

const start = async () => {
  if (loading.value) return;
  chatHistory.value = [];
  followupQuestion.value = '';
  await runAnalysis(buildInitialPrompt());
};

const askFollowup = async () => {
  const prompt = followupQuestion.value.trim();
  if (!prompt || loading.value) return;
  await runAnalysis(prompt, true);
};

onMounted(async () => {
  getAICapabilitiesApi().then(capabilities => {knowledgeReady.value = Boolean(capabilities.knowledgeReady);}).catch(() => {knowledgeReady.value = false;});
  const restored = await restoreHistory();
  if (props.autoStart && !restored) start();
});

watch(() => props.questionId, () => {
  activeAbortController.value?.abort();
  started.value  = false;
  loading.value  = false;
  progress.value = 0;
  content.value  = '';
  reasoning.value = '';
  error.value    = '';
});

onBeforeUnmount(() => {
  activeAbortController.value?.abort();
});
</script>

<style scoped>
.wap-wrap {
  animation: fadeUp 0.3s ease;
}

.wap-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 18px;
  border-radius: 12px;
  border: 1.5px dashed rgba(190, 18, 60, 0.3);
  background: #fdf2f8;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  color: #172033;
}
.wap-trigger:hover {
  border-color: rgba(190, 18, 60, 0.55);
  background: #fce7f3;
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(190, 18, 60, 0.1);
}
.wap-trigger-icon { font-size: 20px; flex-shrink: 0; }
.wap-trigger > span:nth-child(2) {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #be123c;
}
.wap-trigger-sub {
  font-size: var(--font-size-xs);
  color: #64748b;
  margin-left: auto;
}

.wap-panel {
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
  background: #ffffff;
}

.wap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.wap-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: #be123c;
}
.wap-title-icon { font-size: 16px; }
.wap-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wap-streaming {
  font-size: var(--font-size-xs);
  color: #b45309;
  font-style: italic;
}
.wap-done {
  font-size: var(--font-size-xs);
  color: #15803d;
}
.wap-retry {
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 8px;
  transition: all 0.15s;
}
.wap-retry:hover {
  border-color: #cbd5e1;
  color: #0f766e;
  background: #f8fafc;
}

.wap-stop {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: #b91c1c;
  font-size: var(--font-size-sm);
  cursor: pointer;
  padding: 2px 8px;
}

.wap-progress-wrap {
  height: 2px;
  background: #e2e8f0;
  overflow: hidden;
}
.wap-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, rgba(255,107,145,0.6), rgba(255,180,100,0.8));
  transition: width 0.4s ease;
  border-radius: 1px;
}

.wap-body {
  padding: 16px 18px;
  min-height: 80px;
}

.wap-chat-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.wap-chat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wap-chat-role {
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.03em;
  color: #64748b;
}

.wap-chat-item.user .wap-chat-role {
  color: #b45309;
}

.wap-chat-item.assistant .wap-chat-role {
  color: #be123c;
}

.wap-user-text {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #334155;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: var(--font-size-sm);
}

.wap-ai-answer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wap-skeleton { display: flex; flex-direction: column; gap: 10px; }
.sk-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(
    90deg,
    #e2e8f0 0%,
    #f1f5f9 50%,
    #e2e8f0 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.wap-content {
  font-size: 15px;
  line-height: 1.72;
  color: #334155;
}
.wap-content :deep(h1),
.wap-content :deep(h2),
.wap-content :deep(h3),
.wap-content :deep(h4) {
  font-weight: 800;
  line-height: 1.3;
  color: #be123c;
}
.wap-content :deep(h1) {
  font-size: 24px;
  margin: 1.05em 0 0.55em;
}
.wap-content :deep(h2) {
  font-size: 20px;
  margin: 0.95em 0 0.5em;
}
.wap-content :deep(h3) {
  font-size: 17px;
  margin: 0.85em 0 0.45em;
}
.wap-content :deep(h4) {
  font-size: 15px;
  margin: 0.8em 0 0.4em;
}
.wap-content :deep(p) {
  margin: 0 0 0.72em;
}
.wap-content :deep(p:last-child) {
  margin-bottom: 0;
}
.wap-content :deep(ul),
.wap-content :deep(ol) {
  padding-left: 1.45em;
  margin: 0.6em 0 0.85em;
}
.wap-content :deep(li) {
  margin: 0.26em 0;
  line-height: 1.68;
}
.wap-content :deep(strong) {
  color: #92400e;
  font-weight: 700;
}
.wap-content :deep(code) {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.9em;
  color: #0f766e;
}
.wap-content :deep(pre) {
  margin: 0.9em 0 1em;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
  overflow-x: auto;
}
.wap-content :deep(pre code) {
  background: transparent;
  padding: 0;
}
.wap-content :deep(blockquote) {
  border-left: 3px solid #fda4af;
  padding: 0.7em 0 0.7em 12px;
  margin: 0.9em 0;
  color: #64748b;
  font-style: italic;
}
.wap-content :deep(.katex) {
  font-size: 1.04em;
}
.wap-content :deep(.katex-display) {
  margin: 0.9em 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.wap-reasoning {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  overflow: hidden;
}
.wap-reasoning summary {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 700;
  color: #be123c;
  cursor: pointer;
  letter-spacing: 0.03em;
  user-select: none;
  list-style: none;
}
.wap-reasoning summary::-webkit-details-marker {
  display: none;
}
.wap-reasoning summary:hover { color: #9d174d; }
.wap-reasoning-text {
  padding: 0 12px 12px;
  font-size: 13px;
  color: #475569;
  white-space: pre-wrap;
  line-height: 1.65;
  max-height: 220px;
  overflow-y: auto;
}

.wap-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #b45309;
  font-size: 13px;
  padding: 2px 0;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
  animation: pulseGlow 0.9s infinite;
}

.wap-error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-top: 1px solid #e2e8f0;
  font-size: 12px;
  color: #b91c1c;
}

.wap-composer {
  padding: 14px 18px 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wap-initial-actions {
  padding: 14px 18px 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.wap-input {
  width: 100%;
  resize: vertical;
  min-height: 76px;
  border-radius: 12px;
  border: 1px solid #dbe3ec;
  background: #ffffff;
  color: #172033;
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  outline: none;
}

.wap-input:focus {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

.wap-input::placeholder {
  color: #94a3b8;
}

.wap-composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.wap-composer-tip {
  font-size: 11px;
  color: #64748b;
}

.wap-send-btn {
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, opacity 0.16s ease;
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.18);
}

.wap-send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 28px rgba(15, 118, 110, 0.24);
}

.wap-send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes pulseGlow {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.82); }
}

.dots {
  display: inline-block;
  animation: blink 1.2s steps(3, end) infinite;
  letter-spacing: 2px;
}
@keyframes blink {
  0%, 100% { opacity: 0.2; }
  50% { opacity: 1; }
}
</style>
