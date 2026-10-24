<template>
  <AppShell subtitle="学习助手">
    <div class="study-assistant student-font" :class="{ 'has-conversation': messages.length }">
      <div class="conversation-tools">
        <span class="assistant-presence"><span></span> 你的物理学习伙伴</span>
        <div>
          <button type="button" class="text-action" :disabled="busy" @click="openSavedPlans">我的复习计划</button>
          <button v-if="messages.length" type="button" class="text-action" :disabled="busy" @click="newConversation">＋ 新对话</button>
        </div>
      </div>

      <section v-if="showSavedPlans" class="saved-plans" aria-label="我的复习计划">
        <header><strong>我的复习计划</strong><button type="button" class="text-action" @click="showSavedPlans = false">收起</button></header>
        <p v-if="plansLoading">正在读取计划…</p>
        <p v-else-if="plansError" role="alert">{{ plansError }}</p>
        <p v-else-if="!savedPlans.length">还没有保存的计划。可以直接告诉我你想如何复习。</p>
        <button v-for="plan in savedPlans" :key="plan.id" type="button" class="saved-plan-link" @click="openPlan(plan)">
          <AppIcon name="task" /><span>{{ plan.days }} 天复习 · {{ planNames[plan.status] ?? plan.status }}</span><span aria-hidden="true">↗</span>
        </button>
      </section>

      <section v-if="!messages.length" class="assistant-welcome">
        <div class="welcome-mark"><AppIcon name="spark" /></div>
        <p class="welcome-eyebrow">从一个问题开始</p>
        <h2>今天，想弄懂什么？</h2>
        <p class="welcome-description">聊知识、找练习、看错题，一起把物理学明白。</p>
      </section>

      <section v-else ref="thread" class="conversation-thread" aria-label="学习助手对话" aria-live="polite" aria-relevant="additions">
        <article v-for="message in messages" :key="message.id" class="conversation-message" :class="message.role">
          <template v-if="message.role === 'user'"><div class="user-bubble">{{ message.content }}</div></template>
          <template v-else>
            <div class="assistant-avatar"><AppIcon name="spark" /></div>
            <div class="assistant-response">
              <div class="response-author">学习助手 <span v-if="message.sources.length">已检索平台题库</span></div>
              <div v-if="message.content" class="answer-markdown" v-html="renderMarkdown(message.content)"></div>
              <div v-if="message.pending" class="thinking-status" role="status"><span class="thinking-dot"></span>{{ message.stage || '正在思考…' }}</div>
              <p v-if="message.stopped" class="response-note">已停止生成</p>
              <div v-if="message.error" class="response-error" role="alert">{{ message.error }} <button type="button" :disabled="busy" @click="retry(message)">重试</button></div>
              <div v-if="message.needsWrong" class="wrong-choices">
                <p>想一起看看哪道题？选择一条作答记录，我会结合你的答案分析。</p>
                <p v-if="wrongLoading" role="status">正在读取错题…</p>
                <p v-else-if="wrongError" role="alert">{{ wrongError }} <button type="button" @click="loadWrongChoices">重新加载</button></p>
                <template v-else>
                  <button v-for="item in wrongOptions" :key="item.recordId" type="button" :disabled="busy" @click="selectWrong(item, message)">{{ item.questionPreview }} <span aria-hidden="true">↗</span></button>
                  <p v-if="!wrongOptions.length">还没有错题记录。也可以直接把题目内容发给我。</p>
                  <RouterLink to="/student/wrong-book">查看完整错题本 ↗</RouterLink>
                </template>
              </div>
              <PlatformSources :sources="message.sources" @open="explainSource" />
              <AssistantLearningPlan v-if="message.kind === 'plan'" :question="message.question ?? ''" :days="message.days ?? 3" :event-id="contextEventId" :saved="message.savedPlan"
                @busy="planBusy = $event" @saved="rememberPlan" />
            </div>
          </template>
        </article>
        <div ref="conversationEnd" class="conversation-end"></div>
      </section>

      <div class="composer-region">
        <form class="assistant-composer" @submit.prevent="send()">
          <div v-if="selectedWrong" class="question-context"><AppIcon name="questions" /><span>{{ selectedWrong.questionPreview }}</span><button type="button" :disabled="busy" aria-label="移除关联题目" @click="selectedWrong = null">×</button></div>
          <textarea ref="composer" v-model="question" aria-label="输入你的物理问题" :placeholder="messages.length ? '继续追问，或者聊一个新问题…' : '有什么物理问题，直接问我…'" rows="2" maxlength="800" @keydown="onKeydown"></textarea>
          <div class="composer-bottom"><span>{{ busy ? '正在为你解答' : '直接提问，我会找到合适的解答方式' }}</span>
            <button v-if="loading" type="button" class="send-action stop-action" aria-label="停止生成" @click="stop"><span aria-hidden="true">■</span></button>
            <button v-else type="submit" class="send-action" aria-label="发送消息" :disabled="busy || !question.trim()"><AppIcon name="arrow" /></button>
          </div>
        </form>
        <div v-if="!messages.length" class="suggested-questions" aria-label="试着问问">
          <button v-for="example in examples" :key="example.text" type="button" @click="useExample(example.text)"><AppIcon :name="example.icon" /><span>{{ example.label }}</span><span class="suggestion-arrow" aria-hidden="true">↗</span></button>
        </div>
        <p class="composer-footnote">{{ messages.length ? 'Enter 发送 · Shift + Enter 换行' : '不必选择模式，像和老师聊天一样提问' }}</p>
      </div>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import AppIcon from '@/components/AppIcon.vue';
