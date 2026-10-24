<template>
  <AppShell subtitle="学生学情" hide-heading>
    <div class="student-analysis-page teacher-font">
      <header class="analysis-heading">
        <div>
          <p>{{ classroom.activeClass?.name ?? '请先选择班级' }}</p>
          <h1>学生学情</h1>
          <span>先了解全班，再看见每位学生的进步与困难。</span>
        </div>
        <span class="scope-note">统计范围：累计作答（含补练）</span>
      </header>
      <div class="class-metrics">
        <section>
          <span>班级学生</span
          ><strong data-testid="class-student-count">{{ studentRows.length }}</strong
          ><small>当前班级成员</small>
        </section>
        <section>
          <span>已开始作答</span
          ><strong
            >{{ answeredStudents }}<small> / {{ studentRows.length }} 人</small></strong
          ><small>暂无作答 {{ studentRows.length - answeredStudents }} 人</small>
        </section>
        <section>
          <span>整体错误率</span><strong data-testid="class-error-rate">{{ classErrorRate }}</strong
          ><small>错答次数 ÷ 总作答次数</small>
        </section>
      </div>
      <section class="students-panel" aria-label="班级学生学情">
        <div class="students-toolbar">
          <div>
            <h3>学生概览</h3>
            <span>{{
              searchText || participation !== 'all'
                ? `找到 ${filteredStudents.length} 位学生`
                : `共 ${studentRows.length} 位学生`
            }}</span>
          </div>
          <div class="student-filters">
            <input
              v-model="searchText"
              placeholder="搜索姓名或用户名…"
              aria-label="搜索学生"
            /><select v-model="participation" aria-label="作答情况">
              <option value="all">全部学生</option>
              <option value="unanswered">暂无作答</option>
              <option value="answered">已开始作答</option></select
            ><select v-model="sort" aria-label="学生排序">
              <option value="name">按姓名排序</option>
              <option value="error-desc">错误率从高到低</option>
              <option value="attempts-desc">作答次数从多到少</option>
            </select>
          </div>
        </div>
        <p v-if="loadError" role="alert" class="error-message">
          {{ loadError }} <button @click="loadInsights">重新加载</button>
        </p>
        <p v-if="loading" class="empty" role="status">正在加载学生学情…</p>
        <div v-else-if="!filteredStudents.length" class="empty">
          <strong>{{ studentRows.length ? '没有匹配的学生' : '当前班级暂无学生' }}</strong>
          <p>
            {{ studentRows.length ? '试试其他姓名或调整作答筛选。' : '可在班级管理中添加学生。' }}
          </p>
        </div>
        <div v-else class="student-table-wrap">
          <table class="student-table">
            <thead>
              <tr>
                <th>学生</th>
                <th>累计作答</th>
                <th>错答次数</th>
                <th>错误率</th>
                <th class="action-column">学情详情</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="student in visibleStudents" :key="student.id" class="student-row">
                <td>
                  <div class="student-identity">
                    <span class="student-avatar">{{ student.realName.slice(0, 1) }}</span>
                    <div>
                      <strong>{{ student.realName }}</strong
                      ><small>@{{ student.username }}</small>
                    </div>
                  </div>
                </td>
                <td>{{ student.totalCount ? `${student.totalCount} 次` : '—' }}</td>
                <td>{{ student.totalCount ? `${student.wrongCount} 次` : '—' }}</td>
                <td>
                  <span
                    class="rate-label"
                    :class="student.totalCount ? rateClass(student.errorRate!) : 'no-data'"
                    >{{ student.errorRateText }}</span
                  >
                </td>
                <td class="action-column">
                  <button
                    class="student-open"
                    :aria-label="`查看${student.realName}学情`"
                    @click="selectStudent(student)"
                  >
                    查看详情 <span aria-hidden="true">↗</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <nav v-if="studentPages > 1" class="table-pagination" aria-label="学生分页">
          <button :disabled="studentPage <= 1" @click="studentPage--">上一页</button
          ><span>{{ studentPage }} / {{ studentPages }} 页</span
          ><button :disabled="studentPage >= studentPages" @click="studentPage++">下一页</button>
        </nav>
      </section>
      <TeacherDialog :open="!!selectedStudent" title="学生学情详情" drawer @close="closeStudent">
        <div v-if="selectedStudent" class="student-detail-panel">
          <header class="student-profile">
            <span class="profile-avatar">{{ selectedStudent.realName.slice(0, 1) }}</span>
            <div>
              <h3 class="detail-student-name">{{ selectedStudent.realName }}</h3>
              <p>{{ classroom.activeClass?.name }} · @{{ selectedStudent.username }}</p>
            </div>
          </header>
          <div class="detail-metrics">
            <section>
              <span>累计作答</span
              ><strong>{{ selectedStudent.totalCount }}<small> 次</small></strong>
            </section>
            <section>
              <span>错答次数</span
              ><strong>{{ selectedStudent.wrongCount }}<small> 次</small></strong>
            </section>
            <section>
              <span>错误率</span><strong>{{ selectedStudent.errorRateText }}</strong>
            </section>
          </div>
          <nav class="detail-tabs" role="tablist" aria-label="学生详情内容">
            <button role="tab" :aria-selected="activeTab === 'all'" @click="changeTab('all')">
              作答记录</button
            ><button role="tab" :aria-selected="activeTab === 'wrong'" @click="changeTab('wrong')">
              错题记录</button
            ><button
              role="tab"
              :aria-selected="activeTab === 'report'"
              @click="changeTab('report')"
            >
              AI 学习报告
            </button>
          </nav>
          <section v-if="activeTab !== 'report'" class="records-card">
            <header class="records-heading">
              <span>{{ activeTab === 'wrong' ? '错答记录' : '全部作答记录' }}</span
              ><span>共 {{ recordTotal }} 条</span>
            </header>
            <p v-if="recordsError" role="alert" class="error-message">
              {{ recordsError }} <button @click="loadStudentRecords()">重试</button>
            </p>
            <p v-if="recordsLoading && !records.length" role="status" class="empty">
              正在读取作答记录…
            </p>
            <p v-else-if="!records.length && !recordsError" class="empty">
              {{ activeTab === 'wrong' ? '该学生当前没有错题记录。' : '该学生当前没有答题记录。' }}
            </p>
            <div v-else class="record-list">
              <article v-for="record in records" :key="record.recordId" class="record-item">
                <button
                  class="record-trigger"
                  :aria-expanded="openedRecordId === record.recordId"
                  @click="toggleRecord(record)"
                >
                  <div class="record-top">
                    <span
                      class="answer-status"
                      :class="
                        record.correct === true
                          ? 'correct'
                          : record.correct === false
                            ? 'wrong'
                            : 'unknown'
                      "
                      >{{
                        record.correct === true
                          ? '正确'
                          : record.correct === false
                            ? '错答'
                            : '未判定'
                      }}</span
                    ><span
                      >{{ record.practice ? '补练' : '作业' }} ·
                      {{ formatDate(record.answeredAt) }}</span
                    ><span class="expand-mark">{{
                      openedRecordId === record.recordId ? '−' : '＋'
                    }}</span>
                  </div>
                  <div class="record-question"><LatexText :content="record.questionPreview" /></div>
                  <span class="record-hint"
                    >题目 #{{ record.questionId }} · 学生选择 {{ record.userAnswer }}</span
                  >
                </button>
                <div v-if="openedRecordId === record.recordId" class="record-expanded">
                  <p v-if="detailLoading" role="status">正在加载题目详情…</p>
                  <p v-else-if="detailError" role="alert" class="error-message">
                    {{ detailError }} <button @click="fetchRecord(record)">重试</button>
                  </p>
                  <template v-else-if="selectedRecord"
                    ><div class="answer-comparison">
                      <span
                        >学生答案：<strong>{{ selectedRecord.userAnswer }}</strong></span
                      ><span
                        >正确答案：<strong>{{ selectedRecord.correctAnswer }}</strong></span
                      ><span>{{ selectedRecord.difficulty }}</span>
                    </div>
                    <div class="full-question">
                      <LatexText :content="selectedRecord.questionContent" /><img
                        v-if="selectedRecord.imageBase64"
                        :src="selectedRecord.imageBase64"
                        alt="题目配图"
                      />
                    </div>
                    <div class="question-options">
                      <div
                        v-for="(text, key) in selectedRecord.questionOptions"
                        :key="key"
                        class="question-option"
                        :class="{
                          correct: key === selectedRecord.correctAnswer,
                          wrong:
                            key === selectedRecord.userAnswer &&
                            key !== selectedRecord.correctAnswer,
                        }"
                      >
                        <strong>{{ key }}</strong
                        ><LatexText :content="text" />
                      </div>
                    </div>
                    <div v-if="selectedRecord.questionAnalysis" class="question-analysis">
                      <strong>题目解析</strong
                      ><LatexText :content="selectedRecord.questionAnalysis" /></div
                  ></template>
                </div>
              </article>
            </div>
            <button
              v-if="recordPage < recordPages"
              class="load-more"
              :disabled="recordsLoading"
              @click="loadStudentRecords(recordPage + 1, true)"
            >
              {{ recordsLoading ? '加载中…' : '加载更多记录' }}
            </button>
          </section>
          <section v-else class="report-card">
            <p v-if="reportLoading" class="empty" role="status">正在读取学习报告…</p>
            <div v-else-if="reportError" class="empty">
              <strong>{{ reportError }}</strong>
              <p>作答记录仍可正常查看。</p>
              <button class="load-more" @click="loadStudentReport">重新加载</button>
            </div>
            <div v-else-if="!reportContent" class="empty">
              <strong>{{
                selectedStudent.totalCount ? '暂未生成学习报告' : '该学生尚未开始作答'
              }}</strong>
              <p>学习报告生成后会显示在这里。</p>
            </div>
            <template v-else
              ><p class="report-time">生成于 {{ formatDate(reportCreateTime) }}</p>
              <div class="report-output" v-html="renderMarkdown(reportContent)"></div
            ></template>
          </section>
        </div>
      </TeacherDialog>
    </div>
  </AppShell>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import TeacherDialog from '@/components/teacher/TeacherDialog.vue';
