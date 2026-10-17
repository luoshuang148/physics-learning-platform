<template>
  <AppShell :subtitle="`答题记录管理 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="record-page ops-typography">
      <section class="record-hero">
        <div class="hero-copy">
          <span class="hero-kicker">Archive Console</span>
          <h2 class="hero-title">答题记录</h2>
          <p class="hero-desc">把会话检索、错题排查与清理操作合并到一条工作流里，方便教师快速定位异常答题轨迹。</p>
        </div>
        <div class="hero-stats">
          <div class="stat-card cyan">
            <span class="stat-num">{{ totalCountText }}</span>
            <span class="stat-label">当前列表记录数</span>
          </div>
          <div class="stat-card amber">
            <span class="stat-num">{{ wrongCount }}</span>
            <span class="stat-label">当前错题记录</span>
          </div>
          <div class="stat-card rose">
            <span class="stat-num">{{ uniqueStudents }}</span>
            <span class="stat-label">涉及学生数</span>
          </div>
        </div>
      </section>

      <section class="toolbar">
        <div class="toolbar-left">
          <button class="mode-btn" :class="{ active: mode === 'all' }" @click="switchMode('all')">全部记录</button>
          <button class="mode-btn" :class="{ active: mode === 'wrong' }" @click="switchMode('wrong')">错题记录</button>
          <input v-model="searchText" class="toolbar-search" placeholder="搜索学生姓名 / 题目内容 / 作答…" />
        </div>
        <div class="toolbar-right">
          <button class="ghost-btn" :disabled="loading" @click="reloadCurrent">刷新当前结果</button>
        </div>
      </section>

      <section class="query-strip">
        <div class="query-card">
          <div class="query-title">按学生姓名筛选</div>
          <div class="query-row">
            <input v-model="studentNameInput" class="mini-input student-name" placeholder="输入学生姓名" />
            <button class="ghost-btn" :disabled="loading || !studentNameInput.trim()" @click="loadStudentLatestSessionByName">查最近记录</button>
          </div>
        </div>
        <div class="query-card danger-zone query-card-wide">
          <div class="query-title">批量清理记录</div>
          <div class="query-row compact-grid">
            <input v-model="batchStart" type="datetime-local" class="field-input" />
            <input v-model="batchEnd" type="datetime-local" class="field-input" />
            <button class="danger-btn" :disabled="loading || deletingBatch || !batchStart || !batchEnd" @click="batchDelete">
              {{ deletingBatch ? '清理中…' : '按时间范围批量删除' }}
            </button>
          </div>
        </div>
      </section>

      <section class="record-layout">
        <div class="record-list-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Records</div>
              <div class="panel-title">记录列表</div>
            </div>
            <span class="panel-count">{{ filteredRecords.length }} / {{ records.length }}</span>
          </div>

          <div v-if="loading" class="panel-empty">正在加载答题记录…</div>
          <div v-else-if="filteredRecords.length === 0" class="panel-empty">暂无匹配记录</div>
          <div v-else class="record-list">
            <button
              v-for="record in filteredRecords"
              :key="record.recordId"
              class="record-item"
              :class="{ active: selectedRecord?.recordId === record.recordId, wrong: !record.correct, correct: record.correct }"
              @click="selectedRecord = record"
            >
              <div class="record-item-top">
                <span class="record-badge">{{ record.correct ? '正确' : '错题' }}</span>
                <span class="record-time">{{ formatDate(record.answeredAt) }}</span>
              </div>
              <div class="record-question"><LatexText :content="crop(record.questionContent, 52)" /></div>
              <div class="record-meta">{{ resolveStudentName(record) }} · 题目 #{{ record.questionId }} · 我答 {{ record.userAnswer }} · 正确 {{ record.correctAnswer }}</div>
            </button>
          </div>
        </div>

        <div class="detail-panel">
          <div v-if="!selectedRecord" class="panel-empty large">从左侧选择一条记录查看详情</div>
          <template v-else>
            <div class="panel-head detail-head">
              <div>
                <div class="panel-kicker">Inspection</div>
                <div class="detail-title">记录详情</div>
                <div class="detail-sub">recordId: {{ selectedRecord.recordId }}</div>
              </div>
              <button class="danger-btn small" :disabled="deletingSingle" @click="removeRecord(selectedRecord.recordId)">
                {{ deletingSingle ? '删除中…' : '删除此记录' }}
              </button>
            </div>

            <div class="detail-card-grid">
              <div class="detail-card">
                <div class="detail-row"><span>学生姓名</span><strong>{{ resolveStudentName(selectedRecord) }}</strong></div>
                <div class="detail-row"><span>题目 ID</span><strong>#{{ selectedRecord.questionId }}</strong></div>
                <div class="detail-row"><span>作答结果</span><strong :class="selectedRecord.correct ? 'ok-text' : 'fail-text'">{{ selectedRecord.correct ? '回答正确' : '回答错误' }}</strong></div>
                <div class="detail-row"><span>学生答案</span><strong>{{ selectedRecord.userAnswer }}</strong></div>
                <div class="detail-row"><span>正确答案</span><strong>{{ selectedRecord.correctAnswer }}</strong></div>
              </div>
              <div class="detail-card">
                <div class="detail-row"><span>作答时间</span><strong>{{ formatDate(selectedRecord.answeredAt) }}</strong></div>
                <div class="detail-row"><span>记录状态</span><strong>{{ selectedRecord.correct ? '用于正确率统计' : '已进入错题链路' }}</strong></div>
                <div class="detail-row"><span>后续操作</span><strong>支持删除或继续排查错误原因</strong></div>
              </div>
            </div>

            <div class="question-card">
              <div class="question-title">题干</div>
              <div class="question-content"><LatexText :content="selectedRecord.questionContent" /></div>
            </div>

            <div class="option-list">
              <div
                v-for="(text, key) in selectedRecord.questionOptions"
                :key="key"
                class="option-row"
                :class="{
                  correct: key === selectedRecord.correctAnswer,
                  wrong: key === selectedRecord.userAnswer && key !== selectedRecord.correctAnswer,
                }"
              >
                <span class="option-key">{{ key }}</span>
                <span class="option-text"><LatexText :content="text" /></span>
              </div>
            </div>

            <div v-if="selectedRecord.questionAnalysis" class="analysis-card">
              <div class="question-title">题目解析</div>
              <div class="question-content"><LatexText :content="selectedRecord.questionAnalysis" /></div>
            </div>

            <div v-if="selectedRecord.errorReason" class="analysis-card reason-card">
              <div class="question-title">学生填写的错误原因</div>
              <div class="question-content">{{ selectedRecord.errorReason }}</div>
            </div>
          </template>
        </div>
      </section>
    </div>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import LatexText from '@/components/LatexText.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import {
  batchDeleteAnswerRecordsApi,
  deleteAnswerRecordApi,
  getAllAnswerRecordsApi,
  getAllWrongAnswerRecordsApi,
} from '@/api/answer-record';
import { getAllUsersApi } from '@/api/user';
import { teacherNavLinks } from '@/constants/navigation';
import type { UserResponse } from '@/types/user';
import type { AnswerRecordResponse } from '@/types/answer-record';

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const links = teacherNavLinks;

