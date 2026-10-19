<template>
  <div class="ai-float-root" :style="{ right: posRight + 'px', bottom: posBottom + 'px' }">
    <button
      class="ai-fab"
      :class="{ active: drawerOpen, dragging: isDragging }"
      :aria-label="drawerOpen ? '关闭 AI 学习助手' : '打开 AI 学习助手'"
      @mousedown="onDragStart"
      @touchstart.passive="onTouchStart"
      @click="onFabClick"
    >
      <span class="ai-fab-icon">{{ drawerOpen ? '×' : 'AI' }}</span>
      <span class="ai-fab-label">{{ drawerOpen ? '关闭' : 'AI' }}</span>
    </button>

    <Transition name="drawer">
      <aside v-if="drawerOpen" class="ai-drawer">
        <div class="ai-drawer-head">
          <div class="ai-drawer-title">
            <span class="ai-pulse-dot"></span>
            <h3>AI 学习助手</h3>
          </div>
          <button class="ghost-btn" @click="clearSession">清空会话</button>
        </div>

        <div class="ai-drawer-body">
          <div class="ai-response-box" ref="scrollContainer">
            <div v-if="!messages.length && !state.loading" class="ai-empty">
            </div>

            <div v-for="item in messages" :key="item.id" class="ai-message" :class="item.role">
              <div class="ai-bubble-head">
                <span class="ai-role-badge">{{ item.role === 'user' ? '你' : 'AI' }}</span>
                <span v-if="item.meta" class="ai-bubble-meta">{{ item.meta }}</span>
              </div>

              <div v-if="item.imageBase64" class="ai-user-image-wrap">
                <img class="ai-user-image" :src="item.imageBase64" alt="用户上传图片" />
              </div>

              <div v-if="item.role === 'assistant' && item.reasoning" class="ai-reasoning-card">
                <details>
                  <summary>思考过程</summary>
                  <div class="reasoning-text markdown-body" v-html="renderMarkdown(item.reasoning)"></div>
                </details>
              </div>

              <div
                class="ai-bubble"
                :class="item.role === 'assistant' ? 'assistant-bubble' : 'user-bubble'"
                v-html="item.role === 'assistant' ? renderMarkdown(item.content) : renderUserText(item.content)"
              ></div>

              <div v-if="item.error" class="ai-error">
                <p>{{ item.error }}</p>
              </div>
            </div>

            <div v-if="state.loading && currentAssistantMessage" class="ai-message assistant">
              <div class="ai-bubble-head">
                <span class="ai-role-badge">AI</span>
                <span class="ai-bubble-meta">流式响应中 · {{ state.progress }}%</span>
              </div>

              <div v-if="currentAssistantMessage.reasoning" class="ai-reasoning-card">
                <details open>
                  <summary>AI 正在思考...</summary>
                  <div class="reasoning-text markdown-body" v-html="renderMarkdown(currentAssistantMessage.reasoning)"></div>
                </details>
              </div>

              <div
                v-if="currentAssistantMessage.content"
                class="ai-bubble assistant-bubble"
                v-html="renderMarkdown(currentAssistantMessage.content)"
              ></div>

              <div v-else class="ai-loading">
                <span class="ai-dot-anim"></span>
                <span>AI 正在生成回复…</span>
              </div>

              <div v-if="currentAssistantMessage.error" class="ai-error">
                <p>{{ currentAssistantMessage.error }}</p>
              </div>
            </div>
          </div>

          <div class="ai-input-area">
            <div class="ai-textarea-wrap">
              <textarea
                ref="questionInputRef"
                v-model="question"
                class="ai-textarea"
                rows="3"
                placeholder="输入问题"
                @keydown.ctrl.enter="askRealtime"
                @paste="onPasteImage"
              />

              <div v-if="pendingImageBase64" class="ai-image-preview-card">
                <img class="ai-image-preview" :src="pendingImageBase64" alt="待发送图片" />
                <div class="ai-image-preview-meta">
                  <span class="ai-image-preview-title">已附加图片</span>

                </div>
                <button class="ai-mini-btn danger" type="button" @click="clearPendingImage">移除</button>
              </div>

              <div class="ai-send-row">
                <div class="ai-tool-row">
                  <button
                    class="ai-icon-btn"
                    type="button"
                    :class="{ active: speechSupported && speechListening }"
                    :disabled="state.loading || !speechSupported"
                    :title="speechSupported ? (speechListening ? '停止语音输入' : '语音输入') : (!isSecureContext ? '请使用 HTTPS 访问以启用语音输入' : '浏览器不支持语音输入')"
                    @click="toggleSpeechInput"
                    aria-label="语音输入"
                  >
                    <span>语音</span>
                  </button>
                  <label class="ai-icon-btn upload" :class="{ disabled: state.loading }" title="上传图片" aria-label="上传图片">
                    <span>图片</span>
                    <input
                      class="ai-file-input"
                      type="file"
                      accept="image/png"
                      :disabled="state.loading"
                      @change="onSelectImage"
                    />
                  </label>
                </div>
                <button
                  v-if="state.loading"
                  class="ai-btn stop"
                  @click="stopCurrentAnalysis"
                >停止</button>
                <button
                  class="ai-btn primary"
                  :disabled="state.loading || (!question.trim() && !pendingImageBase64)"
                  @click="askRealtime"
                >发送</button>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick, onMounted } from 'vue';
