<template>
  <AppShell :subtitle="`学生学情分析 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="student-analysis-page teacher-font">
      <section class="analysis-hero">
        <div>
          <span class="analysis-kicker">Student insight workspace</span>
          <h1 class="analysis-title">学生学情分析</h1>
          <p class="analysis-summary">按学生查看答题记录、错题记录与个人学习报告，快速判断每位学生的学习状态。</p>
        </div>
        <div class="hero-stats">
          <div class="hero-stat-card">
            <span class="hero-stat-label">学生人数</span>
            <strong class="hero-stat-value">{{ filteredStudents.length }}</strong>
            <span class="hero-stat-note">当前筛选结果</span>
          </div>
          <div class="hero-stat-card warm">
            <span class="hero-stat-label">平均错误率</span>
            <strong class="hero-stat-value">{{ averageErrorRateText }}</strong>
            <span class="hero-stat-note">基于当前筛选学生</span>
          </div>
        </div>
      </section>

      <section class="analysis-toolbar">
        <div class="toolbar-filters">
          <button
            v-for="cls in classOptions"
            :key="cls"
            class="filter-btn"
            :class="{ active: classFilter === cls }"
            @click="classFilter = cls"
          >{{ cls || '全部班级' }}</button>
        </div>
        <input v-model="searchText" class="toolbar-search" placeholder="搜索姓名 / 用户名 / 班级…" />
      </section>

      <section class="analysis-layout">
        <aside class="student-list-panel">
          <div class="panel-head">
            <div>
              <div class="panel-kicker">Students</div>
              <div class="panel-title">学生列表</div>
            </div>
            <span class="panel-count">{{ filteredStudents.length }} / {{ studentRows.length }}</span>
          </div>

          <div v-if="loading" class="panel-empty">正在加载学生学情数据…</div>
          <div v-else-if="filteredStudents.length === 0" class="panel-empty">暂无匹配学生</div>
          <div v-else class="student-list">
            <button
              v-for="student in filteredStudents"
              :key="student.id"
              class="student-item"
              :class="{ active: selectedStudent?.id === student.id }"
              @click="selectStudent(student)"
            >
              <div class="student-item-top">
                <div>
                  <div class="student-name">{{ student.realName }}</div>
                  <div class="student-username">@{{ student.username }}</div>
                </div>
                <span class="student-rate" :class="rateClass(student.errorRate)">{{ student.errorRateText }}</span>
              </div>
              <div class="student-meta">
                <span>{{ student.className || '未分班' }}</span>
                <span>{{ student.totalCount }} 条记录</span>
                <span>{{ student.wrongCount }} 条错题</span>
              </div>
            </button>
          </div>
        </aside>

        <section class="student-detail-panel">
          <div v-if="!selectedStudent" class="panel-empty large">从左侧选择一位学生查看学情详情</div>
          <template v-else>
            <div class="detail-hero-card">
              <div>
                <div class="detail-student-name">{{ selectedStudent.realName }}</div>
                <div class="detail-student-meta">{{ selectedStudent.className || '未分班' }} · @{{ selectedStudent.username }}</div>
              </div>
              <div class="detail-metrics">
                <div class="metric-pill">
                  <span>总答题</span>
                  <strong>{{ selectedStudent.totalCount }}</strong>
                </div>
                <div class="metric-pill danger">
                  <span>错题数</span>
                  <strong>{{ selectedStudent.wrongCount }}</strong>
                </div>
                <div class="metric-pill accent">
                  <span>错误率</span>
                  <strong>{{ selectedStudent.errorRateText }}</strong>
                </div>
              </div>
            </div>

            <section class="report-card">
              <div class="panel-head">
                <div>
                  <div class="panel-kicker">Learning report</div>
                  <div class="panel-title">学生学习报告</div>
                </div>
                <span v-if="reportTimeText" class="report-time">{{ reportTimeText }}</span>
              </div>

              <div v-if="reportLoading" class="panel-empty">正在读取学生学习报告…</div>
              <div v-else-if="reportMessage" class="report-placeholder">
                <div class="report-placeholder-icon">📝</div>
                <div>
                  <h3>{{ reportMessage.title }}</h3>
                  <p>{{ reportMessage.desc }}</p>
                </div>
              </div>
              <div v-else class="report-output">
                <StreamOutput
                  :loading="false"
                  :progress="0"
                  :content="reportContent"
                  :reasoning="''"
                  :token-usage="''"
                  :error="''"
                />
              </div>
            </section>

            <section class="records-card">
              <div class="records-toolbar">
                <div class="mode-switch">
                  <button class="mode-btn" :class="{ active: recordMode === 'all' }" @click="recordMode = 'all'">全部记录</button>
                  <button class="mode-btn" :class="{ active: recordMode === 'wrong' }" @click="recordMode = 'wrong'">错题记录</button>
                </div>
                <div class="records-summary">共 {{ displayedRecords.length }} 条{{ recordMode === 'wrong' ? '错题' : '记录' }}</div>
              </div>

              <div v-if="recordsLoading" class="panel-empty">正在加载学生作答记录…</div>
              <div v-else-if="displayedRecords.length === 0" class="panel-empty">{{ emptyRecordMessage }}</div>
              <div v-else class="record-layout">
                <div class="record-list-panel inner">
                  <div class="panel-head compact">
                    <div>
                      <div class="panel-kicker">Records</div>
                      <div class="panel-title">记录列表</div>
                    </div>
                    <span class="panel-count">{{ displayedRecords.length }}</span>
                  </div>
                  <div class="record-list">
                    <button
                      v-for="record in displayedRecords"
                      :key="record.recordId"
                      class="record-item"
                      :class="{ active: selectedRecord?.recordId === record.recordId, wrong: !record.correct, correct: record.correct }"
                      @click="selectedRecord = record"
                    >
                      <div class="record-item-top">
                        <span class="record-badge">{{ record.correct ? '正确' : '错题' }}</span>
                        <span class="record-time">{{ formatDate(record.answeredAt) }}</span>
                      </div>
                      <div class="record-question"><LatexText :content="crop(record.questionContent, 56)" /></div>
                      <div class="record-meta">题目 #{{ record.questionId }} · 我答 {{ record.userAnswer }} · 正确 {{ record.correctAnswer }}</div>
                    </button>
                  </div>
                </div>

                <div class="detail-panel inner">
                  <div v-if="!selectedRecord" class="panel-empty large">选择一条记录查看详情</div>
                  <template v-else>
                    <div class="panel-head compact detail-head">
                      <div>
                        <div class="panel-kicker">Inspection</div>
                        <div class="panel-title">记录详情</div>
                      </div>
                      <span class="detail-record-status" :class="selectedRecord.correct ? 'ok-text' : 'fail-text'">
                        {{ selectedRecord.correct ? '回答正确' : '回答错误' }}
                      </span>
                    </div>

                    <div class="detail-card-grid">
                      <div class="detail-card">
                        <div class="detail-row"><span>题目 ID</span><strong>#{{ selectedRecord.questionId }}</strong></div>
                        <div class="detail-row"><span>学生答案</span><strong>{{ selectedRecord.userAnswer }}</strong></div>
                        <div class="detail-row"><span>正确答案</span><strong>{{ selectedRecord.correctAnswer }}</strong></div>
                        <div class="detail-row"><span>作答时间</span><strong>{{ formatDate(selectedRecord.answeredAt) }}</strong></div>
                      </div>
                      <div class="detail-card">
                        <div class="detail-row"><span>难度</span><strong>{{ selectedRecord.difficulty || '未标注' }}</strong></div>
                        <div class="detail-row"><span>记录类型</span><strong>{{ selectedRecord.correct ? '正常记录' : '错题记录' }}</strong></div>
                        <div class="detail-row"><span>错误原因</span><strong>{{ selectedRecord.errorReason || '未填写' }}</strong></div>
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
                  </template>
                </div>
              </div>
            </section>
          </template>
        </section>
      </section>
    </div>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { teacherNavLinks } from '@/constants/navigation';
