<template>
  <div class="wap-wrap">
    <button v-if="!started" class="wap-trigger" @click="start">
      <span class="wap-trigger-icon">🤖</span>
      <span>AI 解惑</span>
      <span class="wap-trigger-sub">点击获取本题思路分析、讲解与学习建议</span>
    </button>

    <div v-else class="wap-panel">
      <div class="wap-header">
        <span class="wap-title">
          <span class="wap-title-icon">🤖</span>
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
                <summary>查看 AI 思考过程</summary>
                <div class="wap-reasoning-text" v-html="renderMixedContent(item.reasoning)"></div>
              </details>
              <div class="wap-content" v-html="renderMixedContent(item.content)" />
            </div>
          </div>

          <div v-if="loading" class="wap-chat-item assistant pending">
            <div class="wap-chat-role">AI 分析</div>
            <div v-if="reasoning || content" class="wap-ai-answer">
              <details v-if="reasoning" class="wap-reasoning" :open="streamingReasoningOpen">
                <summary>{{ content ? '查看 AI 思考过程' : 'AI 正在思考...' }}</summary>
                <div class="wap-reasoning-text" v-html="renderMixedContent(reasoning)"></div>
              </details>
              <div
                v-if="content"
                class="wap-content"
                v-html="renderedContent"
              />
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
          placeholder="继续追问这道题，例如：这道题的关键突破口是什么？如果我已经做对了，还能怎样更稳地判断？"
        />
        <div class="wap-composer-actions">
          <span class="wap-composer-tip">可以围绕思路、知识点、易错点和举一反三继续深入追问</span>
          <button class="wap-send-btn" :disabled="loading || !followupQuestion.trim()" @click="askFollowup">发送追问</button>
        </div>
      </div>

      <div v-else class="wap-initial-actions">
        <span class="wap-composer-tip">首次分析由系统预设提示词发起，生成完成后才能继续追问</span>
        <button class="wap-send-btn" :disabled="loading" @click="start">
          {{ loading ? '分析中…' : 'AI解惑' }}
        </button>
      </div>

      <div v-if="error" class="wap-error">
        <span>⚠️ {{ error }}</span>
        <button class="wap-retry" @click="start">重试</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue';
import { renderMarkdown as renderMixedContent } from '@/utils/markdown';

import 'katex/dist/katex.min.css';
import { streamWrongQuestionAnalysis } from '@/api/ai';

const props = defineProps<{
  questionId: number;
  recordId?: string;
  autoStart?: boolean;
  initialErrorReason?: string;
}>();

const started  = ref(false);
const loading  = ref(false);
const progress = ref(0);
const content  = ref('');
const reasoning = ref('');
const error    = ref('');
const followupQuestion = ref('');
const chatHistory = ref<Array<{ id: string; role: 'user' | 'assistant'; content: string; reasoning?: string }>>([]);
const activeAbortController = ref<AbortController | null>(null);

const renderedContent = ref('');
const streamingReasoningOpen = computed(() => Boolean(reasoning.value) && !content.value);

