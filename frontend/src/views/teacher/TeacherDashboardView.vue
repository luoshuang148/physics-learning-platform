<template>
  <AppShell hide-heading :subtitle="`教师总览 · ${auth.user?.realName ?? ''}`">
    <div class="tw-page">
      <header class="tw-header">
        <div>
          <h1>教学总览</h1>
          <p>
            {{ classroom.activeClass?.name ?? '当前班级' }} · 先看需要跟进的任务，再安排下一次练习。
          </p>
        </div>
        <RouterLink to="/teacher/event-manage?create=1" class="tw-button primary"
          ><AppIcon name="task" />新建任务</RouterLink
        >
      </header>
      <p v-if="dashboardError" role="alert" class="tw-error">
        {{ dashboardError }} <button class="tw-link" @click="loadDashboard">重新加载</button>
      </p>
      <section class="tw-metrics" aria-label="班级概况">
        <div class="tw-metric">
          <span>班级学生</span><strong>{{ classroom.activeClass?.studentCount ?? '—' }}</strong
          ><small>当前班级成员人数</small>
        </div>
        <div class="tw-metric">
          <span>进行中任务</span
          ><strong>{{ loading || dashboardError ? '—' : activeTasks.length }}</strong
          ><small>{{
            loading || dashboardError ? '任务统计暂未读取' : `${dueSoonCount} 个将在 48 小时内截止`
          }}</small>
        </div>
        <div class="tw-metric">
          <span>待完成人次</span
          ><strong>{{ loading || dashboardError ? '—' : pendingCount }}</strong
          ><small>进行中任务的未完成人数之和</small>
        </div>
        <div class="tw-metric">
          <span>首次作答正确率</span
          ><strong>{{
            loading || dashboardError || !totalAnswered ? '—' : overallAccuracy + '%'
          }}</strong
          ><small>{{
            loading || dashboardError
              ? '作答统计暂未读取'
              : totalAnswered
                ? `累计首次作答 ${totalAnswered} 次`
                : '学生作答后显示统计'
          }}</small>
        </div>
      </section>
      <div class="overview-layout">
        <section class="tw-surface priority-section" aria-label="待跟进任务">
          <div class="tw-section-head">
            <div>
              <h2>
                待跟进任务
                <span class="tw-count">{{
                  loading || dashboardError ? '—' : attentionTasks.length
                }}</span>
              </h2>
              <p>优先显示临近截止的任务，最多展示 5 项。</p>
            </div>
            <RouterLink to="/teacher/event-manage" class="tw-link">全部任务 →</RouterLink>
          </div>
          <div v-if="loading" class="tw-empty" role="status">正在读取教学安排…</div>
          <div v-else-if="dashboardError" class="tw-empty">任务数据暂时无法读取，请重新加载。</div>
          <div v-else-if="!attentionTasks.length" class="tw-empty">
            <strong>{{ activeTasks.length ? '当前任务均已完成' : '暂无待跟进任务' }}</strong
            >{{
              activeTasks.length
                ? '可以查看学情，了解学生的掌握情况。'
                : '发布新任务后，可在这里跟进学生完成进度。'
            }}
            <div class="empty-action">
              <RouterLink
                :to="
                  activeTasks.length
                    ? '/teacher/student-analysis'
                    : '/teacher/event-manage?create=1'
                "
                class="tw-button"
                >{{ activeTasks.length ? '查看学生学情' : '新建任务' }}</RouterLink
              >
            </div>
          </div>
          <div v-else class="priority-list">
            <article
              v-for="task in attentionTasks.slice(0, 5)"
              :key="task.taskId"
              class="priority-task"
            >
              <div class="priority-heading">
                <RouterLink :to="`/teacher/assignments/${task.taskId}`">{{ task.title }}</RouterLink
                ><span class="tw-badge" :class="{ amber: isDueSoon(task) }">{{
                  isDueSoon(task) ? '即将截止' : '进行中'
                }}</span>
              </div>
              <p class="priority-meta">
                {{ task.questionCount }} 道题 <span>·</span>
                {{ task.dueAt ? formatDate(task.dueAt) + ' 截止' : '不限截止时间' }}
              </p>
              <div class="priority-bottom">
                <div class="priority-progress">
                  <div>
                    <span>已完成 {{ task.completedStudents }} / {{ task.totalStudents }} 人</span
                    ><strong>{{ task.completionRate }}%</strong>
                  </div>
                  <progress
                    :value="Math.max(0, Math.min(100, task.completionRate))"
                    max="100"
                    :aria-label="`${task.title}完成率`"
                  />
                </div>
                <RouterLink :to="`/teacher/assignments/${task.taskId}`" class="tw-link"
                  >跟进 {{ Math.max(0, task.totalStudents - task.completedStudents) }} 人
                  <span aria-hidden="true">→</span></RouterLink
                >
              </div>
            </article>
          </div>
        </section>
        <aside class="overview-aside">
          <section class="tw-surface">
            <div class="tw-section-head">
              <div>
                <h2>接下来的安排</h2>
                <p>已发布，等待开始</p>
              </div>
              <span class="tw-count">{{
                loading || dashboardError ? '—' : scheduledTasks.length
              }}</span>
            </div>
            <p v-if="loading" class="upcoming-empty">正在加载…</p>
            <p v-else-if="dashboardError" class="upcoming-empty">暂时无法读取</p>
            <p v-else-if="!scheduledTasks.length" class="upcoming-empty">暂无定时开始的任务</p>
            <div v-else class="upcoming-list">
              <RouterLink
                v-for="task in scheduledTasks.slice(0, 3)"
                :key="task.taskId"
                :to="`/teacher/assignments/${task.taskId}`"
                ><span class="upcoming-icon"><AppIcon name="event-manage" /></span>
                <div>
                  <strong>{{ task.title }}</strong
                  ><small>{{
                    task.startAt ? formatDate(task.startAt) + ' 开始' : '待设置开始时间'
                  }}</small>
                </div></RouterLink
              >
            </div>
            <RouterLink to="/teacher/event-manage?status=draft" class="draft-shortcut"
              ><span>未发布的草稿</span
              ><strong>{{ loading || dashboardError ? '—' : draftCount }}</strong
              ><span aria-hidden="true">→</span></RouterLink
            >
          </section>
          <section class="tw-surface">
            <div class="tw-section-head"><h2>常用操作</h2></div>
            <div class="quick-links">
              <RouterLink to="/teacher/student-analysis"
                ><AppIcon name="student-analysis" />
                <div><strong>查看学生学情</strong><small>查看作答记录与学习报告</small></div>
                <AppIcon name="arrow" /></RouterLink
              ><RouterLink to="/teacher/questions"
                ><AppIcon name="questions" />
                <div><strong>整理教学题库</strong><small>预览题目，补充练习内容</small></div>
                <AppIcon name="arrow"
              /></RouterLink>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </AppShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import '@/assets/teacher-workspace.css';
