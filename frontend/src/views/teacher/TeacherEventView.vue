<template>
  <AppShell :subtitle="`任务管理 · ${auth.user?.realName ?? ''}`">
    <div class="event-page teacher-font">
      <section class="event-hero">
        <div class="event-status-card" :class="openTasks.length ? 'live' : 'idle'">
          <span class="event-status-label">任务状态</span>
          <strong>{{ openTasks.length ? `已发布 ${openTasks.length}` : '未发布' }}</strong>
          <span>进行中 {{ openTasks.length }} · 草稿 {{ draftTasks.length }} · 已截止 {{ closedTasks.length }}</span>
          <span>共 {{ tasks.length }} 个未归档任务</span>
          <button v-if="latestOpenTask" class="archive-btn" @click="archiveTask(latestOpenTask)">归档任务</button>
        </div>
      </section>

      <section class="task-board">
        <div class="task-board-head">
          <h2>任务列表</h2>
          <button class="ghost-btn" :disabled="loadingTasks" @click="loadTasks">刷新任务</button>
        </div>
        <div v-if="loadingTasks" class="empty-hint">加载任务中…</div>
        <div v-else-if="scopedTasks.length === 0" class="empty-hint">暂无任务</div>
        <div v-else class="task-table">
          <article v-for="task in scopedTasks" :key="task.id" class="task-row">
            <div class="task-row-main">
              <div class="task-row-title">
                <strong>{{ task.eventTitle }}</strong>
                <span class="task-status" :class="task.status.toLowerCase()">{{ statusText(task) }}</span>
              </div>
              <div class="task-row-meta">
                <span>{{ task.questionCount ?? task.questionIds?.length ?? 0 }} 题</span>
                <span>开始 {{ formatWindow(task.startAt, '立即') }}</span>
                <span>截止 {{ formatWindow(task.dueAt, '不限') }}</span>
              </div>
            </div>
            <div class="task-row-actions">
              <button v-if="task.status === 'DRAFT'" class="ghost-btn" @click="editDraft(task)">编辑</button>
              <button v-if="task.status === 'DRAFT'" class="ghost-btn primary-text" @click="publishExisting(task)">发布</button>
              <button v-if="task.status === 'PUBLISHED'" class="ghost-btn" @click="closeExisting(task)">截止</button>
              <RouterLink :to="'/teacher/assignments/' + task.id" class="ghost-btn">作业详情</RouterLink>
              <button class="ghost-btn danger-text" @click="archiveTask(task)">归档</button>
            </div>
          </article>
        </div>
      </section>

      <div class="main-layout">
        <section class="left-panel">
          <div class="panel-head">
            <div>
              <h2>{{ editingTask ? '编辑草稿' : '新建任务' }}</h2>
            </div>
            <button v-if="editingTask" class="ghost-btn" @click="resetForm">取消编辑</button>
          </div>

          <label class="field-label" for="task-title">任务名称</label>
          <input id="task-title" v-model="taskTitle" class="title-input" maxlength="20"  />

          <div class="time-grid">
            <label class="field-label">开始时间
              <input v-model="startAt" type="datetime-local" class="time-input" />
            </label>
            <label class="field-label">截止时间
              <input v-model="dueAt" type="datetime-local" class="time-input" />
            </label>
          </div>

          <div class="selected-head">
            <span>已选题目</span>
            <span>{{ selectedIds.length }} 题</span>
          </div>


          <div v-if="selectedIds.length > 0" class="selected-list">
            <div v-for="(id, idx) in selectedIds" :key="id" class="selected-item">
              <span class="rank">{{ idx + 1 }}</span>
              <div class="selected-body">
                <div class="selected-content"><LatexText :content="questionMap[id]?.content ?? `题目 #${id}`" /></div>
                <span class="question-id">#{{ id }}</span>
              </div>
              <div class="selected-actions">
                <button :disabled="idx === 0" @click="moveUp(idx)">↑</button>
                <button :disabled="idx === selectedIds.length - 1" @click="moveDown(idx)">↓</button>
                <button class="danger" @click="removeQuestion(id)">✕</button>
              </div>
            </div>
          </div>

          <div class="publish-zone">
            <button class="publish-btn" :disabled="!canPublish || publishing" @click="publishTask">
              {{ publishing ? '处理中…' : editingTask ? '保存并发布' : '发布任务' }}
            </button>
            <button class="draft-btn" :disabled="!canSaveDraft || publishing" @click="saveDraft">
              {{ editingTask ? '保存草稿' : '保存为新草稿' }}
            </button>
          </div>
        </section>

        <section class="right-panel">
          <div class="panel-head">
            <div>
              <h2>题库</h2>
              <span class="panel-data">共 {{ questionTotal }} 道题</span>
            </div>
            <input v-model="searchText" class="search-input" placeholder="搜索题目 ID 或内容…" />
          </div>

          <div v-if="loadingQuestions" class="empty-hint">加载题目中…</div>
          <div v-else class="bank-list">
            <article v-for="question in allQuestions" :key="question.id" class="bank-item"
              :class="{ added: selectedIds.includes(question.id) }">
              <div class="bank-main">
                <span class="question-id">#{{ question.id }}</span>
                <div class="bank-content"><LatexText :content="question.content" /></div>
                <button class="add-btn" :disabled="selectedIds.includes(question.id) || selectedIds.length >= 200"
                  @click="addQuestion(question.id)">{{ selectedIds.includes(question.id) ? '已加入' : '加入' }}</button>
              </div>
              <small v-if="question.hasImage" class="muted">含配图</small>
            </article>
            <div v-if="allQuestions.length === 0" class="empty-hint"><p>没有匹配的题目</p></div>
          </div>
          <div class="archive-pager"><button :disabled="loadingQuestions || questionPage <= 1" @click="changeQuestionPage(-1)">上一页</button><span>{{ questionPage }} / {{ questionPages || 1 }}</span><button :disabled="loadingQuestions || questionPage >= questionPages" @click="changeQuestionPage(1)">下一页</button><button :disabled="loadingQuestions" @click="loadQuestions">刷新</button></div>
        </section>
      </div>

      <details class="archive-panel">
        <summary>已归档任务（{{ historyTotal }}）</summary>
        <div v-for="event in archivedEvents" :key="event.id" class="archive-row">
          <div>
            <strong>{{ event.eventTitle }}</strong>
            <span>{{ event.questionIds?.length ?? 0 }} 题 · {{ formatDate(event.eventTimestamp) }}</span>
          </div>
        </div>
        <div class="archive-pager">
          <button :disabled="historyPage <= 1" @click="changeHistoryPage(-1)">上一页</button>
          <span>{{ historyPage }} / {{ historyPages || 1 }}</span>
          <button :disabled="historyPage >= historyPages" @click="changeHistoryPage(1)">下一页</button>
        </div>
      </details>
    </div>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import AppToast from '@/components/AppToast.vue';