import PlatformSources from '@/components/assistant/PlatformSources.vue';
import AssistantLearningPlan from '@/components/assistant/AssistantLearningPlan.vue';
import { resolveAssistantIntent, streamAssistantMessage, type AssistantIntent, type AssistantResolution, type PlatformSource } from '@/api/study-assistant';
import { streamWrongQuestionAnalysis } from '@/api/ai';
import { getMyAnswerPageApi } from '@/api/answer-record';
import { getLearningPlansApi } from '@/api/learning-plan';
import type { AnswerRecordSummary } from '@/types/answer-record';
import type { LearningPlanView } from '@/types/learning-plan';
import { renderMarkdown } from '@/utils/markdown';

interface Message {
  id: string; role: 'user' | 'assistant'; content: string; sources: PlatformSource[];
  question?: string; intent?: AssistantIntent; resolution?: AssistantResolution;
  pending?: boolean; stage?: string; error?: string; stopped?: boolean; needsWrong?: boolean;
  kind?: 'plan'; days?: number; savedPlan?: LearningPlanView;
}
const route = useRoute();
const question = ref('');
const messages = ref<Message[]>([]);
const loading = ref(false);
const planBusy = ref(false);
const busy = computed(() => loading.value || planBusy.value);
const controller = ref<AbortController | null>(null);
const composer = ref<HTMLTextAreaElement | null>(null);
const thread = ref<HTMLElement | null>(null);
const conversationEnd = ref<HTMLElement | null>(null);
const previous = ref<{ question: string; intent: AssistantIntent } | null>(null);
const conversationKey = ref(crypto.randomUUID());
const selectedWrong = ref<Pick<AnswerRecordSummary, 'recordId' | 'questionId' | 'questionPreview'> | null>(null);
const contextEventId = computed(() => { const value = Number(route.query.eventId); return Number.isSafeInteger(value) && value > 0 ? value : undefined; });
const wrongOptions = ref<AnswerRecordSummary[]>([]);
const wrongLoading = ref(false);
const wrongError = ref('');
const showSavedPlans = ref(false);
const savedPlans = ref<LearningPlanView[]>([]);
const plansLoading = ref(false);
const plansError = ref('');
const planNames: Record<string, string> = { DRAFT: '草稿', ACTIVE: '进行中', CANCELLED: '已取消', COMPLETED: '已完成' };
const examples = [
  { icon: 'spark', label: '弄懂一个知识点', text: '动量守恒和机械能守恒有什么区别？' },
  { icon: 'questions', label: '找几道题练练', text: '帮我找几道牛顿第二定律的练习题，排除我做过的' },
  { icon: 'task', label: '安排一次复习', text: '根据这次作业，帮我安排三天复习' },
];
function assistantMessage(questionText: string) {
  return reactive<Message>({ id: crypto.randomUUID(), role: 'assistant', question: questionText, content: '', sources: [] });
}
function useExample(text: string) { question.value = text; composer.value?.focus(); }
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault(); if (!busy.value) void send();
  }
}
async function send(text = question.value.trim(), forced?: AssistantResolution) {
  if (!text || busy.value) return;
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: text, sources: [] });
  question.value = '';
  const reply = assistantMessage(text);
  messages.value.push(reply);
  await answer(reply, forced);
  await nextTick(); composer.value?.focus();
}
async function answer(reply: Message, forced?: AssistantResolution) {
  if (busy.value) return;
  loading.value = true;
  reply.pending = true; reply.error = ''; reply.stopped = false; reply.content = ''; reply.sources = []; reply.needsWrong = false;
  reply.stage = '正在理解你的问题…';
  const abort = new AbortController(); controller.value = abort;
  try {
    const resolution = forced ?? reply.resolution ?? await resolveAssistantIntent({ question: reply.question!, previousQuestion: previous.value?.question.slice(0, 800), previousIntent: previous.value?.intent }, abort.signal);
    if (abort.signal.aborted) return;
    reply.resolution = resolution; reply.intent = resolution.intent;
    previous.value = { question: resolution.query, intent: resolution.intent };
    if (resolution.intent === 'PLAN') { reply.kind = 'plan'; reply.days = resolution.days; return; }
    if (resolution.intent === 'WRONG') {
      if (!selectedWrong.value && route.query.questionId) {
        const id = Number(route.query.questionId);
        if (Number.isSafeInteger(id) && id > 0) selectedWrong.value = { questionId: id, recordId: typeof route.query.recordId === 'string' ? route.query.recordId : '', questionPreview: `正在讨论的题目 ${id}` };
      }
      if (!selectedWrong.value) { reply.needsWrong = true; await loadWrongChoices(); return; }
      reply.stage = '正在结合你的作答分析…';
      await streamWrongQuestionAnalysis({ question: reply.question!, questionId: selectedWrong.value.questionId, recordId: selectedWrong.value.recordId || undefined }, {
        signal: abort.signal,
        sessionType: `assistant_wrong_${conversationKey.value}_${selectedWrong.value.recordId || selectedWrong.value.questionId}`,
        onMessage: payload => { reply.content += payload.content; },
        onError: payload => { reply.error = payload.message || '分析暂不可用，请重试。'; },
      });
    } else {
      reply.stage = resolution.intent === 'PLATFORM' ? '正在检索平台题库…' : '正在整理思路…';
      const currentIndex = messages.value.findIndex(message => message.id === reply.id);
      const history = messages.value.slice(0, Math.max(0, currentIndex - 1))
        .filter(message => message.content && !message.error && !message.stopped && !message.pending)
        .slice(-8).map(message => ({ role: message.role === 'user' ? 'USER' as const : 'ASSISTANT' as const,
          content: message.content.slice(0, 4000), sourceIds: message.sources.map(source => source.sourceId).slice(0, 30) }));
      await streamAssistantMessage({ question: reply.question!, mode: resolution.intent, retrievalQuery: resolution.query, excludeAttempted: resolution.excludeAttempted, history, requestId: crypto.randomUUID(), eventId: contextEventId.value }, {
        onProgress: payload => { reply.stage = payload.stage === 'retrieving' ? '正在检索平台题库…' : '正在整理回答…'; },
        onSources: payload => { reply.sources = payload.sources ?? []; },
        onContent: payload => { reply.content += payload.text; },
        onError: payload => { reply.error = payload.message || '服务暂不可用，请重试。'; },
      }, abort.signal);
    }
  } catch (error) {
    if (abort.signal.aborted) reply.stopped = true;
    else reply.error = error instanceof Error ? error.message : '服务暂不可用，请稍后重试。';
  } finally {
    reply.pending = false; loading.value = false;
    if (controller.value === abort) controller.value = null;
  }
}
function stop() { controller.value?.abort(); }
function retry(reply: Message) { void answer(reply); }
async function loadWrongChoices() {
  wrongLoading.value = true; wrongError.value = '';
  try { wrongOptions.value = (await getMyAnswerPageApi({ page: 0, size: 6, wrongOnly: true })).records ?? []; }
  catch { wrongError.value = '错题暂时无法加载，请重试。'; }
  finally { wrongLoading.value = false; }
}
function selectWrong(item: AnswerRecordSummary, reply: Message) { selectedWrong.value = item; void answer(reply, reply.resolution); }
function explainSource(source: PlatformSource) {
  void send(`请讲解题目 ${source.questionId} 的解题思路`, { intent: 'PLATFORM', query: `请讲解这道题：${source.text}`.slice(0, 1600), days: 3, excludeAttempted: false });
}
async function openSavedPlans() {
  showSavedPlans.value = !showSavedPlans.value;
  if (!showSavedPlans.value) return;
  plansLoading.value = true; plansError.value = '';
  try { savedPlans.value = await getLearningPlansApi(); }
  catch { plansError.value = '暂时无法读取计划，请稍后重试。'; }
  finally { plansLoading.value = false; }
}
function rememberPlan(plan: LearningPlanView) { savedPlans.value = [plan, ...savedPlans.value.filter(p => p.id !== plan.id)]; }
function openPlan(plan: LearningPlanView) {
  const reply = assistantMessage('查看我的复习计划'); reply.kind = 'plan'; reply.days = plan.days; reply.savedPlan = plan;
  messages.value.push(reply); showSavedPlans.value = false;
}
function newConversation() {
  if (busy.value) return;
  messages.value = []; previous.value = null; selectedWrong.value = null; question.value = ''; showSavedPlans.value = false;
  conversationKey.value = crypto.randomUUID(); composer.value?.focus();
}
watch(() => messages.value.map(m => `${m.content.length}:${m.pending}:${m.needsWrong}:${m.kind}`).join('|'), async () => {
  await nextTick(); const el = thread.value;
  if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 220) conversationEnd.value?.scrollIntoView({ block: 'nearest' });
});
onBeforeUnmount(stop);
</script>

