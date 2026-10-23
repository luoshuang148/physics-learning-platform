<template>
  <div class="platform-sources">
    <h4>平台来源</h4>
    <div v-if="!sources.length" class="sources-empty">暂无引用来源</div>
    <div v-for="source in sources" :key="source.sourceId" class="source-card">
      <div class="source-meta">
        <span class="source-kind">平台题目</span>
        <span class="source-id">{{ source.sourceId }}</span>
      </div>
      <p class="source-text">{{ source.text }}</p>
      <button class="ghost-btn" @click="openSource(source)">查看原题</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PlatformSource } from '@/api/study-assistant';
import { platformSourceApi } from '@/api/study-assistant';

defineProps<{ sources: PlatformSource[] }>();
const emit = defineEmits<{ (e: 'open', source: PlatformSource): void }>();

const openSource = async (source: PlatformSource) => {
  try {
    const full = await platformSourceApi(source.sourceId);
    emit('open', { ...source, ...full });
  } catch {
    emit('open', source);
  }
};
</script>

<style scoped>
.platform-sources {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sources-empty {
  color: #888;
  font-size: 13px;
}
.source-card {
  border: 1px solid rgba(120, 140, 180, 0.35);
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.04);
}
.source-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 6px;
}
.source-kind {
  color: #6aa6ff;
}
.source-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}
.ghost-btn {
  background: transparent;
  border: 1px solid rgba(140, 160, 200, 0.5);
  color: inherit;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 12px;
}
</style>
