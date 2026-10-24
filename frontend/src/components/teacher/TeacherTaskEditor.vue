<template>
  <div class="task-editor">
    <header class="editor-header">
      <div class="editor-title-group">
        <button
          type="button"
          class="back-link"
          aria-label="返回任务列表"
          :disabled="saving"
          @click="$emit('cancel')"
        >
          <AppIcon name="arrow" />任务管理
        </button>
        <div class="editor-title-line">
          <h1>{{ task ? '编辑草稿' : '新建任务' }}</h1>
          <span class="draft-label">{{ task ? '草稿' : '尚未发布' }}</span>
        </div>
        <p>先安排练习内容，再确认发布设置。</p>
      </div>
      <button
        type="button"
        class="text-button leave-editor"
        :disabled="saving"
        @click="$emit('cancel')"
      >
        取消编辑
      </button>
    </header>
    <p v-if="error" class="editor-error" role="alert">{{ error }}</p>
    <div class="compose-layout">
      <div class="compose-main">
        <section class="compose-card task-basics">
          <div class="card-heading">
            <AppIcon name="task" />
            <h2>任务信息</h2>
          </div>
          <label class="editor-field"
            ><span>任务名称 <small>必填</small></span
            ><input
              v-model="title"
              aria-label="任务名称"
              :disabled="saving"
              maxlength="20"
              placeholder="例如：牛顿运动定律专项练习"
            /><span class="field-caption"
              >用一个清晰的名称，让学生了解这次练习的内容。<span
                >{{ title.length }} / 20</span
              ></span
            ></label
          >
        </section>
        <section class="compose-card assignment-questions">
          <div class="question-section-heading">
            <div class="card-heading">
              <AppIcon name="questions" />
              <h2>题目安排</h2>
              <span class="count-badge">{{ selectedIds.length }}</span>
            </div>
            <button
              v-if="selectedIds.length"
              type="button"
              class="outline-button"
              :disabled="saving || detailsLoading"
              @click="openPicker"
            >
              ＋ 添加题目
            </button>
          </div>
          <div v-if="!selectedIds.length" class="question-empty">
            <span class="empty-icon"><AppIcon name="questions" /></span>
            <h3>为这次任务挑选练习题</h3>
            <p>从题库选择题目，再按教学顺序排列。</p>
            <button type="button" class="primary-button" :disabled="saving" @click="openPicker">
              从题库选题</button
            ><small>可按关键词和难度筛选，最多选择 200 道</small>
          </div>
          <div v-else class="chosen-questions">
            <p class="chosen-caption">按下方顺序展示给学生，可使用箭头调整顺序。</p>
            <p v-if="detailsLoading" class="picker-status" role="status">正在读取草稿题目…</p>
            <article v-for="(id, index) in selectedIds" :key="id" class="chosen-row">
              <span class="question-order">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="chosen-content">
                <span class="chosen-id">题目 #{{ id }}</span>
                <div class="selected-content">
                  <LatexText :content="selected[id]?.content ?? `题目 #${id}`" />
                </div>
              </div>
              <div class="chosen-controls">
                <button
                  type="button"
                  :disabled="saving || index === 0"
                  :aria-label="`上移题目 ${id}`"
                  @click="move(index, -1)"
                >
                  ↑</button
                ><button
                  type="button"
                  :disabled="saving || index === selectedIds.length - 1"
                  :aria-label="`下移题目 ${id}`"
                  @click="move(index, 1)"
                >
                  ↓</button
                ><button
                  type="button"
                  class="remove-question"
                  :disabled="saving"
                  :aria-label="`移除题目 ${id}`"
                  @click="remove(id)"
                >
                  ×
                </button>
              </div>
            </article>
            <button
              type="button"
              class="add-more"
              :disabled="saving || selectedIds.length >= 200"
              @click="openPicker"
            >
              ＋ 继续添加题目
            </button>
          </div>
        </section>
      </div>
      <aside class="publish-settings" aria-label="发布设置">
        <div class="settings-title">
          <h2>发布设置</h2>
          <AppIcon name="event-manage" />
        </div>
        <div class="settings-section">
          <span class="settings-label">发布到</span>
          <div class="target-class">
            <span class="class-symbol"><AppIcon name="users" /></span>
            <div>
              <strong>{{ className }}</strong
              ><small>面向当前班级学生</small>
            </div>
            <span class="class-check" aria-hidden="true">✓</span>
          </div>
        </div>
        <div class="settings-section">
          <label class="editor-field"
            ><span>开始时间</span
            ><select
              v-model="startMode"
              :disabled="saving"
              @change="startMode === 'immediate' && (startAt = '')"
            >
              <option value="immediate">发布后立即开始</option>
              <option value="scheduled">设置开始时间</option>
            </select></label
          ><input
            v-if="startMode === 'scheduled'"
            v-model="startAt"
            class="datetime-field"
            type="datetime-local"
            aria-label="具体开始时间"
            :disabled="saving"
          />
        </div>
        <div class="settings-section">
          <label class="editor-field"
            ><span>截止时间</span
            ><select
              v-model="dueMode"
              :disabled="saving"
              @change="dueMode === 'none' && (dueAt = '')"
            >
              <option value="none">不设置截止时间</option>
              <option value="scheduled">设置截止时间</option>
            </select></label
          ><input
            v-if="dueMode === 'scheduled'"
            v-model="dueAt"
            class="datetime-field"
            type="datetime-local"
            aria-label="具体截止时间"
            :disabled="saving"
          />
        </div>
        <div class="publish-summary">
          <span>练习题目</span><strong>{{ selectedIds.length }} <small>道</small></strong>
        </div>
        <div class="publish-actions" data-testid="editor-actions">
          <button
            type="button"
            class="primary-button publish-button"
            :disabled="saving || !title.trim() || !selectedIds.length || detailsLoading"
            @click="save(true)"
          >
            {{ saving ? '正在保存…' : task ? '保存并发布' : '发布任务'
            }}<AppIcon name="arrow" /></button
          ><button
            type="button"
            class="outline-button save-draft"
            :disabled="saving || !title.trim() || detailsLoading"
            @click="save(false)"
          >
            保存草稿
          </button>
          <p>
            {{
              selectedIds.length
                ? '确认内容后发布，学生即可在开放时间内作答。'
                : '添加题目后即可发布，也可以先保存草稿。'
            }}
          </p>
        </div>
      </aside>
    </div>
    <TeacherDialog :open="pickerOpen" title="从题库选题" wide @close="pickerOpen = false">
      <div class="question-picker">
        <div class="picker-intro">
          <p>选择要加入任务的题目，选中后会保留在任务中。</p>
          <span>题库 {{ total }} 道</span>
        </div>
        <div class="picker-filters">
          <div class="picker-search">
            <AppIcon name="questions" /><input
              v-model="search"
              aria-label="搜索题库"
              placeholder="搜索题目内容或 ID…"
            />
          </div>
          <select v-model.number="difficulty" aria-label="题目难度">
            <option :value="0">全部难度</option>
            <option v-for="n in 5" :key="n" :value="n">难度 {{ n }}</option>
          </select>
        </div>
        <p v-if="catalogError" class="picker-error" role="alert">
          {{ catalogError }} <button type="button" @click="reloadCatalog">重新加载</button>
        </p>
        <p v-if="selectedIds.length >= 200" class="selection-limit" role="status">
          已达到 200 道题上限，可先取消部分题目再添加。
        </p>
        <div class="picker-scroll">
          <p v-if="loading" class="picker-status" role="status">正在加载题库…</p>
          <p v-else-if="!rows.length" class="picker-status">
            没有匹配的题目，试试其他关键词或难度。
          </p>
          <template v-else
            ><div class="picker-table-head">
              <label
                ><input
                  type="checkbox"
                  :checked="selectedOnPage === rows.length"
                  :indeterminate="selectedOnPage > 0 && selectedOnPage < rows.length"
                  aria-label="选择本页题目"
                  @change="togglePage"
                /><span>题目内容</span></label
              ><span>难度</span><span>查看</span>
            </div>
            <div v-for="question in rows" :key="question.id" class="picker-entry">
              <div
                class="picker-row bank-item"
                :class="{ selected: selectedIds.includes(question.id) }"
              >
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(question.id)"
                  :disabled="!selectedIds.includes(question.id) && selectedIds.length >= 200"
                  :aria-label="`选择题目 ${question.id}`"
                  @change="toggleQuestion(question)"
                />
                <div class="picker-question">
                  <span class="picker-question-id"
                    >#{{ question.id }}<span v-if="question.hasImage"> · 含配图</span></span
                  >
                  <div class="bank-content"><LatexText :content="question.content" /></div>
                </div>
                <span class="difficulty-tag">{{ question.difficulty ?? '—' }}</span
                ><button
                  type="button"
                  class="preview-link"
                  :aria-expanded="previewOpen && previewId === question.id"
                  @click="togglePreview(question.id)"
                >
                  {{ previewOpen && previewId === question.id ? '收起' : '预览' }}
                </button>
              </div>
              <div
                v-if="previewOpen && (preview?.id === question.id || previewId === question.id)"
                class="picker-preview"
              >
                <p v-if="previewLoading" role="status">正在读取完整题目…</p>
                <p v-else-if="previewError" role="alert">{{ previewError }}</p>
                <template v-else-if="preview"
                  ><div class="preview-full-content"><LatexText :content="preview.content" /></div>
                  <img v-if="preview.imageBase64" :src="preview.imageBase64" alt="题目配图" />
                  <div
                    v-for="(text, key) in preview.options"
                    :key="key"
                    class="preview-option"
                    :class="{ correct: key === preview.correctOption }"
                  >
                    <strong>{{ key }}</strong
                    ><LatexText :content="text" />
                  </div>
                  <p class="preview-answer">正确答案：{{ preview.correctOption }}</p>
                  <LatexText v-if="preview.explanation" :content="preview.explanation"
                /></template>
              </div></div
          ></template>
        </div>
        <footer class="picker-footer">
          <span
            >已选 <strong>{{ selectedIds.length }}</strong> / 200 题</span
          >
          <nav aria-label="题库分页">
            <button
              type="button"
              :disabled="loading || page <= 1"
              aria-label="上一页"
              @click="changePage(-1)"
            >
              ←</button
            ><span>{{ page }} / {{ pages || 1 }}</span
            ><button
              type="button"
              :disabled="loading || page >= pages"
              aria-label="下一页"
              @click="changePage(1)"
            >
              →
            </button>
          </nav>
          <button type="button" class="primary-button" @click="pickerOpen = false">完成选题</button>
        </footer>
      </div>
    </TeacherDialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import LatexText from '@/components/LatexText.vue';
