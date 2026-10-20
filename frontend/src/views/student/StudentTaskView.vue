<template>
  <AppShell :subtitle="`教师任务 · ${auth.user?.realName ?? ''}`">
    <section class="grid one">
      <GlassPanel title="教师作业">
        <div v-if="loading" class="muted">正在加载任务…</div>
        <div v-else-if="loadError" class="task-error" role="alert"><p>{{ loadError }}</p><button class="ghost-btn" @click="loadTasks">重试</button></div>
        <div v-else-if="results.length === 0" class="task-empty">
          <p class="muted">教师暂未发布任务，暂时不能答题。</p>
        </div>
        <div v-else>
          <div class="task-summary" aria-label="我的作业进度">
            <span>待完成 <strong>{{ pendingCount }}</strong></span>
            <span>已完成 <strong>{{ completedCount }}</strong></span>
            <span>已截止未答完 <strong>{{ overdueCount }}</strong></span>
          </div>
          <p class="task-help">每题提交后保存进度，答完全部题目即完成作业。继续作答会定位到下一道未答题。</p>
          <details class="task-definitions"><summary>状态说明</summary><p>开放期间：0 题为“未开始”，部分作答为“进行中”，全部答完为“已完成”（与答对题数无关）。截止后：0 题为“未交”，部分作答为“未完成”。“未开放”表示尚未到开始时间。</p></details>
          <div class="task-list">
          <article v-for="{ task, answeredQuestions, correctQuestions, progress, canResume } in taskRows" :key="task.id" class="task-item">
            <div class="task-copy">
              <div class="task-heading">
                <h3 class="task-title">{{ task.eventTitle }}</h3>
                <span class="task-status" :class="{ completed: progress.completed }">{{ progress.label }}</span>
                <span class="task-availability">{{ taskAvailabilityText(task.availability ?? task.status) }}</span>
              </div>
              <div class="assignment-meta">
                <span>发布教师：{{ task.teacherName }}</span>
                <span>开始：{{ task.startAt ? formatTime(task.startAt) : '立即' }}</span>
                <span>截止：{{ task.dueAt ? formatTime(task.dueAt) : '不限' }}</span>
              </div>
              <div class="task-progress">
                <div class="task-progress-label"><strong>已完成 {{ progress.answered }} / {{ progress.total }} 题</strong><span>{{ progress.percent }}%</span></div>
                <progress :value="progress.percent" max="100" :aria-label="task.eventTitle + '完成进度'" />
                <p>{{ progress.total ? progress.completed ? '全部题目已保存' : '还剩 ' + progress.remaining + ' 题' : '请等待教师配置题目' }}<span v-if="answeredQuestions > 0"> · 首次答对 {{ correctQuestions }} 题</span></p>
              </div>
            </div>
            <div v-if="answeredQuestions > 0 || canResume" class="task-actions">
              <RouterLink v-if="answeredQuestions > 0" class="task-review-link" :to="{ path: '/student/report', query: { eventId: task.id } }">查看结果 / 复盘</RouterLink>
              <button v-if="canResume" class="neon-btn primary" @click="enterTask(task)">{{ progress.answered ? '继续作答' : '开始作答' }}</button>
            </div>
          </article>
          </div>
        </div>
      </GlassPanel>
    </section>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import GlassPanel from '@/components/GlassPanel.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { getStudentTaskResultsApi, type StudentTaskResult } from '@/api/teacher-event';
import type { TeacherEventResponse } from '@/types/teacher-event';
import { useAuthStore } from '@/stores/auth';
import { sortStudentTasks, taskAvailabilityText } from '@/utils/assignmentProgress';

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';
const auth = useAuthStore();
const router = useRouter();
const { visible, type, message } = useToast();
const getTaskEntryEventIdKey = () => TASK_ENTRY_EVENT_ID_KEY_PREFIX + '_' + (auth.user?.id ?? 'anonymous');
const loading = ref(false);
const results = ref<StudentTaskResult[]>([]);
const taskRows = computed(() => sortStudentTasks(results.value));
const pendingCount = computed(() => taskRows.value.filter(row => row.canResume).length);
const completedCount = computed(() => taskRows.value.filter(row => row.progress.completed).length);
const overdueCount = computed(() => taskRows.value.filter(row => ['ENDED', 'CLOSED', 'ARCHIVED'].includes(row.task.availability ?? row.task.status) && !row.progress.completed).length);
const loadError = ref('');
const formatTime = (time: string) => new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const loadTasks = async () => {
  loading.value = true;
  loadError.value = '';
  try { results.value = await getStudentTaskResultsApi(); }
  catch { loadError.value = '任务加载失败，请稍后重试'; }
  finally { loading.value = false; }
};
const enterTask = async (task: TeacherEventResponse) => {
  sessionStorage.setItem(getTaskEntryEventIdKey(), String(task.id));
  await router.push({ path: '/student/question', query: { resume: '1' } });
};
onMounted(loadTasks);
</script>

<style scoped>
.task-summary { display: flex; flex-wrap: wrap; gap: 12px 28px; color: #64748b; font-size: 13px; margin-bottom: 12px; }
.task-summary strong { color: #1d715b; font-size: 22px; margin-left: 6px; }
.task-help, .task-definitions { color: #64748b; font-size: 12px; line-height: 1.7; }
.task-help { margin: 0 0 6px; }
.task-definitions { margin-bottom: 18px; }
.task-definitions summary { cursor: pointer; width: fit-content; }
.task-definitions p { max-width: 760px; margin: 6px 0; }
.task-copy { flex: 1; min-width: 0; }
.task-progress { margin: 14px 0 0; max-width: 430px; color: #64748b; font-size: 12px; }
.task-progress-label { display: flex; justify-content: space-between; gap: 16px; }
.task-progress-label strong { color: #476658; font-weight: 500; }
.task-progress progress { display: block; width: 100%; height: 6px; appearance: none; border: 0; border-radius: 6px; overflow: hidden; margin: 8px 0; background: #edf2ef; }
.task-progress progress::-webkit-progress-bar { background: #edf2ef; }
.task-progress progress::-webkit-progress-value { background: #42856b; border-radius: 6px; }
.task-progress progress::-moz-progress-bar { background: #42856b; }
.task-progress p { margin: 0; font-size: 11px; }
.task-availability { color: #849389; font-size: 11px; }
.task-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; flex-wrap: wrap; }
.task-review-link { font-size: 12px; color: #1d715b; text-decoration: none; border: 1px solid #dbe6e0; border-radius: 6px; padding: 9px 12px; }
.task-error { display: grid; gap: 12px; justify-items: start; color: #b91c1c; }
.task-list { display: grid; gap: 10px; }
.task-item { display: flex; justify-content: space-between; gap: 18px; align-items: center; padding: 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.task-heading { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.task-title { margin: 0; font-size: 18px; overflow-wrap: anywhere; }
.task-status { padding: 2px 7px; border-radius: 4px; background: #f5f2e9; color: #8c7646; font-size: 11px; }
.task-status.completed { background: #ecfdf5; color: #0f766e; }
.assignment-meta { margin-top: 8px; display: flex; gap: 14px; flex-wrap: wrap; color: #64748b; font-size: 12px; }
.task-empty { min-height: 120px; display: flex; align-items: center; }
@media (max-width: 700px) { .task-item { flex-direction: column; align-items: stretch; } }
</style>
