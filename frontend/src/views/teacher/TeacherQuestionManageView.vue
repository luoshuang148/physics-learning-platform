<template>
  <AppShell hide-heading :subtitle="`题库管理 · ${auth.user?.realName ?? ''}`">
    <header class="tw-header">
      <div>
        <h1>题库管理</h1>
        <p>先预览题目内容，需要修改时再进入编辑。</p>
      </div>
      <button class="tw-button primary" @click="openCreate">＋ 新建题目</button>
    </header>
    <div class="qm-layout">
      <div class="qm-list-panel" :class="{ 'full-width': !showForm }">
        <div class="qm-list-head">
          <div class="qm-list-head-top">
            <span class="qm-list-title">题目列表</span>
            <span class="qm-list-count">共 {{ total }} 道</span>
          </div>
          <div class="qm-list-filters">
            <input v-model="searchText" class="qm-search" placeholder="搜索题目内容或 ID…" />
            <div class="diff-filters">
              <button
                v-for="d in [0, 1, 2, 3, 4, 5]"
                :key="d"
                class="diff-btn"
                :class="{ active: diffFilter === d }"
                @click="diffFilter = d"
              >
                {{ d === 0 ? '全部' : `难度 ${d}` }}
              </button>
            </div>
          </div>
        </div>

        <div v-if="loading" class="qm-loading">加载中…</div>
        <div v-else-if="questions.length === 0" class="qm-empty">
          <p>
            {{
              searchText || diffFilter ? '没有匹配的题目，试试其他关键词或难度。' : '题库暂无题目'
            }}
          </p>
        </div>
        <div v-else class="qm-list">
          <div
            v-for="q in questions"
            :key="q.id"
            class="qm-item"
            :class="{ active: editing?.id === q.id }"
          >
            <div class="qm-item-top">
              <span class="qm-badge">#{{ q.id }}</span>
              <span class="qm-correct">答案 {{ q.correctOption }}</span>
              <span v-if="q.difficulty" class="qm-diff" :class="diffClass(q.difficulty)"
                >难度 {{ q.difficulty }}</span
              >
              <div class="qm-item-actions" @click.stop>
                <button class="icon-act edit" @click="openEdit(q)" title="编辑">编辑</button>
                <details class="question-menu">
                  <summary aria-label="更多题目操作">•••</summary>
                  <button class="question-delete" @click="confirmDelete(q.id)">删除题目</button>
                </details>
              </div>
            </div>
            <button
              type="button"
              class="question-preview-trigger"
              :aria-label="`预览题目 ${q.id}`"
              @click="openPreview(q.id)"
            >
              <div class="qm-item-content"><LatexText :content="q.content ?? ''" /></div>
              <span class="question-preview-hint"
                >{{ q.hasImage ? '含配图 · 查看完整题目' : '查看完整题目' }} →</span
              >
            </button>
          </div>
        </div>
        <div class="catalog-pager">
          <button class="ghost-btn" :disabled="loading || page <= 1" @click="changePage(-1)">
            上一页</button
          ><span>{{ page }} / {{ pages || 1 }}</span
          ><button class="ghost-btn" :disabled="loading || page >= pages" @click="changePage(1)">
            下一页</button
          ><button class="ghost-btn" :disabled="loading" @click="load">刷新</button>
        </div>
      </div>

      <div v-if="showForm" class="qm-form-panel" :class="{ visible: showForm }">
        <div class="qm-form-head">
          <span class="qm-form-title">{{ editing ? `编辑题目 #${editing.id}` : '新建题目' }}</span>
          <button class="close-btn" @click="closeForm">✕</button>
        </div>

        <div class="qm-form-body">
          <div class="form-section">
            <div class="form-label">题目内容 <span class="form-required">*</span></div>
            <textarea
              v-model="form.content"
              rows="4"
              class="form-textarea"
              placeholder="支持 LaTeX，用 $公式$ 包裹"
            ></textarea>
            <div v-if="form.content" class="latex-preview">
              <div class="preview-label">预览</div>
              <div class="preview-content"><LatexText :content="form.content" /></div>
            </div>
          </div>

          <div class="form-section">
            <div class="form-label">题目图片</div>
            <label
              class="image-upload-box"
              :class="{ uploading: imageReading, dragging: isDragOver }"
              @dragover.prevent="handleImageDragOver"
              @dragleave.prevent="handleImageDragLeave"
              @drop.prevent="handleImageDrop"
            >
              <input
                class="image-file-input"
                type="file"
                accept="image/png"
                @change="handleImageChange"
              />
              <div class="image-upload-copy">
                <strong>{{
                  imageReading ? '图片处理中…' : form.imageBase64 ? '更换图片' : '上传图片'
                }}</strong>
                <span>支持点击选择或直接将图片拖拽到这里</span>
              </div>
            </label>
            <div v-if="form.imageBase64" class="image-preview-card">
              <img :src="form.imageBase64" alt="题目图片预览" class="image-preview" />
              <div class="image-preview-actions">
                <button class="image-remove-btn" type="button" @click="clearImage">移除图片</button>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="form-label">选项 <span class="form-required">*</span></div>
            <div class="options-grid">
              <div
                v-for="key in optionKeys"
                :key="key"
                class="opt-row"
                :class="{ correct: form.correctOption === key }"
              >
                <button
                  class="opt-key-btn"
                  :class="{ correct: form.correctOption === key }"
                  @click="form.correctOption = key"
                >
                  {{ key }}
                </button>
                <div class="opt-main">
                  <div class="field-stack">
                    <input
                      v-model="optionInputs[key]"
                      class="opt-input"
                      :placeholder="
                        editing ? optionInputs[key] || '此处请填入选项内容' : '此处请填入选项内容'
                      "
                    />
                    <div v-if="optionInputs[key]" class="latex-preview opt-preview-box">
                      <div class="preview-label">选项 {{ key }} 预览</div>
                      <div class="preview-content"><LatexText :content="optionInputs[key]" /></div>
                    </div>
                  </div>
                </div>
                <span v-if="form.correctOption === key" class="opt-correct-mark">✓</span>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="form-label">难度</div>
            <div class="diff-picker">
              <button
                v-for="d in [1, 2, 3, 4, 5]"
                :key="d"
                class="diff-pick-btn"
                :class="{ active: form.difficulty === d }"
                @click="form.difficulty = d"
              >
                {{ d }}
              </button>
            </div>
          </div>

          <div class="form-section">
            <div class="form-label">知识点（人工维护，可留空）</div>
            <div
              v-for="(points, topic) in groupedKnowledgePoints"
              :key="topic"
              class="knowledge-topic"
            >
              <strong>{{ topic }}</strong>
              <label v-for="point in points" :key="point.code" class="knowledge-point-option">
                <input v-model="form.knowledgePointCodes" type="checkbox" :value="point.code" />
                <span>{{ point.name }}</span>
              </label>
            </div>
            <span class="muted">没有人工确认的标签时保持未标注，系统不会推断学生的认知错误。</span>
          </div>

          <div class="form-section">
            <div class="form-label">题目解析</div>
            <textarea
              v-model="form.explanation"
              rows="3"
              class="form-textarea"
              placeholder="解析内容（支持 LaTeX）"
            ></textarea>
            <div v-if="form.explanation" class="latex-preview">
              <div class="preview-label">解析预览</div>
              <div class="preview-content"><LatexText :content="form.explanation" /></div>
            </div>
          </div>

          <div class="form-section">
            <div class="form-label">AI 补练</div>
            <label class="ai-toggle">
              <input v-model="form.aiPracticeEnabled" type="checkbox" />
              <span>允许用于 AI 补练检索</span>
            </label>
            <p class="ai-hint">
              仅当题干与解析均为文字且完整时才能开启。开启后题目会进入向量索引。
            </p>
            <div v-if="indexStatus" class="index-status">
              <span
                >索引状态：<strong :class="'idx-' + indexStatus.status.toLowerCase()">{{
                  indexStatus.status
                }}</strong></span
              >
              <span v-if="indexStatus.failureCode" class="idx-fail"
                >原因 {{ indexStatus.failureCode }}</span
              >
              <button class="ghost-btn" :disabled="indexBusy" @click="reindexNow">
                {{ indexBusy ? '排队中…' : '重试索引' }}
              </button>
            </div>
          </div>

          <div class="form-actions">
            <button class="cancel-btn" @click="closeForm">取消</button>
            <button
              class="submit-btn"
              :disabled="submitting || imageReading || !form.content.trim()"
              @click="submit"
            >
              {{ submitting ? '保存中…' : editing ? '保存修改' : '创建题目' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <TeacherDialog
      :open="previewOpen"
      :title="`题目预览 · #${previewId ?? ''}`"
      wide
      @close="closePreview"
      ><p v-if="previewLoading" class="tw-empty" role="status">正在加载完整题目…</p>
      <p v-else-if="previewError" class="tw-error" role="alert">
        {{ previewError }}
        <button class="tw-link" @click="previewId && openPreview(previewId)">重新加载</button>
      </p>
      <article v-else-if="preview" class="question-preview-body">
        <span class="tw-badge">难度 {{ preview.difficulty ?? '未设置' }}</span>
        <div class="question-preview-stem"><LatexText :content="preview.content" /></div>
        <img v-if="preview.imageBase64" :src="preview.imageBase64" alt="题目配图" />
        <div
          v-for="(text, key) in preview.options"
          :key="key"
          class="question-preview-option"
          :class="{ correct: key === preview.correctOption }"
        >
          <strong>{{ key }}</strong
          ><LatexText :content="text" />
        </div>
        <p class="question-preview-answer">正确答案：{{ preview.correctOption }}</p>
        <section v-if="preview.explanation" class="question-preview-explanation">
          <h3>解析</h3>
          <LatexText :content="preview.explanation" />
        </section>
      </article>
      <div class="tw-dialog-footer">
        <button class="tw-button" @click="closePreview">关闭</button
        ><button v-if="preview" class="tw-button primary" @click="editPreview">编辑题目</button>
      </div></TeacherDialog
    >
    <TeacherDialog
      :open="deleteTargetId !== null"
      title="删除题目"
      :busy="deleting"
      @close="deleteTargetId = null"
      ><p class="tw-dialog-copy">
        确定删除题目 <strong>#{{ deleteTargetId }}</strong> 吗？删除后无法恢复。
      </p>
      <p v-if="deleteError" class="tw-error" role="alert">{{ deleteError }}</p>
      <div class="tw-dialog-footer">
        <button class="tw-button" :disabled="deleting" @click="deleteTargetId = null">取消</button
        ><button class="tw-button danger" :disabled="deleting" @click="doDelete">
          {{ deleting ? '正在删除…' : '确认删除' }}
        </button>
      </div></TeacherDialog
    >

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import '@/assets/teacher-workspace.css';
import TeacherDialog from '@/components/teacher/TeacherDialog.vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import LatexText from '@/components/LatexText.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { useQuestionCatalog } from '@/composables/useQuestionCatalog';
import {
  createQuestionApi,
  deleteQuestionApi,
  getQuestionByIdApi,
  getKnowledgePointsApi,
  updateQuestionApi,
  getQuestionIndexStatusApi,
  reindexQuestionApi,
} from '@/api/question';
import type { QuestionResponse, QuestionCatalogRow, KnowledgePoint } from '@/types/question';

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const optionKeys = ['A', 'B', 'C', 'D'] as const;
type OptionKey = (typeof optionKeys)[number];

const {
  rows: questions,
  loading,
  page,
  total,
  pages,
  search: searchText,
  difficulty: diffFilter,
  load,
  changePage,
} = useQuestionCatalog(show);
let editSequence = 0;
const submitting = ref(false);
const imageReading = ref(false);
const isDragOver = ref(false);
const showForm = ref(false);
const editing = ref<QuestionResponse | null>(null);
const knowledgePointOptions = ref<KnowledgePoint[]>([]);
const groupedKnowledgePoints = computed(() =>
  knowledgePointOptions.value.reduce<Record<string, KnowledgePoint[]>>((groups, point) => {
    (groups[point.topic] ??= []).push(point);
    return groups;
  }, {}),
);
const deleteTargetId = ref<number | null>(null),
  deleting = ref(false),
  deleteError = ref('');
const previewOpen = ref(false),
  previewLoading = ref(false),
  previewError = ref(''),
  previewId = ref<number | null>(null),
  preview = ref<QuestionResponse | null>(null);
let previewSequence = 0;
function closePreview() {
  previewOpen.value = false;
  ++previewSequence;
}
async function openPreview(id: number) {
  const owner = ++previewSequence;
  previewId.value = id;
  preview.value = null;
  previewOpen.value = true;
  previewLoading.value = true;
  previewError.value = '';
  try {
    const data = await getQuestionByIdApi(id);
    if (owner === previewSequence) preview.value = data;
  } catch {
    if (owner === previewSequence) previewError.value = '题目加载失败，请重试。';
  } finally {
    if (owner === previewSequence) previewLoading.value = false;
  }
}
function editPreview() {
  if (!preview.value) return;
  const row = preview.value;
  closePreview();
  void openEdit(row);
}
onBeforeUnmount(() => {
  ++previewSequence;
  ++editSequence;
});

const form = reactive({
  content: '',
  correctOption: 'A' as OptionKey,
  explanation: '',
  difficulty: 3 as number | null,
  imageBase64: '',
  knowledgePointCodes: [] as string[],
  aiPracticeEnabled: false,
});
const indexStatus = ref<import('@/types/question').QuestionIndexStatus | null>(null);
const indexBusy = ref(false);

void getKnowledgePointsApi()
  .then((points) => {
    knowledgePointOptions.value = points;
  })
  .catch(() => show('知识点目录加载失败', 'error'));

const optionInputs = reactive<Record<OptionKey, string>>({ A: '', B: '', C: '', D: '' });

const diffClass = (d: number | null) => {
  if (!d) return '';
  if (d >= 4) return 'diff-hard';
  if (d >= 3) return 'diff-mid';
  return 'diff-easy';
};

const resetForm = () => {
  form.content = '';
  form.correctOption = 'A';
  form.explanation = '';
  form.difficulty = 3;
  form.imageBase64 = '';
  form.knowledgePointCodes = [];
  form.aiPracticeEnabled = false;
  indexStatus.value = null;
  optionInputs.A = '';
  optionInputs.B = '';
  optionInputs.C = '';
  optionInputs.D = '';
};

const openCreate = () => {
  ++editSequence;
  editing.value = null;
  resetForm();
  showForm.value = true;
};

const openEdit = async (row: Pick<QuestionCatalogRow, 'id'>) => {
  const owner = ++editSequence;
  try {
    const q = await getQuestionByIdApi(row.id);
    if (owner !== editSequence) return;
    editing.value = q;
    form.content = q.content;
    form.correctOption = q.correctOption;
    form.explanation = q.explanation ?? '';
    form.difficulty = q.difficulty;
    form.imageBase64 = q.imageBase64 ?? '';
    form.knowledgePointCodes = q.knowledgePointCodes ?? [];
    form.aiPracticeEnabled = q.aiPracticeEnabled ?? false;
    optionInputs.A = q.options?.A ?? '';
    optionInputs.B = q.options?.B ?? '';
    optionInputs.C = q.options?.C ?? '';
    optionInputs.D = q.options?.D ?? '';
    showForm.value = true;
    void getQuestionIndexStatusApi(q.id)
      .then((s) => {
        indexStatus.value = s;
      })
      .catch(() => {
        indexStatus.value = null;
      });
  } catch {
    if (owner === editSequence) show('题目详情加载失败，请重试', 'error');
  }
};

const reindexNow = async () => {
  if (!editing.value) return;
  indexBusy.value = true;
  try {
    indexStatus.value = await reindexQuestionApi(editing.value.id);
    show('已重新排队索引', 'success');
  } catch {
    show('重试索引失败', 'error');
  } finally {
    indexBusy.value = false;
  }
};

const closeForm = () => {
  ++editSequence;
  showForm.value = false;
  editing.value = null;
  resetForm();
};

const clearImage = () => {
  form.imageBase64 = '';
};

const readFileAsDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('图片读取失败'));
    reader.readAsDataURL(file);
  });

