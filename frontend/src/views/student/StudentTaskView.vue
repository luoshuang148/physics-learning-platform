<template>
  <AppShell :subtitle="`教师任务 · ${auth.user?.realName ?? ''}`" :links="links">
    <section class="grid one">
      <GlassPanel title="教师作业">
        <div v-if="loading" class="muted">正在加载任务…</div>
        <div v-else-if="tasks.length === 0" class="task-empty">
          <p class="muted">教师暂未发布任务，暂时不能答题。</p>
        </div>
        <div v-else class="task-list">
          <article v-for="task in tasks" :key="task.id" class="task-item">
            <div>
              <div class="task-heading">
                <h3 class="task-title">{{ task.eventTitle }}</h3>
                <span class="task-status" :class="task.availability?.toLowerCase()">{{ statusText(task) }}</span>
              </div>
              <p class="task-desc">共 {{ task.questionCount ?? task.questionIds?.length ?? 0 }} 道题，按教师配置的顺序作答。</p>
              <div class="task-meta">
                <span>发布教师：{{ task.teacherName }}</span>
                <span>开始：{{ task.startAt ? formatTime(task.startAt) : '立即' }}</span>
                <span>截止：{{ task.dueAt ? formatTime(task.dueAt) : '不限' }}</span>
              </div>
            </div>
            <button v-if="task.canAnswer" class="neon-btn primary" @click="enterTask(task)">开始本次任务</button>
            <span v-else class="task-unavailable">{{ statusText(task) }}</span>
          </article>
        </div>
      </GlassPanel>
    </section>
    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import GlassPanel from '@/components/GlassPanel.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { getStudentTasksApi } from '@/api/teacher-event';
import type { TeacherEventResponse } from '@/types/teacher-event';
import { useAuthStore } from '@/stores/auth';

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';
const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();
const getTaskEntryEventIdKey = () => TASK_ENTRY_EVENT_ID_KEY_PREFIX + '_' + (auth.user?.id ?? 'anonymous');
const links = [
  { label: '首页', to: '/student/home' },
  { label: '3D 实验', to: '/student/experiments' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
  { label: '作业', to: '/student/task' },
];
const loading = ref(false);
const tasks = ref<TeacherEventResponse[]>([]);
const formatTime = (time: string) => new Date(time).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
const statusText = (task: TeacherEventResponse) => {
  if (task.availability === 'SCHEDULED') return '未开始';
  if (task.availability === 'ENDED') return '已截止';
  if (task.availability === 'CLOSED') return '已截止';
  return '进行中';
};
const loadTasks = async () => {
  loading.value = true;
  try { tasks.value = await getStudentTasksApi(); }
  catch { show('任务加载失败，请稍后重试', 'error'); }
  finally { loading.value = false; }
};
const enterTask = async (task: TeacherEventResponse) => {
  sessionStorage.setItem(getTaskEntryEventIdKey(), String(task.id));
  await router.push('/student/question');
};
onMounted(loadTasks);
</script>

<style scoped>
.task-list { display: grid; gap: 10px; }
.task-item { display: flex; justify-content: space-between; gap: 18px; align-items: center; padding: 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.task-heading { display: flex; align-items: center; gap: 8px; }
.task-title { margin: 0; font-size: 18px; }
.task-status { padding: 2px 7px; border-radius: 4px; background: #ecfdf5; color: #0f766e; font-size: 11px; }
.task-status.scheduled { background: #fef3c7; color: #92400e; }
.task-status.ended, .task-status.closed { background: #f1f5f9; color: #64748b; }
.task-desc { margin: 6px 0 0; color: #64748b; font-size: 13px; }
.task-meta { margin-top: 8px; display: flex; gap: 14px; flex-wrap: wrap; color: #64748b; font-size: 12px; }
.task-empty { min-height: 120px; display: flex; align-items: center; }
.task-unavailable { color: #64748b; font-size: 12px; }
@media (max-width: 700px) { .task-item { flex-direction: column; align-items: stretch; } }
</style>
