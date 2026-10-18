<template>
  <AppShell :subtitle="`教师总览 · ${auth.user?.realName ?? ''}`" :links="links">
    <div class="dashboard-page teacher-font">
      <p v-if="dashboardError" role="alert">{{ dashboardError }} <button @click="loadDashboard">重试</button></p>
      <section class="dashboard-hero">
        <div class="hero-visuals">
          <div class="hero-donut-card">
            <div class="hero-donut-head">
              <span class="hero-donut-label">当前启用学生完成率</span>
              <strong>{{ completionRateText }}</strong>
            </div>
            <div class="hero-donut-body">
              <div class="donut-shell completion-shell" :style="donutStyle(completionRate)">
                <div class="donut-core">
                  <span class="donut-value">{{ completionRateText }}</span>
                  <span class="donut-caption">已完成</span>
                </div>
              </div>
              <div class="hero-donut-legend">
                <div class="legend-row done">
                  <span class="legend-dot"></span>
                  <span>已完成（当前启用）{{ answerStats.answeredStudentCount }}</span>
                </div>
                <div class="legend-row pending">
                  <span class="legend-dot"></span>
                  <span>未完成 {{ unAnsweredStudentCount }}</span>
                </div>
                <div class="legend-note">当前启用学生 {{ answerStats.totalStudentCount }} 人</div>
              </div>
            </div>
          </div>

          <div class="hero-donut-card accuracy-card">
            <div class="hero-donut-head">
              <span class="hero-donut-label">累计作答正确率</span>
              <strong>{{ accuracyRateText }}</strong>
            </div>
            <div class="hero-donut-body">
              <div class="donut-shell accuracy-shell" :style="donutStyle(accuracyRate)">
                <div class="donut-core">
                  <span class="donut-value">{{ accuracyRateText }}</span>
                  <span class="donut-caption">回答正确</span>
                </div>
              </div>
              <div class="hero-donut-legend">
                <div class="legend-row done">
                  <span class="legend-dot"></span>
                  <span>正确 {{ correctAnswerCount }}</span>
                </div>
                <div class="legend-row pending">
                  <span class="legend-dot"></span>
                  <span>错误 {{ wrongAnswerCount }}</span>
                </div>
                <div class="legend-note">总答题记录 {{ totalAnswers }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="metric-grid">
        <div class="metric-card" v-for="m in metrics" :key="m.label">
          <div class="metric-value">{{ m.loading ? '…' : m.value }}</div>
          <div class="metric-label">{{ m.label }}</div>
        </div>
      </section>

      <section class="workbench-grid">
        <div class="panel-card event-panel" :class="event ? 'live' : 'idle'">
          <div class="panel-head">
            <h3>当前教学任务</h3>
            <router-link to="/teacher/event-manage" class="panel-link">任务管理 →</router-link>
          </div>
          <div v-if="event" class="event-body">
            <h4>{{ event.eventTitle }}</h4>
            <p>{{ eventQuestionCount }} 道题 · {{ formatTime(event.eventTimestamp) }}</p>
          </div>
          <div v-if="event" class="event-stats">
            <span>已参与 {{ answerStats.answeredStudentCount }} / {{ answerStats.totalStudentCount }} 人</span>
            <span>完成率 {{ completionRateText }}</span>
          </div>
          <div v-else class="empty-copy">
            <p>当前没有发布中的任务。</p>
          </div>
        </div>

        <div class="panel-card action-panel">
          <div class="panel-head">
            <h3>常用操作</h3>
          </div>
          <div class="action-list">
            <router-link v-for="s in shortcuts" :key="s.to" :to="s.to" class="action-item">
              <span class="action-label">{{ s.label }}</span>
            </router-link>
          </div>
        </div>
      </section>

      <section class="roster-panel">
        <div class="roster-head">
          <div class="roster-head-left">
            <span class="roster-title">学生名册</span>
            <span class="roster-count">{{ filteredUsers.length }} / {{ users.length }}</span>
          </div>
          <div class="roster-filters">
            <button
              v-for="cls in classOptions" :key="cls"
              class="filter-btn"
              :class="{ active: classFilter === cls }"
              @click="classFilter = cls"
            >{{ cls || '全部' }}</button>
            <input v-model="searchText" class="roster-search" placeholder="搜索姓名 / 账号…" />
          </div>
        </div>

        <div v-if="loading" class="roster-loading">加载中…</div>
        <div v-else-if="filteredUsers.length === 0" class="roster-empty">暂无匹配学生</div>
        <div v-else class="roster-table">
          <div class="roster-table-head">
            <span>姓名</span>
            <span>账号</span>
            <span>班级</span>
            <span>最近登录</span>
            <span>状态</span>
          </div>
          <div v-for="u in filteredUsers" :key="u.id" class="roster-row">
            <span class="roster-name">{{ u.realName }}</span>
            <span class="roster-username">{{ u.username }}</span>
            <span class="roster-class">{{ u.className || '未分班' }}</span>
            <span class="roster-login">{{ u.lastLogin ? formatDate(u.lastLogin) : '从未' }}</span>
            <span class="roster-status" :class="u.active ? 'active' : 'inactive'">{{ u.active ? '正常' : '停用' }}</span>
          </div>
        </div>
      </section>
    </div>
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/layouts/AppShell.vue';
import { useAuthStore } from '@/stores/auth';
import { getAllUsersApi } from '@/api/user';
import { getLearningSummaryApi } from '@/api/answer-record';
import { getTeacherEventApi } from '@/api/teacher-event';
import { teacherNavLinks } from '@/constants/navigation';
import type { UserResponse } from '@/types/user';
import type { TeacherEventResponse } from '@/types/teacher-event';
import type { AnswerStatsResponse } from '@/types/answer-record';

const auth = useAuthStore();

const links = teacherNavLinks;

const shortcuts = [
  { label: '用户管理', to: '/teacher/users' },
  { label: '学生学情', to: '/teacher/student-analysis' },
  { label: '题库管理', to: '/teacher/questions' },
  { label: '任务管理', to: '/teacher/event-manage' },
];

const users = ref<UserResponse[]>([]);
const totalAnswers = ref(0);
const dashboardError=ref('');
const currentSummary=ref<import('@/api/answer-record').LearningSummary|null>(null);
const totalQuestions = ref(0);
const correctAnswerCount = ref(0);
const event = ref<TeacherEventResponse | null>(null);
const loading = ref(true);
const searchText = ref('');
const classFilter = ref('');
const answerStats = ref<AnswerStatsResponse>({
  answeredStudentCount: 0,
  totalStudentCount: 0,
  coverageRate: 0,
});

const parseQuestionIds = (desc: string | null) => {
  if (!desc?.startsWith('QIDS:')) return [];
  return desc.slice(5).split(',').map(Number).filter(n => n > 0);
};

const eventQuestionCount = computed(() => (event.value?.questionIds ?? parseQuestionIds(event.value?.eventDescription ?? null)).length);
const studentUsers = computed(() => users.value.filter(u => u.userType === 'STUDENT'));
const wrongAnswerCount = computed(() => Math.max(totalAnswers.value - correctAnswerCount.value, 0));
const accuracyRate = computed(() => totalAnswers.value ? (correctAnswerCount.value / totalAnswers.value) * 100 : 0);
const completionRate = computed(() => answerStats.value.totalStudentCount ? (answerStats.value.answeredStudentCount / answerStats.value.totalStudentCount) * 100 : 0);
const unAnsweredStudentCount = computed(() => Math.max(answerStats.value.totalStudentCount - answerStats.value.answeredStudentCount, 0));
const accuracyRateText = computed(() => `${accuracyRate.value.toFixed(1)}%`);
const completionRateText = computed(() => `${completionRate.value.toFixed(1)}%`);

const classOptions = computed(() => {
  const set = new Set(studentUsers.value.map(u => u.className ?? ''));
  return ['', ...Array.from(set).filter(Boolean).sort()];
});

const filteredUsers = computed(() => {
  let list = studentUsers.value;
  if (classFilter.value) list = list.filter(u => u.className === classFilter.value);
  const s = searchText.value.trim().toLowerCase();
  if (s) list = list.filter(u => u.realName.toLowerCase().includes(s) || u.username.toLowerCase().includes(s));
  return list;
});

const metrics = computed(() => [
  { label: '累计答题次数', value: totalAnswers.value.toLocaleString(), loading: loading.value },
  { label: '当前启用学生', value: answerStats.value.totalStudentCount.toLocaleString(), loading: loading.value },
  { label: '题库题目数', value: totalQuestions.value.toLocaleString(), loading: loading.value },
  { label: '当前任务题数', value: event.value ? `${eventQuestionCount.value}` : '0', loading: loading.value },
  {label:'当前任务首次正确率',value:currentSummary.value?.assignmentAnswers ? `${(100*currentSummary.value.assignmentCorrect/currentSummary.value.assignmentAnswers).toFixed(1)}%` : '—',loading:loading.value},
  {label:'当前任务补练次数',value:currentSummary.value?.practiceAttempts ?? 0,loading:loading.value},
]);

const donutStyle = (percent: number) => ({
  background: `conic-gradient(var(--donut-fill) 0deg ${Math.max(0, Math.min(percent, 100)) * 3.6}deg, var(--donut-track) ${Math.max(0, Math.min(percent, 100)) * 3.6}deg 360deg)`,
});

const formatTime = (t: string) => new Date(t).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const formatDate = (t: string) => new Date(t).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const loadDashboard = async () => {
  loading.value=true;dashboardError.value='';
  try {
    const [allUsers,currentEvent,lifetime]=await Promise.all([getAllUsersApi(),getTeacherEventApi(),getLearningSummaryApi()]);
    users.value=allUsers;event.value=currentEvent;
    totalAnswers.value=lifetime.totalAttempts;correctAnswerCount.value=lifetime.correctAttempts;totalQuestions.value=lifetime.questionCount;
    currentSummary.value=currentEvent?await getLearningSummaryApi(currentEvent.id):null;
    answerStats.value={answeredStudentCount:currentSummary.value?.completedStudents ?? 0,totalStudentCount:lifetime.eligibleStudents,
      coverageRate:lifetime.eligibleStudents?100*(currentSummary.value?.completedStudents ?? 0)/lifetime.eligibleStudents:0};
  } catch {dashboardError.value='统计加载失败，请重试。';}
  finally {loading.value=false;}
};
onMounted(loadDashboard);
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 18px;
}