import { useAuthStore } from '@/stores/auth';
import { useClassroomStore } from '@/stores/classroom';
import { taskAvailabilityText } from '@/utils/assignmentProgress';
import { useToast } from '@/composables/useToast';
import {
  closeTaskApi,
  createTeacherEventApi,
  deleteTeacherEventApi,
  getTeacherEventHistoryApi,
  getTeacherTasksApi,
  getTaskQuestionsApi,
  publishDraftTaskApi,
  updateDraftTaskApi,
} from '@/api/teacher-event';
import { useQuestionCatalog } from '@/composables/useQuestionCatalog';
import type { QuestionCatalogRow } from '@/types/question';
import type { TeacherEventResponse } from '@/types/teacher-event';

const auth = useAuthStore();
const classroom = useClassroomStore();
const { visible, type, message, show } = useToast();

const { rows: allQuestions, loading: loadingQuestions, page: questionPage, total: questionTotal, pages: questionPages, search: searchText, load: loadQuestions, changePage: changeQuestionPage } = useQuestionCatalog(show);
const selectedQuestions = ref<Record<number, Pick<QuestionCatalogRow, 'id' | 'content'>>>({});
const selectedIds = ref<number[]>([]);
const tasks = ref<TeacherEventResponse[]>([]);
const editingTask = ref<TeacherEventResponse | null>(null);
const taskTitle = ref('');
const startAt = ref('');
const dueAt = ref('');
const loadingTasks = ref(false);
const publishing = ref(false);
const archivedEvents = ref<TeacherEventResponse[]>([]);
const historyPage = ref(1);
const historyTotal = ref(0);
const historyPages = ref(0);

