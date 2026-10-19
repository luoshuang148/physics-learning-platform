<template>
  <AppShell :subtitle="`教师总览 · ${auth.user?.realName ?? ''}`">
    <div class="dashboard-page">
      <p v-if="dashboardError" role="alert" class="dashboard-error">{{ dashboardError }} <button @click="loadDashboard">重试</button></p>

      <section class="overview-strip">
        <div class="summary-card">
          <span>进行中任务</span>
          <strong>{{ activeTasks.length }}</strong>
          <small>草稿 {{ draftCount }} · 已截止 {{ closedCount }}</small>
        </div>
        <div class="summary-card">
          <span>作业最多参与人数</span>
          <strong>{{ totalStudents }}</strong>
          <small>按各作业应参与名单统计</small>
        </div>
        <div class="summary-card">
          <span>待完成人次</span>
          <strong>{{ pendingCount }}</strong>
          <small>开放作业中尚未答完</small>
        </div>
        <div class="summary-card">
          <span>首次正确率</span>
          <strong>{{ overallAccuracy }}%</strong>
          <small>{{ totalCorrect }} / {{ totalAnswered }}</small>
        </div>
      </section>

      <section class="task-section">
        <div class="section-head">
          <h2>任务概览</h2>
          <RouterLink to="/teacher/event-manage" class="ghost-btn">任务管理</RouterLink>
        </div>
        <div v-if="loading" class="panel-empty">加载任务概览…</div>
        <div v-else-if="tasks.length === 0" class="panel-empty">暂无任务</div>
        <div v-else class="task-list">
          <article v-for="task in tasks" :key="task.taskId" class="task-card" :class="statusClass(task)">
            <div class="task-main">
              <div class="task-head">
                <h3>{{ task.title }}</h3>
                <span class="status-pill">{{ statusText(task) }}</span>
              </div>
              <div class="task-meta">
                <span>{{ task.questionCount }} 题</span>
                <span>{{ dueText(task) }}</span>
              </div>
            </div>
            <div class="task-progress">
              <div class="progress-label"><span>完成</span><strong>{{ task.completedStudents }} / {{ task.totalStudents }}</strong></div>
              <div class="progress-track"><div class="progress-fill" :style="{ width: progressWidth(task.completionRate) }" /></div>
            </div>
            <div class="task-numbers">
              <span>完成率 {{ task.completionRate }}%</span>
              <span>正确率 {{ task.accuracy }}%</span>
              <span v-if="task.status !== 'DRAFT'">{{ studentProgressText('NOT_STARTED', task.availability) }} {{ task.notStartedStudents }}</span>
            </div>
            <RouterLink :to="'/teacher/assignments/' + task.taskId" class="ghost-btn">作业详情</RouterLink>
          </article>
        </div>
      </section>

    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import { useAuthStore } from '@/stores/auth';
import { getTeacherTaskOverviewApi } from '@/api/teacher-event';
import type { TaskOverviewRow } from '@/types/teacher-event';
import { studentProgressText, taskAvailabilityText } from '@/utils/assignmentProgress';

const auth = useAuthStore();
const loading = ref(true);
const dashboardError = ref('');
const tasks = ref<TaskOverviewRow[]>([]);

const activeTasks = computed(() => tasks.value.filter(task => task.status === 'PUBLISHED' && task.availability === 'OPEN'));
const draftCount = computed(() => tasks.value.filter(task => task.status === 'DRAFT').length);
const closedCount = computed(() => tasks.value.filter(task => task.status === 'CLOSED' || task.availability === 'ENDED').length);
const pendingCount = computed(() => activeTasks.value.reduce((sum, task) => sum + Math.max(0, task.totalStudents - task.completedStudents), 0));
const totalStudents = computed(() => Math.max(0, ...tasks.value.map(task => task.totalStudents)));
const totalAnswered = computed(() => tasks.value.reduce((sum, task) => sum + task.answeredQuestions, 0));
const totalCorrect = computed(() => tasks.value.reduce((sum, task) => sum + task.correctQuestions, 0));
const overallAccuracy = computed(() => totalAnswered.value ? Math.round(totalCorrect.value / totalAnswered.value * 1000) / 10 : 0);
const statusText = (task: TaskOverviewRow) => taskAvailabilityText(task.availability ?? task.status);
const statusClass = (task: TaskOverviewRow) => ({
  draft: task.status === 'DRAFT',
  scheduled: task.availability === 'SCHEDULED',
  closed: task.status === 'CLOSED' || task.availability === 'ENDED',
});
const dueText = (task: TaskOverviewRow) => task.dueAt ? '截止 ' + formatDate(task.dueAt) : '不限截止';
const progressWidth = (value: number) => Math.max(0, Math.min(100, value)) + '%';
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const loadDashboard = async () => {
  loading.value = true; dashboardError.value = '';
  try {
    tasks.value = await getTeacherTaskOverviewApi();
  } catch { dashboardError.value = '统计加载失败'; }
  finally { loading.value = false; }
};
onMounted(loadDashboard);
</script>

<style scoped>
.dashboard-page { display: grid; gap: 16px; }
.dashboard-error { color: #b91c1c; font-size: 12px; }
.overview-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.summary-card { display: grid; gap: 4px; padding: 14px 16px; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; }
.summary-card span { color: #64748b; font-size: 11px; }
.summary-card strong { color: #0f766e; font-size: 26px; line-height: 1.1; }
.summary-card small { color: #94a3b8; font-size: 10px; }
.task-section { padding: 16px 18px; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-head h2 { margin: 0; color: #172033; font-size: 16px; }
.task-list { display: grid; gap: 8px; }
.task-card { display: grid; grid-template-columns: minmax(220px, 1.4fr) minmax(180px, .8fr) minmax(180px, .8fr) 104px; gap: 14px; align-items: center; padding: 12px 14px; border: 1px solid #eef2f7; border-radius: 8px; background: #fff; }
.task-card.draft { background: #fffdf5; border-color: #fde68a; }
.task-card.closed { background: #f8fafc; }
.task-head { display: flex; align-items: center; gap: 8px; }
.task-head h3 { margin: 0; color: #172033; font-size: 13px; }
.task-main { min-width: 0; display: grid; gap: 4px; }
.task-meta { display: flex; gap: 12px; color: #64748b; font-size: 11px; }
.status-pill { padding: 1px 6px; border-radius: 4px; background: #ecfdf5; color: #0f766e; font-size: 10px; white-space: nowrap; }
.task-card.draft .status-pill { background: #fef3c7; color: #92400e; }
.task-card.scheduled .status-pill { background: #fef3c7; color: #92400e; }
.task-card.closed .status-pill { background: #f1f5f9; color: #64748b; }
.task-progress { display: grid; gap: 5px; }
.progress-label { display: flex; justify-content: space-between; color: #64748b; font-size: 10px; }
.progress-label strong { color: #334155; font-weight: 600; }
.progress-track { height: 6px; border-radius: 999px; background: #f1f5f9; overflow: hidden; }
.progress-fill { height: 100%; border-radius: inherit; background: #0f766e; }
.task-numbers { display: flex; flex-wrap: wrap; gap: 4px 10px; color: #64748b; font-size: 10px; }
.task-card > a { justify-self: end; white-space: nowrap; min-width: 88px; }
@media (max-width: 1100px) {
  .overview-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .task-card { grid-template-columns: 1fr 1fr; }
  .task-numbers { grid-column: 1 / -1; }
  .task-card > a { justify-self: start; }
}
@media (max-width: 640px) {
  .task-card { grid-template-columns: 1fr; }
}
</style>