import { streamRealtimeQA, clearSessionApi, getSessionHistoryApi } from '@/api/ai';
import { useAIStreamState } from '@/composables/useAIStreamState';
import { useToast } from '@/composables/useToast';
import type { ConversationHistory } from '@/types/ai';
import { renderMarkdown } from '@/utils/markdown';
import { useRoute } from 'vue-router';

import 'katex/dist/katex.min.css';

const route = useRoute();

// 配置 marked 开启 GFM 支持


interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  reasoning?: string;
  tokenUsage?: string;
  error?: string;
  meta?: string;
  imageBase64?: string;
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

const { state, reset } = useAIStreamState();
const { show } = useToast();
const drawerOpen = ref(false);
const question = ref('');
const messages = ref<ChatMessage[]>([]);
const currentAssistantMessage = ref<ChatMessage | null>(null);
const activeAbortController = ref<AbortController | null>(null);
const historyLoaded = ref(false);
const SESSION_TYPE = 'student_realtime';
const pendingImageBase64 = ref('');
const questionInputRef = ref<HTMLTextAreaElement | null>(null);
// 检测语音输入是否可用：需要安全上下文 + 浏览器支持
const isSecureContext = typeof window !== 'undefined' && window.isSecureContext;
const speechSupported = typeof window !== 'undefined' && Boolean(window.SpeechRecognition || window.webkitSpeechRecognition) && isSecureContext;
const speechListening = ref(false);
let speechRecognition: SpeechRecognition | null = null;

const appendRecognizedText = (text: string) => {
  const normalized = text.trim();
  if (!normalized) return;
  question.value = question.value.trim() ? `${question.value.trim()} ${normalized}` : normalized;
  nextTick(() => questionInputRef.value?.focus());
};

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
    appendRecognizedText(transcript);
  };
  speechRecognition.onerror = (event: SpeechRecognitionErrorEvent) => {
    speechListening.value = false;
    // 处理常见错误：权限被拒绝、静默失败
    if (event.error === 'not-allowed' || event.error === 'denied') {
      show('请在浏览器设置中允许使用麦克风', 'error');
    }
  };
  speechRecognition.onend = () => {
    speechListening.value = false;
  };
  return speechRecognition;
};

const toggleSpeechInput = async () => {
  const recognition = ensureSpeechRecognition();
  if (!recognition || state.value.loading) return;
  if (speechListening.value) {
    recognition.stop();
    speechListening.value = false;
    return;
  }
  // 启动前先聚焦输入框，防止权限弹窗导致焦点丢失
  questionInputRef.value?.focus();
  speechListening.value = true;
  try {
    recognition.start();
  } catch {
    speechListening.value = false;
  }
};

const scrollContainer = ref<HTMLElement | null>(null);
const scrollToBottom = () => {
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight;
  }
};
watch([messages, currentAssistantMessage], () => {
  nextTick(scrollToBottom);
}, { deep: true });

