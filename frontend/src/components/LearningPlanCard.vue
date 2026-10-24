<template>
  <section v-if="preview" class="plan-card">
    <header>
      <div>
        <span class="plan-eyebrow">我的复习安排</span>
        <h3>{{ preview.days }} 天复习计划</h3>
      </div>
      <span class="plan-state">{{
        savedPlan ? statusNames[savedPlan.status] : preview.partial ? '待完善的建议' : '复习建议'
      }}</span>
    </header>
    <p v-if="!savedPlan" class="plan-notice">先看看每天的练习安排，满意后保存并启用计划。</p>
    <p v-else-if="savedPlan.status === 'DRAFT'" class="plan-notice">
      计划已保存为草稿，启用后即可开始补练。
    </p>
    <p v-else-if="savedPlan.status === 'ACTIVE'" class="plan-notice">
      按天完成练习，需要时可以刷新补练进度。
    </p>
    <ol v-if="displayedItems.length" class="plan-items">
      <li
        v-for="(item, index) in displayedItems"
        :key="`${item.day}-${item.questionId}-${item.knowledgePointCode}-${index}`"
      >
        <span class="plan-day">第 {{ item.day }} 天</span>
        <div class="plan-item-copy">
          <strong>{{ pointNames[item.knowledgePointCode] || '知识点巩固' }}</strong>
          <p>{{ item.questionId ? `练习 ${index + 1}` : '暂无匹配的练习题' }}</p>
        </div>
        <button
          v-if="savedPlan?.status === 'ACTIVE' && item.questionId"
          class="plan-button secondary"
          :disabled="busy"
          @click="$emit('practice', item.questionId, savedPlan.eventId, item.id)"
        >
          开始补练 →
        </button>
      </li>
    </ol>
    <p v-else class="plan-notice">
      当前还没有合适的练习安排，可以补充作业或调整复习要求后重新生成。
    </p>
    <details
      v-if="
        preview.evidenceSummary.length || displayedItems.some((item) => item.reasonSourceIds.length)
      "
      class="plan-evidence"
    >
      <summary>查看计划依据</summary>
      <p v-if="preview.evidenceSummary.length">{{ preview.evidenceSummary.join('、') }}</p>
      <p
        v-for="(item, index) in displayedItems.filter((item) => item.reasonSourceIds.length)"
        :key="index"
      >
        第 {{ item.day }} 天：{{ item.reasonSourceIds.join('、') }}
      </p>
    </details>
    <footer v-if="!savedPlan">
      <button class="plan-button" :disabled="busy || !runId" @click="$emit('save')">
        {{ busy ? '正在保存…' : '保存为我的计划' }}
      </button>
    </footer>
    <footer v-else>
      <span class="plan-progress-copy"
        >已补练 {{ savedPlan.practiceAttempts }} 次 · 答对 {{ savedPlan.practiceCorrect }} 次</span
      >
      <div>
        <button
          v-if="savedPlan.status === 'DRAFT'"
          class="plan-button"
          :disabled="busy"
          @click="$emit('activate')"
        >
          启用计划</button
        ><button
          v-if="savedPlan.status === 'ACTIVE'"
          class="plan-button secondary"
          :disabled="busy"
          @click="$emit('refresh')"
        >
          刷新进度</button
        ><button
          v-if="savedPlan.status === 'ACTIVE' || savedPlan.status === 'DRAFT'"
          class="plan-cancel"
          :disabled="busy"
          @click="$emit('cancel')"
        >
          取消计划
        </button>
      </div>
    </footer>
    <p v-if="savedPlan?.progressStatus === 'ON_TRACK'" class="plan-on-track">
      当前已记录的补练均已答对，继续按计划巩固。
    </p>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { getKnowledgePointsApi } from '@/api/question';