const mode = ref<'all' | 'wrong'>('all');
const loading = ref(false);
const deletingSingle = ref(false);
const deletingBatch = ref(false);
const searchText = ref('');
const studentNameInput = ref('');
const batchStart = ref('');
const batchEnd = ref('');
const users = ref<UserResponse[]>([]);
const records = ref<AnswerRecordResponse[]>([]);
const selectedRecord = ref<AnswerRecordResponse | null>(null);
const currentLoader = ref<'all' | 'wrong' | 'student-latest'>('all');

const totalCountText = computed(() => records.value.length.toLocaleString());
const wrongCount = computed(() => records.value.filter(item => !item.correct).length);
const uniqueStudents = computed(() => new Set(records.value.map(item => resolveStudentName(item))).size);

const filteredRecords = computed(() => {
  const keyword = searchText.value.trim().toLowerCase();
  if (!keyword) return records.value;
  return records.value.filter((item) => {
    const studentName = resolveStudentName(item).toLowerCase();
    return studentName.includes(keyword)
      || item.questionContent.toLowerCase().includes(keyword)
      || item.userAnswer.toLowerCase().includes(keyword)
      || item.correctAnswer.toLowerCase().includes(keyword);
  });
});

const studentNameMap = computed(() => new Map(users.value.map(user => [String(user.id), user.realName])));

const resolveStudentName = (record: AnswerRecordResponse) => {
  const userId = String(record.userId);
  return studentNameMap.value.get(userId) ?? '未知学生';
};

const crop = (text: string, len: number) => text.length > len ? `${text.slice(0, len)}…` : text;
const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const applyRecords = (next: AnswerRecordResponse[]) => {
  records.value = next;
  selectedRecord.value = next[0] ?? null;
};

const loadAll = async () => {
  currentLoader.value = 'all';
  loading.value = true;
  try {
    const data = await getAllAnswerRecordsApi();
    applyRecords(data.records ?? []);
  } catch (error: any) {
    show(error?.response?.data?.message ?? '加载全部记录失败', 'error');
  } finally {
    loading.value = false;
  }
};

