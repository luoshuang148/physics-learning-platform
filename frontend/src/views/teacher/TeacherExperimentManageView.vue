<template>
  <AppShell :subtitle="`实验配置 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="exp-admin-page ops-typography">
      <section class="exp-hero">
        <div class="hero-copy">
          <span class="hero-kicker">Scenario Mapping</span>
          <h2 class="hero-title">实验配置</h2>
          <p class="hero-desc">围绕实验顺序、题目绑定与场景说明组织配置，减少入口分散，让教师按教学流程完成实验编排。</p>
        </div>
        <div class="hero-stats">
          <div class="stat-card cyan">
            <span class="stat-num">{{ experiments.length }}</span>
            <span class="stat-label">实验配置数</span>
          </div>
          <div class="stat-card amber">
            <span class="stat-num">{{ totalQuestionLinks }}</span>
            <span class="stat-label">关联题目数</span>
          </div>
          <div class="stat-card rose">
            <span class="stat-num">{{ maxOrder }}</span>
            <span class="stat-label">最大序号</span>
          </div>
        </div>
      </section>

      <section class="exp-layout">
        <div class="exp-list-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Configurations</div>
              <h3 class="panel-title">实验配置列表</h3>
            </div>
            <input v-model="searchText" class="search-input" placeholder="搜索实验名称 / 描述" />
          </div>

          <div class="panel-actions">
            <button class="ghost-btn" :disabled="loading" @click="loadExperiments">刷新</button>
            <button class="primary-btn" @click="startCreate">新增实验</button>
          </div>

          <div v-if="loading" class="panel-empty">正在加载实验配置…</div>
          <div v-else-if="filteredExperiments.length === 0" class="panel-empty">暂无匹配实验</div>
          <div v-else class="exp-list">
            <button
              v-for="item in filteredExperiments"
              :key="item.id"
              class="exp-item"
              :class="{ active: editingId === item.id }"
              @click="startEdit(item)"
            >
              <div class="exp-item-top">
                <span class="exp-order">#{{ item.orderNumber }}</span>
                <span class="exp-link-count">{{ item.questionIds.length }} 题</span>
              </div>
              <div class="exp-name">{{ item.name }}</div>
              <div class="exp-desc">{{ item.description || '暂无描述' }}</div>
            </button>
          </div>
        </div>

        <div class="exp-form-panel">
          <div class="panel-head form-head">
            <div>
              <div class="panel-kicker">Editor</div>
              <h3 class="panel-title">{{ editingId ? `编辑实验 #${editingId}` : '新建实验' }}</h3>
            </div>
            <button class="ghost-btn" @click="resetForm">重置</button>
          </div>

          <div class="form-grid">
            <label class="field-block wide">
              <span class="field-label">实验名称</span>
              <input v-model="form.name" class="field-input" placeholder="例如：光电效应实验" />
            </label>

            <label class="field-block small">
              <span class="field-label">排序序号</span>
              <input v-model.number="form.orderNumber" type="number" min="1" class="field-input" placeholder="1" />
            </label>

            <label class="field-block wide">
              <span class="field-label">实验说明</span>
              <textarea v-model="form.description" rows="4" class="field-textarea" placeholder="写明实验对应场景、展示内容或教学提示" />
            </label>

            <label class="field-block wide">
              <span class="field-label">题目 ID 列表</span>
              <input v-model="questionIdsText" class="field-input" placeholder="例如：1,2,5,8" />
              <span class="field-hint">使用英文逗号分隔；保存时会转换为数字数组</span>
            </label>
          </div>

          <div class="question-preview">
            <div class="preview-head">
              <div>
                <div class="preview-title">已关联题目预览</div>
                <div class="preview-sub">当前共 {{ parsedQuestionIds.length }} 道题</div>
              </div>
              <span class="preview-order">排序位 {{ form.orderNumber }}</span>
            </div>
            <div v-if="parsedQuestionIds.length === 0" class="preview-empty">尚未填写关联题目</div>
            <div v-else class="preview-chips">
              <span v-for="qid in parsedQuestionIds" :key="qid" class="qid-chip">#{{ qid }}</span>
            </div>
          </div>

          <div class="form-actions">
            <button v-if="editingId" class="danger-btn" :disabled="deleting" @click="removeCurrent">
              {{ deleting ? '删除中…' : '删除实验' }}
            </button>
            <div class="action-right">
              <button class="ghost-btn" :disabled="submitting" @click="resetForm">取消</button>
              <button class="primary-btn" :disabled="submitting || !canSubmit" @click="submitForm">
                {{ submitting ? '保存中…' : editingId ? '保存修改' : '创建实验' }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { teacherNavLinks } from '@/constants/navigation';
import { createExperimentApi, deleteExperimentApi, getAllExperimentsApi, updateExperimentApi } from '@/api/experiment';
import type { ExperimentResponse } from '@/types/experiment';

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const links = teacherNavLinks;

const loading = ref(false);
const submitting = ref(false);
const deleting = ref(false);
const searchText = ref('');
const editingId = ref<number | null>(null);
const questionIdsText = ref('');
const experiments = ref<ExperimentResponse[]>([]);

const form = reactive({
  name: '',
  description: '',
  orderNumber: 1,
});

const parsedQuestionIds = computed(() => questionIdsText.value
  .split(',')
  .map(item => Number(item.trim()))
  .filter(id => Number.isInteger(id) && id > 0));

const canSubmit = computed(() => form.name.trim() && form.orderNumber > 0);
const totalQuestionLinks = computed(() => experiments.value.reduce((sum, item) => sum + item.questionIds.length, 0));
const maxOrder = computed(() => experiments.value.reduce((max, item) => Math.max(max, item.orderNumber), 0));

const filteredExperiments = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  const sorted = [...experiments.value].sort((a, b) => a.orderNumber - b.orderNumber);
  if (!keyword) return sorted;
  return sorted.filter(item =>
    item.name.toLowerCase().includes(keyword)
    || item.description.toLowerCase().includes(keyword)
  );
});

