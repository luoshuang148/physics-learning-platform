<template>
  <div class="assistant-plan">
    <form v-if="!plan.preview && !busy" class="plan-setup" @submit.prevent="generate">
      <p>{{ tasks.length ? '这份复习计划要参考哪次作业？' : '暂时没有可用作业，完成作业后就可以按薄弱点安排复习。' }}</p>
      <template v-if="tasks.length">
        <label for="plan-task">选择作业</label>
        <select id="plan-task" v-model.number="eventId" required>
          <option :value="null" disabled>选择一份作业</option>
          <option v-for="task in tasks" :key="task.id" :value="task.id">{{ task.eventTitle }}</option>
        </select>
        <label for="plan-days">复习天数</label>
        <select id="plan-days" v-model.number="days"><option v-for="day in 7" :key="day" :value="day">{{ day }} 天</option></select>
        <button type="submit" :disabled="!eventId">生成建议</button>
      </template>
    </form>
    <p v-if="busy" class="plan-progress" role="status">{{ plan.preview ? '正在更新计划…' : '正在结合你的作业和错题安排复习…' }}</p>
    <button v-if="busy && abort" type="button" class="plan-stop" @click="stop">停止生成</button>
    <p v-if="error" role="alert" class="plan-error">{{ error }}</p>
    <details v-if="plan.steps.length" class="plan-evidence"><summary>查看生成依据</summary><AgentExecutionSteps :steps="plan.steps" /></details>
    <LearningPlanCard :preview="plan.preview" :saved-plan="plan.savedPlan" :run-id="plan.runId ?? ''" :busy="busy"
      @save="save" @activate="activate" @refresh="refresh" @cancel="cancel" @practice="practice" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { streamLearningPlanPreview } from '@/api/ai';
import { getStudentTasksApi } from '@/api/teacher-event';
import { activateLearningPlanApi, cancelLearningPlanApi, getLearningPlanApi, saveLearningPlanApi } from '@/api/learning-plan';
import { acceptLearningPlanEvent, emptyLearningPlanState, startLearningPlanRun } from '@/composables/learningPlanState';
import type { LearningAssistantStep, LearningPlanPreview, LearningPlanView } from '@/types/learning-plan';
import AgentExecutionSteps from '@/components/AgentExecutionSteps.vue';
import LearningPlanCard from '@/components/LearningPlanCard.vue';

const props = defineProps<{ question: string; days: number; eventId?: number; saved?: LearningPlanView }>();
const emit = defineEmits<{ busy: [value: boolean]; saved: [value: LearningPlanView] }>();
const router = useRouter();
const plan = ref(emptyLearningPlanState());
const tasks = ref<Awaited<ReturnType<typeof getStudentTasksApi>>>([]);
const eventId = ref<number | null>(props.eventId ?? null);
const days = ref(props.days);
const busy = ref(false);
const error = ref('');
const abort = ref<AbortController | null>(null);
const requestId = ref('');
let disposed = false;
watch(busy, value => emit('busy', value), { flush: 'sync' });

async function generate() {
  if (!eventId.value || busy.value || disposed) return;
  busy.value = true;
  error.value = '';
  plan.value = emptyLearningPlanState();
  const controller = new AbortController();
  abort.value = controller;
  requestId.value = crypto.randomUUID();
  try {
    await streamLearningPlanPreview({ eventId: eventId.value, days: days.value, question: props.question, requestId: requestId.value }, {
      signal: controller.signal,
      onProgress: payload => {
        const event = payload as typeof payload & { runId?: string; requestId?: string };
        if (event.runId && event.requestId === requestId.value) plan.value = startLearningPlanRun(event.runId);
      },
      onToolStep: payload => {
        const step = payload as unknown as LearningAssistantStep;
        plan.value = acceptLearningPlanEvent(plan.value, step, current => ({ ...current, steps: [...current.steps, step] }));
      },
      onPlan: payload => {
        const event = payload as { runId: string; seq: number; planPreview: LearningPlanPreview };
        plan.value = acceptLearningPlanEvent(plan.value, event, current => ({ ...current, preview: event.planPreview }));
      },
      onError: payload => { error.value = payload.message || '暂时无法生成复习计划，请重试。'; },
    });
    if (!plan.value.preview && !error.value) error.value = '未收到完整的复习建议，请重试。';
  } catch (e) {
    error.value = controller.signal.aborted ? '已停止生成，可以调整后重试。' : e instanceof Error ? e.message : '复习建议暂不可用。';
  } finally { abort.value = null; busy.value = false; }
}
function stop() { abort.value?.abort(); }
async function update(action: () => Promise<LearningPlanView>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try { const saved = await action(); plan.value = { ...plan.value, savedPlan: saved }; emit('saved', saved); }
  catch (e) { error.value = e instanceof Error ? e.message : '计划更新失败，请重试。'; }
  finally { busy.value = false; }
}
function save() { if (plan.value.runId) void update(() => saveLearningPlanApi(plan.value.runId!, requestId.value)); }
function activate() { const p = plan.value.savedPlan; if (p) void update(() => activateLearningPlanApi(p.id, p.version)); }
function cancel() { const p = plan.value.savedPlan; if (p) void update(() => cancelLearningPlanApi(p.id, p.version)); }
function refresh() { const p = plan.value.savedPlan; if (p) void update(() => getLearningPlanApi(p.id)); }
function practice(questionId: number, sourceEventId: number) { void router.push({ path: '/student/practice', query: { eventId: sourceEventId, questionId } }); }

onMounted(async () => {
  if (props.saved) {
    const saved = props.saved;
    plan.value = { ...startLearningPlanRun(`saved-${saved.id}`), savedPlan: saved,
      preview: { days: saved.days, sourceEventId: saved.eventId, sourceAnswerRevision: saved.sourceAnswerRevision,
        executionMode: saved.executionMode, items: saved.items, evidenceSummary: saved.evidenceSummary, partial: false } };
    return;
  }
  busy.value = true;
  try {
    tasks.value = await getStudentTasksApi();
    if (disposed) return;
    if (!tasks.value.some(task => task.id === eventId.value)) eventId.value = tasks.value.length === 1 ? tasks.value[0]!.id : null;
  } catch { error.value = '暂时无法加载作业，请稍后重试。'; }
  finally { busy.value = false; }
  if (eventId.value) await generate();
});
onUnmounted(() => { disposed = true; stop(); emit('busy', false); });
</script>

<style scoped>
.assistant-plan { display:grid; gap:14px; min-width:0; }
.plan-setup { display:grid; grid-template-columns:1fr; gap:10px; max-width:420px; }
.plan-setup p { margin:0 0 6px; line-height:1.7; }
.plan-setup label { font-size:13px; color:var(--text-muted); }
.plan-setup select { width:100%; min-height:44px; padding:10px; }
.plan-setup button,.plan-stop { justify-self:start; border:0; border-radius:10px; padding:10px 16px; background:var(--brand); color:white; cursor:pointer; }
.plan-setup button:disabled { opacity:.5; }
.plan-stop { background:var(--bg-soft); color:var(--text); }
.plan-progress { color:var(--brand); margin:0; }
.plan-error { color:#b42318; margin:0; }
.plan-evidence { color:var(--text-muted); font-size:13px; }
.plan-evidence summary { cursor:pointer; margin-bottom:10px; }
</style>