import AppShell from '@/layouts/AppShell.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useAuthStore } from '@/stores/auth';
import { useClassroomStore } from '@/stores/classroom';
import { getTeacherTaskOverviewApi } from '@/api/teacher-event';
import type { TaskOverviewRow } from '@/types/teacher-event';
const auth = useAuthStore(),
  classroom = useClassroomStore();
const loading = ref(true),
  dashboardError = ref(''),
  tasks = ref<TaskOverviewRow[]>([]),
  now = ref(Date.now());
let alive = true,
  sequence = 0;
const visibleTasks = computed(() =>
  tasks.value.filter(
    (task) => task.classId === classroom.activeClassId && task.status !== 'ARCHIVED',
  ),
);
const activeTasks = computed(() =>
  visibleTasks.value.filter((task) => task.status === 'PUBLISHED' && task.availability === 'OPEN'),
);
const draftCount = computed(
  () => visibleTasks.value.filter((task) => task.status === 'DRAFT').length,
);
const scheduledTasks = computed(() =>
  visibleTasks.value
    .filter((task) => task.status === 'PUBLISHED' && task.availability === 'SCHEDULED')
    .sort((a, b) => deadline(a.startAt) - deadline(b.startAt)),
);
const pendingCount = computed(() =>
  activeTasks.value.reduce(
    (sum, task) => sum + Math.max(0, task.totalStudents - task.completedStudents),
    0,
  ),
);
const totalAnswered = computed(() =>
  visibleTasks.value.reduce((sum, task) => sum + task.answeredQuestions, 0),
);
const totalCorrect = computed(() =>
  visibleTasks.value.reduce((sum, task) => sum + task.correctQuestions, 0),
);
const overallAccuracy = computed(() =>
  totalAnswered.value ? Math.round((totalCorrect.value / totalAnswered.value) * 1000) / 10 : 0,
);
const deadline = (date: string | null) => (date ? new Date(date).getTime() : Infinity);
const attentionTasks = computed(() =>
  activeTasks.value
    .filter((task) => task.completedStudents < task.totalStudents)
    .sort(
      (a, b) =>
        deadline(a.dueAt) - deadline(b.dueAt) ||
        b.totalStudents - b.completedStudents - (a.totalStudents - a.completedStudents),
    ),
);
const isDueSoon = (task: TaskOverviewRow) =>
  !!task.dueAt &&
  deadline(task.dueAt) >= now.value &&
  deadline(task.dueAt) - now.value <= 48 * 60 * 60 * 1000;