const formatTime = (value?: string) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const loadHistory = async () => {
  if (historyLoaded.value) return;
  try {
    const history = await getSessionHistoryApi(SESSION_TYPE);
    messages.value = history.flatMap((item: ConversationHistory) => {
      const meta = formatTime(item.createdAt);
      return [
        {
          id: `user-${item.id}`,
          role: 'user' as const,
          content: item.question,
          meta,
        },
        {
          id: `assistant-${item.id}`,
          role: 'assistant' as const,
          content: item.answer,
          meta,
        },
      ];
    });
  } catch {
    messages.value = [];
  } finally {
    historyLoaded.value = true;
  }
};

watch(drawerOpen, (open) => {
  if (open) {
    loadHistory();
    nextTick(scrollToBottom);
  }
});

const posRight  = ref(24);
const posBottom = ref(24);
const isDragging = ref(false);
let dragStartX = 0, dragStartY = 0;
let startRight = 0, startBottom = 0;
let dragMoved = false;

const clampPosition = (nextRight: number, nextBottom: number) => {
  const fabSize = 58;
  const minEdge = 8;
  const minRight = minEdge;
  const navigationHeight = document.querySelector('.app-navigation-bottom')?.getBoundingClientRect().height ?? 0;
  const minBottom = navigationHeight ? navigationHeight + 12 : minEdge;
  const maxBottom = Math.max(minEdge, window.innerHeight - (fabSize + minEdge));
  const maxRightDefault = Math.max(minRight, window.innerWidth - (fabSize + minEdge));

  const maxRight = maxRightDefault;

  return {
    right: Math.max(minRight, Math.min(maxRight, nextRight)),
    bottom: Math.max(minBottom, Math.min(maxBottom, nextBottom)),
  };
};

const normalizeFloatingPosition = () => {
  const next = clampPosition(posRight.value, posBottom.value);
  posRight.value = next.right;
  posBottom.value = next.bottom;
};

const handleWindowResize = () => {
  normalizeFloatingPosition();
};

watch(() => route.path, () => nextTick(normalizeFloatingPosition));

const onDragStart = (e: MouseEvent) => {
  dragMoved = false;
  isDragging.value = false;
  dragStartX = e.clientX;
  dragStartY = e.clientY;
  startRight  = posRight.value;
  startBottom = posBottom.value;
  window.addEventListener('mousemove', onDragMove);
  window.addEventListener('mouseup', onDragEnd);
};

const onDragMove = (e: MouseEvent) => {
  const dx = e.clientX - dragStartX;
  const dy = e.clientY - dragStartY;
  if (!dragMoved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return;
  dragMoved = true;
  isDragging.value = true;
  const next = clampPosition(startRight - dx, startBottom - dy);
  posRight.value = next.right;
  posBottom.value = next.bottom;
};

const onDragEnd = () => {
  window.removeEventListener('mousemove', onDragMove);
  window.removeEventListener('mouseup', onDragEnd);
  setTimeout(() => { isDragging.value = false; }, 50);
};

const onTouchStart = (e: TouchEvent) => {
  const t = e.touches[0];
  dragMoved = false;
  isDragging.value = false;
  dragStartX = t.clientX;
  dragStartY = t.clientY;
  startRight  = posRight.value;
  startBottom = posBottom.value;
  window.addEventListener('touchmove', onTouchMove, { passive: false });
  window.addEventListener('touchend', onTouchEnd);
};

const onTouchMove = (e: TouchEvent) => {
  e.preventDefault();
  const t = e.touches[0];
  const dx = t.clientX - dragStartX;
  const dy = t.clientY - dragStartY;
  if (!dragMoved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return;
  dragMoved = true;
  isDragging.value = true;
  const next = clampPosition(startRight - dx, startBottom - dy);
  posRight.value = next.right;
  posBottom.value = next.bottom;
};

const onTouchEnd = () => {
  window.removeEventListener('touchmove', onTouchMove);
  window.removeEventListener('touchend', onTouchEnd);
  setTimeout(() => { isDragging.value = false; }, 50);
};

const onFabClick = () => {
  if (dragMoved) return;
  drawerOpen.value = !drawerOpen.value;
};

onUnmounted(() => {
  activeAbortController.value?.abort();
  window.removeEventListener('mousemove', onDragMove);
  window.removeEventListener('mouseup', onDragEnd);
  window.removeEventListener('resize', handleWindowResize);
  speechRecognition?.stop();
});

onMounted(() => {
  window.addEventListener('resize', handleWindowResize);
  normalizeFloatingPosition();
  if (drawerOpen.value) loadHistory();
});


const renderUserText = (content: string) => content
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/\n/g, '<br />');

const normalizeError = (msg: string | undefined) => {
  if (!msg) return 'AI 服务暂不可用，请稍后重试。';
  if (msg.includes('401') || msg.includes('API') || msg.includes('认证')) {
    return 'AI 认证失败：后端未配置有效 API Key，请联系管理员。';
  }
  return msg;
};

const isAbortError = (error: unknown) => {
  if (!error) return false;
  return (error instanceof DOMException && error.name === 'AbortError')
    || (typeof error === 'object' && (error as { name?: string }).name === 'AbortError');
};

const fileToBase64 = (file: File) => new Promise<string>((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '');
  reader.onerror = () => reject(new Error('图片读取失败'));
  reader.readAsDataURL(file);
});

