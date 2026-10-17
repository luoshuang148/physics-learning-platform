<template>
  <AppShell :subtitle="`教师任务 · ${auth.user?.realName ?? ''}`" :links="links">
    <section class="grid one">
      <GlassPanel title="本次教师发布任务">
        <div v-if="loading" class="muted">正在加载任务…</div>
        <div v-else-if="!task" class="task-empty">
          <p class="muted">教师暂未发布任务，暂时不能进入实验与答题。</p>
        </div>
        <template v-else>
          <h3 class="task-title">{{ task.eventTitle }}</h3>
          <p class="task-desc">当前实验系列包含 {{ task.eventTitle }}，关联题目 ID：{{ taskQuestionIds.join('、') || '无' }}</p>
          <div class="task-meta">
            <span>发布教师：{{ task.teacherName }}</span>
            <span>任务时间：{{ formatTime(task.eventTimestamp) }}</span>
          </div>

          <p class="task-series-tip" v-if="taskQuestionIds.length === 0">当前任务未配置题目，可先进入实验学习。</p>

          <div class="button-row">
            <button class="neon-btn primary" @click="enterTask">开始本次任务</button>
          </div>
        </template>
      </GlassPanel>
    </section>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AppShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/layouts/AppShell.vue';
import GlassPanel from '@/components/GlassPanel.vue';
import AppToast from '@/components/AppToast.vue';
import { useToast } from '@/composables/useToast';
import { getTeacherEventApi } from '@/api/teacher-event';
import type { TeacherEventResponse } from '@/types/teacher-event';
import { useAuthStore } from '@/stores/auth';

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';

const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();

const getTaskEntryEventIdKey = () => `${TASK_ENTRY_EVENT_ID_KEY_PREFIX}_${auth.user?.id ?? 'anonymous'}`;

const links = [
  { label: '首页', to: '/student/home' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '学习报告', to: '/student/report' },
  { label: '任务记录', to: '/student/task' },
];

const loading = ref(false);
const task = ref<TeacherEventResponse | null>(null);

const parseQuestionIds = (description: string | null): number[] => {
  if (!description) return [];
  const normalized = description.trim();
  if (!normalized.startsWith('QIDS:')) return [];
  return normalized
    .slice(5)
    .split(/[，,]/)
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isInteger(id) && id > 0);
};

const taskQuestionIds = computed(() => parseQuestionIds(task.value?.eventDescription ?? null));

const formatTime = (time: string) => new Date(time).toLocaleString();

const loadTask = async () => {
  loading.value = true;
  try {
    task.value = await getTeacherEventApi();
  } catch {
    show('任务加载失败，请稍后重试', 'error');
  } finally {
    loading.value = false;
  }
};

const enterTask = async () => {
  if (!task.value) {
    show('当前无可进入的教师任务', 'info');
    return;
  }

  sessionStorage.setItem(getTaskEntryEventIdKey(), String(task.value.id));
  await router.push('/student/experiment');
};

onMounted(loadTask);
</script>

<style scoped>
.task-title {
  margin: 0;
  font-size: var(--font-size-2xl);
}

.task-desc {
  margin: 10px 0 0;
  color: var(--text-muted);
}

.task-series-tip {
  margin-top: 10px;
  color: #ffd166;
  font-size: var(--font-size-sm);
}

.task-meta {
  margin-top: 14px;
  display: grid;
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.task-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
}
</style>