import { useClassroomStore } from '@/stores/classroom';
import { getClassStudentsApi } from '@/api/classroom';
import {
  getUserAnswerPageApi,
  getAnswerRecordDetailApi,
  getStudentAnswerStatsApi,
} from '@/api/answer-record';
import { getStudentLatestReportApi } from '@/api/ai';
import { renderMarkdown } from '@/utils/markdown';
import type { AnswerRecordResponse, AnswerRecordSummary } from '@/types/answer-record';
interface StudentInsightRow {
  id: number;
  username: string;
  realName: string;
  totalCount: number;
  wrongCount: number;
  errorRate: number | null;
  errorRateText: string;
}
type DetailTab = 'all' | 'wrong' | 'report';
const classroom = useClassroomStore();
const studentRows = ref<StudentInsightRow[]>([]),
  loading = ref(false),
  loadError = ref(''),
  searchText = ref(''),
  participation = ref('all'),
  sort = ref('name'),
  studentPage = ref(1);
let alive = true,
  classVersion = 0,
  selectionVersion = 0,
  recordsVersion = 0,
  detailVersion = 0,
  reportVersion = 0;
const answeredStudents = computed(() => studentRows.value.filter((s) => s.totalCount > 0).length);
const classErrorRate = computed(() => {
  const total = studentRows.value.reduce((n, s) => n + s.totalCount, 0),
    wrong = studentRows.value.reduce((n, s) => n + s.wrongCount, 0);
  return total ? `${((wrong / total) * 100).toFixed(1)}%` : '暂无作答';
});
const filteredStudents = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase();
  return studentRows.value
    .filter(
      (s) =>
        (!keyword ||
          s.realName.toLocaleLowerCase().includes(keyword) ||
          s.username.toLocaleLowerCase().includes(keyword)) &&
        (participation.value === 'all' ||
          (participation.value === 'unanswered' ? !s.totalCount : s.totalCount > 0)),
    )
    .sort((a, b) => {
      if (sort.value === 'error-desc')
        return (
          (b.errorRate ?? -1) - (a.errorRate ?? -1) || a.realName.localeCompare(b.realName, 'zh-CN')
        );
      if (sort.value === 'attempts-desc')
        return b.totalCount - a.totalCount || a.realName.localeCompare(b.realName, 'zh-CN');
      return a.realName.localeCompare(b.realName, 'zh-CN');
    });
});
const studentPages = computed(() => Math.ceil(filteredStudents.value.length / 20));
const visibleStudents = computed(() =>
  filteredStudents.value.slice((studentPage.value - 1) * 20, studentPage.value * 20),
);
watch([searchText, participation, sort], () => {
  studentPage.value = 1;
});
const selectedStudent = ref<StudentInsightRow | null>(null),
  activeTab = ref<DetailTab>('all');