const isPngFile = (file: File) => file.type === 'image/png' || file.name.toLowerCase().endsWith('.png');

const attachImageFile = async (file: File | null | undefined) => {
  if (!file) return;
  if (!isPngFile(file)) {
    show('仅支持 PNG 格式图片', 'error');
    return;
  }
  pendingImageBase64.value = await fileToBase64(file);
};

const onSelectImage = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  try {
    await attachImageFile(file);
  } finally {
    input.value = '';
  }
};

const onPasteImage = async (event: ClipboardEvent) => {
  const file = Array.from(event.clipboardData?.items ?? [])
    .find((item) => item.type.startsWith('image/'))
    ?.getAsFile();
  if (!file) return;
  event.preventDefault();
  await attachImageFile(file);
};

const clearPendingImage = () => {
  pendingImageBase64.value = '';
};

const callbacks = () => ({
  onMessage: (p: any) => {
    const chunk = typeof p === 'string'
      ? p
      : (p?.content ?? p?.data ?? (typeof p === 'object' ? '' : String(p)));
    if (!chunk || chunk === '连接成功') return;
    if (currentAssistantMessage.value) {
      currentAssistantMessage.value.content += chunk;
    }
    state.value.content += chunk;
  },
  onReasoning: (p: any) => {
    const reasoning = typeof p === 'string' ? p : (p?.reasoning ?? p?.data ?? '');
    if (currentAssistantMessage.value) {
      currentAssistantMessage.value.reasoning = (currentAssistantMessage.value.reasoning ?? '') + reasoning;
    }
    state.value.reasoning += reasoning;
  },
  onProgress: (p: { progress: number }) => { state.value.progress = p.progress; },
  onComplete: (p: { totalTokens?: number; promptTokens?: number; completionTokens?: number }) => {
    state.value.loading = false;
    state.value.tokenUsage = `Token: ${p.totalTokens ?? 0} (in ${p.promptTokens ?? 0} / out ${p.completionTokens ?? 0})`;
    if (currentAssistantMessage.value) {
      currentAssistantMessage.value.tokenUsage = state.value.tokenUsage;
      messages.value.push(currentAssistantMessage.value);
      currentAssistantMessage.value = null;
    }
  },
  onError: (p: { message: string }) => {
    state.value.loading = false;
    state.value.error = normalizeError(p.message);
    if (currentAssistantMessage.value) {
      currentAssistantMessage.value.error = state.value.error;
      messages.value.push(currentAssistantMessage.value);
      currentAssistantMessage.value = null;
    }
  },
});

