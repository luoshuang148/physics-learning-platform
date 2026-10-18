<template>
  <AppShell :subtitle="`作业详情 · ${data?.title ?? ''}`" :links="links">
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
          <RouterLink :to="{ path: '/teacher/records', query: { eventId: data.taskId } }" class="ghost-btn">查看原始记录</RouterLink>
          <button class="ghost-btn" @click="exportCsv">导出 CSV</button>
        </div>
      </section>

      <section class="stat-grid">
        <div class="detail-stat"><span>应交</span><strong>{{ data.totalStudents }}</strong></div>
        <div class="detail-stat"><span>已参与</span><strong>{{ data.participatedStudents }}</strong><small>{{ data.participationRate }}%</small></div>
        <div class="detail-stat"><span>已完成</span><strong>{{ data.completedStudents }}</strong><small>{{ data.completionRate }}%</small></div>
        <div class="detail-stat"><span>未交</span><strong>{{ data.notStartedStudents }}</strong></div>
        <div class="detail-stat"><span>未完成</span><strong>{{ data.partialStudents }}</strong></div>
        <div class="detail-stat"><span>首次正确率</span><strong>{{ data.accuracy }}%</strong><small>{{ data.correctQuestions }} / {{ data.answeredQuestions }}</small></div>
      </section>

      <section class="panel-card detail-section">
        <div class="section-head">
          <div>
            <h2>学生完成情况</h2>
          </div>
          <div class="filter-tabs">
            <button v-for="item in filters" :key="item.key" class="filter-btn" :class="{ active: filter === item.key }" @click="filter = item.key">
              {{ item.label }} {{ item.count }}
            </button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="detail-table">
            <thead><tr><th>学生</th><th>账号</th><th>班级</th><th>状态</th><th>已答</th><th>正确</th><th>未答</th><th>最后作答</th><th></th></tr></thead>
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
                <td><RouterLink :to="{ path: '/teacher/records', query: { eventId: data.taskId, studentName: student.realName } }" class="table-link">记录</RouterLink></td>
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
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import { teacherNavLinks } from '@/constants/navigation';
import { getTaskDetailApi } from '@/api/teacher-event';
import type { TaskDetailResponse, TaskStudentRow } from '@/types/teacher-event';

const route = useRoute();
const links = teacherNavLinks;
const loading = ref(true);
const error = ref('');
const data = ref<TaskDetailResponse | null>(null);
const filter = ref<'all' | 'NOT_STARTED' | 'PARTIAL' | 'COMPLETED'>('all');

const statusTextMap: Record<string, string> = { DRAFT: '草稿', SCHEDULED: '未开始', OPEN: '进行中', ENDED: '已截止', CLOSED: '已截止', ARCHIVED: '已归档' };
const studentStatusMap: Record<string, string> = { NOT_STARTED: '未交', PARTIAL: '未完成', COMPLETED: '已完成' };
const statusText = computed(() => data.value ? (statusTextMap[data.value.availability] ?? data.value.availability) : '');
const studentStatusText = (status: TaskStudentRow['status']) => studentStatusMap[status] ?? status;
const filters = computed(() => [
  { key: 'all' as const, label: '全部', count: data.value?.students.length ?? 0 },
  { key: 'NOT_STARTED' as const, label: '未交', count: data.value?.notStartedStudents ?? 0 },
  { key: 'PARTIAL' as const, label: '未完成', count: data.value?.partialStudents ?? 0 },
  { key: 'COMPLETED' as const, label: '已完成', count: data.value?.completedStudents ?? 0 },
]);
const filteredStudents = computed(() => (data.value?.students ?? []).filter(student => filter.value === 'all' || student.status === filter.value));
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const load = async () => {
  loading.value = true; error.value = '';
  try {
    data.value = await getTaskDetailApi(Number(route.params.id));
  } catch (err: any) {
    error.value = err?.response?.data?.message ?? '作业统计加载失败，请稍后重试。';
  } finally { loading.value = false; }
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
@media (max-width: 1100px) { .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } .detail-hero { flex-direction: column; align-items: stretch; } }
@media (max-width: 640px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .detail-section { padding: 12px; } }
</style>