const buildInitialPrompt = () => {
  const reason = props.initialErrorReason?.trim();
  if (reason && reason !== '未填写') {
    return `我对这道题的自我分析是：${reason}。请结合这道题分析我的思路特点，说明关键解题路径，并给出学习建议。`;
  }
  return '请结合这道题分析关键思路、知识点和解题路径，并给出学习建议。';
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
      { question, questionId: props.questionId, recordId: props.recordId },
      {
        signal: abortController.signal,
        sessionType: `student_question_wrong_${props.recordId ?? props.questionId}`,
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
        onComplete: () => {
          progress.value = 100;
          loading.value = false;
          chatHistory.value.push({
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: content.value,
            reasoning: reasoning.value,
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

onMounted(() => {
  if (props.autoStart) start();
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
  border: 1.5px dashed rgba(255, 107, 145, 0.4);
  background: rgba(255, 107, 145, 0.05);
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  color: var(--text-primary, #e8eaf6);
}
.wap-trigger:hover {
  border-color: rgba(255, 107, 145, 0.7);
  background: rgba(255, 107, 145, 0.1);
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(255, 107, 145, 0.15);
}
.wap-trigger-icon { font-size: 20px; flex-shrink: 0; }
.wap-trigger > span:nth-child(2) {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: rgba(255, 140, 160, 0.95);
}
.wap-trigger-sub {
  font-size: var(--font-size-xs);
  color: var(--text-muted, rgba(180,185,210,0.6));
  margin-left: auto;
}

.wap-panel {
  border: 1.5px solid rgba(255, 107, 145, 0.25);
  border-radius: 14px;
  overflow: hidden;
  background: rgba(20, 14, 28, 0.6);
  backdrop-filter: blur(8px);
}

.wap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(255, 107, 145, 0.07);
  border-bottom: 1px solid rgba(255, 107, 145, 0.12);
}
.wap-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: rgba(255, 150, 170, 0.95);
}
.wap-title-icon { font-size: 16px; }
.wap-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wap-streaming {
  font-size: var(--font-size-xs);
  color: rgba(255, 180, 100, 0.8);
  font-style: italic;
}
.wap-done {
  font-size: var(--font-size-xs);
  color: rgba(100, 220, 140, 0.8);
}
.wap-retry {
  background: none;
  border: 1px solid rgba(255, 107, 145, 0.25);
  border-radius: 6px;
  color: rgba(255, 150, 170, 0.7);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 8px;
  transition: all 0.15s;
}
.wap-retry:hover {
  border-color: rgba(255, 107, 145, 0.6);
  color: rgba(255, 150, 170, 1);
  background: rgba(255, 107, 145, 0.08);
}

.wap-stop {
  background: rgba(255, 107, 145, 0.14);
  border: 1px solid rgba(255, 107, 145, 0.45);
  border-radius: 6px;
  color: rgba(255, 220, 228, 0.95);
  font-size: var(--font-size-sm);
  cursor: pointer;
  padding: 2px 8px;
}

.wap-progress-wrap {
  height: 2px;
  background: rgba(255,255,255,0.05);
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
  color: rgba(180, 185, 210, 0.68);
}

.wap-chat-item.user .wap-chat-role {
  color: rgba(255, 190, 140, 0.92);
}

.wap-chat-item.assistant .wap-chat-role {
  color: rgba(255, 150, 170, 0.92);
}

.wap-user-text {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.04);
  color: rgba(240, 243, 255, 0.92);
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
    rgba(255,255,255,0.04) 0%,
    rgba(255,255,255,0.09) 50%,
    rgba(255,255,255,0.04) 100%
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
  color: rgba(228, 233, 248, 0.92);
}
.wap-content :deep(h1),
.wap-content :deep(h2),
.wap-content :deep(h3),
.wap-content :deep(h4) {
  font-weight: 800;
  line-height: 1.3;
  color: rgba(255, 160, 175, 0.95);
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
  color: rgba(255, 200, 120, 0.95);
  font-weight: 700;
}
.wap-content :deep(code) {
  background: rgba(255,255,255,0.07);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.9em;
  color: rgba(150, 220, 255, 0.9);
}
.wap-content :deep(pre) {
  margin: 0.9em 0 1em;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(5, 7, 18, 0.4);
  overflow-x: auto;
}
.wap-content :deep(pre code) {
  background: transparent;
  padding: 0;
}
.wap-content :deep(blockquote) {
  border-left: 3px solid rgba(255,107,145,0.4);
  padding: 0.7em 0 0.7em 12px;
  margin: 0.9em 0;
  color: rgba(214, 219, 240, 0.8);
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
  border: 1px solid rgba(255, 107, 145, 0.16);
  border-radius: 12px;
  background: rgba(255, 107, 145, 0.06);
  overflow: hidden;
}
.wap-reasoning summary {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 175, 190, 0.95);
  cursor: pointer;
  letter-spacing: 0.03em;
  user-select: none;
  list-style: none;
}
.wap-reasoning summary::-webkit-details-marker {
  display: none;
}
.wap-reasoning summary:hover { color: rgba(255, 205, 215, 0.98); }
.wap-reasoning-text {
  padding: 0 12px 12px;
  font-size: 13px;
  color: rgba(255, 230, 236, 0.84);
  white-space: pre-wrap;
  line-height: 1.65;
  max-height: 220px;
  overflow-y: auto;
}

.wap-thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(255, 190, 140, 0.86);
  font-size: 13px;
  padding: 2px 0;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 160, 120, 0.9);
  animation: pulseGlow 0.9s infinite;
}

.wap-error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 18px;
  border-top: 1px solid rgba(255,107,145,0.15);
  font-size: 12px;
  color: rgba(255, 140, 120, 0.85);
}

.wap-composer {
  padding: 14px 18px 16px;
  border-top: 1px solid rgba(255, 107, 145, 0.1);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wap-initial-actions {
  padding: 14px 18px 16px;
  border-top: 1px solid rgba(255, 107, 145, 0.1);
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
  border: 1px solid rgba(255, 107, 145, 0.2);
  background: rgba(11, 10, 18, 0.38);
  color: rgba(238, 244, 255, 0.92);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  outline: none;
}

.wap-input:focus {
  border-color: rgba(255, 150, 170, 0.45);
  box-shadow: 0 0 0 3px rgba(255, 107, 145, 0.08);
}

.wap-input::placeholder {
  color: rgba(180, 185, 210, 0.5);
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
  color: rgba(180, 185, 210, 0.6);
}

.wap-send-btn {
  padding: 9px 16px;
  border-radius: 999px;
  border: 1px solid rgba(255, 107, 145, 0.28);
  background: linear-gradient(120deg, rgba(255, 107, 145, 0.84), rgba(255, 162, 122, 0.78));
  color: #1a0912;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, opacity 0.16s ease;
  box-shadow: 0 8px 24px rgba(255, 107, 145, 0.18);
}

.wap-send-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 28px rgba(255, 107, 145, 0.24);
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
