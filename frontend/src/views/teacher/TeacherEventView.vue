<template>
  <AppShell subtitle="任务管理" hide-heading>
    <div class="task-management teacher-font">
      <TeacherTaskEditor
        v-if="editorOpen && editorClassId"
        ref="editor"
        :task="editingTask"
        :class-id="editorClassId"
        :class-name="editorClassName"
        @cancel="leaveEditor"
        @saved="editorSaved"
        @busy="editorBusy = $event"
      />
      <template v-else>
        <header class="management-heading">
          <div>
            <p>{{ classroom.activeClass?.name ?? '选择班级以管理任务' }}</p>
            <h1>任务管理</h1>
            <span>查看任务进度，让每一次练习都有着落。</span>
          </div>
          <button
            class="primary"
            aria-label="新建任务"
            :disabled="!classroom.activeClassId"
            @click="openEditor(null)"
          >
            ＋ 新建任务
          </button>
        </header>
        <div class="task-summary">
          <div>
            <span>未归档任务</span
            ><strong data-testid="task-total">{{ scopedTasks.length }}</strong>
          </div>
          <div>
            <span>进行中</span><strong>{{ count('open') }}</strong>
          </div>
          <div>
            <span>草稿</span><strong>{{ count('draft') }}</strong>
          </div>
          <div>
            <span>已截止</span><strong>{{ count('closed') }}</strong>
          </div>
        </div>
        <section class="task-board" aria-label="任务列表">
          <div class="list-toolbar">
            <div class="status-tabs" role="tablist" aria-label="任务状态">
              <button
                v-for="tab in tabs"
                :key="tab.id"
                role="tab"
                :aria-selected="filter === tab.id"
                :class="{ active: filter === tab.id }"
                @click="changeFilter(tab.id)"
              >
                {{ tab.label }}<span v-if="tab.id !== 'archived'">{{ count(tab.id) }}</span>
              </button>
            </div>
            <button class="refresh-action" :disabled="loading || historyLoading" @click="refresh">
              刷新
            </button>
          </div>
          <div v-if="filter !== 'archived'" class="task-search">
            <input v-model="search" placeholder="搜索任务名称…" aria-label="搜索任务名称" /><span
              >找到 {{ visibleTasks.length }} 个任务</span
            >
          </div>
          <p v-if="loadError" class="load-error" role="alert">
            {{ loadError }} <button @click="refresh">重试</button>
          </p>
          <p
            v-if="loading || (filter === 'archived' && historyLoading)"
            class="empty"
            role="status"
          >
            正在加载任务…
          </p>
          <div v-else-if="!classroom.activeClassId" class="empty">
            <strong>先选择一个班级</strong>
            <p>加入或创建班级后，就可以发布任务。</p>
            <RouterLink to="/teacher/classroom">前往班级管理 ↗</RouterLink>
          </div>
          <div v-else-if="!displayedTasks.length" class="empty">
            <strong>{{
              search && filter !== 'archived' ? '没有匹配的任务' : '这里还没有任务'
            }}</strong>
            <p>
              {{
                filter === 'archived'
                  ? '归档的任务会保留在这里，历史作答不会删除。'
                  : '可以调整筛选条件，或创建一份新的练习。'
              }}
            </p>
          </div>
          <div v-else class="task-table-wrap">
            <table class="task-table">
              <thead>
                <tr>
                  <th>任务名称</th>
                  <th>题目</th>
                  <th>完成情况</th>
                  <th>截止时间</th>
                  <th class="actions-head">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in displayedTasks" :key="task.id" class="task-row">
                  <td>
                    <RouterLink :to="`/teacher/assignments/${task.id}`" class="task-title">{{
                      task.eventTitle
                    }}</RouterLink>
                    <div class="task-row-meta">
                      <span class="task-status" :class="category(task)">{{ statusText(task) }}</span
                      ><span v-if="task.startAt">开始 {{ formatTime(task.startAt) }}</span>
                    </div>
                  </td>
                  <td>{{ task.questionCount ?? task.questionIds?.length ?? 0 }} 题</td>
                  <td>
                    <template v-if="task.status !== 'DRAFT' && overviewById[task.id]"
                      ><span class="completion-value"
                        >{{ overviewById[task.id]!.completedStudents }} /
                        {{ overviewById[task.id]!.totalStudents }} 人</span
                      >
                      <div class="completion-track">
                        <span
                          :style="{
                            width: `${Math.max(0, Math.min(100, overviewById[task.id]!.completionRate))}%`,
                          }"
                        ></span></div></template
                    ><span v-else class="muted">{{
                      task.status === 'DRAFT' ? '尚未发布' : '—'
                    }}</span>
                  </td>
                  <td>
                    <time :datetime="task.dueAt ?? undefined">{{
                      task.dueAt ? formatTime(task.dueAt) : '不限'
                    }}</time>
                  </td>
                  <td>
                    <div class="task-row-actions">
                      <button
                        v-if="task.status === 'DRAFT'"
                        class="view-action"
                        @click="openEditor(task)"
                      >
                        编辑草稿</button
                      ><RouterLink
                        v-else
                        :to="`/teacher/assignments/${task.id}`"
                        class="view-action"
                        >查看进度</RouterLink
                      >
                      <details v-if="task.status !== 'ARCHIVED'" class="task-menu">
                        <summary aria-label="更多操作" role="button">⋯</summary>
                        <div>
                          <button v-if="task.status === 'DRAFT'" @click="ask(task, 'publish')">
                            发布任务</button
                          ><button v-if="task.status === 'PUBLISHED'" @click="ask(task, 'close')">
                            截止任务</button
                          ><button class="danger" @click="ask(task, 'archive')">归档任务</button>
                        </div>
                      </details>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <nav
            v-if="filter === 'archived' && historyPages > 1"
            class="pagination"
            aria-label="归档分页"
          >
            <button
              :disabled="historyLoading || historyPage <= 1"
              @click="loadHistory(historyPage - 1)"
            >
              上一页</button
            ><span>第 {{ historyPage }} / {{ historyPages }} 页 · 共 {{ historyTotal }} 个任务</span
            ><button
              :disabled="historyLoading || historyPage >= historyPages"
              @click="loadHistory(historyPage + 1)"
            >
              下一页
            </button>
          </nav>
        </section>
      </template>
      <TeacherDialog
        :open="!!confirmation"
        :title="actionTitle"
        :busy="acting"
        @close="confirmation = null"
        ><div v-if="confirmation" class="confirmation-content">
          <p>
            确定{{ actionVerb }}「<strong>{{ confirmation.task.eventTitle }}</strong
            >」吗？
          </p>
          <p class="muted">
            {{
              confirmation.action === 'archive'
                ? '任务将移至已归档，历史作答记录会保留。'
                : confirmation.action === 'close'
                  ? '截止后学生将无法继续提交本次任务。'
                  : '发布后，学生可在设定的时间内作答。'
            }}
          </p>
          <p v-if="actionError" role="alert" class="load-error">{{ actionError }}</p>
          <footer>
            <button class="secondary" :disabled="acting" @click="confirmation = null">取消</button
            ><button
              :class="confirmation.action === 'publish' ? 'primary' : 'danger-button'"
              :disabled="acting"
              @click="confirmAction"
            >
              {{ acting ? '处理中…' : `确认${actionVerb}` }}
            </button>
          </footer>
        </div></TeacherDialog
      >
    </div>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import TeacherDialog from '@/components/teacher/TeacherDialog.vue';
