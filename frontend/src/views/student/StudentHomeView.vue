<template>
  <AppShell :subtitle="`${greeting}，${auth.user?.realName ?? '同学'}`" :links="links">
    <div v-if="loading" class="home-loading">
      <div class="loading-ring" />
      <p>正在加载…</p>
    </div>

    <template v-else>
      <section class="overview-card" :class="{ idle: !task }">
        <div class="overview-main">
          <div class="overview-kicker">学生首页</div>
          <h1 class="overview-title">{{ greeting }}，{{ auth.user?.realName ?? auth.user?.username }}</h1>

          <div v-if="task" class="overview-task-meta">
            <span class="task-live-badge">⚡ 当前任务</span>
            <span class="task-meta">{{ task.teacherName }} · {{ formatTime(task.eventTimestamp) }}</span>
          </div>

          <h2 class="overview-task-title">{{ task ? task.eventTitle : '当前暂无教师任务' }}</h2>

          <div class="overview-actions">
            <button v-if="task" class="primary-action" @click="enterExperiment">
              <span>进入实验</span>
              <span class="start-arrow">→</span>
            </button>
            <button v-else class="secondary-action" @click="loadAll">刷新任务</button>
          </div>
        </div>

        <div class="overview-side">
          <div class="status-panel">
            <span class="panel-label">学习状态</span>
            <div class="status-grid">
              <div class="status-item">
                <span class="status-value">{{ myStats.total }}</span>
                <span class="status-name">累计答题</span>
              </div>
              <div class="status-item">
                <span class="status-value">{{ myStats.accuracy }}%</span>
                <span class="status-name">正确率</span>
              </div>
              <div class="status-item">
                <span class="status-value">{{ myStats.wrong }}</span>
                <span class="status-name">待复习</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="home-grid">
        <div class="main-column">
          <div v-if="recentWrong.length" class="panel-card wrong-preview">
            <div class="panel-head">
              <div>
                <h3 class="panel-title">最近错题</h3>
                <p class="panel-subtitle">优先复习最近暴露的问题</p>
              </div>
              <router-link to="/student/wrong-book" class="panel-link">进入错题本 →</router-link>
            </div>

            <div class="wp-list">
              <div v-for="r in recentWrong" :key="r.recordId" class="wp-item">
                <span class="wp-ans wrong">{{ r.userAnswer }}</span>
                <span class="wp-correct">→ {{ r.correctAnswer }}</span>
                <div class="wp-content"><LatexText :content="r.questionContent?.slice(0, 80) ?? ''" /></div>
              </div>
            </div>
          </div>

          <div v-else class="panel-card empty-card">
            <div class="empty-icon">🎉</div>
            <div>
              <h3 class="panel-title">暂无错题</h3>
              <p class="panel-subtitle">继续保持，完成任务后可再查看学习报告。</p>
            </div>
          </div>
        </div>

        <aside class="side-column">
          <div class="panel-card next-step-card">
            <div class="panel-head compact">
              <h3 class="panel-title">下一步</h3>
            </div>

            <div class="step-list">
              <router-link v-if="task" to="/student/experiment" class="step-item step-item-disabled" @click.prevent="enterExperiment">
                <span class="step-icon">🧪</span>
                <div class="step-body">
                  <span class="step-title">开始当前实验</span>
                  <span class="step-desc">继续本次教师发布任务</span>
                </div>
              </router-link>

              <router-link to="/student/wrong-book" class="step-item">
                <span class="step-icon">📖</span>
                <div class="step-body">
                  <span class="step-title">复习错题</span>
                  <span class="step-desc">查看历史错误与解析</span>
                </div>
              </router-link>

              <router-link to="/student/report" class="step-item">
                <span class="step-icon">📊</span>
                <div class="step-body">
                  <span class="step-title">查看学习报告</span>
                  <span class="step-desc">生成本阶段学习总结</span>
                </div>
              </router-link>
            </div>
          </div>
        </aside>
      </section>
    </template>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import LatexText from '@/components/LatexText.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { getTeacherEventApi } from '@/api/teacher-event';