const loadImageFile = async (file: File) => {
  if (file.type !== 'image/png') {
    show('题目图片仅支持 PNG 格式', 'error');
    return false;
  }

  imageReading.value = true;
  try {
    form.imageBase64 = await readFileAsDataUrl(file);
    show('题目图片已加载', 'success');
    return true;
  } catch {
    show('图片读取失败，请重试', 'error');
    return false;
  } finally {
    imageReading.value = false;
    isDragOver.value = false;
  }
};

const handleImageChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  await loadImageFile(file);
  input.value = '';
};

const handleImageDragOver = () => {
  if (!imageReading.value) isDragOver.value = true;
};

const handleImageDragLeave = () => {
  isDragOver.value = false;
};

const handleImageDrop = async (event: DragEvent) => {
  isDragOver.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (!file) return;
  await loadImageFile(file);
};

const buildOptionsJson = () =>
  JSON.stringify({ A: optionInputs.A, B: optionInputs.B, C: optionInputs.C, D: optionInputs.D });

const submit = async () => {
  if (!form.content.trim()) {
    show('请填写题目内容', 'info');
    return;
  }
  if (!optionInputs.A || !optionInputs.B || !optionInputs.C || !optionInputs.D) {
    show('请填写全部四个选项', 'info');
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      content: form.content,
      options: buildOptionsJson(),
      correctOption: form.correctOption,
      explanation: form.explanation || undefined,
      difficulty: form.difficulty,
      imageBase64: form.imageBase64 || undefined,
      knowledgePointCodes: form.knowledgePointCodes,
      aiPracticeEnabled: form.aiPracticeEnabled,
    };
    if (editing.value) {
      await updateQuestionApi({ ...payload, id: editing.value.id });
      show('题目已更新', 'success');
    } else {
      await createQuestionApi(payload);
      show('题目创建成功', 'success');
    }
    await load();
    closeForm();
  } catch (e: any) {
    show(e?.response?.data?.message ?? '操作失败', 'error');
  } finally {
    submitting.value = false;
  }
};