.dashboard-hero,
.metric-card,
.panel-card,
.roster-panel,
.status-card,
.hero-donut-card {
  border: 1px solid rgba(123, 146, 255, 0.14);
  border-radius: 22px;
  background: linear-gradient(160deg, rgba(12, 18, 37, 0.96), rgba(7, 11, 24, 0.98));
  box-shadow: 0 24px 60px rgba(2, 6, 18, 0.32);
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(280px, 0.95fr) minmax(0, 1.25fr);
  gap: 18px;
  padding: 24px 28px;
  align-items: stretch;
}

.hero-kicker {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(123, 146, 255, 0.18);
  color: #95abd8;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  font-size: var(--font-size-xs);
}

.hero-title {
  margin: 14px 0 8px;
  font-size: clamp(30px, 4vw, 42px);
}

.hero-summary {
  color: #b7c4e8;
  line-height: 1.8;
  max-width: 34rem;
}

.hero-visuals {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.hero-donut-card {
  padding: 18px;
  display: grid;
  gap: 16px;
}

.hero-donut-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.hero-donut-head strong {
  font-size: 28px;
  color: #f5f7ff;
}

.hero-donut-label {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: #b2c2ea;
}

.hero-donut-body {
  display: flex;
  align-items: center;
  gap: 18px;
  justify-content: space-between;
}

.donut-shell {
  --donut-fill: #67f0d0;
  --donut-track: rgba(255, 255, 255, 0.08);
  width: 156px;
  height: 156px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.04);
}

