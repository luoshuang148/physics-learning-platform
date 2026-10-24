<template>
  <AppShell hide-heading subtitle="我的作业">
    <div class="sw-page">
      <header class="sw-header">
        <div>
          <h1>我的作业</h1>
          <p>找到下一份待完成的练习，按自己的节奏继续。</p>
        </div>
        <RouterLink to="/student/ai" class="sw-button"><AppIcon name="spark" />学习助手</RouterLink>
      </header>
      <section class="sw-surface">
        <div class="sw-section-head">
          <div>
            <h2>作业安排</h2>
            <p>每题提交后自动保存，答完全部题目即完成作业。</p>
          </div>
          <button class="sw-link" :disabled="loading" @click="loadTasks">刷新</button>
        </div>
        <div class="sw-tabs" role="tablist" aria-label="作业状态">
          <button
            v-for="(tab, index) in tabs"
            :id="`assignment-${tab.id}`"
            :key="tab.id"
            role="tab"
            :aria-selected="filter === tab.id"
            :tabindex="filter === tab.id ? 0 : -1"
            aria-controls="assignments-panel"
            @click="filter = tab.id"
            @keydown="navigateTabs($event, index)"
          >
            {{ tab.label
            }}<span v-if="!loading && !loadError" class="sw-count">{{
              tab.id === 'all'
                ? taskRows.length
                : taskRows.filter((row) => bucket(row) === tab.id).length
            }}</span>
          </button>
        </div>
        <div class="sw-toolbar">
          <input
            v-model="search"
            class="sw-input sw-search"
            aria-label="搜索作业"
            placeholder="搜索作业名称或教师…"
          /><span class="sw-muted">待完成作业按截止时间优先排列</span>
        </div>
        <div id="assignments-panel" role="tabpanel" :aria-labelledby="`assignment-${filter}`">
          <div v-if="loading" class="sw-empty" role="status">正在读取作业安排…</div>
          <p v-else-if="loadError" class="sw-error task-load-error" role="alert">
            {{ loadError }} <button class="sw-link" @click="loadTasks">重新加载</button>
          </p>
          <div v-else-if="!taskRows.length" class="sw-empty">
            <strong>暂时没有教师作业</strong>可以先复习错题，或向学习助手提问。
            <div class="sw-actions">
              <RouterLink class="sw-button primary" to="/student/wrong-book">去复习错题</RouterLink
              ><RouterLink class="sw-button" to="/student/ai">问学习助手</RouterLink>
            </div>
          </div>
          <div v-else-if="!filteredTasks.length" class="sw-empty">
            <strong>这里暂时没有作业</strong
            >{{
              search
                ? '试试其他名称或教师，或者切换作业状态。'
                : filter === 'pending'
                  ? '当前没有待完成作业，继续保持。'
                  : '可以切换其他状态查看作业。'
            }}
            <div v-if="search" class="sw-actions">
              <button class="sw-button" @click="search = ''">清除搜索</button>
            </div>
          </div>
          <div v-else class="assignment-list">
            <article v-for="row in pagedTasks" :key="row.task.id" class="assignment-row">
              <div class="assignment-symbol" :class="bucket(row)" aria-hidden="true">
                <AppIcon name="task" />
              </div>
              <div class="assignment-copy">
                <div class="assignment-heading">
                  <h3>{{ row.task.eventTitle }}</h3>
                  <span
                    class="sw-badge"
                    :class="{
                      amber: bucket(row) === 'pending',
                      neutral: bucket(row) === 'upcoming',
                      rose: bucket(row) === 'overdue',
                    }"
                    >{{ bucket(row) === 'upcoming' ? '未开放' : row.progress.label }}</span
                  >
                </div>
                <div class="assignment-meta">
                  <span>{{ row.task.teacherName }}</span
                  ><span>{{ row.progress.total }} 道题</span
                  ><span v-if="bucket(row) === 'upcoming'">{{
                    row.task.startAt ? formatTime(row.task.startAt) + ' 开始' : '等待开放'
                  }}</span
                  ><span v-else :class="{ urgent: isDueSoon(row) }">{{
                    row.task.dueAt ? formatTime(row.task.dueAt) + ' 截止' : '不限截止时间'
                  }}</span>
                </div>
                <div class="assignment-progress">
                  <div>
                    <span>已完成 {{ row.progress.answered }} / {{ row.progress.total }} 题</span
                    ><strong>{{ row.progress.percent }}%</strong>
                  </div>
                  <progress
                    class="sw-progress"
                    :value="row.progress.percent"
                    max="100"
                    :aria-label="row.task.eventTitle + '完成进度'"
                  />
                </div>
              </div>
              <div class="assignment-actions">
                <button v-if="row.canResume" class="sw-button primary" @click="enterTask(row.task)">
                  {{ row.progress.answered ? '继续作答' : '开始作答'
                  }}<AppIcon name="arrow" /></button
                ><RouterLink
                  v-if="row.answeredQuestions > 0 || bucket(row) === 'overdue'"
                  class="sw-link"
                  :to="{ path: '/student/report', query: { eventId: row.task.id } }"
                  >查看结果 / 复盘</RouterLink
                ><span v-else-if="!row.canResume" class="sw-muted">{{
                  row.progress.total
                    ? taskAvailabilityText(row.task.availability ?? row.task.status)
                    : '等待教师配置题目'
                }}</span>
              </div>
            </article>
          </div>
        </div>
        <footer v-if="!loading && !loadError && filteredTasks.length" class="sw-pagination">
          <span>共 {{ filteredTasks.length }} 份作业 · 每页 10 份</span>
          <nav aria-label="作业分页">
            <button class="sw-button" :disabled="page <= 1" @click="page--">上一页</button
            ><span>{{ page }} / {{ pages }}</span
            ><button class="sw-button" :disabled="page >= pages" @click="page++">下一页</button>
          </nav>
        </footer>
      </section>
    </div>
  </AppShell>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import '@/assets/student-workspace.css';