const confirmDelete = (id: number) => {
  document.querySelectorAll<HTMLDetailsElement>('.question-menu[open]').forEach((el) => {
    el.querySelector('summary')?.focus();
    el.open = false;
  });
  deleteError.value = '';
  deleteTargetId.value = id;
};

const doDelete = async () => {
  if (deleteTargetId.value === null || deleting.value) return;
  const id = deleteTargetId.value;
  deleting.value = true;
  deleteError.value = '';
  try {
    await deleteQuestionApi(id);
    if (editing.value?.id === deleteTargetId.value) closeForm();
    deleteTargetId.value = null;
    show('题目已删除', 'success');
    await load();
  } catch (error: any) {
    deleteError.value = error?.response?.data?.message ?? '删除失败，请重试。';
  } finally {
    deleting.value = false;
  }
};
</script>

<style scoped>
.question-preview-trigger {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  color: inherit;
  cursor: pointer;
  font: inherit;
}
.question-preview-trigger:hover .qm-item-content {
  color: #2c7651;
}
.question-preview-trigger .qm-item-content {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}
.question-preview-hint {
  display: block;
  margin-top: 12px;
  color: #7e9b88;
  font-size: 11px;
}
.question-menu {
  position: relative;
}
.question-menu summary {
  list-style: none;
  width: 30px;
  padding: 5px;
  text-align: center;
  cursor: pointer;
  color: #87a08f;
  border-radius: 5px;
}
.question-menu summary::-webkit-details-marker {
  display: none;
}
.question-menu[open] summary {
  background: #eff5f0;
}
.question-delete {
  position: absolute;
  right: 0;
  top: 100%;
  z-index: 5;
  white-space: nowrap;
  padding: 12px 18px;
  border: 1px solid #e2eae4;
  border-radius: 8px;
  background: #fff;
  color: #aa5b50;
  box-shadow: 0 6px 22px #23392c12;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}
.question-preview-body {
  font-size: 14px;
  line-height: 1.9;
  color: #3f5b47;
  overflow-wrap: anywhere;
}
.question-preview-stem {
  margin: 22px 0;
}
.question-preview-body > img {
  display: block;
  max-width: 100%;
  max-height: 330px;
  object-fit: contain;
  margin: 20px 0;
}
.question-preview-option {
  display: flex;
  gap: 16px;
  padding: 11px 16px;
  margin: 10px 0;
  border: 1px solid #e4ece7;
  border-radius: 8px;
}
.question-preview-option.correct {
  background: #f3f8f4;
  border-color: #cddfd2;
}
.question-preview-answer {
  font-size: 13px;
  color: #557d60;
}
.question-preview-explanation {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e6eee8;
}
.question-preview-explanation h3 {
  font-size: 14px;
  font-weight: 500;
}

.catalog-pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 18px;
  color: #64748b;
  font-size: 12px;
}
.knowledge-topic {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  margin: 8px 0;
  color: #cbd5e1;
  font-size: 12px;
}
.knowledge-topic strong {
  width: 100%;
  color: #94a3b8;
}
.knowledge-point-option {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
}
.qm-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  min-height: 600px;
}