const questionMap = computed<Record<number, Pick<QuestionCatalogRow, 'id' | 'content'>>>(() => ({ ...selectedQuestions.value, ...Object.fromEntries(allQuestions.value.map(q => [q.id, q])) }));
const scopedTasks = computed(() => tasks.value.filter(task => task.classId === classroom.activeClassId));
const draftTasks = computed(() => scopedTasks.value.filter(task => task.status === 'DRAFT'));
const openTasks = computed(() => scopedTasks.value.filter(task => task.status === 'PUBLISHED' && task.availability === 'OPEN'));
const closedTasks = computed(() => scopedTasks.value.filter(task => task.status === 'CLOSED' || task.availability === 'ENDED'));
const latestOpenTask = computed(() => openTasks.value[0] ?? null);
const canPublish = computed(() => Boolean(classroom.activeClassId) && Boolean(taskTitle.value.trim()) && selectedIds.value.length > 0);
const canSaveDraft = computed(() => Boolean(classroom.activeClassId) && Boolean(taskTitle.value.trim()));

const statusText = (task: TeacherEventResponse) => taskAvailabilityText(task.availability ?? task.status);
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const formatWindow = (value: string | null, fallback: string) => value ? formatDate(value) : fallback;
const toUtcIso = (value: string) => value ? new Date(value).toISOString().slice(0, 19) : null;
const toLocalInput = (value: string | null) => {
  if (!value) return '';
  const date = new Date(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes());
};
const validateTime = () => {
  if (startAt.value && dueAt.value && new Date(dueAt.value) <= new Date(startAt.value)) {
    show('截止时间必须晚于开始时间', 'error');
    return false;
  }
  return true;
};