const syncForm = (item: ExperimentResponse) => {
  editingId.value = item.id;
  form.name = item.name;
  form.description = item.description ?? '';
  form.orderNumber = item.orderNumber;
  questionIdsText.value = item.questionIds.join(',');
};

const resetForm = () => {
  editingId.value = null;
  form.name = '';
  form.description = '';
  form.orderNumber = maxOrder.value > 0 ? maxOrder.value + 1 : 1;
  questionIdsText.value = '';
};

const startCreate = () => resetForm();
const startEdit = (item: ExperimentResponse) => syncForm(item);

const loadExperiments = async () => {
  loading.value = true;
  try {
    experiments.value = await getAllExperimentsApi();
    if (editingId.value) {
      const latest = experiments.value.find(item => item.id === editingId.value);
      if (latest) syncForm(latest);
      else resetForm();
    } else if (!form.name && experiments.value.length) {
      resetForm();
    }
  } catch (error: any) {
    show(error?.response?.data?.message ?? '实验配置加载失败', 'error');
  } finally {
    loading.value = false;
  }
};

const submitForm = async () => {
  if (!canSubmit.value) return;
  submitting.value = true;
  try {
    const payload = {
      ...(editingId.value ? { id: editingId.value } : {}),
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      orderNumber: form.orderNumber,
      questionIds: parsedQuestionIds.value,
    };

    if (editingId.value) {
      await updateExperimentApi(payload);
      show(`实验 #${editingId.value} 已更新`, 'success');
    } else {
      await createExperimentApi(payload);
      show('实验创建成功', 'success');
    }

    await loadExperiments();
    resetForm();
  } catch (error: any) {
    show(error?.response?.data?.message ?? '保存实验失败', 'error');
  } finally {
    submitting.value = false;
  }
};

const removeCurrent = async () => {
  if (!editingId.value) return;
  deleting.value = true;
  try {
    await deleteExperimentApi(editingId.value);
    show(`实验 #${editingId.value} 已删除`, 'success');
    await loadExperiments();
    resetForm();
  } catch (error: any) {
    show(error?.response?.data?.message ?? '删除实验失败', 'error');
  } finally {
    deleting.value = false;
  }
};

onMounted(async () => {
  await loadExperiments();
  resetForm();
});
</script>

<style scoped>
.exp-admin-page {
  display: grid;
  gap: 18px;
}

.ops-typography {
  font-family: 'Georgia', 'Times New Roman', 'Songti SC', 'STSong', serif;
}

.exp-hero {
  display: grid;
  grid-template-columns: 1.2fr 0.95fr;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(124, 147, 255, 0.14);
  background:
    radial-gradient(circle at top left, rgba(110, 84, 255, 0.18), transparent 28%),
    radial-gradient(circle at right bottom, rgba(74, 229, 186, 0.14), transparent 30%),
    linear-gradient(140deg, rgba(14, 17, 36, 0.98), rgba(8, 10, 24, 0.98));
}

.hero-copy {
  display: grid;
  gap: 12px;
  align-content: center;
}