import TeacherDialog from './TeacherDialog.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useQuestionCatalog } from '@/composables/useQuestionCatalog';
import { getQuestionByIdApi } from '@/api/question';
import {
  createTeacherEventApi,
  getTaskQuestionsApi,
  publishDraftTaskApi,
  updateDraftTaskApi,
} from '@/api/teacher-event';
import type { TeacherEventResponse } from '@/types/teacher-event';
import type { QuestionCatalogRow, QuestionResponse } from '@/types/question';
const props = defineProps<{
  task: TeacherEventResponse | null;
  classId: number;
  className: string;
}>();
const emit = defineEmits<{ cancel: []; saved: [published: boolean]; busy: [value: boolean] }>();
const error = ref(''),
  saving = ref(false),
  detailsLoading = ref(false);
const catalogError = ref('');
const pickerOpen = ref(false);
const { rows, loading, page, total, pages, search, difficulty, changePage, load } =
  useQuestionCatalog((message) => {
    catalogError.value = message;
  });
const localTime = (value: string | null) => {
  if (!value) return '';
  const d = new Date(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const title = ref(props.task?.eventTitle ?? ''),
  startAt = ref(localTime(props.task?.startAt ?? null)),
  dueAt = ref(localTime(props.task?.dueAt ?? null));
const selectedIds = ref<number[]>([...(props.task?.questionIds ?? [])]),
  selected = ref<Record<number, Pick<QuestionCatalogRow, 'id' | 'content'>>>({});
const startMode = ref(startAt.value ? 'scheduled' : 'immediate');
const dueMode = ref(dueAt.value ? 'scheduled' : 'none');
const snapshot = () =>
  JSON.stringify([
    title.value,
    startAt.value,
    dueAt.value,
    startMode.value,
    dueMode.value,
    selectedIds.value,
  ]);
const baseline = ref(snapshot()),
  dirty = computed(() => baseline.value !== snapshot());
defineExpose({ dirty, saving });
let alive = true,
  previewVersion = 0;
const previewId = ref<number | null>(null);
const previewOpen = ref(false),
  previewLoading = ref(false),
  previewError = ref(''),
  preview = ref<QuestionResponse | null>(null);
const selectedOnPage = computed(
  () => rows.value.filter((q) => selectedIds.value.includes(q.id)).length,
);
watch(loading, (value) => {
  if (value) catalogError.value = '';
});
function openPicker() {
  pickerOpen.value = true;
}
function toggleQuestion(question: QuestionCatalogRow) {
  if (selectedIds.value.includes(question.id)) remove(question.id);
  else add(question);
}
function togglePage() {
  if (selectedOnPage.value === rows.value.length) rows.value.forEach((q) => remove(q.id));
  else rows.value.forEach(add);
}
function togglePreview(id: number) {
  if (previewOpen.value && previewId.value === id) {
    previewOpen.value = false;
    ++previewVersion;
    return;
  }
  void previewQuestion(id);
}
async function reloadCatalog() {
  catalogError.value = '';
  await load();
}
function add(q: QuestionCatalogRow) {
  if (selectedIds.value.length >= 200 || selectedIds.value.includes(q.id)) return;
  selected.value[q.id] = q;
  selectedIds.value.push(q.id);
}
function remove(id: number) {
  selectedIds.value = selectedIds.value.filter((value) => value !== id);
}
function move(index: number, offset: number) {
  const next = index + offset;
  if (next < 0 || next >= selectedIds.value.length) return;
  [selectedIds.value[index], selectedIds.value[next]] = [
    selectedIds.value[next]!,
    selectedIds.value[index]!,
  ];
}
async function previewQuestion(id: number) {
  previewId.value = id;
  const owner = ++previewVersion;
  previewOpen.value = true;
  previewLoading.value = true;
  previewError.value = '';
  preview.value = null;
  try {
    const q = await getQuestionByIdApi(id);
    if (alive && owner === previewVersion) preview.value = q;
  } catch {
    if (alive && owner === previewVersion) previewError.value = '题目暂时无法加载，请重试。';
  } finally {
    if (alive && owner === previewVersion) previewLoading.value = false;
  }
}
async function save(publish: boolean) {
  if (
    saving.value ||
    !title.value.trim() ||
    detailsLoading.value ||
    (publish && !selectedIds.value.length)
  )
    return;
  if (
    (startMode.value === 'scheduled' && !startAt.value) ||
    (dueMode.value === 'scheduled' && !dueAt.value)
  ) {
    error.value = '请填写已选择的开始或截止时间。';
    return;
  }
  if (startAt.value && dueAt.value && new Date(dueAt.value) <= new Date(startAt.value)) {
    error.value = '截止时间必须晚于开始时间';
    return;
  }
  saving.value = true;
  emit('busy', true);
  error.value = '';
  try {
    const utc = (value: string) => (value ? new Date(value).toISOString().slice(0, 19) : null);
    const payload = {
      eventTitle: title.value.trim(),
      questionIds: [...selectedIds.value],
      classId: props.classId,
      eventTimestamp: new Date().toISOString().slice(0, 19),
      status: 'DRAFT' as const,
      startAt: utc(startAt.value),
      dueAt: utc(dueAt.value),
    };
    let task = props.task;
    if (task) await updateDraftTaskApi(task.id, payload);
    else {
      task = await createTeacherEventApi({ ...payload, status: publish ? 'PUBLISHED' : 'DRAFT' });
    }
    if (props.task && publish) await publishDraftTaskApi(task.id);
    baseline.value = snapshot();
    emit('saved', publish);
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? '保存任务失败，请稍后重试。';
  } finally {
    saving.value = false;
    emit('busy', false);
  }
}
onBeforeRouteLeave(
  () => !saving.value && (!dirty.value || window.confirm('任务尚未保存，确定离开并放弃修改吗？')),
);
onMounted(async () => {
  if (!props.task) return;
  detailsLoading.value = true;
  try {
    const qs = await getTaskQuestionsApi(props.task.id);
    if (alive) for (const q of qs) selected.value[q.id] = q;
  } catch {
    if (alive) error.value = '草稿题目详情加载失败，题号和顺序已保留。';
  } finally {
    if (alive) detailsLoading.value = false;
  }
});
onBeforeUnmount(() => {
  alive = false;
  ++previewVersion;
});
</script>
<style scoped>
.task-editor {
  color: #263b30;
  min-width: 0;
}
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 26px;
}
.back-link {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: none;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: #82958b;
  cursor: pointer;
}
.back-link .app-icon {
  width: 14px;
  height: 14px;
  transform: rotate(180deg);
}
.editor-title-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0 9px;
}
.editor-title-line h1 {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.035em;
  margin: 0;
}
.draft-label {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 5px;
  color: #85968b;
  background: #eaf0ec;
}
.editor-title-group > p {
  margin: 0;
  font-size: 13px;
  color: #8a9c90;
}
.text-button {
  padding: 8px 0;
  border: 0;
  background: none;
  color: #7c9485;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.compose-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 304px;
  gap: 24px;
  align-items: start;
}
.compose-main {
  display: grid;
  gap: 22px;
  min-width: 0;
}
.compose-card {
  background: white;
  border: 1px solid #e0e8e2;
  border-radius: 14px;
  padding: 25px;
}
.card-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}
.card-heading > .app-icon {
  width: 18px;
  height: 18px;
  color: #709381;
}
.card-heading h2,
.settings-title h2 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: #36503f;
}
.task-basics .card-heading {
  margin-bottom: 25px;
}
.editor-field {
  display: grid;
  gap: 10px;
  min-width: 0;
}
.editor-field > span:first-child {
  font-size: 12px;
  color: #627e6c;
}
.editor-field > span > small {
  font-size: 11px;
  font-weight: 400;
  margin-left: 6px;
  color: #a4b1a8;
}
.editor-field input,
.editor-field select,
.datetime-field {
  width: 100%;
  min-width: 0;
  padding: 12px 13px;
  border: 1px solid #dce6df;
  border-radius: 8px;
  background: white;
  color: #36513f;
  font: inherit;
  font-size: 13px;
  outline: none;
}
.editor-field input:focus,
.editor-field select:focus,
.datetime-field:focus {
  border-color: #73a88c;
  box-shadow: 0 0 0 3px #26715d08;
}
.editor-field input::placeholder {
  color: #a7b4ab;
}
.task-basics input {
  font-size: 16px;
  padding: 15px;
}
.field-caption {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  line-height: 1.7;
  color: #728779;
  font-size: 12px;
}
.field-caption > span {
  white-space: nowrap;
}
.question-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.count-badge {
  padding: 2px 7px;
  border-radius: 5px;
  background: #edf4ef;
  color: #729680;
  font-size: 11px;
}
.question-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
  padding: 49px 16px 30px;
}
.empty-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: #f1f7f3;
  color: #7aa68b;
  margin-bottom: 18px;
}
.empty-icon .app-icon {
  width: 27px;
  height: 27px;
}
.question-empty h3 {
  font-size: 16px;
  font-weight: 500;
  color: #46654f;
  margin: 0 0 10px;
}
.question-empty p {
  font-size: 12px;
  color: #91a497;
  line-height: 1.8;
  margin: 0 0 23px;
}
.question-empty > small {
  font-size: 11px;
  color: #788d80;
  margin-top: 16px;
}
.primary-button,
.outline-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 38px;
  padding: 10px 16px;
  border: 1px solid #2b7058;
  border-radius: 8px;
  background: #2b7058;
  color: #fff;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.primary-button:hover {
  background: #235e4a;
}
.outline-button {
  background: #fff;
  color: #5d7e68;
  border-color: #dce6df;
}
.outline-button:hover {
  background: #f7faf8;
}
.publish-settings {
  position: sticky;
  top: 100px;
  background: #fff;
  border: 1px solid #e0e8e2;
  border-radius: 14px;
  padding: 23px;
  min-width: 0;
}
.settings-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 25px;
}
.settings-title .app-icon {
  color: #94ad9d;
  width: 17px;
  height: 17px;
}
.settings-section {
  margin-bottom: 22px;
}
.settings-label {
  font-size: 12px;
  color: #627e6c;
  display: block;
  margin-bottom: 10px;
}
.target-class {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f5f9f6;
  border: 1px solid #e5eee7;
  border-radius: 9px;
  padding: 12px 10px;
}
.class-symbol {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: #e8f0eb;
  border-radius: 8px;
  color: #73967e;
}
.class-symbol .app-icon {
  width: 17px;
  height: 17px;
}
.target-class strong {
  display: block;
  font-size: 12px;
  color: #54745d;
  font-weight: 500;
}
.target-class small {
  display: block;
  font-size: 11px;
  color: #99aa9e;
  margin-top: 5px;
}
.class-check {
  color: #87a68f;
  margin-left: auto;
  font-size: 12px;
}
.datetime-field {
  margin-top: 10px;
  font-size: 12px;
  padding: 10px 8px;
}
.publish-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #e9efea;
  padding-top: 19px;
  margin-top: 3px;
  font-size: 12px;
  color: #829b89;
}
.publish-summary strong {
  font-size: 21px;
  font-weight: 500;
  color: #517d5e;
}
.publish-summary small {
  font-size: 11px;
  color: #a0b0a4;
  font-weight: 400;
}
.publish-actions {
  display: grid;
  gap: 10px;
  margin-top: 23px;
}
.publish-button {
  width: 100%;
  justify-content: space-between;
  padding: 12px 14px;
  font-size: 13px;
  min-height: 43px;
}
.publish-button .app-icon {
  width: 17px;
  height: 17px;
}
.save-draft {
  width: 100%;
}
.publish-actions p {
  font-size: 11px;
  line-height: 1.9;
  color: #718677;
  margin: 0;
}
.chosen-caption {
  font-size: 11px;
  color: #9aae9e;
  line-height: 1.8;
  margin: 18px 0 5px;
}
.chosen-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 0;
  border-bottom: 1px solid #edf2ee;
  min-width: 0;
}
.question-order {
  width: 26px;
  flex-shrink: 0;
  color: #92aa9a;
  font-size: 12px;
  letter-spacing: 0.03em;
}
.chosen-content {
  flex: 1;
  min-width: 0;
}
.chosen-id {
  color: #a2b1a7;
  font-size: 11px;
}
.selected-content {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  margin-top: 6px;
}
.chosen-controls {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.chosen-controls button {
  border: 0;
  background: none;
  color: #94ac9c;
  font-size: 16px;
  width: 25px;
  height: 29px;
  cursor: pointer;
  border-radius: 5px;
}
.chosen-controls button:hover {
  background: #f0f6f2;
}
.chosen-controls .remove-question {
  color: #b69c92;
  margin-left: 5px;
}
.add-more {
  display: block;
  width: 100%;
  margin-top: 20px;
  border: 1px dashed #d6e3da;
  border-radius: 8px;
  padding: 13px;
  background: #fbfdfb;
  color: #759b81;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.editor-error,
.picker-error {
  padding: 12px 15px;
  border: 1px solid #ecdcd3;
  border-radius: 8px;
  background: #fffaf7;
  color: #b57f67;
  font-size: 12px;
  line-height: 1.8;
}
.editor-error {
  margin: 0 0 18px;
}
.picker-error button {
  border: 0;
  background: none;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.picker-intro {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  margin: -8px 0 20px;
  font-size: 12px;
  color: #8ba091;
}
.picker-intro p {
  margin: 0;
}
.picker-intro > span {
  font-size: 11px;
  white-space: nowrap;
}
.picker-filters {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.picker-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  border: 1px solid #dce6df;
  border-radius: 8px;
  padding: 0 12px;
}
.picker-search .app-icon {
  width: 15px;
  height: 15px;
  color: #9ab0a1;
}
.picker-search input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: none;
  background: none;
  padding: 11px 0;
  font: inherit;
  font-size: 12px;
  color: #52775e;
}
.picker-filters select {
  padding: 10px 12px;
  width: 130px;
  border: 1px solid #dce6df;
  border-radius: 8px;
  background: white;
  color: #73917c;
  font: inherit;
  font-size: 12px;
}
.picker-scroll {
  height: min(480px, 52dvh);
  overflow: auto;
  overscroll-behavior: contain;
  border: 1px solid #e3ece6;
  border-radius: 9px;
}
.picker-table-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 64px 48px;
  gap: 12px;
  align-items: center;
  padding: 13px 16px;
  background: #f8fbf9;
  color: #94a99a;
  font-size: 11px;
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 1px solid #e8eeea;
}
.picker-table-head label {
  display: flex;
  gap: 14px;
  align-items: center;
}
.question-picker input[type='checkbox'] {
  width: 15px;
  height: 15px;
  accent-color: #4e8b63;
  flex-shrink: 0;
  margin: 0;
  cursor: pointer;
}
.picker-row {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr) 64px 48px;
  gap: 14px;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #edf2ee;
  min-width: 0;
}
.picker-row.selected {
  background: #f4f9f5;
}
.picker-question {
  min-width: 0;
}
.picker-question-id {
  font-size: 11px;
  color: #9fb0a4;
}
.bank-content {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  margin-top: 5px;
}
.difficulty-tag {
  justify-self: start;
  border: 1px solid #e3eae5;
  border-radius: 5px;
  padding: 3px 7px;
  font-size: 11px;
  color: #809c89;
}
.preview-link {
  padding: 5px 0;
  border: 0;
  background: none;
  color: #5b8c6b;
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.selection-limit {
  font-size: 12px;
  color: #886b36;
  background: #fcf8ed;
  border-radius: 6px;
  padding: 10px 12px;
  margin: 0 0 12px;
}
.picker-status {
  padding: 36px 16px;
  text-align: center;
  color: #9aae9f;
  font-size: 12px;
  line-height: 1.8;
}
.picker-preview {
  padding: 20px 24px 24px 46px;
  background: #fafdfb;
  border-bottom: 1px solid #e7eee9;
  font-size: 13px;
  line-height: 1.9;
  color: #65846e;
  overflow-wrap: anywhere;
}
.picker-preview > img {
  display: block;
  max-width: 100%;
  max-height: 260px;
  object-fit: contain;
  margin: 14px 0;
}
.preview-full-content {
  margin-bottom: 14px;
}
.preview-option {
  display: flex;
  gap: 12px;
  padding: 9px 12px;
  margin-top: 8px;
  border: 1px solid #e6ede8;
  border-radius: 6px;
}
.preview-option.correct {
  background: #f0f7f2;
  border-color: #cfe2d5;
}
.preview-answer {
  font-size: 11px;
  color: #7b9d86;
}
.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 19px;
  color: #94a99b;
  font-size: 12px;
}
.picker-footer > span strong {
  color: #5a8869;
  font-weight: 500;
}
.picker-footer nav {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 11px;
}
.picker-footer nav button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid #e1eae4;
  border-radius: 6px;
  background: white;
  color: #7e9c87;
  cursor: pointer;
}
@media (max-width: 1000px) {
  .compose-layout {
    grid-template-columns: minmax(0, 1fr) 270px;
    gap: 18px;
  }
  .publish-settings {
    padding: 20px;
  }
  .compose-card {
    padding: 22px;
  }
  .chosen-controls {
    flex-direction: column;
    gap: 0;
  }
}
@media (max-width: 760px) {
  .compose-layout {
    grid-template-columns: 1fr;
  }
  .publish-settings {
    position: static;
  }
  .editor-header {
    margin-bottom: 22px;
  }
  .editor-title-line h1 {
    font-size: 24px;
  }
  .compose-card {
    padding: 20px;
  }
  .question-empty {
    padding: 35px 8px 26px;
  }
  .chosen-controls {
    flex-direction: row;
  }
  .publish-settings {
    padding: 22px;
  }
  .publish-actions {
    grid-template-columns: 1fr 1.5fr;
  }
  .publish-button {
    grid-column: 2;
    grid-row: 1;
  }
  .save-draft {
    grid-column: 1;
    grid-row: 1;
  }
  .publish-actions p {
    grid-column: 1/-1;
  }
  .picker-intro {
    display: block;
  }
  .picker-intro > span {
    display: none;
  }
  .picker-row {
    grid-template-columns: 16px minmax(0, 1fr) 34px;
    gap: 10px;
    padding: 14px 12px;
  }
  .picker-row > .difficulty-tag {
    display: none;
  }
  .picker-table-head {
    grid-template-columns: minmax(0, 1fr) 34px;
    padding: 12px;
  }
  .picker-table-head > span:nth-child(2) {
    display: none;
  }
  .picker-filters {
    gap: 8px;
  }
  .picker-filters select {
    width: 100px;
    padding: 10px 7px;
  }
  .picker-search {
    padding: 0 9px;
  }
  .picker-search > .app-icon {
    display: none;
  }
  .picker-search input {
    font-size: 11px;
  }
  .picker-scroll {
    height: 50dvh;
  }
  .picker-preview {
    padding: 16px;
    font-size: 12px;
  }
  .picker-footer {
    flex-wrap: wrap;
    gap: 12px;
  }
  .picker-footer > .primary-button {
    padding: 9px 12px;
  }
  .picker-footer nav {
    gap: 10px;
  }
  .picker-footer > span {
    font-size: 11px;
  }
  .chosen-row {
    gap: 9px;
  }
  .question-order {
    width: 22px;
  }
  .chosen-controls button {
    width: 21px;
  }
  .field-caption {
    font-size: 11px;
    gap: 10px;
  }
}
@media (max-width: 400px) {
  .chosen-controls {
    flex-direction: column;
  }
  .leave-editor {
    display: none;
  }
  .editor-title-group > p {
    font-size: 12px;
  }
  .picker-footer > span {
    flex: 1;
  }
  .picker-footer nav {
    order: 3;
    width: 100%;
    justify-content: center;
  }
  .picker-filters {
    flex-wrap: wrap;
  }
  .picker-search {
    flex-basis: 100%;
  }
  .picker-filters select {
    width: 100%;
  }
  .picker-scroll {
    height: 43dvh;
  }
}
</style>