import TeacherTaskEditor from '@/components/teacher/TeacherTaskEditor.vue';
import { useClassroomStore } from '@/stores/classroom';
import { useToast } from '@/composables/useToast';
import { taskAvailabilityText } from '@/utils/assignmentProgress';
import {
  closeTaskApi,
  deleteTeacherEventApi,
  getTeacherEventHistoryApi,
  getTeacherTaskOverviewApi,
  getTeacherTasksApi,
  publishDraftTaskApi,
} from '@/api/teacher-event';
import type { TeacherEventResponse, TaskOverviewRow } from '@/types/teacher-event';
type Filter = 'all' | 'open' | 'scheduled' | 'draft' | 'closed' | 'archived';
type Action = 'publish' | 'close' | 'archive';
const route = useRoute(),
  router = useRouter();
const classroom = useClassroomStore(),
  { visible, type, message, show } = useToast();
const tasks = ref<TeacherEventResponse[]>([]),
  overview = ref<TaskOverviewRow[]>([]),
  loading = ref(false),
  loadError = ref('');
const search = ref(''),
  filter = ref<Filter>('all');
const tabs: { id: Filter; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: 'open', label: '进行中' },
  { id: 'scheduled', label: '未开始' },
  { id: 'draft', label: '草稿' },
  { id: 'closed', label: '已截止' },
  { id: 'archived', label: '已归档' },
];
const scopedTasks = computed(() =>
  tasks.value.filter((t) => t.classId === classroom.activeClassId && t.status !== 'ARCHIVED'),
);
const category = (task: TeacherEventResponse): Filter =>
  task.status === 'ARCHIVED'
    ? 'archived'
    : task.status === 'DRAFT'
      ? 'draft'
      : task.status === 'CLOSED' || task.availability === 'ENDED'
        ? 'closed'
        : task.availability === 'SCHEDULED'
          ? 'scheduled'
          : 'open';