.hero-kicker,
.panel-kicker {
  font-size: 11px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(192, 203, 239, 0.64);
}

.hero-title {
  margin: 0;
  font-size: 34px;
  color: #f6f9ff;
}

.hero-desc {
  margin: 0;
  color: #bcc8e7;
  line-height: 1.9;
  font-size: 14px;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-card {
  min-height: 120px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.08);
  background: rgba(255,255,255,0.04);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-num { font-size: 30px; font-weight: 800; color: #fff; }
.stat-label { font-size: 12px; color: #ccd6f3; }
.cyan { box-shadow: inset 0 0 0 1px rgba(90,243,211,0.08); }
.amber { box-shadow: inset 0 0 0 1px rgba(253,163,72,0.08); }
.rose { box-shadow: inset 0 0 0 1px rgba(255,107,145,0.08); }

.exp-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  min-height: 620px;
}

.exp-list-panel,
.exp-form-panel {
  border: 1px solid rgba(124, 147, 255, 0.14);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(11, 14, 30, 0.98), rgba(8, 10, 22, 0.98));
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title {
  margin: 4px 0 0;
  font-size: 22px;
  color: #f4f8ff;
}

.search-input,
.field-input,
.field-textarea {
  width: 100%;
  border: 1px solid rgba(124, 147, 255, 0.18);
  border-radius: 14px;
  background: rgba(255,255,255,0.04);
  color: #eef4ff;
  padding: 11px 14px;
  outline: none;
}

select.field-input {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  background:
    linear-gradient(180deg, rgba(17, 24, 45, 0.98), rgba(10, 14, 28, 0.98)),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23b9c7ea' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E") no-repeat right 14px center / 12px;
  border-color: rgba(123, 146, 255, 0.28);
  color: #f5f8ff;
  padding-right: 42px;
  cursor: pointer;
}

select.field-input:focus {
  border-color: rgba(90,243,211,0.56);
  box-shadow: 0 0 0 3px rgba(90,243,211,0.12);
}

select.field-input option {
  background: #11182d;
  color: #eef4ff;
}

.field-textarea { resize: vertical; }

.panel-actions,
.form-actions,
.action-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.panel-actions { margin-bottom: 14px; }

.ghost-btn,
.primary-btn,
.danger-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid rgba(124, 147, 255, 0.16);
  background: rgba(255,255,255,0.03);
  color: #d4ddf8;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: linear-gradient(135deg, rgba(94, 120, 255, 0.34), rgba(76, 233, 186, 0.2));
  color: #fff;
}

.danger-btn {
  border-color: rgba(255, 107, 145, 0.24);
  background: rgba(255, 107, 145, 0.12);
  color: #ff9ab7;
}

.exp-list {
  display: grid;
  gap: 10px;
  max-height: 720px;
  overflow: auto;
}

.exp-item {
  border: 1px solid rgba(124, 147, 255, 0.12);
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  color: inherit;
  padding: 15px;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 8px;
}

.exp-item.active {
  border-color: rgba(76, 233, 186, 0.34);
  background: rgba(76, 233, 186, 0.08);
}

.exp-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.exp-order,
.exp-link-count,
.qid-chip,
.preview-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(124, 147, 255, 0.1);
  color: #dfe7ff;
  font-size: 11px;
  font-weight: 700;
}

.exp-name { color: #f6f9ff; font-size: 17px; font-weight: 700; }
.exp-desc { color: #9fb0d9; font-size: 12px; line-height: 1.8; }

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.field-block {
  display: grid;
  gap: 8px;
}

.field-block.wide { grid-column: 1 / -1; }
.field-block.small { max-width: 200px; }

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: #dbe5ff;
}

.field-hint {
  font-size: 11px;
  color: #91a3d4;
}

.question-preview {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid rgba(124, 147, 255, 0.12);
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
}

.preview-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.preview-title {
  font-size: 14px;
  font-weight: 700;
  color: #edf3ff;
}

.preview-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #94a6d8;
}

.preview-empty { color: #94a6d8; }
.preview-chips { display: flex; gap: 8px; flex-wrap: wrap; }

.panel-empty {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(124, 147, 255, 0.14);
  border-radius: 16px;
  color: #94a6d8;
}

@media (max-width: 1180px) {
  .exp-hero,
  .exp-layout { grid-template-columns: 1fr; }
}

@media (max-width: 760px) {
  .hero-stats,
  .form-grid,
  .panel-head,
  .panel-actions,
  .form-actions,
  .action-right,
  .preview-head { flex-direction: column; align-items: stretch; }
}
</style>
