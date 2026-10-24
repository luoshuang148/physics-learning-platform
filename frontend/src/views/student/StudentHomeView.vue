<template>
  <AppShell hide-heading :subtitle="`${greeting}，${auth.user?.realName ?? '同学'}`">
    <div v-if="loading" class="student-home-loading" role="status">正在加载…</div>
    <template v-else>
      <header class="student-welcome">
        <div>
          <span class="student-eyebrow">学习空间</span>
          <h1>
            {{ greeting }}，{{ auth.user?.realName ?? '同学' }}<span aria-hidden="true">☀</span>
          </h1>
          <p>从今天的一次练习开始。</p>
        </div>
        <div class="student-today">
          {{ today.date }}<span>{{ today.weekday }}</span>
        </div>
      </header>
      <p v-if="sectionErrors.tasks" class="sw-error" role="alert">
        作业安排暂时无法读取。<button class="sw-link" @click="loadAll">重新加载</button>
      </p>
      <section v-else class="student-current-task">
        <div class="student-task-copy">
          <div v-if="task" class="student-task-meta">
            <span class="student-task-tag">{{ currentResult?.progress.label }}</span>
            <span>{{ taskAvailabilityText(task.availability ?? task.status) }}</span>
            <span>{{ task.teacherName }}</span>
            <span v-if="openTasks.length > 1">另有 {{ openTasks.length - 1 }} 个待完成任务</span>
          </div>
          <span v-else class="student-eyebrow">教师作业</span>
          <h2>{{ task?.eventTitle ?? '当前暂无教师任务' }}</h2>
          <p v-if="task">
            {{ taskQuestionCount }} 道题 ·
            {{ task.dueAt ? '截止 ' + formatTime(task.dueAt) : '不限截止时间' }}
          </p>
          <p v-else>教师发布任务后，会显示在这里。</p>
          <div v-if="currentResult" class="student-task-progress">
            <span
              >已完成 {{ currentResult.progress.answered }} /
              {{ currentResult.progress.total }} 题</span
            >
            <span>{{
              !currentResult.progress.total
                ? '等待教师配置题目'
                : currentResult.progress.completed
                  ? '全部题目已保存'
                  : '还剩 ' + currentResult.progress.remaining + ' 题'
            }}</span>
            <progress
              :value="currentResult.progress.percent"
              max="100"
              aria-label="当前作业完成进度"
            />
          </div>
          <div class="student-task-actions">
            <button v-if="currentResult?.canResume" class="student-start" @click="enterTask">
              {{ currentResult.progress.answered ? '继续作答' : '开始作答'
              }}<AppIcon name="arrow" />
            </button>
            <RouterLink
              v-else-if="currentResult && currentResult.answeredQuestions > 0"
              class="student-start"
              :to="{ path: '/student/report', query: { eventId: task?.id } }"
              >查看结果 / 复盘<AppIcon name="arrow"
            /></RouterLink>
            <button v-else-if="!task" class="student-refresh" @click="loadAll">刷新任务</button>
            <span v-else class="student-unavailable">{{
              taskAvailabilityText(task.availability ?? task.status)
            }}</span>
            <RouterLink class="student-text-link" to="/student/task"
              >全部作业 <span>{{ tasks.length }}</span></RouterLink
            >
          </div>
        </div>
        <div class="student-atom" aria-hidden="true">
          <i class="student-orbit" /><i class="student-orbit orbit-two" /><i
            class="student-orbit orbit-three"
          />
          <i class="student-nucleus" /><i class="student-electron" /><span>F = ma</span>
        </div>
      </section>
      <section
        v-if="!sectionErrors.tasks && upcomingHomework.length"
        class="home-next-tasks sw-surface"
      >
        <div class="sw-section-head">
          <div>
            <h2>其他待办</h2>
            <p>完成当前练习后，可以接着安排这些作业。</p>
          </div>
          <RouterLink class="sw-link" to="/student/task?status=pending">全部待完成 →</RouterLink>
        </div>
        <button
          v-for="row in upcomingHomework"
          :key="row.task.id"
          type="button"
          class="home-next-task"
          @click="enterHomework(row.task.id)"
        >
          <span class="home-next-icon"><AppIcon name="task" /></span>
          <div>
            <strong>{{ row.task.eventTitle }}</strong
            ><small
              >{{ row.task.dueAt ? formatTime(row.task.dueAt) + ' 截止' : '不限截止时间' }} · 还剩
              {{ row.progress.remaining }} 题</small
            >
          </div>
          <AppIcon name="arrow" />
        </button>
      </section>
      <section class="student-summary" aria-label="学习概览">
        <div>
          <span>累计答题</span
          ><strong>{{ sectionErrors.summary ? '—' : myStats.total }}<small>次</small></strong>
        </div>
        <div>
          <span>正确率</span
          ><strong
            >{{ sectionErrors.summary ? '—' : myStats.accuracy
            }}<small v-if="!sectionErrors.summary && typeof myStats.accuracy === 'number'"
              >%</small
            ></strong
          >
        </div>
        <div>
          <span>待复习错题</span
          ><strong>{{ sectionErrors.wrong ? '—' : myStats.wrong }}<small>道</small></strong>
        </div>
        <RouterLink to="/student/task" aria-label="查看我的作业"
          ><AppIcon name="arrow"
        /></RouterLink>
      </section>
      <p v-if="sectionErrors.summary" class="sw-error" role="alert">
        学习统计暂时无法读取。<button class="sw-link" @click="loadAll">重新加载</button>
      </p>
      <div class="student-home-lower">
        <section class="student-recent-wrong">
          <div class="student-panel-heading">
            <h2>最近错题</h2>
            <RouterLink class="student-text-link" to="/student/wrong-book"
              >查看全部<AppIcon name="arrow"
            /></RouterLink>
          </div>
          <p v-if="sectionErrors.wrong" class="sw-error" role="alert">
            待复习错题暂时无法读取。<button class="sw-link" @click="loadAll">重新加载</button>
          </p>
          <div v-else-if="recentWrong.length" class="student-wrong-list">
            <RouterLink
              v-for="(record, index) in recentWrong"
              :key="record.recordId"
              :to="{
                path: '/student/wrong-book',
                query: { status: 'pending', recordId: record.recordId },
              }"
              class="student-wrong-row"
            >
              <span class="student-question-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <div class="student-wrong-copy">
                <LatexText :content="record.questionPreview?.slice(0, 80) || '查看题目详情'" /><span
                  >我的答案：{{ record.userAnswer }}</span
                >
              </div>
              <span class="student-row-arrow" aria-hidden="true">›</span>
            </RouterLink>
          </div>
          <p v-else class="student-wrong-empty">暂无待复习错题，继续保持。</p>
        </section>
        <aside class="home-revision-shortcuts">
          <div class="student-panel-heading"><h2>继续巩固</h2></div>
          <RouterLink to="/student/wrong-book?status=pending"
            ><span class="home-shortcut-icon"><AppIcon name="wrong-book" /></span
            ><span><strong>补练我的错题</strong><small>先弄懂原题，再练同类题</small></span
            ><AppIcon name="arrow" /></RouterLink
          ><RouterLink to="/student/ai?plans=1"
            ><span class="home-shortcut-icon"><AppIcon name="task" /></span
            ><span><strong>我的复习计划</strong><small>查看安排，继续上次的复习</small></span
            ><AppIcon name="arrow"
          /></RouterLink>
        </aside>
      </div>
    </template>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import '@/assets/student-workspace.css';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import AppToast from '@/components/AppToast.vue';