const loadWrong = async () => {
  currentLoader.value = 'wrong';
  loading.value = true;
  try {
    const data = await getAllWrongAnswerRecordsApi();
    applyRecords(data.records ?? []);
  } catch (error: any) {
    show(error?.response?.data?.message ?? '加载错题记录失败', 'error');
  } finally {
    loading.value = false;
  }
};

const switchMode = async (next: 'all' | 'wrong') => {
  mode.value = next;
  if (next === 'all') await loadAll();
  else await loadWrong();
};

const reloadCurrent = async () => {
  if (currentLoader.value === 'wrong') await loadWrong();
  else if (currentLoader.value === 'student-latest' && studentNameInput.value.trim()) await loadStudentLatestSessionByName();
  else await loadAll();
};

const loadStudentLatestSessionByName = async () => {
  const keyword = studentNameInput.value.trim().toLowerCase();
  if (!keyword) return;
  currentLoader.value = 'student-latest';
  loading.value = true;
  try {
    const latestRecords = mode.value === 'wrong'
      ? await getAllWrongAnswerRecordsApi()
      : await getAllAnswerRecordsApi();
    const matchedUsers = users.value.filter(user => user.realName.toLowerCase().includes(keyword));
    if (!matchedUsers.length) {
      applyRecords([]);
      show('未找到匹配的学生姓名', 'error');
      return;
    }
    const matchedUserIds = new Set(matchedUsers.map(user => String(user.id)));
    applyRecords((latestRecords.records ?? []).filter(record => matchedUserIds.has(String(record.userId))));
    show(`已按学生姓名筛选 ${studentNameInput.value.trim()} 的记录`, 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '按学生姓名筛选记录失败', 'error');
  } finally {
    loading.value = false;
  }
};

const removeRecord = async (recordId: string) => {
  deletingSingle.value = true;
  try {
    await deleteAnswerRecordApi(recordId);
    records.value = records.value.filter(item => item.recordId !== recordId);
    if (selectedRecord.value?.recordId === recordId) {
      selectedRecord.value = records.value[0] ?? null;
    }
    show('记录已删除', 'success');
  } catch (error: any) {
    show(error?.response?.data?.message ?? '删除记录失败', 'error');
  } finally {
    deletingSingle.value = false;
  }
};

const batchDelete = async () => {
  if (!batchStart.value || !batchEnd.value) return;
  deletingBatch.value = true;
  try {
    await batchDeleteAnswerRecordsApi({
      startTime: new Date(batchStart.value).toISOString().slice(0, 19),
      endTime: new Date(batchEnd.value).toISOString().slice(0, 19),
    });
    show('批量删除完成', 'success');
    await reloadCurrent();
  } catch (error: any) {
    show(error?.response?.data?.message ?? '批量删除失败', 'error');
  } finally {
    deletingBatch.value = false;
  }
};

onMounted(async () => {
  try {
    users.value = await getAllUsersApi();
  } catch {
    users.value = [];
  }
  await loadAll();
});
</script>

<style scoped>
.record-page {
  display: grid;
  gap: 18px;
}

.ops-typography {
  font-family: 'Georgia', 'Times New Roman', 'Songti SC', 'STSong', serif;
}

.record-hero {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(134, 154, 255, 0.15);
  background:
    radial-gradient(circle at top right, rgba(255, 120, 120, 0.14), transparent 26%),
    radial-gradient(circle at left bottom, rgba(90, 243, 211, 0.12), transparent 30%),
    linear-gradient(145deg, rgba(15, 18, 38, 0.98), rgba(8, 10, 24, 0.98));
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
  color: rgba(189, 200, 233, 0.62);
}

.hero-title {
  margin: 0;
  font-size: 34px;
  color: #f7fbff;
}