const count = (id: Filter) =>
  id === 'all'
    ? scopedTasks.value.length
    : scopedTasks.value.filter((t) => category(t) === id).length;
const visibleTasks = computed(() =>
  scopedTasks.value.filter(
    (t) =>
      (filter.value === 'all' || category(t) === filter.value) &&
      t.eventTitle.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()),
  ),
);
const overviewById = computed(
  () =>
    Object.fromEntries(overview.value.map((row) => [row.taskId, row])) as Record<
      number,
      TaskOverviewRow
    >,
);
const archived = ref<TeacherEventResponse[]>([]),
  historyLoading = ref(false),
  historyPage = ref(1),
  historyPages = ref(0),
  historyTotal = ref(0);
const displayedTasks = computed(() =>
  filter.value === 'archived' ? archived.value : visibleTasks.value,
);
let alive = true,
  loadVersion = 0,
  historyVersion = 0;
const statusText = (t: TeacherEventResponse) => taskAvailabilityText(t.availability ?? t.status);
const formatTime = (value: string) =>
  new Date(value).toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
async function loadTasks() {
  const owner = ++loadVersion;
  loading.value = true;
  loadError.value = '';
  try {
    const [all, stats] = await Promise.all([getTeacherTasksApi(), getTeacherTaskOverviewApi()]);
    if (alive && owner === loadVersion) {
      tasks.value = all;
      overview.value = stats;
    }
  } catch (e: any) {
    if (alive && owner === loadVersion)
      loadError.value = e?.response?.data?.message ?? '任务加载失败，请重试。';
  } finally {
    if (alive && owner === loadVersion) loading.value = false;
  }
}
async function loadHistory(page = 1) {
  const classId = classroom.activeClassId,
    owner = ++historyVersion;
  if (!classId) {
    archived.value = [];
    return;
  }
  historyLoading.value = true;
  loadError.value = '';
  try {
    const result = await getTeacherEventHistoryApi(page, classId);
    if (!alive || owner !== historyVersion || classId !== classroom.activeClassId) return;
    archived.value = result.records;
    historyPage.value = result.currentPage;
    historyPages.value = result.pages;
    historyTotal.value = result.total;
  } catch (e: any) {
    if (alive && owner === historyVersion)
      loadError.value = e?.response?.data?.message ?? '归档任务加载失败，请重试。';
  } finally {
    if (alive && owner === historyVersion) historyLoading.value = false;
  }
}
function changeFilter(id: Filter) {
  filter.value = id;
  if (id === 'archived') void loadHistory();
}
function refresh() {
  if (filter.value === 'archived') void loadHistory(historyPage.value);
  else void loadTasks();
}
const editor = ref<InstanceType<typeof TeacherTaskEditor> | null>(null),
  editorOpen = ref(false),
  editorBusy = ref(false),
  editingTask = ref<TeacherEventResponse | null>(null),
  editorClassId = ref<number | null>(null),
  editorClassName = ref('');