import AppShell from '@/layouts/AppShell.vue';
import LatexText from '@/components/LatexText.vue';
import StreamOutput from '@/components/StreamOutput.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { getAllUsersApi } from '@/api/user';
import { getUserAnswerRecordsApi, getUserWrongAnswerRecordsApi } from '@/api/answer-record';
import { getStudentLatestReportApi } from '@/api/ai';
import type { UserResponse } from '@/types/user';
import type { AnswerRecordResponse } from '@/types/answer-record';

interface StudentInsightRow extends UserResponse {
  totalCount: number;
  wrongCount: number;
  errorRate: number;
  errorRateText: string;
}

const auth = useAuthStore();
const { visible, type, message, show } = useToast();

const links = teacherNavLinks;

const loading = ref(true);
const recordsLoading = ref(false);
const reportLoading = ref(false);
const searchText = ref('');
const classFilter = ref('');
const recordMode = ref<'all' | 'wrong'>('all');
const studentRows = ref<StudentInsightRow[]>([]);
const selectedStudent = ref<StudentInsightRow | null>(null);
const allRecords = ref<AnswerRecordResponse[]>([]);
const wrongRecords = ref<AnswerRecordResponse[]>([]);
const selectedRecord = ref<AnswerRecordResponse | null>(null);
const reportContent = ref('');
const reportCreateTime = ref('');
const reportMessage = ref<{ title: string; desc: string } | null>(null);