const askRealtime = async () => {
  const currentQuestion = question.value.trim();
  const currentImage = pendingImageBase64.value;
  if ((!currentQuestion && !currentImage) || state.value.loading) return;
  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: currentQuestion || '[图片提问]',
    imageBase64: currentImage || undefined,
    meta: formatTime(new Date().toISOString()),
  });
  currentAssistantMessage.value = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    reasoning: '',
    meta: formatTime(new Date().toISOString()),
  };
  reset();
  state.value.loading = true;
  question.value = '';
  pendingImageBase64.value = '';
  const abortController = new AbortController();
  activeAbortController.value = abortController;
  try {
    await streamRealtimeQA(
      { question: currentQuestion, imageBase64: currentImage || undefined },
      { ...callbacks(), sessionType: SESSION_TYPE, signal: abortController.signal }
    );
  } catch (e: any) {
    if (isAbortError(e)) {
      state.value.loading = false;
      if (currentAssistantMessage.value) {
        currentAssistantMessage.value.tokenUsage = '已手动停止';
        messages.value.push(currentAssistantMessage.value);
        currentAssistantMessage.value = null;
      }
      return;
    }

    state.value.error = normalizeError(e?.message);
    state.value.loading = false;
    if (currentAssistantMessage.value) {
      currentAssistantMessage.value.error = state.value.error;
      messages.value.push(currentAssistantMessage.value);
      currentAssistantMessage.value = null;
    }
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

const clearSession = async () => {
  reset();
  messages.value = [];
  currentAssistantMessage.value = null;
  pendingImageBase64.value = '';
  historyLoaded.value = true;
  try { await clearSessionApi(SESSION_TYPE); } catch { /* ignore */ }
};
</script>

<style scoped>
.ai-float-root { position: fixed; z-index: 200; user-select: none; }
.ai-fab.dragging { cursor: grabbing; transform: scale(1.08); box-shadow: 0 16px 40px rgba(90,243,211,0.6); }
.ai-float-root.compact .ai-fab {
  width: 52px;
  height: 52px;
  box-shadow: 0 6px 18px rgba(90,243,211,0.36);
}
.ai-float-root.compact .ai-fab-icon { font-size: 16px; }
.ai-float-root.compact .ai-fab-label { font-size: 9px; }

.ai-fab {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 58px;
  height: 58px;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #5af3d3, #8e7bff);
  color: #0a0f19;
  font-weight: 900;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  box-shadow: 0 8px 24px rgba(90,243,211,0.45);
  transition: transform 0.2s, box-shadow 0.2s;
}
.ai-fab:hover { transform: scale(1.1); box-shadow: 0 12px 32px rgba(90,243,211,0.55); }
.ai-fab.active { background: linear-gradient(135deg, #ff6b91, #8e7bff); }
.ai-fab-icon { font-size: 18px; line-height: 1; }
.ai-fab-label { font-size: 10px; letter-spacing: 0.05em; }

.ai-drawer {
  position: absolute;
  bottom: 70px;
  right: 0;
  width: min(520px, calc(100vw - 32px));
  max-height: min(760px, calc(100vh - 96px));
  border-radius: 22px;
  border: 1px solid rgba(151,172,249,0.25);
  background: linear-gradient(160deg, rgba(17,21,40,0.97), rgba(26,31,56,0.97));
  box-shadow: 0 24px 60px rgba(6,8,20,0.65);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ai-float-root.compact .ai-drawer {
  width: min(430px, calc(100vw - 24px));
  max-height: min(700px, calc(100vh - 96px));
}

.ai-drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(151,172,249,0.18);
  flex-shrink: 0;
}
.ai-drawer-title { display: flex; align-items: center; gap: 8px; }
.ai-drawer-title h3 { font-size: 17px; color: #f1f6ff; margin: 0; }
.ai-pulse-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #5af3d3;
  box-shadow: 0 0 8px #5af3d3;
  animation: pulse 1.6s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.ai-drawer-body { display: flex; flex-direction: column; flex: 1; overflow: hidden; }
.ai-response-box {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ai-empty { color: #c2d0ef; font-size: 16px; line-height: 1.8; padding: 12px 4px; }
.ai-message {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-message.user {
  align-items: flex-end;
}
.ai-message.assistant {
  align-items: flex-start;
}
.ai-bubble-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-message.user .ai-bubble-head {
  justify-content: flex-end;
}
.ai-role-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  background: rgba(129,150,232,0.14);
  color: #dbe6ff;
}
.ai-message.user .ai-role-badge {
  background: rgba(90,243,211,0.16);
  color: #5af3d3;
}
.ai-bubble-meta {
  font-size: 12px;
  color: #98aad4;
}
.ai-user-image-wrap {
  max-width: 94%;
  padding: 8px;
  border-radius: 16px;
  border: 1px solid rgba(90,243,211,0.2);
  background: rgba(90,243,211,0.05);
}
.ai-user-image {
  display: block;
  width: min(220px, 100%);
  max-height: 220px;
  object-fit: cover;
  border-radius: 12px;
}
.ai-bubble {
  max-width: 94%;
  padding: 15px 17px;
  border-radius: 18px;
  word-break: break-word;
  font-size: 16px;
  line-height: 1.9;
}
.user-bubble {
  background: linear-gradient(135deg, rgba(90,243,211,0.18), rgba(56,161,255,0.16));
  border: 1px solid rgba(90,243,211,0.24);
  color: #ecfffb;
  border-bottom-right-radius: 6px;
}
.assistant-bubble {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(151,172,249,0.14);
  color: #dbe6ff;
  border-bottom-left-radius: 6px;
}
.ai-loading { display: flex; align-items: center; gap: 8px; color: #8ef7e0; font-size: 15px; padding: 10px 4px; }
.ai-dot-anim {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #5af3d3;
  animation: pulse 0.8s infinite;
}
.ai-reasoning-card {
  width: 94%;
  border: 1px solid rgba(151,172,249,0.18);
  background: rgba(151,172,249,0.08);
  border-radius: 14px;
  padding: 10px 12px;
  font-size: 14px;
  color: #d7e4ff;
}
.ai-reasoning-card summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  color: #e6efff;
}
.ai-reasoning-card pre {
  white-space: pre-wrap;
  margin: 10px 0 0;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.7;
  color: #dce8ff;
}
.markdown-body :deep(p) {
  margin: 0 0 2px;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  color: #5af3d3;
  margin: 4px 0 3px;
  font-weight: 700;
}
.markdown-body :deep(h1) { font-size: 26px; }
.markdown-body :deep(h2) { font-size: 22px; }
.markdown-body :deep(h3) { font-size: 19px; }
.markdown-body :deep(strong) { color: #ffd700; font-weight: 700; }
.markdown-body :deep(em) { color: #ff8eb0; font-style: italic; }
.markdown-body :deep(code) {
  background: rgba(255,255,255,0.1);
  padding: 2px 6px;
  border-radius: 6px;
  color: #ffe38a;
  font-size: 0.95em;
}
.markdown-body :deep(pre) {
  background: rgba(4,8,20,0.6);
  border: 1px solid rgba(151,172,249,0.14);
  padding: 12px 14px;
  border-radius: 12px;
  overflow-x: auto;
  margin: 12px 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 22px;
  margin: 4px 0;
}
.markdown-body :deep(li) {
  margin: 4px 0;
}
.markdown-body :deep(blockquote) {
  margin: 4px 0;
  padding: 6px 12px;
  border-left: 3px solid rgba(90,243,211,0.5);
  background: rgba(90,243,211,0.06);
  color: #d7fdf5;
}
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(151,172,249,0.14);
  margin: 18px 0;
}
.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 14px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid rgba(151,172,249,0.15);
  padding: 8px 10px;
}
.markdown-body :deep(th) {
  background: rgba(151,172,249,0.08);
  color: #f2f6ff;
}

/* GFM 删除线样式 */
.markdown-body :deep(del),
.markdown-body :deep(s),
.markdown-body :deep(strike) {
  color: #888;
  text-decoration: line-through;
}

/* GFM 任务列表样式 */
.markdown-body :deep(ul.contains-task-list) {
  list-style: none;
  padding-left: 4px;
}
.markdown-body :deep(li.contains-task-list) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.markdown-body :deep(input[type="checkbox"]) {
  margin-top: 6px;
  accent-color: #5af3d3;
}

.ai-error {
  max-width: 94%;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255,107,145,0.12);
  border: 1px solid rgba(255,107,145,0.25);
  color: #ffd2dc;
  font-size: var(--font-size-sm);
}
.ai-error p { margin: 0; }
.ai-input-area {
  padding: 14px;
  border-top: 1px solid rgba(151,172,249,0.14);
  background: rgba(10,14,28,0.8);
}
.ai-textarea-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-textarea {
  width: 100%;
  resize: vertical;
  min-height: 88px;
  max-height: 180px;
  border-radius: 16px;
  border: 1px solid rgba(151,172,249,0.18);
  background: rgba(255,255,255,0.04);
  color: #eef4ff;
  padding: 13px 14px;
  font-size: var(--font-size-md);
  line-height: 1.7;
  outline: none;
}
.ai-textarea:focus {
  border-color: rgba(90,243,211,0.42);
  box-shadow: 0 0 0 3px rgba(90,243,211,0.08);
}
.ai-image-preview-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(151,172,249,0.16);
  background: rgba(255,255,255,0.03);
}
.ai-image-preview {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}
.ai-image-preview-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ai-image-preview-title {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: #f1f6ff;
}
.ai-image-preview-sub {
  font-size: var(--font-size-xs);
  color: #98aad4;
}
.ai-send-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.ai-tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ai-btn,
.ghost-btn,
.ai-icon-btn,
.ai-mini-btn {
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: transform 0.16s, opacity 0.16s, background 0.16s;
}
.ai-btn:disabled,
.ai-icon-btn:disabled,
.ai-icon-btn.disabled,
.ai-mini-btn:disabled,
.ghost-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.ai-btn:hover:not(:disabled),
.ai-icon-btn:hover:not(:disabled),
.ai-mini-btn:hover:not(:disabled),
.ghost-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}
.ai-btn.primary {
  padding: 10px 18px;
  background: linear-gradient(135deg, #5af3d3, #8e7bff);
  color: #09111d;
  font-weight: 800;
  font-size: var(--font-size-sm);
}
.ai-btn.stop {
  padding: 10px 16px;
  background: rgba(255,107,145,0.14);
  border: 1px solid rgba(255,107,145,0.45);
  color: #ffd5e2;
  font-weight: 700;
  font-size: var(--font-size-sm);
}
.ghost-btn {
  padding: 8px 14px;
  background: rgba(255,255,255,0.05);
  color: #dce7ff;
  font-size: var(--font-size-sm);
}
.ai-icon-btn {
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: rgba(255,255,255,0.06);
  color: #dce7ff;
  font-size: 17px;
  line-height: 1;
}
.ai-icon-btn.active {
  background: rgba(90,243,211,0.16);
  color: #5af3d3;
}
.ai-icon-btn.upload {
  position: relative;
  overflow: hidden;
}
.ai-mini-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(255,255,255,0.06);
  color: #dce7ff;
  font-size: var(--font-size-sm);
  font-weight: 700;
}
.ai-mini-btn.danger {
  background: rgba(255,107,145,0.12);
  color: #ffd2dc;
}
.ai-file-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.drawer-enter-active,
.drawer-leave-active {
  transition: all 0.18s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

@media (max-width: 680px) {
  .ai-drawer {
    width: min(100vw - 16px, 100vw - 16px);
    right: -6px;
    bottom: 68px;
    max-height: calc(100vh - 92px);
  }

  .ai-response-box {
    padding: 14px;
  }

  .ai-bubble,
  .ai-reasoning-card,
  .ai-error,
  .ai-user-image-wrap {
    max-width: 100%;
  }

  .ai-send-row {
    align-items: stretch;
  }

  .ai-tool-row,
  .ai-btn.primary {
    width: 100%;
  }

  .ai-tool-row {
    justify-content: space-between;
  }
}
.ai-fab { width: 44px; height: 44px; background: #fff !important; border: 1px solid #d9e4df !important; box-shadow: 0 4px 15px #2e57421c !important; font-weight: 600; }
.ai-fab-icon { color: #1d715b !important; font-size: 14px; }
.ai-fab.active { background: #edf6f2 !important; }
.ai-fab-label { display: none; }
@media (max-width: 760px) {
  .ai-drawer { position: fixed; left: 12px; right: 12px; bottom: calc(144px + env(safe-area-inset-bottom, 0px)); width: auto; max-height: calc(100dvh - 168px - env(safe-area-inset-bottom, 0px)); }
}
</style>
