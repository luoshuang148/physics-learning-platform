<template>
  <div class="stream-box" :class="{ compact: compactMode }">
    <div class="stream-meta" v-if="loading">
      <span v-if="loading" class="pulse">AI 努力分析中…</span>
    </div>

    <div v-if="shouldShowFriendlyEmpty" class="stream-empty-state">
      <div class="stream-empty-title">当前还没有学生答题记录哦</div>
    </div>

    <!-- 依赖Vue响应式：loading变化时自动重新渲染，表格在结束后会被完整解析 -->
    <div v-else-if="content" class="stream-content markdown-body" v-html="renderMarkdown(content)"></div>

    <details v-if="reasoning" class="reasoning-box">
      <summary>思考过程</summary>
      <div class="reasoning-content markdown-body" v-html="renderMarkdown(reasoning)"></div>
    </details>

    <p v-if="showVisibleError" class="error-text">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { renderMarkdown } from '@/utils/markdown';

import 'katex/dist/katex.min.css';

// 配置 marked 开启 GFM 支持


const props = withDefaults(defineProps<{
  loading: boolean;
  progress: number;
  content: string;
  reasoning: string;
  tokenUsage: string;
  error: string;
}>(), {
  loading: false,
  progress: 0,
  content: '',
  reasoning: '',
  tokenUsage: '',
  error: '',
});

const friendlyEmptyPatterns = [
  '该会话暂无学生作答记录',
  '暂无学生作答记录',
  '还没有学生答题记录',
  '没有学生答题记录',
  '无法生成任务学习报告',
];

const shouldShowFriendlyEmpty = computed(() => {
  if (props.loading) return false;
  if (props.content.trim()) return false;
  const message = props.error.trim();
  if (!message) return false;
  return friendlyEmptyPatterns.some(pattern => message.includes(pattern));
});

const showVisibleError = computed(() => Boolean(props.error) && !shouldShowFriendlyEmpty.value);
const compactMode = computed(() => shouldShowFriendlyEmpty.value && !props.reasoning);

</script>

<style scoped>
.stream-box {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  background: #ffffff;
  padding: 16px 18px;
  display: grid;
  gap: 12px;
}

.stream-box.compact {
  gap: 0;
}

.stream-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  color: #64748b;
}

.pulse {
  color: #0f766e;
  font-weight: 700;
}

.stream-empty-state {
  display: grid;
  justify-items: start;
  gap: 10px;
  padding: 18px 4px 4px;
}

.stream-empty-icon {
  font-size: 26px;
  line-height: 1;
}

.stream-empty-title {
  font-size: 18px;
  font-weight: 700;
  color: #172033;
}

.stream-empty-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

.stream-content,
.reasoning-content {
  word-break: break-word;
  font-family: inherit;
  font-size: 15px;
  line-height: 1.72;
  color: #334155;
}

.stream-content {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 16px 18px;
  min-height: clamp(260px, 56vh, 720px);
}

.reasoning-box {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #f8fafc;
  padding: 12px 14px;
}

.reasoning-box summary {
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  color: #334155;
}

.reasoning-content {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.64;
  color: #475569;
}

.error-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #b91c1c;
}

.markdown-body :deep(p) {
  margin: 0 0 0.72em;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  color: #0f766e;
  font-weight: 800;
  line-height: 1.3;
}

.markdown-body :deep(h1) {
  margin: 1.1em 0 0.55em;
  font-size: 28px;
}

.markdown-body :deep(h2) {
  margin: 1em 0 0.5em;
  font-size: 23px;
}

.markdown-body :deep(h3) {
  margin: 0.9em 0 0.45em;
  font-size: 19px;
}

.markdown-body :deep(h4) {
  margin: 0.85em 0 0.4em;
  font-size: 17px;
}

.markdown-body :deep(strong) { color: #92400e; font-weight: 700; }
.markdown-body :deep(em) { color: #be123c; font-style: italic; }
.markdown-body :deep(code) {
  background: #f1f5f9;
  color: #0f766e;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.92em;
}
.markdown-body :deep(pre) {
  background: #f1f5f9;
  color: #334155;
  padding: 14px 16px;
  border-radius: 10px;
  overflow-x: auto;
  margin: 0.9em 0 1em;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { margin: 0.6em 0 0.85em; padding-left: 1.5em; }
.markdown-body :deep(li) { margin: 0.28em 0; line-height: 1.7; }
.markdown-body :deep(a) { color: #2563eb; text-decoration: underline; }
.markdown-body :deep(blockquote) {
  margin: 0.9em 0;
  padding: 0.7em 0.95em;
  border-left: 3px solid #0f766e;
  background: #ecfdf5;
  color: #475569;
}
.markdown-body :deep(.katex) { font-size: 1.05em; }
.markdown-body :deep(.katex-display) { margin: 0.95em 0; text-align: center; }
.markdown-body :deep(.katex-display .katex) { display: inline-block; }
.markdown-body :deep(.katex-error) { color: #b91c1c; font-style: italic; }

/* GFM 表格样式 */
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.95em 0 1.05em;
  width: 100%;
  font-size: 14px;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f8fafc;
  font-weight: 700;
  color: #0f766e;
}
.markdown-body :deep(tr:nth-child(even)) {
  background: #f8fafc;
}

/* GFM 删除线样式 */
.markdown-body :deep(del),
.markdown-body :deep(s),
.markdown-body :deep(strike) {
  color: #94a3b8;
  text-decoration: line-through;
}

/* GFM 任务列表样式 */
.markdown-body :deep(ul.contains-task-list) {
  list-style: none;
  padding-left: 4px;
}
.markdown-body :deep(li.contains-task-list) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.markdown-body :deep(input[type="checkbox"]) {
  margin-top: 6px;
  accent-color: #0f766e;
}
</style>