const classOptions = computed(() => {
  const set = new Set(studentRows.value.map(student => student.className ?? ''));
  return ['', ...Array.from(set).filter(Boolean).sort()];
});

const filteredStudents = computed(() => {
  let list = studentRows.value;
  if (classFilter.value) list = list.filter(student => student.className === classFilter.value);
  const keyword = searchText.value.trim().toLowerCase();
  if (keyword) {
    list = list.filter(student =>
      student.realName.toLowerCase().includes(keyword)
      || student.username.toLowerCase().includes(keyword)
      || (student.className ?? '').toLowerCase().includes(keyword)
    );
  }
  return list;
});

const averageErrorRateText = computed(() => {
  if (!filteredStudents.value.length) return '0.0%';
  const total = filteredStudents.value.reduce((sum, student) => sum + student.errorRate, 0);
  return `${(total / filteredStudents.value.length).toFixed(1)}%`;
});

const displayedRecords = computed(() => recordMode.value === 'wrong' ? wrongRecords.value : allRecords.value);
const emptyRecordMessage = computed(() => recordMode.value === 'wrong' ? '该学生当前没有错题记录。' : '该学生当前没有答题记录。');
const reportTimeText = computed(() => reportCreateTime.value ? `生成时间：${formatDate(reportCreateTime.value)}` : '');

const crop = (text: string, len: number) => text.length > len ? `${text.slice(0, len)}…` : text;
const formatDate = (date: string) => new Date(date).toLocaleString('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const rateClass = (rate: number) => {
  if (rate >= 60) return 'rate-high';
  if (rate >= 30) return 'rate-mid';
  return 'rate-low';
};

const buildStudentRow = async (student: UserResponse): Promise<StudentInsightRow> => {
  try {
    const [allData, wrongData] = await Promise.all([
      getUserAnswerRecordsApi(student.id),
      getUserWrongAnswerRecordsApi(student.id),
    ]);
    const totalCount = allData.records?.length ?? 0;
    const wrongCount = wrongData.records?.length ?? 0;
    const errorRate = totalCount ? (wrongCount / totalCount) * 100 : 0;
    return {
      ...student,
      totalCount,
      wrongCount,
      errorRate,
      errorRateText: `${errorRate.toFixed(1)}%`,
    };
  } catch {
    return {
      ...student,
      totalCount: 0,
      wrongCount: 0,
      errorRate: 0,
      errorRateText: '0.0%',
    };
  }
};

const applySelectedRecords = () => {
  selectedRecord.value = displayedRecords.value[0] ?? null;
};

const loadStudentRecords = async (studentId: number) => {
  recordsLoading.value = true;
  try {
    const [allData, wrongData] = await Promise.all([
      getUserAnswerRecordsApi(studentId),
      getUserWrongAnswerRecordsApi(studentId),
    ]);
    allRecords.value = allData.records ?? [];
    wrongRecords.value = wrongData.records ?? [];
    applySelectedRecords();
  } catch (error: any) {
    allRecords.value = [];
    wrongRecords.value = [];
    applySelectedRecords();
    show(error?.response?.data?.message ?? '读取学生作答记录失败', 'error');
  } finally {
    recordsLoading.value = false;
  }
};