const dueSoonCount = computed(() => activeTasks.value.filter(isDueSoon).length);
const formatDate = (date: string) =>
  new Date(date).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
async function loadDashboard() {
  const owner = ++sequence;
  loading.value = true;
  dashboardError.value = '';
  try {
    const data = await getTeacherTaskOverviewApi();
    if (!classroom.classes.length) await classroom.loadClasses();
    if (alive && owner === sequence) {
      tasks.value = data;
      now.value = Date.now();
    }
  } catch {
    if (alive && owner === sequence) {
      tasks.value = [];
      dashboardError.value = '教学数据加载失败，请稍后重试。';
    }
  } finally {
    if (alive && owner === sequence) loading.value = false;
  }
}
onMounted(loadDashboard);
onBeforeUnmount(() => {
  alive = false;
  ++sequence;
});
</script>
<style scoped>
.overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 22px;
  align-items: start;
}
.overview-aside {
  display: grid;
  gap: 22px;
}
.priority-list {
  padding: 0 24px;
}
.priority-task {
  padding: 22px 0;
  border-top: 1px solid #edf2ee;
}
.priority-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.priority-heading > a {
  font-size: 15px;
  font-weight: 500;
  color: #365c43;
  text-decoration: none;
  overflow-wrap: anywhere;
}
.priority-meta {
  display: flex;
  gap: 9px;
  margin: 11px 0 20px;
  font-size: 12px;
  color: #86988c;
}
.priority-bottom {
  display: flex;
  align-items: center;
  gap: 26px;
}
.priority-progress {
  flex: 1;
  min-width: 0;
}
.priority-progress > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 11px;
  color: #84998b;
}
.priority-progress strong {
  font-weight: 400;
  color: #527d5e;
}
.priority-progress progress {
  display: block;
  width: 100%;
  height: 5px;
  border: 0;
  border-radius: 9px;
  overflow: hidden;
  accent-color: #609573;
  background: #edf3ef;
}
.priority-progress progress::-webkit-progress-bar {
  background: #edf3ef;
}
.priority-progress progress::-webkit-progress-value {
  background: #609573;
  border-radius: 9px;
}
.priority-progress progress::-moz-progress-bar {
  background: #609573;
}
.priority-bottom > .tw-link {
  flex-shrink: 0;
}
.empty-action {
  margin-top: 20px;
}
.upcoming-empty {
  font-size: 12px;
  padding: 0 24px 24px;
  margin: 0;
  color: #8b9e91;
}
.upcoming-list {
  padding: 0 24px 4px;
  display: grid;
  gap: 22px;
}
.upcoming-list > a {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
}
.upcoming-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: #f0f5f1;
  border-radius: 9px;
  color: #89a78f;
  flex-shrink: 0;
}
.upcoming-icon .app-icon {
  width: 17px;
  height: 17px;
}
.upcoming-list strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.upcoming-list small {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: #8c9f91;
}
.draft-shortcut {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 22px 24px 0;
  padding: 18px 0;
  border-top: 1px solid #eaf0ec;
  font-size: 12px;
  color: #7b9382;
  text-decoration: none;
}
.draft-shortcut strong {
  margin-left: auto;
  color: #5f8769;
  font-weight: 500;
}
.quick-links {
  display: grid;
  padding: 0 24px 24px;
  gap: 24px;
}
.quick-links > a {
  display: flex;
  align-items: center;
  gap: 12px;
  text-decoration: none;
  color: #7a9b83;
}
.quick-links strong {
  font-size: 13px;
  font-weight: 500;
  display: block;
}
.quick-links small {
  display: block;
  font-size: 11px;
  color: #8da293;
  margin-top: 6px;
}
.quick-links > a > .app-icon:last-child {
  margin-left: auto;
  width: 15px;
  height: 15px;
}
.tw-metrics .tw-metric > strong {
  color: #38674a;
}
@media (max-width: 1000px) {
  .overview-layout {
    grid-template-columns: 1fr;
  }
  .overview-aside {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 640px) {
  .overview-aside {
    grid-template-columns: 1fr;
  }
  .priority-list {
    padding-inline: 16px;
  }
  .priority-bottom {
    gap: 16px;
  }
  .priority-heading > a {
    font-size: 14px;
  }
  .priority-meta {
    flex-wrap: wrap;
    line-height: 1.7;
  }
  .priority-bottom > .tw-link {
    font-size: 11px;
  }
}
</style>
