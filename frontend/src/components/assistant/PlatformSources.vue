<template>
  <div v-if="sources.length" class="assistant-sources" aria-label="平台题目来源">
    <p class="sources-label">参考平台题目 · {{ uniqueSources.length }} 道</p>
    <div class="source-links">
      <button v-for="source in uniqueSources" :key="source.sourceId" type="button" :disabled="loading === source.sourceId" @click="openSource(source)">
        <AppIcon name="questions" />
        <span>{{ loading === source.sourceId ? '正在读取…' : `查看原题 · 题目 ${source.questionId}` }}</span><span aria-hidden="true">↗</span>
      </button>
    </div>
    <p v-if="error" class="source-error" role="alert">{{ error }}</p>
    <div v-if="opened" class="source-preview">
      <header><strong>题目 {{ opened.questionId }}</strong><button type="button" aria-label="收起原题" @click="opened = null">×</button></header>
      <div class="source-body" v-html="renderMarkdown(opened.text)"></div>
      <button type="button" class="source-follow-up" @click="$emit('open', opened)">继续讲解这道题</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { platformSourceApi, type PlatformSource } from '@/api/study-assistant';
import { renderMarkdown } from '@/utils/markdown';
import AppIcon from '@/components/AppIcon.vue';
const props = defineProps<{ sources: PlatformSource[] }>();
defineEmits<{ open: [source: PlatformSource] }>();
const loading = ref('');
const error = ref('');
const opened = ref<PlatformSource | null>(null);
const uniqueSources = computed(() => props.sources.filter((source, index, all) => all.findIndex(s => s.questionId === source.questionId) === index));
async function openSource(source: PlatformSource) {
  loading.value = source.sourceId; error.value = ''; opened.value = null;
  try {
    const full = await platformSourceApi(source.sourceId);
    if (full.available === false) throw new Error('unavailable');
    opened.value = full;
  } catch { error.value = '来源已失效或暂时无法访问，请重新提问检索。'; }
  finally { loading.value = ''; }
}
</script>

<style scoped>
.assistant-sources { margin-top:18px; }
.sources-label { color:var(--text-muted); font-size:12px; margin:0 0 10px; }
.source-links { display:flex; flex-wrap:wrap; gap:8px; }
.source-links button { display:flex; align-items:center; gap:8px; padding:9px 12px; border:1px solid var(--line); background:var(--panel); border-radius:10px; color:var(--text); font-size:12px; cursor:pointer; }
.source-links button:hover { border-color:var(--brand); color:var(--brand); }
.source-links :deep(svg) { width:16px; height:16px; }
.source-error { color:#b42318; font-size:13px; }
.source-preview { margin-top:12px; padding:18px; border-left:2px solid var(--brand); background:var(--bg-soft); border-radius:0 12px 12px 0; }
.source-preview header { display:flex; justify-content:space-between; align-items:center; }
.source-preview header button { border:0; background:none; font-size:20px; color:var(--text-muted); cursor:pointer; }
.source-body { line-height:1.8; overflow-wrap:anywhere; }
.source-follow-up { border:0; padding:8px 0 0; background:none; color:var(--brand); cursor:pointer; }
</style>