const loadStudentReport = async (studentId: number, totalCount: number) => {
  reportLoading.value = true;
  reportContent.value = '';
  reportCreateTime.value = '';
  reportMessage.value = null;

  try {
    const report = await getStudentLatestReportApi(studentId);
    if (!report?.latestReportContent?.trim()) {
      reportMessage.value = totalCount > 0
        ? { title: '暂未生成学习报告', desc: '该学生已有答题记录，但还没有生成个人学习报告。' }
        : { title: '该学生尚未开始作答', desc: '学生还没有答题记录，因此暂时没有可读取的学习报告。' };
      return;
    }
    reportContent.value = report.latestReportContent;
    reportCreateTime.value = report.reportCreateTime;
  } catch (error: any) {
    const raw = error?.response?.data?.message ?? error?.message ?? '';
    if (raw.includes('无答题记录') || raw.includes('没有报告') || raw.includes('未生成') || raw.includes('not found')) {
      reportMessage.value = totalCount > 0
        ? { title: '暂未生成学习报告', desc: '该学生已有答题记录，但还没有生成个人学习报告。' }
        : { title: '该学生尚未开始作答', desc: '学生还没有答题记录，因此暂时没有可读取的学习报告。' };
      return;
    }
    reportMessage.value = { title: '该学生尚未生成学习报告哦', desc: '当前无法获取该学生的学习报告，请稍后再试。' };
  } finally {
    reportLoading.value = false;
  }
};

const selectStudent = async (student: StudentInsightRow) => {
  selectedStudent.value = student;
  recordMode.value = 'all';
  await Promise.all([
    loadStudentRecords(student.id),
    loadStudentReport(student.id, student.totalCount),
  ]);
};

watch(recordMode, () => {
  applySelectedRecords();
});

onMounted(async () => {
  loading.value = true;
  try {
    const users = await getAllUsersApi();
    const students = users.filter(user => user.userType === 'STUDENT');
    studentRows.value = await Promise.all(students.map(buildStudentRow));
    if (studentRows.value.length) {
      await selectStudent(studentRows.value[0]);
    }
  } catch (error: any) {
    show(error?.response?.data?.message ?? '加载学生列表失败', 'error');
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.student-analysis-page {
  display: grid;
  gap: 18px;
}

.analysis-hero,
.hero-stat-card,
.analysis-toolbar,
.student-list-panel,
.student-detail-panel,
.detail-hero-card,
.report-card,
.records-card,
.record-list-panel.inner,
.detail-panel.inner,
.detail-card,
.question-card,
.analysis-card {
  border: 1px solid rgba(151, 172, 249, 0.14);
  border-radius: 20px;
  background: linear-gradient(160deg, rgba(10, 16, 34, 0.94), rgba(15, 12, 30, 0.96));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.28);
}

.analysis-hero {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 18px;
  padding: 24px 28px;
}

.analysis-kicker,
.panel-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(90, 243, 211, 0.22);
  background: rgba(90, 243, 211, 0.08);
  color: rgba(183, 248, 235, 0.92);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: var(--font-size-xs);
}

.analysis-title {
  margin: 14px 0 8px;
  font-size: clamp(28px, 4vw, 40px);
}

.analysis-summary {
  color: var(--text-muted);
  line-height: 1.8;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(150px, 1fr));
  gap: 14px;
  width: min(420px, 100%);
}

.hero-stat-card {
  padding: 18px 20px;
  display: grid;
  gap: 8px;
}

.hero-stat-card.warm {
  border-color: rgba(255, 184, 76, 0.18);
}

.hero-stat-label,
.hero-stat-note,
.student-username,
.student-meta,
.detail-student-meta,
.report-time,
.records-summary,
.record-time,
.record-meta,
.record-session,
.panel-count,
.panel-empty {
  color: var(--text-muted);
}

.hero-stat-value {
  font-size: 28px;
}

.analysis-toolbar {
  display: grid;
  gap: 14px;
  padding: 16px 18px;
}

