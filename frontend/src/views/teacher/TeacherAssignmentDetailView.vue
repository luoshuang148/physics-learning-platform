<template>
  <AppShell :subtitle="`作业详情 · ${data?.title ?? ''}`">
    <div v-if="loading" class="detail-loading">正在加载作业统计…</div>
    <div v-else-if="error" class="panel-card detail-error">{{ error }}</div>
    <div v-else-if="data" class="detail-page">
      <section class="detail-hero">
        <div>
          <RouterLink to="/teacher/event-manage" class="back-link">← 返回任务管理</RouterLink>
          <div class="detail-meta">
            <span class="status-pill" :class="data.availability.toLowerCase()">{{ statusText }}</span>
            <span>共 {{ data.questionCount }} 题</span>
            <span>开始 {{ data.startAt ? formatDate(data.startAt) : '立即' }}</span>
            <span>截止 {{ data.dueAt ? formatDate(data.dueAt) : '不限' }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <button class="ghost-btn" @click="exportCsv">导出 CSV</button>
        </div>
      </section>

      <section class="panel-card detail-section">
        <div class="section-head">
          <div>
            <h2>批量学生报告</h2>
            <p>按任务应参与名单逐人生成，使用每名学生已保存的作答记录；无作答的学生自动跳过，不调用模型。</p>
          </div>
          <div class="batch-actions">
            <button v-if="batch" class="ghost-btn" :disabled="refreshing" @click="refreshBatch">{{ refreshing ? '刷新中…' : '刷新进度' }}</button>
            <span v-if="!taskClosed && !batch" class="batch-hint">任务截止后可生成</span>
            <button v-if="taskClosed" class="primary-btn"
              :disabled="creating || (!pendingCreation && isReportBatchActive(batch))" @click="createBatch">
              {{ creating ? '创建中…' : pendingCreation ? '重试创建' : batch ? '生成新批次' : '生成全班报告' }}
            </button>
          </div>
        </div>

        <div v-if="batchError" class="batch-error">{{ batchError }}</div>
        <template v-if="batch">
          <div class="batch-summary">
            <span class="batch-status" :class="batch.status.toLowerCase()">{{ reportBatchStatusText(batch.status) }}</span>
            <span>进度 {{ batch.finishedCount }} / {{ batch.totalCount }}</span>
            <span class="batch-chip ok">成功 {{ batch.succeededCount }}</span>
            <span class="batch-chip skip">跳过 {{ batch.skippedCount }}</span>
            <span class="batch-chip fail">失败 {{ batch.failedCount }}</span>
            <span v-if="batch.pendingCount + batch.processingCount > 0" class="batch-chip running">处理中 {{ batch.pendingCount + batch.processingCount }}</span>
            <span v-if="batch.finishedAt" class="batch-time">完成于 {{ formatDate(batch.finishedAt) }}</span>
          </div>
          <div class="batch-bar"><span :style="{ width: batchPercent + '%' }"></span></div>

          <div class="table-wrap">
            <table class="detail-table">
              <thead><tr><th>学生</th><th>状态</th><th>尝试模型</th><th>说明</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in batchItems" :key="item.id">
                  <td><strong>{{ item.studentName }}</strong></td>
                  <td><span class="row-status" :class="item.status.toLowerCase()">{{ reportBatchItemStatusText(item.status) }}</span></td>
                  <td>{{ item.attempts }} / {{ item.maxAttempts }}<small v-if="item.manualRetryCount">（手动 {{ item.manualRetryCount }}）</small></td>
                  <td class="batch-note">{{ item.errorMessage || item.skipReason || '—' }}</td>
                  <td class="batch-row-actions">
                    <button v-if="item.status === 'SUCCEEDED'" class="table-link" @click="openReport(item)">查看报告</button>
                    <button v-if="item.status === 'FAILED'" class="table-link retry" :disabled="retryingIds.includes(item.id)" @click="retryItem(item)">
                      {{ retryingIds.includes(item.id) ? '提交中…' : '重试' }}
                    </button>
                  </td>
                </tr>
                <tr v-if="batchItems.length === 0"><td colspan="5" class="table-empty">批次内没有学生</td></tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>

      <div v-if="report || reportLoading" class="report-modal" @click.self="closeReport">
        <div class="report-card">
          <header>
            <div>
              <h3>{{ report?.studentName ?? '' }} · 学情报告</h3>
              <p>{{ report?.generatedAt ? '生成于 ' + formatDate(report.generatedAt) : '' }}</p>
            </div>
            <button class="ghost-btn" @click="closeReport">关闭</button>
          </header>
          <div v-if="reportLoading" class="report-loading">正在读取报告…</div>
          <pre v-else class="report-content">{{ report?.content }}</pre>
        </div>
      </div>

      <section class="stat-grid">
        <div class="detail-stat"><span>应交</span><strong>{{ data.totalStudents }}</strong></div>
        <div class="detail-stat"><span>已参与</span><strong>{{ data.participatedStudents }}</strong><small>{{ data.participationRate }}%</small></div>
        <div class="detail-stat"><span>已完成</span><strong>{{ data.completedStudents }}</strong><small>{{ data.completionRate }}%</small></div>
        <div class="detail-stat"><span>{{ studentStatusText('NOT_STARTED') }}</span><strong>{{ data.notStartedStudents }}</strong></div>
        <div class="detail-stat"><span>{{ studentStatusText('PARTIAL') }}</span><strong>{{ data.partialStudents }}</strong></div>
        <div class="detail-stat"><span>首次正确率</span><strong>{{ data.accuracy }}%</strong><small>{{ data.correctQuestions }} / {{ data.answeredQuestions }}</small></div>
      </section>

      <section class="panel-card detail-section">
        <div class="section-head">
          <div>
            <h2>学生完成情况</h2>
            <p v-if="data.legacyRoster">升级前名单按迁移时现有数据初始化，原始名单无法完全还原；升级后名单不再随新注册或停用变化。</p>
            <p>按作业应参与名单及已保存题数统计，停用账号仍保留历史。答完全部题目即完成；截止后，未作答记为未交，部分作答记为未完成。</p>
          </div>
          <div class="filter-tabs">
            <button v-for="item in filters" :key="item.key" class="filter-btn" :class="{ active: filter === item.key }" @click="filter = item.key">
              {{ item.label }} {{ item.count }}
            </button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="detail-table">
            <thead><tr><th>学生</th><th>账号</th><th>班级</th><th>状态</th><th>已答</th><th>正确</th><th>未答</th><th>最后作答</th></tr></thead>
            <tbody>
              <tr v-for="student in filteredStudents" :key="student.userId">
                <td><strong>{{ student.realName }}</strong></td>
                <td>{{ student.username }}</td>
                <td>{{ student.className || '未分班' }}</td>
                <td><span class="row-status" :class="student.status.toLowerCase()">{{ studentStatusText(student.status) }}</span></td>
                <td>{{ student.answeredQuestions }}</td>
                <td>{{ student.correctQuestions }}</td>
                <td>{{ student.missingQuestions }}</td>
                <td>{{ student.lastAnsweredAt ? formatDate(student.lastAnsweredAt) : '—' }}</td>

              </tr>
              <tr v-if="filteredStudents.length === 0"><td colspan="9" class="table-empty">当前筛选下没有学生</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel-card detail-section">
        <div class="section-head">
          <div>
            <h2>每题表现</h2>
          </div>
        </div>
        <div class="table-wrap">
          <table class="detail-table">
            <thead><tr><th>题目</th><th>内容</th><th>作答人数</th><th>正确人数</th><th>未作答</th><th>正确率</th></tr></thead>
            <tbody>
              <tr v-for="question in data.questions" :key="question.questionId">
                <td>#{{ question.questionId }}</td>
                <td class="question-preview">{{ question.contentPreview }}</td>
                <td>{{ question.answeredStudents }}</td>
                <td>{{ question.correctStudents }}</td>
                <td>{{ question.unansweredStudents }}</td>
                <td><strong>{{ question.accuracy }}%</strong></td>
              </tr>
              <tr v-if="data.questions.length === 0"><td colspan="6" class="table-empty">该作业还没有题目</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import AppShell from '@/layouts/AppShell.vue';
import { getTaskDetailApi } from '@/api/teacher-event';
import {
  createReportBatchApi,
  getReportBatchApi,
  getReportBatchItemReportApi,
  isReportBatchActive,
  listReportBatchItemsApi,
  listReportBatchesApi,
  reportBatchItemStatusText,
  reportBatchStatusText,
  retryReportBatchItemApi,
} from '@/api/report-batch';
import type { ReportBatch, ReportBatchItem, ReportBatchItemReport } from '@/api/report-batch';
import type { TaskDetailResponse, TaskStudentRow } from '@/types/teacher-event';
import { studentProgressText, taskAvailabilityText } from '@/utils/assignmentProgress';

const route = useRoute();
const eventId = Number(route.params.id);
const auth = useAuthStore();
const creationStorageKey = `report_batch_pending_${auth.user?.id}_${eventId}`;
const pendingCreation = ref(Boolean(localStorage.getItem(creationStorageKey)));
const loading = ref(true);
const error = ref('');
const data = ref<TaskDetailResponse | null>(null);
const filter = ref<'all' | 'NOT_STARTED' | 'PARTIAL' | 'COMPLETED'>('all');

const batch = ref<ReportBatch | null>(null);
const batchItems = ref<ReportBatchItem[]>([]);
const batchError = ref('');
const creating = ref(false);
const refreshing = ref(false);
const retryingIds = ref<number[]>([]);
const report = ref<ReportBatchItemReport | null>(null);
const reportLoading = ref(false);
const MAX_POLLS = 120;
let pollTimer: number | null = null;
let pollCount = 0;
let refreshVersion = 0;

const statusText = computed(() => taskAvailabilityText(data.value?.availability));
const taskClosed = computed(() => ['ENDED', 'CLOSED', 'ARCHIVED'].includes(data.value?.availability ?? ''));
const studentStatusText = (status: TaskStudentRow['status']) => studentProgressText(status, data.value?.availability);
const filters = computed(() => [
  { key: 'all' as const, label: '全部', count: data.value?.students.length ?? 0 },
  { key: 'NOT_STARTED' as const, label: studentStatusText('NOT_STARTED'), count: data.value?.notStartedStudents ?? 0 },
  { key: 'PARTIAL' as const, label: studentStatusText('PARTIAL'), count: data.value?.partialStudents ?? 0 },
  { key: 'COMPLETED' as const, label: '已完成', count: data.value?.completedStudents ?? 0 },
]);
const filteredStudents = computed(() => (data.value?.students ?? []).filter(student => filter.value === 'all' || student.status === filter.value));
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const batchPercent = computed(() => {
  const current = batch.value;
  if (!current || !current.totalCount) return 0;
  return Math.round((current.finishedCount / current.totalCount) * 100);
});

const load = async () => {
  loading.value = true; error.value = '';
  try {
    data.value = await getTaskDetailApi(eventId);
    await loadBatchForEvent();
  } catch (err: any) {
    error.value = err?.response?.data?.message ?? '作业统计加载失败，请稍后重试。';
  } finally { loading.value = false; }
};

const idempotencyKey = () => {
  let key = localStorage.getItem(creationStorageKey);
  if (!key) {
    key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}_${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(creationStorageKey, key);
  }
  return key;
};

const loadBatchForEvent = async () => {
  try {
    const page = await listReportBatchesApi(1, 1, eventId);
    batch.value = page.records.find(item => item.eventId === eventId) ?? null;
    if (batch.value) await refreshBatch();
    else stopPolling();
  } catch { /* 批次加载失败不影响作业统计展示 */ }
};

const refreshBatch = async () => {
  if (!batch.value) return;
  const targetId = batch.value.id;
  const version = ++refreshVersion;
  refreshing.value = true; batchError.value = '';
  try {
    const [detail, items] = await Promise.all([
      getReportBatchApi(targetId),
      fetchAllItems(targetId),
    ]);
    if (version !== refreshVersion || batch.value?.id !== targetId) return;
    batch.value = detail;
    batchItems.value = items;
    ensurePolling();
  } catch (err: any) {
    if (version === refreshVersion && batch.value?.id === targetId)
      batchError.value = err?.response?.data?.message ?? '批次进度加载失败，请稍后重试。';
  } finally { if (version === refreshVersion) refreshing.value = false; }
};

const fetchAllItems = async (batchId: number) => {
  const first = await listReportBatchItemsApi(batchId, 1, 50);
  const items = [...first.records];
  for (let page = 2; page <= first.pages; page += 1) {
    const next = await listReportBatchItemsApi(batchId, page, 50);
    items.push(...next.records);
  }
  return items;
};

const createBatch = async () => {
  if (creating.value) return;
  creating.value = true; batchError.value = '';
  try {
    const key = idempotencyKey();
    pendingCreation.value = true;
    const created = await createReportBatchApi(eventId, key);
    localStorage.removeItem(creationStorageKey);
    pendingCreation.value = false;
    batch.value = created;
    report.value = null;
    await refreshBatch();
  } catch (err: any) {
    batchError.value = err?.response?.data?.message ?? '批量报告创建失败，请稍后重试。';
  } finally { creating.value = false; }
};

const retryItem = async (item: ReportBatchItem) => {
  if (!batch.value) return;
  retryingIds.value = [...retryingIds.value, item.id];
  try {
    await retryReportBatchItemApi(batch.value.id, item.id);
    await refreshBatch();
  } catch (err: any) {
    batchError.value = err?.response?.data?.message ?? '重试失败，请稍后再试。';
  } finally {
    retryingIds.value = retryingIds.value.filter(id => id !== item.id);
  }
};

const openReport = async (item: ReportBatchItem) => {
  if (!batch.value) return;
  reportLoading.value = true;
  try {
    report.value = await getReportBatchItemReportApi(batch.value.id, item.id);
  } catch (err: any) {
    batchError.value = err?.response?.data?.message ?? '报告读取失败，请稍后重试。';
  } finally { reportLoading.value = false; }
};

const closeReport = () => { report.value = null; };

const stopPolling = () => {
  if (pollTimer !== null) { window.clearInterval(pollTimer); pollTimer = null; }
};

const ensurePolling = () => {
  if (!isReportBatchActive(batch.value)) { stopPolling(); return; }
  if (pollTimer !== null) return;
  pollCount = 0;
  pollTimer = window.setInterval(async () => {
    pollCount += 1;
    if (pollCount > MAX_POLLS) { stopPolling(); return; }
    await refreshBatch();
    if (!isReportBatchActive(batch.value)) stopPolling();
  }, 2500);
};

const exportCsv = () => {
  if (!data.value) return;
  const rows = [['姓名', '账号', '班级', '状态', '已答', '正确', '未答', '最后作答'], ...filteredStudents.value.map(s => [
    s.realName, s.username, s.className ?? '', studentStatusText(s.status), String(s.answeredQuestions), String(s.correctQuestions), String(s.missingQuestions), s.lastAnsweredAt ? formatDate(s.lastAnsweredAt) : '',
  ])];
  const csv = '\ufeff' + rows.map(row => row.map(value => '"' + String(value ?? '').replace(/"/g, '""') + '"').join(',')).join('\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
  const anchor = document.createElement('a');
  anchor.href = url; anchor.download = '作业_' + data.value.title + '_学生明细.csv'; anchor.click();
  URL.revokeObjectURL(url);
};
onMounted(load);
onBeforeUnmount(() => { refreshVersion += 1; stopPolling(); });
</script>

<style scoped>
.detail-page { display: grid; gap: 16px; }
.detail-loading { padding: 48px; color: #64748b; }
.detail-error { padding: 24px; color: #b91c1c; }
.detail-hero { display: flex; justify-content: space-between; gap: 18px; align-items: flex-end; padding: 18px 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; }
.back-link { color: #64748b; font-size: 12px; text-decoration: none; }
.detail-kicker { margin-top: 10px; color: #0f766e; font-size: 10px; font-weight: 600; }
.detail-hero h1 { margin: 4px 0 8px; font-size: 24px; color: #172033; }
.detail-meta { display: flex; gap: 12px; flex-wrap: wrap; align-items: center; color: #64748b; font-size: 12px; }
.status-pill, .row-status { padding: 2px 7px; border-radius: 4px; font-size: 11px; background: #f1f5f9; color: #475569; }
.status-pill.open { background: #ecfdf5; color: #0f766e; }
.status-pill.scheduled { background: #fef3c7; color: #92400e; }
.status-pill.ended, .status-pill.closed { background: #f1f5f9; color: #64748b; }
.detail-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.stat-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.detail-stat { display: grid; gap: 4px; padding: 14px; background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; }
.detail-stat span { color: #64748b; font-size: 11px; }
.detail-stat strong { color: #0f766e; font-size: 24px; line-height: 1.1; }
.detail-stat small { color: #94a3b8; font-size: 10px; }
.detail-section { padding: 16px 18px; }
.section-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 12px; }
.section-head h2 { margin: 0 0 3px; font-size: 16px; color: #172033; }
.section-head p { margin: 0; color: #64748b; font-size: 12px; }
.filter-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.filter-btn { border: 1px solid #e2e8f0; background: #fff; color: #475569; border-radius: 6px; padding: 4px 8px; font-size: 11px; cursor: pointer; }
.filter-btn.active { background: #ecfdf5; border-color: #a7f3d0; color: #0f766e; }
.table-wrap { overflow-x: auto; }
.detail-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.detail-table th { text-align: left; padding: 8px; color: #64748b; background: #f8fafc; font-weight: 600; white-space: nowrap; }
.detail-table td { padding: 9px 8px; border-top: 1px solid #eef2f7; color: #334155; }
.detail-table td strong { color: #172033; }
.question-preview { max-width: 420px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table-link { color: #0f766e; text-decoration: none; white-space: nowrap; }
.table-empty { text-align: center; color: #94a3b8; padding: 22px !important; }
.row-status.not_started { background: #fef2f2; color: #b91c1c; }
.row-status.partial { background: #fef3c7; color: #92400e; }
.row-status.completed { background: #ecfdf5; color: #0f766e; }
.batch-actions { display: flex; gap: 8px; align-items: center; }
.batch-hint { color: #94a3b8; font-size: 12px; }
.batch-error { margin-bottom: 10px; padding: 8px 10px; background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; color: #b91c1c; font-size: 12px; }
.batch-summary { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; color: #475569; font-size: 12px; }
.batch-status { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #f1f5f9; color: #475569; }
.batch-status.pending, .batch-status.processing { background: #eff6ff; color: #1d4ed8; }
.batch-status.succeeded { background: #ecfdf5; color: #0f766e; }
.batch-status.partial_failed { background: #fef3c7; color: #92400e; }
.batch-status.failed { background: #fef2f2; color: #b91c1c; }
.batch-chip { padding: 2px 8px; border-radius: 4px; font-size: 11px; }
.batch-chip.ok { background: #ecfdf5; color: #0f766e; }
.batch-chip.skip { background: #f1f5f9; color: #64748b; }
.batch-chip.fail { background: #fef2f2; color: #b91c1c; }
.batch-chip.running { background: #eff6ff; color: #1d4ed8; }
.batch-time { color: #94a3b8; font-size: 11px; }
.batch-bar { margin: 10px 0 4px; height: 6px; border-radius: 999px; background: #eef2f7; overflow: hidden; }
.batch-bar span { display: block; height: 100%; background: #0f766e; transition: width .3s ease; }
.batch-note { max-width: 380px; color: #64748b; }
.batch-row-actions { display: flex; gap: 10px; align-items: center; }
.batch-row-actions button { border: none; background: none; padding: 0; font-size: 12px; cursor: pointer; }
.batch-row-actions button:disabled { color: #94a3b8; cursor: not-allowed; }
.row-status.pending, .row-status.processing { background: #eff6ff; color: #1d4ed8; }
.row-status.succeeded { background: #ecfdf5; color: #0f766e; }
.row-status.failed { background: #fef2f2; color: #b91c1c; }
.row-status.skipped { background: #f1f5f9; color: #64748b; }
.report-modal { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; padding: 24px; background: rgba(15, 23, 42, .45); }
.report-card { width: min(760px, 100%); max-height: 82vh; display: flex; flex-direction: column; background: #fff; border-radius: 12px; overflow: hidden; }
.report-card header { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; padding: 16px 18px; border-bottom: 1px solid #eef2f7; }
.report-card h3 { margin: 0 0 3px; font-size: 16px; color: #172033; }
.report-card p { margin: 0; color: #94a3b8; font-size: 11px; }
.report-loading { padding: 32px; color: #64748b; }
.report-content { margin: 0; padding: 18px; overflow: auto; white-space: pre-wrap; word-break: break-word; color: #334155; font-size: 13px; line-height: 1.7; font-family: inherit; }
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .detail-hero { flex-direction: column; align-items: stretch; } }
@media (max-width: 640px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .detail-section { padding: 12px; } }
</style>
