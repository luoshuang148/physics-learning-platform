<template>
  <section v-if="steps.length" class="steps" aria-label="执行步骤">
    <h3>本次执行步骤</h3>
    <ol>
      <li v-for="step in steps" :key="`${step.runId}-${step.seq}`" :class="step.status.toLowerCase()">
        <span class="step-number">{{ step.seq }}</span>
        <div><strong>{{ labels[step.toolName] ?? step.toolName }}</strong><p>{{ step.summary }}</p></div>
        <span class="step-status">{{ step.status === 'SUCCEEDED' ? '完成' : '未完成' }}</span>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { LearningAssistantStep } from '@/types/learning-plan';
defineProps<{ steps: LearningAssistantStep[] }>();
const labels: Record<string, string> = {
  get_learning_summary: '读取个人作业统计', get_wrong_questions: '整理本次错题',
  search_knowledge: '检索可访问资料', recommend_practice: '筛选练习题',
};
</script>

<style scoped>
.steps{padding:18px;border:1px solid #dce4ee;border-radius:16px;background:#fff;color:#1c2938}.steps h3{margin:0 0 12px}.steps ol{list-style:none;padding:0;margin:0;display:grid;gap:10px}.steps li{display:flex;align-items:center;gap:12px}.step-number{width:28px;height:28px;border-radius:50%;display:grid;place-items:center;background:#e8f2ff}.steps p{margin:3px 0;color:#536273;font-size:.9rem}.step-status{margin-left:auto;font-size:.85rem;color:#24734a}.failed .step-status{color:#b42318}
</style>