function openEditor(task: TeacherEventResponse | null) {
  if (!classroom.activeClassId) return;
  editingTask.value = task;
  editorClassId.value = classroom.activeClassId;
  editorClassName.value = classroom.activeClass?.name ?? '';
  editorOpen.value = true;
}
function leaveEditor() {
  if (editorBusy.value) return;
  if (editor.value?.dirty && !window.confirm('任务尚未保存，确定返回并放弃修改吗？')) return;
  editorOpen.value = false;
}
async function editorSaved(published: boolean) {
  editorOpen.value = false;
  search.value = '';
  filter.value = published ? 'all' : 'draft';
  show(published ? '任务已发布' : '草稿已保存', 'success');
  await loadTasks();
}
const confirmation = ref<{ task: TeacherEventResponse; action: Action } | null>(null),
  acting = ref(false),
  actionError = ref('');
const actionVerb = computed(() =>
  confirmation.value?.action === 'archive'
    ? '归档'
    : confirmation.value?.action === 'close'
      ? '截止'
      : '发布',
);
const actionTitle = computed(() => `${actionVerb.value}任务`);
function ask(task: TeacherEventResponse, action: Action) {
  document.querySelectorAll<HTMLDetailsElement>('.task-menu[open]').forEach((el) => {
    el.querySelector('summary')?.focus();
    el.open = false;
  });
  actionError.value = '';
  confirmation.value = { task, action };
}
async function confirmAction() {
  if (!confirmation.value || acting.value) return;
  const { task, action } = confirmation.value;
  acting.value = true;
  actionError.value = '';
  try {
    if (action === 'archive') await deleteTeacherEventApi(task.id);
    else if (action === 'close') await closeTaskApi(task.id);
    else await publishDraftTaskApi(task.id);
    confirmation.value = null;
    show(
      `任务已${action === 'archive' ? '归档' : action === 'close' ? '截止' : '发布'}`,
      'success',
    );
    await loadTasks();
    if (filter.value === 'archived') await loadHistory();
  } catch (e: any) {
    actionError.value = e?.response?.data?.message ?? '操作失败，请重试。';
  } finally {
    acting.value = false;
  }
}
let revertingClass = false;
watch(
  () => classroom.activeClassId,
  (id, old) => {
    if (revertingClass) {
      revertingClass = false;
      return;
    }
    if (
      editorOpen.value &&
      (editorBusy.value ||
        (editor.value?.dirty && !window.confirm('切换班级会放弃尚未保存的任务修改，确定切换吗？')))
    ) {
      revertingClass = true;
      classroom.setActiveClass(old);
      return;
    }
    editorOpen.value = false;
    search.value = '';
    confirmation.value = null;
    ++historyVersion;
    archived.value = [];
    historyLoading.value = false;
    historyPage.value = 1;
    historyPages.value = 0;
    historyTotal.value = 0;
    if (id && filter.value === 'archived') void loadHistory();
  },
);
onMounted(async () => {
  if (!classroom.classes.length) await classroom.loadClasses().catch(() => {});
  if (alive) {
    await loadTasks();
    if (!alive) return;
    if (route.query.status === 'draft') filter.value = 'draft';
    if (route.query.create === '1') openEditor(null);
    if (route.query.create || route.query.status) {
      const query = { ...route.query };
      delete query.create;
      delete query.status;
      void router.replace({ query });
    }
  }
});
onBeforeUnmount(() => {
  alive = false;
  ++loadVersion;
  ++historyVersion;
});
</script>
<style scoped>
.task-management {
  display: grid;
  gap: 24px;
  color: #263b30;
  min-width: 0;
}
.management-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}
.management-heading p {
  margin: 0 0 7px;
  color: #7e8d83;
  font-size: 12px;
}
.management-heading h1 {
  font-size: 26px;
  font-weight: 600;
  margin: 0 0 10px;
}
.management-heading span {
  font-size: 13px;
  color: #7e8d83;
}
.primary,
.secondary,
.danger-button {
  padding: 11px 17px;
  border-radius: 9px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.primary {
  background: #26715d;
  border: 1px solid #26715d;
  color: #fff;
}
.secondary {
  background: white;
  border: 1px solid #dfe6e1;
  color: #597062;
}
.danger-button {
  border: 1px solid #b64c40;
  background: #b64c40;
  color: #fff;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.task-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  padding: 22px 8px;
  border: 1px solid #e1e8e2;
  border-radius: 14px;
  background: #fff;
}
.task-summary > div {
  display: grid;
  gap: 10px;
  padding: 0 24px;
  border-right: 1px solid #edf0ed;
}
.task-summary > div:last-child {
  border: 0;
}
.task-summary span {
  font-size: 12px;
  color: #839087;
}
.task-summary strong {
  font-size: 27px;
  font-weight: 600;
  color: #2f6853;
}
.task-board {
  border: 1px solid #e1e8e2;
  border-radius: 14px;
  background: #fff;
  min-width: 0;
}
.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  border-bottom: 1px solid #edf0ed;
}
.status-tabs {
  display: flex;
  gap: 20px;
  overflow: auto;
}
.status-tabs > button {
  display: flex;
  gap: 7px;
  align-items: center;
  white-space: nowrap;
  padding: 18px 0;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: #7b8c81;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.status-tabs > button.active {
  color: #26715d;
  border-bottom-color: #26715d;
}
.status-tabs span {
  font-size: 11px;
  border-radius: 5px;
  padding: 1px 5px;
  background: #f1f5f2;
}
.refresh-action {
  border: 0;
  background: none;
  color: #839087;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.task-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
}
.task-search input {
  min-width: 0;
  width: 300px;
  padding: 10px 12px;
  border: 1px solid #e1e8e2;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
}
.task-search span {
  color: #8a968e;
  font-size: 12px;
  white-space: nowrap;
}
.task-table-wrap {
  overflow: visible;
  min-height: 170px;
}
.task-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}
.task-table th {
  font-size: 11px;
  color: #849388;
  font-weight: 500;
  background: #fafcfb;
  padding: 13px 20px;
  white-space: nowrap;
}
.task-table td {
  padding: 20px;
  border-top: 1px solid #edf0ed;
  vertical-align: middle;
}
.task-table td:first-child {
  width: 36%;
  min-width: 190px;
}
.task-title {
  color: #2f4336;
  font-size: 14px;
  text-decoration: none;
  font-weight: 500;
}
.task-title:hover {
  color: #26715d;
}
.task-row-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 9px;
  color: #92a095;
  font-size: 11px;
}
.task-status {
  padding: 3px 7px;
  border-radius: 5px;
  background: #f0f3f1;
  color: #7a8c80;
  white-space: nowrap;
}
.task-status.open {
  background: #ecf6ef;
  color: #33805d;
}
.task-status.draft {
  background: #fcf4e5;
  color: #ad8742;
}
.task-status.scheduled {
  background: #eff4fa;
  color: #647fa1;
}
.completion-value {
  font-size: 12px;
  white-space: nowrap;
  color: #688273;
}
.completion-track {
  height: 4px;
  width: 90px;
  max-width: 100%;
  border-radius: 3px;
  background: #edf2ee;
  margin-top: 8px;
  overflow: hidden;
}
.completion-track span {
  display: block;
  height: 100%;
  background: #81b39b;
  border-radius: 3px;
}
.task-table time {
  white-space: nowrap;
  color: #718478;
  font-size: 12px;
}
.task-row-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}
.view-action {
  border: 0;
  background: none;
  color: #2f8064;
  font: inherit;
  font-size: 12px;
  white-space: nowrap;
  text-decoration: none;
  cursor: pointer;
}
.actions-head {
  text-align: right;
}
.task-menu {
  position: relative;
}
.task-menu summary {
  list-style: none;
  border: 1px solid #e3e9e4;
  border-radius: 7px;
  padding: 0 9px;
  height: 29px;
  color: #8b9b90;
  cursor: pointer;
  font-size: 20px;
  line-height: 22px;
}
.task-menu summary::-webkit-details-marker {
  display: none;
}
.task-menu > div {
  position: absolute;
  right: 0;
  top: 32px;
  z-index: 10;
  min-width: 120px;
  padding: 5px;
  background: white;
  border: 1px solid #dfe6e1;
  border-radius: 9px;
  box-shadow: 0 8px 24px #263d2d12;
}
.task-menu button {
  width: 100%;
  border: 0;
  border-radius: 5px;
  text-align: left;
  padding: 9px 12px;
  background: white;
  font-size: 12px;
  color: #52735f;
  cursor: pointer;
}
.task-menu button:hover {
  background: #f3f7f4;
}
.task-menu .danger {
  color: #b26050;
}
.empty {
  padding: 48px 20px;
  text-align: center;
  color: #8b9b90;
  font-size: 13px;
}
.empty strong {
  color: #526d5d;
  font-weight: 500;
  font-size: 15px;
}
.empty p {
  line-height: 1.8;
}
.empty a {
  color: #26715d;
}
.muted {
  color: #8b9b90;
  font-size: 12px;
}
.load-error {
  margin: 18px 20px;
  color: #b45b4d;
  font-size: 13px;
}
.load-error button {
  border: 0;
  background: none;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.confirmation-content {
  line-height: 1.8;
  font-size: 14px;
}
.confirmation-content footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 26px;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  font-size: 12px;
  color: #829288;
}
.pagination button {
  border: 1px solid #dfe6e1;
  background: white;
  border-radius: 7px;
  padding: 8px 12px;
  color: #647d6d;
  cursor: pointer;
}
@media (max-width: 800px) {
  .management-heading h1 {
    font-size: 23px;
  }
  .task-summary > div {
    padding: 0 14px;
  }
  .task-summary strong {
    font-size: 23px;
  }
  .status-tabs {
    gap: 16px;
  }
  .list-toolbar {
    padding: 0 14px;
    gap: 12px;
  }
  .task-table th,
  .task-table td {
    padding: 15px;
  }
  .task-table td:first-child {
    min-width: 160px;
  }
  .task-table-wrap {
    overflow: visible;
  }
  .task-table thead {
    display: none;
  }
  .task-table,
  .task-table tbody {
    display: block;
  }
  .task-row {
    display: grid;
    grid-template-columns: 1fr auto;
    padding: 16px;
    border-top: 1px solid #edf0ed;
    gap: 12px;
  }
  .task-row td {
    display: block;
    padding: 0;
    border: 0;
  }
  .task-row td:first-child {
    width: auto;
    grid-column: 1/-1;
  }
  .task-row td:nth-child(2) {
    font-size: 12px;
    color: #8b9b90;
  }
  .task-row td:nth-child(3) {
    grid-column: 2;
    grid-row: 2/4;
    align-self: center;
  }
  .task-row td:nth-child(4) {
    grid-column: 1;
  }
  .task-row td:last-child {
    grid-column: 1/-1;
    margin-top: 5px;
  }
  .task-search {
    padding: 16px;
  }
  .task-search input {
    width: 100%;
  }
  .task-search span {
    display: none;
  }
}
@media (max-width: 480px) {
  .management-heading {
    align-items: flex-start;
  }
  .management-heading > div > span {
    display: none;
  }
  .primary {
    padding: 10px 12px;
    font-size: 12px;
  }
  .task-summary {
    padding: 18px 0;
  }
  .task-summary > div {
    padding: 0 10px;
  }
  .task-summary span {
    font-size: 11px;
  }
  .task-summary strong {
    font-size: 22px;
  }
  .task-management {
    gap: 18px;
  }
  .list-toolbar {
    display: block;
  }
  .refresh-action {
    display: none;
  }
  .status-tabs {
    gap: 16px;
  }
  .status-tabs > button {
    padding: 16px 0;
    font-size: 12px;
  }
  .status-tabs span {
    display: none;
  }
}
</style>