const loadTasks = async () => {
  loadingTasks.value = true;
  try { tasks.value = await getTeacherTasksApi(); }
  catch (error: any) { show(error?.response?.data?.message ?? '加载任务失败', 'error'); }
  finally { loadingTasks.value = false; }
};
const loadHistory = async () => {
  try {
    const data = await getTeacherEventHistoryApi(historyPage.value);
    archivedEvents.value = data.records;
    historyTotal.value = data.total;
    historyPages.value = data.pages;
  } catch (error: any) { show(error?.response?.data?.message ?? '读取归档任务失败', 'error'); }
};
const changeHistoryPage = async (delta: number) => {
  const next = historyPage.value + delta;
  if (next < 1 || (historyPages.value && next > historyPages.value)) return;
  historyPage.value = next;
  await loadHistory();
};
const addQuestion = (id: number) => {
  if (!selectedIds.value.includes(id) && selectedIds.value.length < 200) {
    const row = allQuestions.value.find(q => q.id === id);
    if (row) selectedQuestions.value[id] = row;
    selectedIds.value.push(id);
  }
};
const removeQuestion = (id: number) => { selectedIds.value = selectedIds.value.filter(value => value !== id); };
const moveUp = (index: number) => {
  if (index <= 0) return;
  [selectedIds.value[index - 1], selectedIds.value[index]] = [selectedIds.value[index], selectedIds.value[index - 1]];
};
const moveDown = (index: number) => {
  if (index >= selectedIds.value.length - 1) return;
  [selectedIds.value[index + 1], selectedIds.value[index]] = [selectedIds.value[index], selectedIds.value[index + 1]];
};
const resetForm = () => {
  editingTask.value = null; taskTitle.value = ''; selectedIds.value = []; selectedQuestions.value = {}; startAt.value = ''; dueAt.value = '';
};
const editDraft = async (task: TeacherEventResponse) => {
  editingTask.value = task;
  taskTitle.value = task.eventTitle;
  selectedIds.value = [...(task.questionIds ?? [])];
  startAt.value = toLocalInput(task.startAt);
  dueAt.value = toLocalInput(task.dueAt);
  try {
    const details = await getTaskQuestionsApi(task.id);
    if (editingTask.value?.id !== task.id) return;
    for (const item of details) selectedQuestions.value[item.id] = item;
  } catch { if (editingTask.value?.id === task.id) show('草稿题目详情加载失败，可按题号继续编辑', 'error'); }
};
const payload = (status: 'DRAFT' | 'PUBLISHED') => ({
  eventTitle: taskTitle.value.trim(),
  questionIds: [...selectedIds.value],
  eventTimestamp: new Date().toISOString().slice(0, 19),
  classId: classroom.activeClassId,
  status,
  startAt: toUtcIso(startAt.value),
  dueAt: toUtcIso(dueAt.value),
});
const saveDraft = async () => {
  if (!canSaveDraft.value || publishing.value || !validateTime()) return;
  publishing.value = true;
  try {
    if (editingTask.value) await updateDraftTaskApi(editingTask.value.id, payload('DRAFT'));
    else await createTeacherEventApi(payload('DRAFT'));
    resetForm(); await Promise.all([loadTasks(), loadHistory()]);
    show('草稿已保存', 'success');
  } catch (error: any) { show(error?.response?.data?.message ?? '保存草稿失败', 'error'); }
  finally { publishing.value = false; }
};
const publishTask = async () => {
  if (!canPublish.value || publishing.value || !validateTime()) return;
  publishing.value = true;
  try {
    if (editingTask.value) {
      await updateDraftTaskApi(editingTask.value.id, payload('DRAFT'));
      await publishDraftTaskApi(editingTask.value.id);
    } else {
      await createTeacherEventApi(payload('PUBLISHED'));
    }
    resetForm(); await Promise.all([loadTasks(), loadHistory()]);
    show('任务已发布', 'success');
  } catch (error: any) { show(error?.response?.data?.message ?? '发布失败，请检查任务名称和题目后重试', 'error'); }
  finally { publishing.value = false; }
};
const publishExisting = async (task: TeacherEventResponse) => {
  try { await publishDraftTaskApi(task.id); await loadTasks(); show('草稿已发布', 'success'); }
  catch (error: any) { show(error?.response?.data?.message ?? '发布失败', 'error'); }
};
const closeExisting = async (task: TeacherEventResponse) => {
  try { await closeTaskApi(task.id); await loadTasks(); show('任务已截止，学生不能再提交', 'success'); }
  catch (error: any) { show(error?.response?.data?.message ?? '截止失败', 'error'); }
};
const archiveTask = async (task: TeacherEventResponse) => {
  try {
    await deleteTeacherEventApi(task.id);
    if (editingTask.value?.id === task.id) resetForm();
    await Promise.all([loadTasks(), loadHistory()]);
    show('任务已归档，历史作答已保留', 'success');
  } catch (error: any) { show(error?.response?.data?.message ?? '归档失败，请重试', 'error'); }
};
onMounted(async () => {
  if (!classroom.classes.length) await classroom.loadClasses().catch(() => {});
  await Promise.all([loadTasks(), loadHistory()]);
});
</script>