.qm-layout:has(.qm-form-panel.visible) {
  grid-template-columns: minmax(0, 1fr) 420px;
}

.qm-list-panel {
  border: 1px solid rgba(151, 172, 249, 0.15);
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.qm-list-head {
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
  background: rgba(151, 172, 249, 0.03);
}
.qm-list-head-top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 8px;
}
.qm-list-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.qm-list-count {
  font-size: var(--font-size-xs);
  color: var(--accent);
  background: rgba(151, 172, 249, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
}
.new-btn {
  margin-left: auto;
  padding: 8px 14px;
  border: 1px solid rgba(90, 243, 211, 0.35);
  border-radius: 8px;
  background: rgba(90, 243, 211, 0.08);
  color: var(--brand);
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}
.qm-list-filters {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px 12px;
  flex-wrap: wrap;
}
.qm-search {
  flex: 1;
  min-width: 180px;
  padding: 8px 10px;
  border-radius: 9px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
}
.diff-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.diff-btn {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: transparent;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  cursor: pointer;
}
.diff-btn.active {
  color: var(--accent);
  border-color: rgba(151, 172, 249, 0.32);
  background: rgba(151, 172, 249, 0.08);
}
.qm-loading,
.qm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: var(--text-muted);
  gap: 8px;
}
.empty-icon {
  font-size: 40px;
  opacity: 0.35;
}
.qm-list {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
}
.qm-item {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  cursor: pointer;
  transition:
    background 0.15s,
    border-color 0.15s;
}
.qm-item:hover {
  background: rgba(151, 172, 249, 0.04);
}
.qm-item.active {
  background: rgba(151, 172, 249, 0.08);
  border-left: 2px solid var(--accent);
}
.qm-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.qm-badge {
  font-family: monospace;
  font-size: var(--font-size-xs);
  color: var(--accent);
  background: rgba(151, 172, 249, 0.12);
  padding: 2px 7px;
  border-radius: 4px;
}
.qm-correct {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
.qm-diff {
  font-size: var(--font-size-xs);
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.diff-easy {
  color: #15803d;
}
.diff-mid {
  color: #b45309;
}
.diff-hard {
  color: #b91c1c;
}
.qm-item-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.icon-act {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: 1px solid rgba(151, 172, 249, 0.15);
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
}
.icon-act.ai {
  border-color: rgba(90, 243, 211, 0.28);
  background: rgba(90, 243, 211, 0.08);
}
.icon-act.del {
  border-color: rgba(255, 100, 100, 0.18);
}
.qm-item-content {
  margin-top: 8px;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  line-height: 1.7;
}
.qm-item-image-wrap {
  margin-top: 10px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.02);
}
.qm-item-image {
  display: block;
  max-width: 100%;
  max-height: 180px;
  margin: 0 auto;
  object-fit: contain;
}
.qm-item-opts {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.qm-opt-chip {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--text-muted);
  background: rgba(151, 172, 249, 0.08);
}
.qm-opt-chip.correct {
  color: #15803d;
  background: #ecfdf5;
}
.qm-form-panel {
  border: 1px solid rgba(151, 172, 249, 0.15);
  border-radius: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.02);
  position: sticky;
  top: 16px;
}
.qm-form-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.08);
  background: rgba(151, 172, 249, 0.04);
}
.qm-form-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.close-btn {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-muted);
  cursor: pointer;
}
.qm-form-body {
  padding: 16px;
  display: grid;
  gap: 14px;
}
.form-section {
  display: grid;
  gap: 8px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.half {
  min-width: 0;
}
.form-label {
  font-size: 12px;
  color: var(--text-muted);
}
.ai-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
}
.ai-hint {
  margin: 0;
  font-size: 11px;
  color: var(--text-muted);
  line-height: 1.5;
}
.index-status {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  flex-wrap: wrap;
}
.idx-ready {
  color: #3d9a6a;
}
.idx-failed {
  color: #c45c5c;
}
.idx-pending,
.idx-indexing {
  color: #b08a3e;
}
.idx-fail {
  color: #c45c5c;
}
.form-required {
  color: #dc2626;
}
.form-textarea,
.form-input,
.opt-input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
  outline: none;
}
.image-upload-box {
  display: grid;
  gap: 10px;
  padding: 14px;
  border-radius: 14px;
  border: 1px dashed rgba(15, 118, 110, 0.35);
  background: #f8fdfb;
  cursor: pointer;
}
.image-upload-box.uploading {
  opacity: 0.72;
  pointer-events: none;
}
.image-upload-box.dragging {
  border-color: #0f766e;
  background: #ecfdf5;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}
.image-file-input {
  display: none;
}
.image-upload-copy {
  display: grid;
  gap: 4px;
}
.image-upload-copy strong {
  color: #0f766e;
  font-size: 13px;
}
.image-upload-copy span {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.6;
}
.image-preview-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.02);
}
.image-preview {
  width: 100%;
  max-height: 240px;
  object-fit: contain;
  border-radius: 10px;
  background: rgba(4, 8, 20, 0.65);
}
.image-preview-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.image-preview-tip {
  color: var(--text-muted);
  font-size: 12px;
}
.image-remove-btn {
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  cursor: pointer;
}
.form-textarea {
  resize: vertical;
}
.form-textarea:focus,
.form-input:focus,
.opt-input:focus,
.qm-search:focus {
  border-color: rgba(90, 243, 211, 0.42);
  box-shadow: 0 0 0 3px rgba(90, 243, 211, 0.1);
}
.options-grid {
  display: grid;
  gap: 14px;
}
.opt-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 24px;
  gap: 12px;
  align-items: start;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.02);
}
.opt-main {
  display: grid;
  gap: 8px;
}
.field-stack {
  display: grid;
  gap: 6px;
  position: relative;
}
.field-tip {
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}
.opt-key-btn {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
  cursor: pointer;
}
.opt-key-btn.correct,
.opt-row.correct .opt-input {
  border-color: rgba(120, 220, 150, 0.35);
}
.opt-correct-mark {
  color: #15803d;
  font-weight: 700;
}
.form-hint {
  font-size: 11px;
  color: var(--text-muted);
  opacity: 0.7;
}
.diff-picker {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.diff-pick-btn {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
  cursor: pointer;
}
.diff-pick-btn.active {
  border-color: rgba(90, 243, 211, 0.35);
  color: var(--brand);
}
.latex-preview {
  border: 1px dashed rgba(151, 172, 249, 0.16);
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.02);
}
.opt-preview-box {
  margin-top: 2px;
}
.preview-label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-bottom: 6px;
}
.preview-content {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  line-height: 1.7;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.cancel-btn,
.submit-btn,
.danger-btn {
  padding: 10px 16px;
  border-radius: 10px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}
.cancel-btn {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #475569;
}
.submit-btn {
  border: 1px solid #0f766e;
  background: #0f766e;
  color: #ffffff;
}
.submit-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: rgba(6, 8, 20, 0.7);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.dialog {
  width: min(100%, 360px);
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 18px;
}
.dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: #172033;
}
.dialog-body {
  margin-top: 10px;
  font-size: var(--font-size-sm);
  color: #64748b;
  line-height: 1.7;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.danger-btn {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.ai-mask {
  z-index: 140;
}
.ai-dialog {
  width: min(100%, 920px);
  max-height: calc(100vh - 48px);
  display: grid;
  grid-template-rows: auto 1fr;
  border-radius: 18px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: linear-gradient(160deg, rgba(20, 25, 50, 0.98), rgba(12, 16, 34, 0.98));
  overflow: hidden;
  box-shadow: 0 24px 80px rgba(4, 6, 18, 0.6);
}
.ai-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}
.ai-dialog-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
}
.ai-dialog-sub {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.ai-dialog-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 0;
  min-height: 0;
}
.ai-question-card {
  padding: 18px;
  border-right: 1px solid rgba(151, 172, 249, 0.1);
  background: rgba(255, 255, 255, 0.02);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-question-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.ai-question-content {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.8;
}
.ai-chat-box {
  min-height: 0;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ai-empty-state {
  margin: auto;
  max-width: 420px;
  text-align: center;
  color: var(--text-muted);
  line-height: 1.8;
}

@media (max-width: 1180px) {
  .qm-layout,
  .qm-layout:has(.qm-form-panel.visible) {
    grid-template-columns: 1fr;
  }
  .qm-form-panel {
    position: static;
  }
}

@media (max-width: 720px) {
  .form-row {
    grid-template-columns: 1fr;
  }
  .qm-list-head-top,
  .qm-list-filters,
  .qm-form-body {
    padding-left: 12px;
    padding-right: 12px;
  }
  .image-preview-actions {
    align-items: flex-start;
  }
}
</style>

<style scoped>
.qm-list-panel {
  overflow: visible;
}
.qm-list {
  max-height: none;
  overflow: visible;
}
.qm-list-head {
  border-radius: 12px 12px 0 0;
}
.qm-item {
  cursor: default;
}
.qm-item-actions {
  overflow: visible;
}
</style>