.toolbar-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filter-btn,
.mode-btn,
.toolbar-search {
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.18);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
}

.filter-btn,
.mode-btn {
  padding: 9px 14px;
  cursor: pointer;
}

.filter-btn.active,
.mode-btn.active {
  border-color: rgba(90, 243, 211, 0.42);
  background: rgba(90, 243, 211, 0.12);
  color: #dffff8;
}

.toolbar-search {
  width: min(360px, 100%);
  padding: 10px 12px;
}

.analysis-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 18px;
  align-items: start;
}

.student-list-panel,
.student-detail-panel,
.report-card,
.records-card,
.record-list-panel.inner,
.detail-panel.inner {
  overflow: hidden;
}

.student-list-panel,
.student-detail-panel,
.report-card,
.records-card {
  padding: 18px;
}

.student-list {
  display: grid;
  gap: 12px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  margin-top: 14px;
}

.student-item {
  display: grid;
  gap: 8px;
  text-align: left;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
}

.student-item.active {
  border-color: rgba(90, 243, 211, 0.45);
  background: rgba(90, 243, 211, 0.08);
}

.student-item-top,
.student-meta,
.panel-head,
.records-toolbar,
.record-item-top,
.detail-row,
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.student-name,
.panel-title,
.detail-student-name,
.question-title {
  font-weight: 800;
  color: #eef4ff;
}

.student-rate,
.record-badge,
.detail-record-status {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.rate-high,
.fail-text,
.record-item.wrong .record-badge {
  color: rgba(255, 152, 152, 0.96);
}

.rate-mid {
  color: rgba(255, 198, 112, 0.96);
}

.rate-low,
.ok-text,
.record-item.correct .record-badge {
  color: rgba(120, 220, 150, 0.96);
}

.student-detail-panel {
  display: grid;
  gap: 18px;
}

.detail-hero-card {
  padding: 20px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.detail-metrics {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.metric-pill {
  min-width: 110px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.03);
  display: grid;
  gap: 4px;
}

.metric-pill.danger {
  border-color: rgba(255, 122, 122, 0.18);
}

.metric-pill.accent {
  border-color: rgba(90, 243, 211, 0.2);
}

.report-placeholder,
.panel-empty.large {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  text-align: center;
}

.report-placeholder {
  padding: 20px;
}

.report-placeholder-icon {
  font-size: 36px;
}

.report-output {
  margin-top: 14px;
}

.records-toolbar {
  align-items: center;
  margin-bottom: 14px;
}

.mode-switch {
  display: flex;
  gap: 10px;
}

.record-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}

.record-list-panel.inner,
.detail-panel.inner {
  padding: 16px;
}

.panel-head.compact {
  margin-bottom: 14px;
}

.record-list {
  display: grid;
  gap: 10px;
  max-height: 640px;
  overflow-y: auto;
}

.record-item {
  display: grid;
  gap: 6px;
  width: 100%;
  text-align: left;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
}

.record-item.active {
  border-color: rgba(90, 243, 211, 0.44);
  background: rgba(90, 243, 211, 0.08);
}

.record-question {
  font-size: 14px;
  line-height: 1.7;
  color: #eef4ff;
}

.detail-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.detail-card {
  padding: 16px;
  display: grid;
  gap: 10px;
}

.detail-row {
  align-items: center;
}

.question-card,
.analysis-card {
  padding: 18px;
  margin-bottom: 16px;
}

.question-content {
  margin-top: 12px;
  line-height: 1.85;
}

.option-list {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}

.option-row {
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(151, 172, 249, 0.12);
  background: rgba(255, 255, 255, 0.03);
}

.option-row.correct {
  border-color: rgba(120, 220, 150, 0.32);
  background: rgba(120, 220, 150, 0.08);
}

.option-row.wrong {
  border-color: rgba(255, 122, 122, 0.3);
  background: rgba(255, 122, 122, 0.08);
}

.option-key {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(151, 172, 249, 0.12);
}

@media (max-width: 1200px) {
  .analysis-layout,
  .record-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .analysis-hero,
  .detail-hero-card,
  .records-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-stats,
  .detail-card-grid {
    grid-template-columns: 1fr;
  }
}
</style>