import { getMyAnswersApi, getMyWrongAnswersApi } from '@/api/answer-record';
import type { TeacherEventResponse } from '@/types/teacher-event';
import type { AnswerRecordResponse } from '@/types/answer-record';

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';

const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();

const getTaskEntryEventIdKey = () => `${TASK_ENTRY_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
];

const loading = ref(true);
const task = ref<TeacherEventResponse | null>(null);
const allRecords = ref<AnswerRecordResponse[]>([]);
const wrongRecords = ref<AnswerRecordResponse[]>([]);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const myStats = computed(() => {
  const total = allRecords.value.length;
  const correct = allRecords.value.filter(r => r.correct).length;
  const wrong = wrongRecords.value.length;
  const accuracy = total > 0 ? Math.round((correct / total) * 100) : 0;
  return { total, correct, wrong, accuracy };
});

const recentWrong = computed(() => wrongRecords.value.slice(0, 3));

const parseQuestionIds = (desc: string | null) => {
  if (!desc?.startsWith('QIDS:')) return [];
  return desc.slice(5).split(',').map(Number).filter(n => n > 0);
};

const taskQuestionCount = computed(() => parseQuestionIds(task.value?.eventDescription ?? null).length);
void taskQuestionCount;

const formatTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

const loadAll = async () => {
  loading.value = true;
  try {
    const [taskData, myData, wrongData] = await Promise.allSettled([
      getTeacherEventApi(),
      getMyAnswersApi(),
      getMyWrongAnswersApi(),
    ]);
    if (taskData.status === 'fulfilled') task.value = taskData.value;
    if (myData.status === 'fulfilled') allRecords.value = myData.value.records ?? [];
    if (wrongData.status === 'fulfilled') {
      const seen = new Set<string>();
      wrongRecords.value = (wrongData.value.records ?? []).filter(r => {
        if (seen.has(r.questionContent)) return false;
        seen.add(r.questionContent);
        return true;
      });
    }
  } finally {
    loading.value = false;
  }
};

const enterExperiment = () => {
  if (!task.value) {
    show('当前无可进入的教师任务', 'info');
    return;
  }
  sessionStorage.setItem(getTaskEntryEventIdKey(), String(task.value.id));
  router.push('/student/experiment');
};

onMounted(loadAll);
</script>

<style scoped>
.home-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-muted);
}

.loading-ring {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 3px solid rgba(90, 243, 211, 0.12);
  border-top-color: var(--brand);
  animation: spin 0.85s linear infinite;
}

.overview-card,
.panel-card,
.status-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(151, 172, 249, 0.14);
  background: linear-gradient(160deg, rgba(10, 16, 34, 0.92), rgba(14, 21, 42, 0.86));
  box-shadow: 0 24px 60px rgba(4, 8, 20, 0.34);
}

.overview-card {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(300px, 0.85fr);
  gap: 18px;
  border-radius: 24px;
  padding: 28px;
  background:
    radial-gradient(circle at 16% 18%, rgba(90, 243, 211, 0.14), transparent 24%),
    radial-gradient(circle at 88% 82%, rgba(255, 176, 124, 0.12), transparent 20%),
    linear-gradient(135deg, rgba(9, 15, 34, 0.96), rgba(18, 12, 44, 0.92));
}

.overview-card.idle {
  background:
    radial-gradient(circle at 16% 18%, rgba(120, 146, 255, 0.12), transparent 24%),
    linear-gradient(135deg, rgba(9, 15, 34, 0.96), rgba(12, 18, 36, 0.92));
}

.overview-main {
  display: grid;
  gap: 14px;
  align-content: center;
}

.overview-kicker,
.panel-label {
  display: inline-flex;
  width: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(90, 243, 211, 0.22);
  background: rgba(90, 243, 211, 0.08);
  font-size: var(--font-size-xs);
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(183, 248, 235, 0.92);
}

.overview-title {
  margin: 0;
  font-size: clamp(28px, 4.2vw, 42px);
  line-height: 1.08;
}

.overview-task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.task-live-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid rgba(90, 243, 211, 0.45);
  background: rgba(90, 243, 211, 0.1);
  color: var(--brand);
  font-size: var(--font-size-xs);
  font-weight: 800;
  letter-spacing: 0.08em;
}

.task-meta,
.panel-subtitle,
.status-name,
.wp-content,
.wp-correct,
.text-link {
  color: var(--text-muted);
}

.overview-task-title {
  margin: 0;
  font-size: clamp(22px, 3.2vw, 34px);
  line-height: 1.18;
  color: #f4f7ff;
}

.overview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.primary-action,
.secondary-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 50px;
  padding: 0 22px;
  border-radius: 14px;
  border: 1px solid transparent;
  font-size: var(--font-size-md);
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.primary-action {
  color: #071018;
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.95), rgba(255, 199, 122, 0.95));
  box-shadow: 0 14px 34px rgba(90, 243, 211, 0.24);
}

.secondary-action {
  color: var(--text);
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(151, 172, 249, 0.18);
}

.primary-action:hover,
.secondary-action:hover,
.step-item:hover {
  transform: translateY(-2px);
}

.start-arrow {
  font-size: 16px;
  transition: transform 0.2s;
}

.primary-action:hover .start-arrow {
  transform: translateX(4px);
}

.text-link {
  text-decoration: none;
  font-size: var(--font-size-sm);
}

.overview-side {
  display: flex;
}

.status-panel {
  width: 100%;
  border-radius: 20px;
  padding: 18px;
  display: grid;
  gap: 16px;
  background:
    radial-gradient(circle at 80% 10%, rgba(142, 123, 255, 0.16), transparent 24%),
    linear-gradient(160deg, rgba(18, 13, 42, 0.92), rgba(10, 16, 32, 0.96));
}

.status-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.status-item {
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  display: grid;
  gap: 6px;
}

.status-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--brand);
  line-height: 1;
}

.home-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.9fr);
  gap: 18px;
  align-items: start;
}

.main-column,
.side-column {
  display: grid;
  gap: 18px;
}

.panel-card {
  border-radius: 20px;
  padding: 18px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head.compact {
  margin-bottom: 10px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text);
}

.panel-subtitle {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.panel-link {
  font-size: var(--font-size-sm);
  color: var(--brand);
  text-decoration: none;
  white-space: nowrap;
}

.wp-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wp-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.025);
  font-size: var(--font-size-sm);
}

.wp-ans {
  font-family: monospace;
  font-weight: 700;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: var(--font-size-xs);
}

.wp-ans.wrong {
  background: rgba(255, 100, 100, 0.15);
  color: rgba(255, 120, 120, 0.9);
}

.wp-correct {
  font-size: var(--font-size-xs);
  flex-shrink: 0;
}

.wp-content {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.empty-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 160px;
}

.empty-icon {
  font-size: 34px;
  flex-shrink: 0;
}

.step-list {
  display: grid;
  gap: 12px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid rgba(151, 172, 249, 0.1);
  background: rgba(255, 255, 255, 0.02);
  text-decoration: none;
  transition: all 0.2s ease;
}

.step-item:hover {
  border-color: rgba(90, 243, 211, 0.3);
  background: rgba(90, 243, 211, 0.04);
}

.step-item-disabled {
  cursor: pointer;
}

.step-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.step-body {
  display: grid;
  gap: 4px;
}

.step-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}

.step-desc {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

@media (max-width: 1180px) {
  .overview-card,
  .home-grid {
    grid-template-columns: 1fr;
  }

  .status-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .overview-card,
  .panel-card,
  .status-panel {
    padding: 18px;
  }

  .overview-actions,
  .panel-head,
  .wp-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .status-grid {
    grid-template-columns: 1fr;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