import LatexText from '@/components/LatexText.vue';
import AppIcon from '@/components/AppIcon.vue';
import { useToast } from '@/composables/useToast';
import { useAuthStore } from '@/stores/auth';
import { getStudentTaskResultsApi, type StudentTaskResult } from '@/api/teacher-event';
import { sortStudentTasks, taskAvailabilityText } from '@/utils/assignmentProgress';
import { getStudentAnswerSummaryApi, getPracticeReviewPageApi } from '@/api/answer-record';
import type { AnswerRecordSummary, StudentAnswerSummary } from '@/types/answer-record';

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';

const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();

const getTaskEntryEventIdKey = () =>
  `${TASK_ENTRY_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const loading = ref(true);
const sectionErrors = ref({ tasks: false, summary: false, wrong: false });
let alive = true,
  sequence = 0;
const tasks = ref<StudentTaskResult[]>([]);
const sortedTasks = computed(() => sortStudentTasks(tasks.value));
const openTasks = computed(() => sortedTasks.value.filter((item) => item.canResume));
const currentResult = computed(() => sortedTasks.value[0] ?? null);
const upcomingHomework = computed(() =>
  openTasks.value
    .filter((row) => row.task.id !== currentResult.value?.task.id)
    .sort(
      (a, b) =>
        (a.task.dueAt ? new Date(a.task.dueAt).getTime() : Infinity) -
          (b.task.dueAt ? new Date(b.task.dueAt).getTime() : Infinity) || 0,
    )
    .slice(0, 2),
);
const task = computed(() => currentResult.value?.task ?? null);
const mySummary = ref<StudentAnswerSummary>({
  totalAttempts: 0,
  correctAttempts: 0,
  wrongQuestions: 0,
});
const wrongRecords = ref<AnswerRecordSummary[]>([]);
const pendingWrong = ref(0);

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const myStats = computed(() => {
  const { totalAttempts: total, correctAttempts: correct } = mySummary.value;
  const accuracy = total > 0 ? Math.round((correct / total) * 100) : '—';
  return { total, correct, wrong: pendingWrong.value, accuracy };
});

const recentWrong = computed(() => wrongRecords.value.slice(0, 3));

const taskQuestionCount = computed(() => currentResult.value?.progress.total ?? 0);
const now = new Date();
const today = {
  date: now.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }),
  weekday: now.toLocaleDateString('zh-CN', { weekday: 'long' }),
};

const formatTime = (time: string) =>
  new Date(time).toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

const loadAll = async () => {
  const owner = ++sequence;
  loading.value = true;
  try {
    const [taskData, summaryData, wrongData] = await Promise.allSettled([
      getStudentTaskResultsApi(),
      getStudentAnswerSummaryApi(),
      getPracticeReviewPageApi({ page: 1, size: 3, mastered: false }),
    ]);
    if (!alive || owner !== sequence) return;
    sectionErrors.value = {
      tasks: taskData.status === 'rejected',
      summary: summaryData.status === 'rejected',
      wrong: wrongData.status === 'rejected',
    };
    if (taskData.status === 'fulfilled') tasks.value = taskData.value;
    if (summaryData.status === 'fulfilled') mySummary.value = summaryData.value;
    if (wrongData.status === 'fulfilled') {
      pendingWrong.value = wrongData.value.total;
      wrongRecords.value = (wrongData.value.records ?? [])
        .filter((r) => r.correct === false)
        .slice(0, 3);
    }
  } finally {
    if (alive && owner === sequence) loading.value = false;
  }
};

const enterTask = () => {
  if (!task.value || !currentResult.value?.canResume) {
    show('当前没有可作答的教师任务', 'info');
    return;
  }
  sessionStorage.setItem(getTaskEntryEventIdKey(), String(task.value.id));
  router.push({ path: '/student/question', query: { resume: '1' } });
};

function enterHomework(eventId: number) {
  sessionStorage.setItem(getTaskEntryEventIdKey(), String(eventId));
  void router.push({ path: '/student/question', query: { eventId, resume: '1' } });
}
onMounted(loadAll);
onBeforeUnmount(() => {
  alive = false;
  ++sequence;
});
</script>

<style scoped>
.student-home-loading {
  padding: 80px 0;
  text-align: center;
  color: var(--text-muted);
}
.student-welcome {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}
.student-eyebrow {
  display: block;
  color: #7c8c9e;
  font-size: 0.6875rem;
  letter-spacing: 0.07em;
  margin-bottom: 8px;
}
.student-welcome h1 {
  font-size: 1.75rem;
  font-weight: 600;
  letter-spacing: -0.025em;
  overflow-wrap: anywhere;
}
.student-welcome h1 > span {
  color: #c09c54;
  font-size: 1.5rem;
  margin-left: 12px;
  font-weight: 400;
}
.student-welcome p {
  margin-top: 7px;
  font-size: 0.8125rem;
  color: #7c8c9e;
}
.student-today {
  color: #64748b;
  font-size: 0.8125rem;
  flex-shrink: 0;
}
.student-today > span {
  color: #8492a3;
  margin-left: 10px;
}
.student-current-task {
  border: 1px solid #dae6e0;
  background: #fff;
  border-radius: 12px;
  padding: 32px 36px;
  min-height: 232px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  overflow: hidden;
}
.student-task-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.student-task-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: #7c8c9e;
  font-size: 0.75rem;
}
.student-task-tag {
  background: #e9f5ee;
  color: #287b56;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 0.6875rem;
}
.student-current-task h2 {
  font-size: 1.5rem;
  font-weight: 600;
  line-height: 1.5;
  margin-top: 15px;
  letter-spacing: -0.025em;
  overflow-wrap: anywhere;
}
.student-task-copy > p {
  color: #7c8c9e;
  font-size: 0.8125rem;
  margin-top: 9px;
}
.student-task-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 24px;
}
.student-task-progress {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 20px;
  max-width: 360px;
  margin-top: 16px;
  color: #527464;
  font-size: 12px;
}
.student-task-progress progress {
  flex-basis: 100%;
  width: 100%;
  height: 6px;
  border: 0;
  border-radius: 6px;
  overflow: hidden;
  appearance: none;
  background: #edf2ef;
}
.student-task-progress progress::-webkit-progress-bar {
  background: #edf2ef;
}
.student-task-progress progress::-webkit-progress-value {
  background: #42856b;
  border-radius: 6px;
}
.student-task-progress progress::-moz-progress-bar {
  background: #42856b;
}
.student-start,
.student-refresh {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 26px;
  border: 1px solid #1d715b;
  background: #1d715b;
  color: #fff;
  padding: 11px 17px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.8125rem;
}
.student-start:hover {
  background: #125f4c;
}
.student-start {
  text-decoration: none;
}
.student-refresh {
  background: #fff;
  color: #1d715b;
}
.student-start .app-icon {
  width: 17px;
  height: 17px;
}
.student-text-link {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  color: #4e7568;
  font-size: 0.75rem;
  text-decoration: none;
  white-space: nowrap;
}
.student-text-link > span {
  color: #7c8c9e;
}
.student-text-link .app-icon {
  width: 16px;
  height: 16px;
}
.student-unavailable {
  font-size: 0.8125rem;
  color: #7c8c9e;
}
.student-atom {
  position: relative;
  width: 200px;
  height: 165px;
  margin: 0 54px 0 30px;
  flex-shrink: 0;
}
.student-orbit {
  width: 162px;
  height: 63px;
  border: 1px solid #bddacf;
  border-radius: 50%;
  position: absolute;
  top: 40px;
  left: 12px;
  transform: rotate(-30deg);
}
.orbit-two {
  transform: rotate(35deg);
}
.orbit-three {
  transform: rotate(90deg);
}
.student-nucleus {
  width: 15px;
  height: 15px;
  background: #488d76;
  border-radius: 50%;
  position: absolute;
  top: 64px;
  left: 86px;
  box-shadow: 0 0 0 8px #dcece5;
}
.student-electron {
  width: 6px;
  height: 6px;
  background: #52967d;
  border-radius: 50%;
  position: absolute;
  top: 42px;
  left: 29px;
}
.student-atom > span {
  font:
    italic 17px Georgia,
    serif;
  color: #6a9682;
  position: absolute;
  right: -26px;
  top: 112px;
}
.student-summary {
  display: flex;
  align-items: center;
  border: 1px solid #e3e8ec;
  border-radius: 10px;
  padding: 20px 32px;
  background: #fff;
  gap: 35px;
  margin-top: -8px;
}
.student-summary > div {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
  min-width: 0;
  border-right: 1px solid #edf0f2;
}
.student-summary > div:nth-child(3) {
  border: 0;
}
.student-summary > div > span {
  color: #758598;
  font-size: 0.75rem;
}
.student-summary strong {
  font-size: 1.65rem;
  font-weight: 600;
  letter-spacing: -0.03em;
}
.student-summary small {
  font-size: 0.75rem;
  color: #7c8c9e;
  margin-left: 5px;
  font-weight: 400;
}
.student-summary > a {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #f4f7f6;
}
.student-home-lower {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}
.student-recent-wrong {
  border: 1px solid #e3e8ec;
  border-radius: 10px;
  background: #fff;
  min-width: 0;
}
.student-panel-heading {
  padding: 23px 25px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.student-panel-heading h2 {
  font-size: 1rem;
  font-weight: 600;
}
.student-wrong-row {
  display: flex;
  align-items: center;
  gap: 15px;
  border-top: 1px solid #f0f2f4;
  margin: 0 25px;
  padding: 18px 0;
  text-decoration: none;
}
.student-question-number {
  width: 31px;
  height: 34px;
  border-radius: 5px;
  background: #f5f3ef;
  display: grid;
  place-items: center;
  font:
    0.8125rem Georgia,
    serif;
  color: #8b8069;
  flex-shrink: 0;
}
.student-wrong-copy {
  min-width: 0;
  color: var(--text);
  font-size: 0.875rem;
}
.student-wrong-copy :deep(.latex-text) {
  display: block;
  max-height: 3em;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.student-wrong-copy > span {
  display: block;
  margin-top: 5px;
  color: #7c8c9e;
  font-size: 0.6875rem;
}
.student-row-arrow {
  margin-left: auto;
  color: #94a3b8;
  font-size: 20px;
}
.student-wrong-empty {
  padding: 35px 25px 50px;
  color: #7c8c9e;
  font-size: 0.875rem;
}
@media (max-width: 1000px) {
  .student-summary {
    gap: 20px;
    padding: 20px 25px;
  }
  .student-summary > div {
    gap: 15px;
  }
  .student-atom {
    margin-right: 0;
    transform: scale(0.9);
  }
  .student-home-lower {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 760px) {
  .student-welcome {
    align-items: flex-start;
    gap: 12px;
  }
  .student-welcome h1 {
    font-size: 1.5rem;
  }
  .student-welcome h1 > span {
    font-size: 1.25rem;
    margin-left: 7px;
  }
  .student-welcome p {
    font-size: 0.75rem;
  }
  .student-today {
    font-size: 0.6875rem;
    padding-top: 8px;
  }
  .student-today > span {
    display: block;
    margin: 5px 0 0;
    text-align: right;
  }
  .student-current-task {
    padding: 23px;
    min-height: 232px;
  }
  .student-task-meta {
    gap: 8px;
    font-size: 0.6875rem;
  }
  .student-current-task h2 {
    font-size: 1.25rem;
  }
  .student-task-copy > p {
    font-size: 0.75rem;
  }
  .student-task-actions {
    gap: 18px;
  }
  .student-atom {
    position: absolute;
    right: -50px;
    top: 30px;
    opacity: 0.2;
    margin: 0;
    transform: scale(0.8);
    pointer-events: none;
  }
  .student-summary {
    padding: 17px 18px;
    gap: 15px;
  }
  .student-summary > div {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
    padding-right: 8px;
  }
  .student-summary > div > span {
    font-size: 0.6875rem;
  }
  .student-summary strong {
    font-size: 1.5rem;
  }
  .student-summary small {
    font-size: 0.6875rem;
  }
  .student-summary > a {
    display: none;
  }
  .student-home-lower {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .student-panel-heading {
    padding: 20px;
  }
  .student-panel-heading h2 {
    font-size: 0.9375rem;
  }
  .student-wrong-row {
    margin: 0 20px;
    gap: 12px;
    padding: 15px 0;
  }
  .student-wrong-copy {
    font-size: 0.8125rem;
  }
}
</style>

<style scoped>
.home-next-tasks {
  padding-bottom: 8px;
}
.home-next-task {
  width: calc(100% - 48px);
  background: none;
  border: 0;
  text-align: left;
  font: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 24px;
  padding: 16px 0;
  border-top: 1px solid #edf2ee;
  text-decoration: none;
}
.home-next-icon,
.home-shortcut-icon {
  width: 36px;
  height: 38px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  background: #f0f6f2;
  color: #87a78f;
  flex-shrink: 0;
}
.home-next-task > div {
  flex: 1;
  min-width: 0;
}
.home-next-task strong {
  font-size: 13px;
  font-weight: 500;
  display: block;
  overflow-wrap: anywhere;
}
.home-next-task small {
  font-size: 11px;
  display: block;
  color: #8c9f91;
  margin-top: 7px;
}
.home-next-task > .app-icon {
  width: 16px;
  height: 16px;
  color: #8fa896;
}
.student-home-lower {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(260px, 1fr);
  gap: 22px;
}
.home-revision-shortcuts {
  padding: 24px;
  border: 1px solid #e0e8e2;
  border-radius: 12px;
  background: white;
}
.home-revision-shortcuts > a {
  display: flex;
  gap: 12px;
  align-items: center;
  text-decoration: none;
  margin-top: 25px;
}
.home-revision-shortcuts strong {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #57765f;
}
.home-revision-shortcuts small {
  display: block;
  font-size: 11px;
  color: #91a396;
  margin-top: 7px;
}
.home-revision-shortcuts > a > .app-icon:last-child {
  margin-left: auto;
  width: 15px;
  height: 15px;
  color: #90aa98;
}
.home-revision-shortcuts .student-panel-heading {
  margin-bottom: 0;
}
@media (max-width: 900px) {
  .student-home-lower {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .home-next-task {
    margin-inline: 16px;
    width: calc(100% - 32px);
  }
  .home-revision-shortcuts {
    padding: 20px;
  }
  .home-next-task small {
    line-height: 1.8;
  }
  .home-next-task strong {
    font-size: 12px;
  }
}
</style>