import AppShell from '@/layouts/AppShell.vue';
import AppIcon from '@/components/AppIcon.vue';
import { getStudentTaskResultsApi, type StudentTaskResult } from '@/api/teacher-event';
import type { TeacherEventResponse } from '@/types/teacher-event';
import { useAuthStore } from '@/stores/auth';
import { sortStudentTasks, taskAvailabilityText } from '@/utils/assignmentProgress';
type Filter = 'all' | 'pending' | 'upcoming' | 'completed' | 'overdue';
const auth = useAuthStore(),
  router = useRouter(),
  route = useRoute();
const tabs: Array<{ id: Filter; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'pending', label: '待完成' },
  { id: 'upcoming', label: '未开放' },
  { id: 'completed', label: '已完成' },
  { id: 'overdue', label: '已截止未完成' },
];
const filter = ref<Filter>(
    tabs.some((tab) => tab.id === route.query.status) ? (route.query.status as Filter) : 'all',
  ),
  search = ref(''),
  page = ref(1),
  loading = ref(true),
  loadError = ref(''),
  results = ref<StudentTaskResult[]>([]),
  now = ref(Date.now());
type TaskRow = ReturnType<typeof sortStudentTasks>[number];
const bucket = (row: TaskRow): Exclude<Filter, 'all'> =>
  row.progress.completed
    ? 'completed'
    : row.task.availability === 'SCHEDULED'
      ? 'upcoming'
      : ['ENDED', 'CLOSED', 'ARCHIVED'].includes(row.task.availability ?? row.task.status)
        ? 'overdue'
        : 'pending';
const taskRows = computed(() =>
  sortStudentTasks(results.value).sort((a, b) =>
    bucket(a) === 'pending' && bucket(b) === 'pending'
      ? (a.task.dueAt ? new Date(a.task.dueAt).getTime() : Infinity) -
          (b.task.dueAt ? new Date(b.task.dueAt).getTime() : Infinity) || 0
      : 0,
  ),
);
const filteredTasks = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return taskRows.value.filter(
    (row) =>
      (filter.value === 'all' || bucket(row) === filter.value) &&
      (!keyword ||
        `${row.task.eventTitle} ${row.task.teacherName}`.toLowerCase().includes(keyword)),
  );
});
const pages = computed(() => Math.max(1, Math.ceil(filteredTasks.value.length / 10))),
  pagedTasks = computed(() => filteredTasks.value.slice((page.value - 1) * 10, page.value * 10));