const records = ref<AnswerRecordSummary[]>([]),
  recordsLoading = ref(false),
  recordsError = ref(''),
  recordPage = ref(1),
  recordPages = ref(0),
  recordTotal = ref(0);
const selectedRecord = ref<AnswerRecordResponse | null>(null),
  openedRecordId = ref(''),
  detailLoading = ref(false),
  detailError = ref('');
const reportLoading = ref(false),
  reportLoaded = ref(false),
  reportError = ref(''),
  reportContent = ref(''),
  reportCreateTime = ref('');
const formatDate = (value: string) =>
  value
    ? new Date(value).toLocaleString('zh-CN', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '—';
const rateClass = (rate: number) => (rate >= 60 ? 'high' : rate >= 30 ? 'medium' : 'low');
function closeStudent() {
  ++selectionVersion;
  ++recordsVersion;
  ++detailVersion;
  ++reportVersion;
  selectedStudent.value = null;
  records.value = [];
  recordsLoading.value = false;
  reportLoading.value = false;
  detailLoading.value = false;
}
function selectStudent(student: StudentInsightRow) {
  closeStudent();
  selectedStudent.value = student;
  activeTab.value = 'all';
  recordPage.value = 1;
  recordPages.value = 0;
  recordTotal.value = 0;
  recordsError.value = '';
  openedRecordId.value = '';
  selectedRecord.value = null;
  reportLoaded.value = false;
  reportError.value = '';
  reportContent.value = '';
  reportCreateTime.value = '';
  void loadStudentRecords();
}
async function loadStudentRecords(page = 1, append = false) {
  const student = selectedStudent.value;
  if (!student || activeTab.value === 'report') return;
  const selection = selectionVersion,
    owner = ++recordsVersion,
    wrongOnly = activeTab.value === 'wrong';
  recordsLoading.value = true;
  recordsError.value = '';
  if (!append) {
    records.value = [];
    recordTotal.value = 0;
    recordPages.value = 0;
    openedRecordId.value = '';
    selectedRecord.value = null;
    ++detailVersion;
  }
  try {
    const data = await getUserAnswerPageApi(student.id, { page, size: 20, wrongOnly });
    if (!alive || selection !== selectionVersion || owner !== recordsVersion) return;
    const incoming = (data.records ?? []).filter(
      (r) => !append || !records.value.some((old) => old.recordId === r.recordId),
    );
    records.value = append ? [...records.value, ...incoming] : incoming;
    recordPage.value = data.currentPage;
    recordPages.value = data.pages;
    recordTotal.value = data.total;
  } catch (e: any) {
    if (alive && selection === selectionVersion && owner === recordsVersion)
      recordsError.value = e?.response?.data?.message ?? '作答记录读取失败，请重试。';
  } finally {
    if (alive && selection === selectionVersion && owner === recordsVersion)
      recordsLoading.value = false;
  }
}
function toggleRecord(record: AnswerRecordSummary) {
  if (openedRecordId.value === record.recordId) {
    openedRecordId.value = '';
    ++detailVersion;
    detailLoading.value = false;
    return;
  }
  openedRecordId.value = record.recordId;
  void fetchRecord(record);
}
async function fetchRecord(record: AnswerRecordSummary) {
  const selection = selectionVersion,
    owner = ++detailVersion;
  detailLoading.value = true;
  detailError.value = '';
  selectedRecord.value = null;
  try {
    const detail = await getAnswerRecordDetailApi(record.recordId);
    if (alive && selection === selectionVersion && owner === detailVersion)
      selectedRecord.value = detail;
  } catch {
    if (alive && selection === selectionVersion && owner === detailVersion)
      detailError.value = '题目详情读取失败，请重试。';
  } finally {
    if (alive && selection === selectionVersion && owner === detailVersion)
      detailLoading.value = false;
  }
}
function changeTab(tab: DetailTab) {
  if (activeTab.value === tab) return;
  const wasReport = activeTab.value === 'report';
  activeTab.value = tab;
  if (tab === 'report') {
    if (!reportLoaded.value && !reportLoading.value) void loadStudentReport();
  } else if (wasReport || tab === 'all' || tab === 'wrong') {
    void loadStudentRecords();
  }
}
async function loadStudentReport() {
  const student = selectedStudent.value;
  if (!student) return;
  const selection = selectionVersion,
    owner = ++reportVersion;
  reportLoading.value = true;
  reportError.value = '';
  try {
    const report = await getStudentLatestReportApi(student.id);
    if (!alive || selection !== selectionVersion || owner !== reportVersion) return;
    reportContent.value = report?.latestReportContent?.trim() ?? '';
    reportCreateTime.value = report?.reportCreateTime ?? '';
    reportLoaded.value = true;
  } catch (e: any) {
    if (!alive || selection !== selectionVersion || owner !== reportVersion) return;
    const message = e?.response?.data?.message ?? '';
    if (
      e?.response?.data?.code === 4402 ||
      e?.response?.status === 404 ||
      /无答题记录|没有报告|未生成/.test(message)
    ) {
      reportLoaded.value = true;
      reportContent.value = '';
    } else reportError.value = '学习报告暂时无法加载，请稍后重试。';
  } finally {
    if (alive && selection === selectionVersion && owner === reportVersion)
      reportLoading.value = false;
  }
}
async function loadInsights() {
  const classId = classroom.activeClassId,
    owner = ++classVersion;
  closeStudent();
  studentRows.value = [];
  loading.value = !!classId;
  loadError.value = '';
  if (!classId) return;
  try {
    const [members, statistics] = await Promise.all([
      getClassStudentsApi(classId),
      getStudentAnswerStatsApi(),
    ]);
    if (!alive || owner !== classVersion || classId !== classroom.activeClassId) return;
    const stats = new Map(statistics.map((s) => [s.userId, s]));
    studentRows.value = members.map((member) => {
      const { totalCount = 0, wrongCount = 0 } = stats.get(member.userId) ?? {};
      const errorRate = totalCount ? (wrongCount / totalCount) * 100 : null;
      return {
        id: member.userId,
        username: member.username,
        realName: member.realName,
        totalCount,
        wrongCount,
        errorRate,
        errorRateText: errorRate === null ? '暂无作答' : `${errorRate.toFixed(1)}%`,
      };
    });
  } catch (e: any) {
    if (alive && owner === classVersion)
      loadError.value = e?.response?.data?.message ?? '学生学情加载失败，请重试。';
  } finally {
    if (alive && owner === classVersion) loading.value = false;
  }
}
watch(
  () => classroom.activeClassId,
  () => {
    searchText.value = '';
    participation.value = 'all';
    studentPage.value = 1;
    void loadInsights();
  },
);
onMounted(async () => {
  if (!classroom.classes.length) {
    await classroom.loadClasses().catch(() => {
      loadError.value = '班级加载失败，请重试。';
    });
  }
  if (alive && !loading.value && !studentRows.value.length) void loadInsights();
});
onBeforeUnmount(() => {
  alive = false;
  ++classVersion;
  closeStudent();
});
</script>
<style scoped>
.student-analysis-page {
  display: grid;
  gap: 24px;
  color: #2b4033;
  min-width: 0;
}
.analysis-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.analysis-heading p {
  margin: 0 0 7px;
  font-size: 12px;
  color: #86978a;
}
.analysis-heading h1 {
  font-size: 26px;
  font-weight: 600;
  margin: 0 0 10px;
}
.analysis-heading > div > span {
  color: #809385;
  font-size: 13px;
}
.scope-note {
  font-size: 11px;
  color: #8a9b8e;
  background: #edf3ee;
  padding: 8px 12px;
  border-radius: 7px;
}
.class-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}
.class-metrics > section {
  padding: 23px 26px;
  border: 1px solid #e1e8e2;
  border-radius: 14px;
  display: grid;
  gap: 12px;
  background: white;
}
.class-metrics > section > span {
  font-size: 12px;
  color: #839488;
}
.class-metrics strong {
  color: #356e53;
  font-size: 29px;
  font-weight: 600;
  line-height: 1.3;
}
.class-metrics small {
  color: #91a094;
  font-size: 11px;
  font-weight: 400;
}
.class-metrics strong > small {
  font-size: 14px;
}
.students-panel {
  border: 1px solid #e1e8e2;
  border-radius: 14px;
  background: white;
  overflow: hidden;
}
.students-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 23px;
}
.students-toolbar h3 {
  font-size: 15px;
  margin: 0 0 6px;
}
.students-toolbar > div > span {
  color: #91a094;
  font-size: 11px;
}
.student-filters {
  display: flex;
  gap: 10px;
  align-items: center;
}
.student-filters input,
.student-filters select {
  font: inherit;
  font-size: 12px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e0e7e1;
  border-radius: 8px;
  background: white;
  color: #657d6c;
}
.student-filters input {
  width: 220px;
}
.student-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
.student-table th {
  font-size: 11px;
  color: #82988a;
  background: #fafcfb;
  padding: 14px 24px;
  font-weight: 500;
}
.student-table td {
  padding: 17px 24px;
  border-top: 1px solid #edf1ee;
  font-size: 13px;
  color: #6c8272;
}
.student-table tr:hover {
  background: #fcfdfc;
}
.student-identity {
  display: flex;
  align-items: center;
  gap: 12px;
}
.student-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: #eff5f0;
  color: #709b7d;
  border-radius: 10px;
  font-size: 13px;
}
.student-identity strong {
  display: block;
  color: #334f3d;
  font-weight: 500;
  font-size: 13px;
}
.student-identity small {
  display: block;
  font-size: 11px;
  color: #9aa89d;
  margin-top: 5px;
}
.rate-label {
  display: inline-flex;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.rate-label.low {
  background: #edf7f0;
  color: #5d9471;
}
.rate-label.medium {
  background: #fcf5e9;
  color: #b09559;
}
.rate-label.high {
  background: #fcf0ec;
  color: #bc7b67;
}
.rate-label.no-data {
  color: #97a59b;
  background: #f3f5f3;
}
.action-column {
  text-align: right;
}
.student-open {
  font: inherit;
  font-size: 12px;
  border: 0;
  background: none;
  color: #4a8966;
  cursor: pointer;
  white-space: nowrap;
}
.student-open span {
  margin-left: 8px;
  color: #9fb6a7;
}
.table-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  padding: 20px;
  border-top: 1px solid #edf1ee;
  font-size: 12px;
  color: #839a89;
}
.table-pagination button,
.load-more {
  border: 1px solid #dfe7e0;
  border-radius: 8px;
  padding: 9px 14px;
  background: #fff;
  color: #5d836a;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.empty {
  padding: 44px 18px;
  text-align: center;
  color: #92a196;
  font-size: 13px;
  line-height: 1.9;
}
.empty strong {
  font-weight: 500;
  color: #6b8774;
  font-size: 15px;
}
.error-message {
  font-size: 13px;
  color: #b76a59;
  line-height: 1.8;
  padding: 12px;
}
.error-message button {
  border: 0;
  background: none;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.student-profile {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 24px;
}
.profile-avatar {
  display: grid;
  place-items: center;
  background: #ebf3ed;
  color: #588363;
  width: 50px;
  height: 50px;
  border-radius: 14px;
  font-size: 20px;
}
.detail-student-name {
  font-size: 23px;
  margin: 0 0 7px;
  font-weight: 600;
  color: #2f4e39;
}
.student-profile p {
  font-size: 12px;
  color: #8a9f8f;
  margin: 0;
}
.detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border: 1px solid #e0e9e1;
  border-radius: 12px;
  background: #fbfdfb;
  margin-bottom: 24px;
}
.detail-metrics section {
  display: grid;
  gap: 10px;
  padding: 18px 20px;
}
.detail-metrics section > span {
  font-size: 11px;
  color: #8ca091;
}
.detail-metrics strong {
  font-size: 21px;
  font-weight: 500;
  color: #52745b;
}
.detail-metrics small {
  font-size: 11px;
  color: #8ca091;
  font-weight: 400;
}
.detail-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #e5ece6;
  margin-bottom: 20px;
}
.detail-tabs button {
  font: inherit;
  font-size: 13px;
  padding: 13px 0;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: #8ca091;
  cursor: pointer;
  white-space: nowrap;
}
.detail-tabs button[aria-selected='true'] {
  border-color: #4a8a62;
  color: #42805a;
}
.records-heading {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #8a9f90;
  margin-bottom: 15px;
}
.record-list {
  display: grid;
  gap: 12px;
}
.record-item {
  border: 1px solid #e0e8e1;
  border-radius: 11px;
  overflow: hidden;
}
.record-trigger {
  display: block;
  width: 100%;
  text-align: left;
  border: 0;
  background: #fff;
  padding: 16px;
  cursor: pointer;
  color: #4c6755;
  font: inherit;
}
.record-trigger:hover {
  background: #fafcfb;
}
.record-top {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #93a599;
  font-size: 11px;
  margin-bottom: 10px;
}
.answer-status {
  border-radius: 4px;
  padding: 3px 7px;
  background: #f2f5f2;
}
.answer-status.correct {
  background: #edf6ef;
  color: #6a9c78;
}
.answer-status.wrong {
  background: #fcf0eb;
  color: #b87f6b;
}
.expand-mark {
  margin-left: auto;
  color: #88a590;
  font-size: 15px;
}
.record-question {
  font-size: 14px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.record-hint {
  display: block;
  margin-top: 9px;
  color: #96a89b;
  font-size: 11px;
}
.record-expanded {
  padding: 18px 16px;
  border-top: 1px solid #edf1ee;
  line-height: 1.85;
  font-size: 14px;
  overflow-wrap: anywhere;
  background: #fdfefd;
}
.answer-comparison {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 0 0 16px;
  font-size: 12px;
  color: #789482;
}
.full-question {
  margin-bottom: 16px;
}
.full-question img {
  display: block;
  max-width: 100%;
  max-height: 320px;
  object-fit: contain;
  margin-top: 12px;
}
.question-options {
  display: grid;
  gap: 8px;
}
.question-option {
  display: flex;
  gap: 14px;
  padding: 10px 12px;
  border: 1px solid #e5ebe6;
  border-radius: 7px;
  color: #6f8877;
}
.question-option.correct {
  border-color: #c9dfcf;
  background: #f1f8f3;
}
.question-option.wrong {
  border-color: #e9d3c8;
  background: #fdf5f0;
}
.question-analysis {
  margin-top: 18px;
  padding: 15px;
  border-radius: 8px;
  background: #f1f6f2;
  color: #64806c;
}
.question-analysis > strong {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
}
.load-more {
  display: block;
  margin: 20px auto;
}
.report-time {
  font-size: 11px;
  color: #96a89b;
  margin: 0 0 18px;
}
.report-output {
  font-size: 14px;
  line-height: 1.9;
  overflow-wrap: anywhere;
  color: #5c7564;
}
.report-output :deep(h1),
.report-output :deep(h2),
.report-output :deep(h3) {
  font-size: 18px;
  color: #3e624a;
  margin: 24px 0 12px;
}
.report-output :deep(table) {
  display: block;
  overflow: auto;
  max-width: 100%;
}
.report-output :deep(pre) {
  white-space: pre-wrap;
}
@media (max-width: 1000px) {
  .students-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .student-filters {
    width: 100%;
  }
  .student-filters input {
    flex: 1;
  }
  .scope-note {
    display: none;
  }
}
@media (max-width: 600px) {
  .student-analysis-page {
    gap: 18px;
  }
  .analysis-heading h1 {
    font-size: 23px;
  }
  .analysis-heading > div > span {
    font-size: 12px;
  }
  .class-metrics {
    gap: 8px;
  }
  .class-metrics > section {
    padding: 16px 11px;
    gap: 9px;
    border-radius: 10px;
  }
  .class-metrics strong {
    font-size: 22px;
  }
  .class-metrics strong > small {
    font-size: 10px;
  }
  .class-metrics > section > small {
    font-size: 9px;
  }
  .class-metrics > section > span {
    font-size: 11px;
  }
  .students-toolbar {
    padding: 18px 14px;
  }
  .student-filters {
    flex-wrap: wrap;
    gap: 8px;
  }
  .student-filters input {
    flex-basis: 100%;
    width: 100%;
  }
  .student-filters select {
    flex: 1;
    width: 0;
  }
  .student-table th,
  .student-table td {
    padding: 14px 10px;
  }
  .student-table th:nth-child(3),
  .student-table td:nth-child(3) {
    display: none;
  }
  .student-avatar {
    display: none;
  }
  .student-table td {
    font-size: 11px;
  }
  .student-identity strong {
    font-size: 12px;
  }
  .student-identity small {
    font-size: 10px;
  }
  .student-open {
    font-size: 11px;
    padding: 0;
  }
  .student-open span {
    display: none;
  }
  .rate-label {
    font-size: 10px;
    padding: 4px 5px;
    white-space: nowrap;
  }
  .detail-metrics section {
    padding: 15px 12px;
  }
  .detail-metrics strong {
    font-size: 19px;
  }
  .detail-tabs {
    gap: 20px;
  }
  .detail-tabs button {
    font-size: 12px;
  }
  .record-trigger {
    padding: 14px;
  }
  .record-top {
    gap: 8px;
    font-size: 10px;
  }
}
</style>