<style scoped>
.study-assistant { --assistant-width:780px; width:100%; max-width:1100px; margin:0 auto; display:flex; flex:1; flex-direction:column; min-height:calc(100dvh - 230px); }
.conversation-tools { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; }
.assistant-presence { display:flex; align-items:center; gap:8px; color:#788579; font-size:12px; }
.assistant-presence>span { width:6px; height:6px; border-radius:50%; background:#60947e; }
.conversation-tools>div { display:flex; gap:18px; }
.text-action { border:0; padding:6px 0; color:var(--text-muted); background:none; font-size:12px; cursor:pointer; }
.text-action:hover { color:var(--brand); }
.text-action:disabled { opacity:.45; cursor:default; }
.assistant-welcome { text-align:center; padding:clamp(45px,9vh,110px) 16px 30px; }
.welcome-mark { display:grid; place-items:center; width:58px; height:58px; margin:0 auto 22px; color:var(--brand); background:#e8f1ec; border-radius:20px; transform:rotate(-7deg); }
.welcome-mark :deep(svg) { width:30px; height:30px; transform:rotate(7deg); }
.welcome-eyebrow { margin:0 0 12px; color:#788579; font-size:12px; letter-spacing:.16em; }
.assistant-welcome h2 { font-size:clamp(27px,3.4vw,38px); letter-spacing:-.04em; font-weight:600; margin:0 0 16px; }
.welcome-description { margin:0; color:var(--text-muted); font-size:14px; line-height:1.8; }
.composer-region { width:100%; max-width:var(--assistant-width); margin:0 auto; padding:8px 0 20px; }
.assistant-composer { padding:16px 18px 12px; border:1px solid #d8e0db; background:#fff; border-radius:20px; box-shadow:0 8px 30px #1e493809; transition:border-color .2s,box-shadow .2s; }
.assistant-composer:focus-within { border-color:#89ac9d; box-shadow:0 4px 24px #1e493810; }
.assistant-composer textarea { display:block; width:100%; min-height:74px; max-height:160px; padding:4px 0; resize:vertical; line-height:1.8; font:inherit; border:0 !important; border-radius:0 !important; background:none !important; box-shadow:none !important; outline:none !important; }
.assistant-composer textarea:focus { box-shadow:none !important; border:0 !important; }
.composer-bottom { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:8px; }
.composer-bottom>span { color:#8a958e; font-size:11px; }
.send-action { display:grid; place-items:center; width:36px; height:36px; flex-shrink:0; border:0; border-radius:12px; color:white; background:var(--brand); cursor:pointer; }
.send-action :deep(svg) { transform:rotate(-90deg); width:20px; height:20px; }
.send-action:disabled { background:#e8ede9; color:#a2ada5; cursor:default; }
.stop-action { background:var(--text); font-size:13px; }
.suggested-questions { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px; margin-top:18px; }
.suggested-questions button { display:flex; align-items:center; gap:10px; min-height:58px; padding:12px 14px; border:1px solid var(--line); border-radius:12px; background:#ffffff80; text-align:left; color:#52625b; cursor:pointer; font:inherit; font-size:12px; }
.suggested-questions button:hover { background:#fff; border-color:#b5c9bd; }
.suggested-questions :deep(svg) { width:17px; height:17px; color:#718d7e; }
.suggestion-arrow { margin-left:auto; color:#9da9a1; }
.composer-footnote { text-align:center; font-size:11px; color:#929a94; margin:16px 0 0; }
.has-conversation { height:calc(100dvh - 220px); min-height:420px; }
.has-conversation .composer-region { padding:14px 0 0; }
.conversation-thread { overflow-y:auto; overscroll-behavior:contain; min-height:0; flex:1; padding:24px max(8px,calc((100% - var(--assistant-width)) / 2)); scrollbar-width:thin; }
.conversation-message { display:flex; gap:14px; margin-bottom:30px; min-width:0; }
.conversation-message.user { justify-content:flex-end; }
.user-bubble { max-width:86%; background:#eaf0ec; border-radius:16px 16px 4px 16px; padding:12px 18px; color:#344b3d; line-height:1.8; white-space:pre-wrap; overflow-wrap:anywhere; }
.assistant-avatar { display:grid; place-items:center; width:30px; height:30px; border-radius:10px; background:#e4eee7; color:var(--brand); flex-shrink:0; }
.assistant-avatar :deep(svg) { width:17px; height:17px; }
.assistant-response { flex:1; min-width:0; padding-top:3px; }
.response-author { display:flex; gap:12px; align-items:center; margin:0 0 12px; font-size:12px; font-weight:600; color:#45594c; }
.response-author>span { font-size:11px; color:#829287; font-weight:400; }
.answer-markdown { line-height:1.85; overflow-wrap:anywhere; }
.answer-markdown :deep(p) { margin:0 0 12px; }
.answer-markdown :deep(p:last-child) { margin-bottom:0; }
.answer-markdown :deep(h1),.answer-markdown :deep(h2),.answer-markdown :deep(h3) { font-size:1.1em; margin:20px 0 10px; }
.answer-markdown :deep(pre) { overflow-x:auto; background:var(--bg-soft); padding:14px; border-radius:10px; }
.answer-markdown :deep(table) { display:block; overflow-x:auto; border-collapse:collapse; max-width:100%; }
.answer-markdown :deep(th),.answer-markdown :deep(td) { padding:8px 12px; border:1px solid var(--line); }
.answer-markdown :deep(blockquote) { border-left:2px solid #a4b9aa; margin:10px 0; padding-left:14px; color:var(--text-muted); }
.answer-markdown :deep(.katex-display) { overflow-x:auto; overflow-y:hidden; padding:4px 0; }
.thinking-status { display:flex; gap:9px; align-items:center; font-size:13px; color:#738277; padding:4px 0; }
.thinking-dot { width:7px; height:7px; border-radius:50%; background:#7e9f88; animation:thinking 1.4s ease-in-out infinite; }
@keyframes thinking { 50% { opacity:.3; } }
.response-note { color:var(--text-muted); font-size:12px; }
.response-error { color:#a83e37; font-size:13px; line-height:1.8; }
.response-error button,.wrong-choices>p button { border:0; background:none; color:var(--brand); text-decoration:underline; cursor:pointer; }
.wrong-choices { display:grid; gap:8px; }
.wrong-choices p { margin:0 0 8px; line-height:1.8; }
.wrong-choices>button { display:flex; justify-content:space-between; gap:15px; text-align:left; padding:12px 14px; line-height:1.7; border:1px solid var(--line); background:var(--panel); border-radius:10px; color:var(--text); cursor:pointer; }
.wrong-choices>a { margin-top:8px; font-size:13px; }
.question-context { display:flex; align-items:center; gap:8px; padding:7px 10px; margin-bottom:10px; background:#f0f5f1; border-radius:8px; font-size:12px; color:#526b5a; }
.question-context>span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.question-context>button { margin-left:auto; border:0; background:none; color:inherit; cursor:pointer; }
.question-context :deep(svg) { width:15px; height:15px; }
.saved-plans { padding:18px; margin-bottom:14px; border:1px solid var(--line); border-radius:14px; background:var(--panel); }
.saved-plans header { display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; }
.saved-plans p { color:var(--text-muted); font-size:13px; }
.saved-plan-link { display:flex; width:100%; gap:10px; padding:12px 0; align-items:center; border:0; border-top:1px solid var(--line); background:none; text-align:left; color:var(--text); cursor:pointer; }
.saved-plan-link>span:last-child { margin-left:auto; }
button:focus-visible { outline:2px solid var(--brand); outline-offset:3px; }
@media(max-width:640px) {
  .study-assistant { min-height:calc(100dvh - 240px); }
  .assistant-welcome { padding:45px 0 28px; }
  .welcome-description { font-size:13px; }
  .conversation-tools { gap:8px; }
  .assistant-presence { font-size:11px; }
  .conversation-tools>div { gap:12px; }
  .text-action { font-size:11px; }
  .suggested-questions { grid-template-columns:1fr; gap:8px; margin-top:14px; }
  .suggested-questions button { min-height:44px; padding:10px 14px; }
  .composer-bottom>span { font-size:10px; }
  .assistant-composer { padding:12px 14px; border-radius:16px; }
  .has-conversation { height:calc(100dvh - 225px); min-height:360px; }
  .conversation-thread { padding:18px 0; }
  .conversation-message { gap:9px; margin-bottom:24px; }
  .assistant-avatar { width:26px; height:26px; border-radius:8px; }
  .response-author { flex-wrap:wrap; gap:6px; }
  .user-bubble { max-width:93%; padding:10px 14px; }
  .composer-footnote { margin-top:10px; }
}
@media(prefers-reduced-motion:reduce) { .thinking-dot { animation:none; } }
</style>