watch([filter, search], () => {
  page.value = 1;
});
watch(pages, (value) => {
  page.value = Math.min(page.value, value);
});
let alive = true,
  sequence = 0;
async function loadTasks() {
  const owner = ++sequence;
  loading.value = true;
  loadError.value = '';
  try {
    const data = await getStudentTaskResultsApi();
    if (alive && owner === sequence) {
      results.value = data;
      now.value = Date.now();
    }
  } catch {
    if (alive && owner === sequence) loadError.value = '作业加载失败，请稍后重试。';
  } finally {
    if (alive && owner === sequence) loading.value = false;
  }
}
const formatTime = (date: string) =>
  new Date(date).toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
const isDueSoon = (row: TaskRow) =>
  bucket(row) === 'pending' &&
  !!row.task.dueAt &&
  new Date(row.task.dueAt).getTime() - now.value >= 0 &&
  new Date(row.task.dueAt).getTime() - now.value <= 48 * 60 * 60 * 1000;
async function enterTask(task: TeacherEventResponse) {
  sessionStorage.setItem(
    'student_task_entry_event_id_' + (auth.user?.id ?? 'anonymous'),
    String(task.id),
  );
  await router.push({ path: '/student/question', query: { resume: '1' } });
}
async function navigateTabs(event: KeyboardEvent, index: number) {
  let next: number;
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
  else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = tabs.length - 1;
  else return;
  event.preventDefault();
  filter.value = tabs[next]!.id;
  await nextTick();
  document.getElementById(`assignment-${filter.value}`)?.focus();
}
watch(
  () => route.query.status,
  (value) => {
    filter.value = tabs.some((tab) => tab.id === value) ? (value as Filter) : 'all';
  },
);
onMounted(loadTasks);
onBeforeUnmount(() => {
  alive = false;
  ++sequence;
});
</script>
<style scoped>
.task-load-error {
  margin: 0 24px 24px;
}
.assignment-list {
  padding: 0 24px;
}
.assignment-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 25px 0;
  border-top: 1px solid #eaf0ec;
}
.assignment-symbol {
  width: 44px;
  height: 48px;
  display: grid;
  place-items: center;
  background: #f2f6f3;
  border-radius: 11px;
  color: #91aa99;
  flex-shrink: 0;
}
.assignment-symbol.pending {
  background: #f7f4e9;
  color: #ad9a6b;
}
.assignment-symbol.completed {
  background: #edf6ef;
  color: #73a07c;
}
.assignment-copy {
  flex: 1;
  min-width: 0;
}
.assignment-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.assignment-heading h3 {
  font-size: 16px;
  font-weight: 500;
  margin: 0;
  color: #3e5f48;
  overflow-wrap: anywhere;
}
.assignment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #8c9e91;
  font-size: 12px;
  line-height: 1.8;
  margin: 10px 0 16px;
}
.assignment-meta > .urgent {
  color: #ac8651;
}
.assignment-progress {
  max-width: 350px;
}
.assignment-progress > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #91a496;
  margin-bottom: 8px;
  gap: 16px;
}
.assignment-progress strong {
  font-weight: 400;
  color: #6f977a;
}
.assignment-actions {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 15px;
  flex-shrink: 0;
}
.assignment-actions > .sw-button {
  min-width: 118px;
  font-size: 12px;
}
.assignment-actions > .sw-link {
  font-size: 11px;
}
@media (max-width: 640px) {
  .assignment-list {
    padding-inline: 16px;
  }
  .assignment-row {
    gap: 12px;
    flex-wrap: wrap;
    align-items: flex-start;
    padding: 22px 0;
  }
  .assignment-symbol {
    width: 34px;
    height: 38px;
    border-radius: 8px;
  }
  .assignment-symbol .app-icon {
    width: 18px;
    height: 18px;
  }
  .assignment-heading h3 {
    font-size: 14px;
  }
  .assignment-actions {
    flex-basis: 100%;
    padding-left: 46px;
    flex-direction: row;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
  .assignment-meta {
    font-size: 11px;
    gap: 5px 10px;
  }
  .assignment-copy {
    flex-basis: calc(100% - 50px);
  }
  .assignment-progress {
    max-width: none;
  }
}
</style>