.completion-shell {
  --donut-fill: #67f0d0;
}

.accuracy-shell {
  --donut-fill: #ffb25e;
}

.donut-core {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, rgba(19, 30, 56, 0.98), rgba(8, 12, 24, 0.98));
  border: 1px solid rgba(255,255,255,0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 10px;
}

.donut-value {
  font-size: 22px;
  font-weight: 800;
  color: #f3f7ff;
}

.donut-caption {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: #93a8d8;
}

.hero-donut-legend {
  display: grid;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #d7e2ff;
  font-size: var(--font-size-sm);
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.legend-row.done .legend-dot {
  background: #67f0d0;
  box-shadow: 0 0 16px rgba(103, 240, 208, 0.48);
}

.legend-row.pending .legend-dot {
  background: rgba(255,255,255,0.2);
}

.accuracy-card .legend-row.done .legend-dot {
  background: #ffb25e;
  box-shadow: 0 0 16px rgba(255, 178, 94, 0.45);
}

.legend-note {
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: #8ea3d6;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.metric-icon {
  font-size: 28px;
}

.metric-value {
  font-size: 28px;
  font-weight: 800;
}

.metric-label {
  font-size: var(--font-size-sm);
  color: #9fb0db;
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 18px;
}

.panel-card {
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.panel-link {
  color: var(--brand);
  text-decoration: none;
  font-size: var(--font-size-sm);
}

.event-body h4 {
  margin: 0 0 8px;
  font-size: 24px;
}

.event-body p,
.empty-copy p,
.action-label,
.roster-username,
.roster-class,
.roster-login {
  color: #9cafda;
}

.action-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border-radius: 16px;
  text-decoration: none;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(123, 146, 255, 0.1);
}

.action-icon {
  font-size: 22px;
}

.action-label {
  font-size: 14px;
  color: #f1f7ff;
}

.roster-panel {
  overflow: hidden;
}

.roster-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.08);
  gap: 12px;
  flex-wrap: wrap;
}

