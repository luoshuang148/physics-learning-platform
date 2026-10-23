<template>
  <section v-if="preview" class="plan-card">
    <header><div><span class="eyebrow">{{ preview.executionMode === 'AGENT' ? '智能复习助手' : '复习工作流' }}</span><h3>{{ preview.days }} 天复习建议</h3></div><span v-if="preview.partial" class="partial">部分建议</span></header>
    <p class="notice">这是一份待你检查的建议。保存后才会建立草稿，激活后才能开始跟踪补练。</p>
    <ol v-if="preview.items.length" class="items">
      <li v-for="item in preview.items" :key="`${item.day}-${item.questionId}`">
        <div><strong>第 {{ item.day }} 天 · {{ item.knowledgePointCode }}</strong><p>{{ item.questionId ? `练习题 ${item.questionId}` : '暂时没有匹配题目' }}</p></div>
        <details v-if="item.reasonSourceIds.length"><summary>依据</summary><p>来源标识：{{ item.reasonSourceIds.join('、') }}</p></details>
        <button v-if="savedPlan?.status === 'ACTIVE'" class="quiet" @click="$emit('practice', item.questionId, savedPlan.eventId)">去练习</button>
      </li>
    </ol>
    <p v-else class="empty">目前没有足够的知识点标签和可练题，助手没有编造练习题。</p>
    <p v-if="preview.evidenceSummary.length" class="evidence">使用的证据标识：{{ preview.evidenceSummary.join('、') }}</p>
    <footer v-if="!savedPlan"><button :disabled="busy || !runId" @click="$emit('save')">保存草稿</button></footer>
    <footer v-else>
      <span>状态：{{ savedPlan.status }} · 补练 {{ savedPlan.practiceAttempts }} 次，答对 {{ savedPlan.practiceCorrect }} 次</span>
      <button v-if="savedPlan.status === 'DRAFT'" :disabled="busy" @click="$emit('activate')">激活计划</button>
      <button v-if="savedPlan.status === 'ACTIVE'" :disabled="busy" @click="$emit('refresh')">刷新进度</button>
      <button v-if="savedPlan.status === 'ACTIVE' || savedPlan.status === 'DRAFT'" class="quiet" :disabled="busy" @click="$emit('cancel')">取消计划</button>
    </footer>
    <p v-if="savedPlan?.progressStatus === 'ON_TRACK'" class="progress">计划题目目前的补练记录均为正确；这只描述记录，不代表计划带来了提分。</p>
  </section>
</template>

<script setup lang="ts">
import type { LearningPlanPreview, LearningPlanView } from '@/types/learning-plan';
defineProps<{ preview: LearningPlanPreview | null; savedPlan: LearningPlanView | null; runId: string; busy: boolean }>();
defineEmits<{ save: []; activate: []; cancel: []; refresh: []; practice: [questionId: number, eventId: number] }>();
</script>

<style scoped>
.plan-card{padding:22px;border:1px solid #dae2ed;border-radius:18px;background:#fff;color:#1c2938}.plan-card header,.plan-card footer{display:flex;align-items:center;justify-content:space-between;gap:12px}.eyebrow{font-size:.8rem;color:#526274}.plan-card h3{margin:4px 0 10px}.notice,.empty,.progress{padding:12px;border-radius:10px;background:#f4f7fb;color:#46576b}.items{padding-left:20px;display:grid;gap:12px}.items li{padding:12px;background:#f8fafc;border-radius:12px}.items p{margin:6px 0;color:#59697a}.evidence{font-size:.9rem;color:#526274}.partial{color:#8a5a00;background:#fff3cf;padding:5px 9px;border-radius:99px}button{border:0;border-radius:10px;padding:10px 14px;background:#185abc;color:white;font-weight:700;cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}.quiet{background:#e9eef5;color:#364152}.plan-card footer{margin-top:14px;flex-wrap:wrap}.progress{margin-bottom:0;background:#eaf7ef}
</style>