.hero-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #b9c7ea;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.stat-card {
  min-height: 122px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(255,255,255,0.08);
  background: rgba(255,255,255,0.04);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.stat-num { font-size: 30px; font-weight: 800; color: #fff; }
.stat-label { font-size: 12px; color: #c7d2ef; }
.cyan { box-shadow: inset 0 0 0 1px rgba(90,243,211,0.08); }
.amber { box-shadow: inset 0 0 0 1px rgba(253,163,72,0.08); }
.rose { box-shadow: inset 0 0 0 1px rgba(255,107,145,0.08); }

.toolbar,
.query-card,
.record-list-panel,
.detail-panel,
.question-card,
.analysis-card,
.detail-card,
.option-row {
  border: 1px solid rgba(134,154,255,0.14);
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(11,15,30,0.98), rgba(8,10,22,0.98));
}

.toolbar {
  padding: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.toolbar-left,
.toolbar-right,
.query-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.toolbar-left { flex: 1; }

.mode-btn,
.ghost-btn,
.danger-btn {
  height: 40px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid rgba(134,154,255,0.16);
  background: rgba(255,255,255,0.03);
  color: #c8d4f3;
  cursor: pointer;
  font-weight: 700;
}

.mode-btn.active {
  border-color: rgba(90,243,211,0.34);
  background: rgba(90,243,211,0.12);
  color: #92f9e3;
}

.toolbar-search,
.mini-input,
.field-input {
  border: 1px solid rgba(134,154,255,0.18);
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

.toolbar-search { min-width: 260px; flex: 1; }
.mini-input { min-width: 120px; }
.mini-input.session { min-width: 220px; flex: 1; }

.query-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.query-card {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.query-title {
  color: #f4f8ff;
  font-size: 14px;
  font-weight: 700;
}

.compact-grid {
  display: grid;
  grid-template-columns: 1fr;
  width: 100%;
}

.danger-zone {
  border-color: rgba(255,107,145,0.16);
}

.danger-btn {
  border-color: rgba(255,107,145,0.24);
  background: rgba(255,107,145,0.12);
  color: #ff9ab7;
}

.danger-btn.small { height: 38px; }

.record-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
  min-height: 620px;
}

.record-list-panel,
.detail-panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-title {
  margin-top: 4px;
  color: #f4f8ff;
  font-size: 22px;
}

.panel-count {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(134,154,255,0.1);
  color: #c8d5f4;
  font-size: 11px;
}

.record-list {
  display: grid;
  gap: 10px;
  max-height: 760px;
  overflow: auto;
}

.record-item {
  border: 1px solid rgba(134,154,255,0.12);
  border-radius: 18px;
  background: rgba(255,255,255,0.03);
  color: inherit;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  display: grid;
  gap: 8px;
}

.record-item.active { border-color: rgba(90,243,211,0.34); background: rgba(90,243,211,0.08); }
.record-item.correct { box-shadow: inset 0 0 0 1px rgba(90,243,211,0.03); }
.record-item.wrong { box-shadow: inset 0 0 0 1px rgba(255,107,145,0.03); }

.record-item-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.record-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(134,154,255,0.1);
  color: #dbe5ff;
  font-size: 11px;
  font-weight: 700;
}

.record-time,
.record-meta,
.record-session,
.detail-sub { color: #9cafda; font-size: 12px; }
.record-question { color: #f7fbff; line-height: 1.7; font-size: 14px; }
.detail-title { font-size: 24px; color: #f7fbff; }

.detail-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.detail-card,
.question-card,
.analysis-card {
  padding: 16px 18px;
}

.detail-card {
  display: grid;
  gap: 12px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.detail-row span { color: #97a9d8; font-size: 12px; }
.detail-row strong { color: #f1f6ff; font-size: 13px; word-break: break-all; }
.ok-text { color: #89f3d9; }
.fail-text { color: #ff9ab7; }

.question-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 700;
  color: #edf3ff;
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.question-content {
  color: #eef4ff;
  line-height: 1.8;
}

.option-list {
  display: grid;
  gap: 10px;
}

.option-row {
  padding: 14px 16px;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.option-row.correct {
  border-color: rgba(90,243,211,0.22);
  background: linear-gradient(180deg, rgba(16,36,38,0.86), rgba(8,10,22,0.98));
}

.option-row.wrong {
  border-color: rgba(255,107,145,0.22);
  background: linear-gradient(180deg, rgba(42,16,28,0.86), rgba(8,10,22,0.98));
}

.option-key {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: rgba(134,154,255,0.12);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #dfe7ff;
  flex-shrink: 0;
}

.option-text {
  color: #eff4ff;
  line-height: 1.75;
}

.panel-empty {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(134,154,255,0.14);
  border-radius: 18px;
  color: #91a3d8;
}

.panel-empty.large {
  min-height: 560px;
}

@media (max-width: 1180px) {
  .record-hero,
  .query-strip,
  .record-layout,
  .detail-card-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-stats,
  .toolbar,
  .panel-head,
  .record-item-top,
  .detail-row,
  .query-row {
    flex-direction: column;
    align-items: stretch;
  }

  .mode-btn,
  .ghost-btn,
  .danger-btn,
  .toolbar-search,
  .mini-input,
  .field-input {
    width: 100%;
  }
}
</style>