import type { LearningPlanPreview, LearningPlanView } from '@/types/learning-plan';
const props = defineProps<{
  preview: LearningPlanPreview | null;
  savedPlan: LearningPlanView | null;
  runId: string;
  busy: boolean;
}>();
defineEmits<{
  save: [];
  activate: [];
  cancel: [];
  refresh: [];
  practice: [questionId: number, eventId: number, itemId?: number];
}>();
const displayedItems = computed(() => props.savedPlan?.items ?? props.preview?.items ?? []);
const statusNames = { DRAFT: '草稿', ACTIVE: '进行中', CANCELLED: '已取消', COMPLETED: '已完成' },
  pointNames = ref<Record<string, string>>({});
let alive = true;
onMounted(async () => {
  try {
    const points = await getKnowledgePointsApi();
    if (alive)
      pointNames.value = Object.fromEntries(points.map((point) => [point.code, point.name]));
  } catch {
    /* The plan remains usable when the optional label directory is unavailable. */
  }
});
onBeforeUnmount(() => {
  alive = false;
});
</script>
<style scoped>
.plan-card {
  border: 1px solid #dce8e0;
  border-radius: 13px;
  background: white;
  padding: 24px;
  color: #4b6b55;
}
.plan-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.plan-eyebrow {
  font-size: 11px;
  color: #8aa491;
}
.plan-card h3 {
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.02em;
  margin: 8px 0 0;
  color: #496c55;
}
.plan-state {
  font-size: 11px;
  color: #7d9a86;
  background: #f0f6f2;
  border-radius: 5px;
  padding: 5px 8px;
  white-space: nowrap;
}
.plan-notice {
  font-size: 12px;
  color: #8ba092;
  line-height: 1.9;
  margin: 19px 0;
}
.plan-items {
  list-style: none;
  padding: 0;
  margin: 23px 0 0;
  display: grid;
  gap: 12px;
}
.plan-items li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: #f8fbf9;
  border: 1px solid #e7eee9;
  border-radius: 9px;
}
.plan-day {
  padding: 7px 9px;
  background: #edf4ef;
  border-radius: 6px;
  font-size: 11px;
  color: #8aa491;
  white-space: nowrap;
}
.plan-item-copy {
  flex: 1;
  min-width: 0;
}
.plan-item-copy strong {
  font-size: 13px;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.plan-item-copy p {
  font-size: 11px;
  color: #98aa9c;
  margin: 7px 0 0;
}
.plan-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 14px;
  border: 1px solid #3c7855;
  border-radius: 7px;
  background: #3c7855;
  color: white;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.plan-button.secondary {
  background: white;
  color: #6d947b;
  border-color: #d5e4da;
}
.plan-button:disabled,
.plan-cancel:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.plan-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 22px;
  margin-top: 22px;
  border-top: 1px solid #e9efeb;
}
.plan-card footer > div {
  display: flex;
  align-items: center;
  gap: 14px;
}
.plan-progress-copy {
  font-size: 11px;
  color: #98aa9b;
  line-height: 1.8;
}
.plan-cancel {
  border: 0;
  background: none;
  padding: 8px 0;
  font: inherit;
  font-size: 11px;
  color: #a09e8e;
  cursor: pointer;
}
.plan-evidence {
  font-size: 11px;
  color: #9aad9e;
  line-height: 1.9;
  margin-top: 21px;
}
.plan-evidence summary {
  cursor: pointer;
}
.plan-evidence p {
  overflow-wrap: anywhere;
}
.plan-on-track {
  font-size: 12px;
  color: #6f9b7b;
  line-height: 1.8;
  margin: 19px 0 0;
}
@media (max-width: 640px) {
  .plan-card {
    padding: 18px 14px;
  }
  .plan-card h3 {
    font-size: 16px;
  }
  .plan-items li {
    gap: 10px;
    padding: 13px 10px;
    flex-wrap: wrap;
  }
  .plan-items .plan-button {
    margin-left: auto;
    font-size: 11px;
    padding: 8px 10px;
  }
  .plan-day {
    font-size: 10px;
    padding: 6px;
  }
  .plan-card footer > div {
    margin-left: auto;
  }
  .plan-item-copy strong {
    font-size: 12px;
  }
}
</style>