<style scoped>
.event-page { display: grid; gap: 18px; }
.event-hero { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 18px; align-items: center; }
.event-status-card { display: grid; gap: 8px; align-content: start; padding: 16px; border-radius: 10px; }
.task-board { padding: 16px 18px; border-radius: 12px; }
.task-board-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
.task-board-head h2 { margin: 0 0 4px; font-size: 16px; }
.task-board-head p { margin: 0; color: #64748b; font-size: 12px; }
.task-table { display: grid; gap: 8px; }
.task-row { display: flex; justify-content: space-between; gap: 16px; align-items: center; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; }
.task-row-main { min-width: 0; display: grid; gap: 4px; }
.task-row-title { display: flex; align-items: center; gap: 8px; }
.task-row-title strong { color: #172033; font-size: 13px; }
.task-status { padding: 1px 6px; border-radius: 4px; background: #f1f5f9; color: #475569; font-size: 10px; }
.task-status.draft { background: #fef3c7; color: #92400e; }
.task-status.published { background: #ecfdf5; color: #0f766e; }
.task-status.closed { background: #f1f5f9; color: #64748b; }
.task-row-meta { display: flex; gap: 14px; flex-wrap: wrap; color: #64748b; font-size: 11px; }
.task-row-actions { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; }
.task-row-actions .ghost-btn { min-height: 30px; padding: 0 9px; font-size: 11px; }
.primary-text { color: #0f766e !important; }
.danger-text { color: #b91c1c !important; }
.time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.time-grid label { display: grid; gap: 6px; }
.time-input { width: 100%; padding: 9px 10px; border: 1px solid #dbe3ec; border-radius: 8px; color: #172033; background: #fff; }
.time-hint { margin: -4px 0 0; color: #94a3b8; font-size: 11px; }
.publish-zone { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.draft-btn { min-height: 38px; padding: 0 14px; border: 1px solid #dbe3ec; border-radius: 8px; background: #fff; color: #334155; cursor: pointer; }
.draft-btn:disabled { opacity: .5; cursor: not-allowed; }
@media (max-width: 900px) {
  .event-hero { grid-template-columns: 1fr; }
  .task-row { flex-direction: column; align-items: stretch; }
  .task-row-actions { justify-content: flex-start; }
  .time-grid { grid-template-columns: 1fr; }
}

.main-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; align-items: start; }
.left-panel, .right-panel { display: grid; gap: 12px; align-content: start; padding: 16px; }
.panel-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.panel-head h2 { margin: 0; font-size: 16px; color: #172033; }
.field-label { display: grid; gap: 6px; color: #334155; font-size: 12px; font-weight: 500; }
.title-input, .search-input, .time-input { width: 100%; min-height: 38px; padding: 8px 10px; border: 1px solid #dbe3ec; border-radius: 8px; background: #fff; color: #172033; }
.selected-head { display: flex; justify-content: space-between; color: #64748b; font-size: 11px; }
.selected-list, .bank-list { display: grid; gap: 8px; }
.selected-item, .bank-item { display: flex; align-items: center; gap: 10px; padding: 10px; border: 1px solid #eef2f7; border-radius: 8px; background: #fff; }
.bank-item { justify-content: space-between; }
.selected-body { display: grid; gap: 4px; min-width: 0; flex: 1; }
.bank-main { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; }
.bank-content { flex: 1; }
.selected-content, .bank-content { color: #334155; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.question-id, .panel-data { color: #94a3b8; font-size: 10px; }
.rank { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; border-radius: 50%; background: #ecfdf5; color: #0f766e; font-size: 10px; }
.selected-actions { display: flex; gap: 4px; }
.selected-actions button, .add-btn { border: 1px solid #e2e8f0; background: #fff; color: #475569; border-radius: 5px; padding: 3px 7px; font-size: 11px; cursor: pointer; }
.selected-actions .danger { color: #b91c1c; }
.selected-actions button:disabled, .add-btn:disabled { opacity: .45; cursor: not-allowed; }
 .add-btn { color: #0f766e; flex: 0 0 auto; white-space: nowrap; }
.empty-hint { padding: 10px 0; color: #94a3b8; font-size: 12px; }
.publish-zone { display: flex; flex-wrap: wrap; gap: 8px; }
.publish-btn { min-height: 38px; padding: 0 16px; border: 1px solid #0f766e; border-radius: 8px; background: #0f766e; color: #fff; cursor: pointer; }
.publish-btn:disabled { opacity: .5; cursor: not-allowed; }
.draft-btn { min-height: 38px; padding: 0 14px; border: 1px solid #dbe3ec; border-radius: 8px; background: #fff; color: #334155; cursor: pointer; }
.draft-btn:disabled { opacity: .5; cursor: not-allowed; }
.time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
@media (max-width: 900px) { .main-layout, .time-grid { grid-template-columns: 1fr; } }
</style>