.roster-head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.roster-title {
  font-size: 15px;
  font-weight: 700;
}

.roster-count {
  font-size: 11px;
  color: #9fb0db;
}

.roster-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-btn {
  padding: 7px 11px;
  border-radius: 999px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: transparent;
  color: var(--text-muted);
  font-size: 11px;
  cursor: pointer;
}

.filter-btn.active {
  color: #f4f7ff;
  border-color: rgba(103, 240, 208, 0.45);
  background: rgba(103, 240, 208, 0.08);
}

.roster-search {
  min-width: 220px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: rgba(255, 255, 255, 0.04);
  color: #f3f7ff;
}

.roster-loading,
.roster-empty {
  padding: 28px 18px;
  color: #95a6cf;
}

.roster-table-head,
.roster-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 1fr 88px;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
}

.roster-table-head {
  font-size: 12px;
  color: #8da3d6;
  border-bottom: 1px solid rgba(151, 172, 249, 0.08);
}

.roster-row {
  border-bottom: 1px solid rgba(255,255,255,0.04);
}

.roster-name {
  color: #f4f7ff;
  font-weight: 700;
}

.roster-status {
  justify-self: start;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
}

.roster-status.active {
  color: #6ff2cb;
  background: rgba(111, 242, 203, 0.12);
}

.roster-status.inactive {
  color: #ff8c98;
  background: rgba(255, 140, 152, 0.12);
}

@media (max-width: 1180px) {
  .dashboard-hero,
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .hero-visuals {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-donut-body {
    flex-direction: column;
    align-items: flex-start;
  }

  .roster-table-head,
  .roster-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

