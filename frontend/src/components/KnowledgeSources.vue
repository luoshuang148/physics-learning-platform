<template>
  <div v-if="sources?.length" class="sources" aria-label="回答依据">
    <span class="sources-label">参考讲义</span>
    <button v-for="source in sources" :key="source.sourceId" class="source-chip" :disabled="source.available === false" @click="selected=source">
      [{{ source.sourceId }}] {{ source.title }}<template v-if="source.page"> · 第{{ source.page }}页</template>
      <span v-if="source.available === false">（已不可用）</span>
    </button>
    <div v-if="selected" class="source-backdrop" role="presentation" @click.self="selected=null">
      <section class="source-dialog" role="dialog" aria-modal="true" :aria-label="selected.title">
        <button class="source-close" aria-label="关闭" @click="selected=null">×</button>
        <p class="source-kicker">{{ selected.sourceId }} · {{ selected.section || '讲义片段' }}</p>
        <h3>{{ selected.title }}</h3>
        <p v-if="selected.page">第 {{ selected.page }} 页</p>
        <pre>{{ selected.text }}</pre>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import type {AISource} from '@/types/ai';
defineProps<{sources?: AISource[]}>();
const selected=ref<AISource|null>(null);
</script>

<style scoped>
.sources{display:flex;flex-wrap:wrap;align-items:center;gap:7px;margin-top:12px}.sources-label{font-size:12px;color:#65748b}.source-chip{border:1px solid #cbd5e1;background:#f8fafc;border-radius:999px;padding:5px 10px;color:#2456a6;font-size:12px;cursor:pointer}.source-chip:disabled{color:#8490a1;cursor:default}.source-backdrop{position:fixed;inset:0;background:#07132799;display:grid;place-items:center;padding:20px;z-index:1000}.source-dialog{position:relative;width:min(620px,100%);max-height:80vh;overflow:auto;background:white;border-radius:16px;padding:26px;box-shadow:0 24px 80px #0003}.source-dialog h3{margin:6px 40px 12px 0}.source-dialog pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.7;background:#f4f7fb;padding:14px;border-radius:10px;font:inherit}.source-kicker{color:#4670af;font-size:12px}.source-close{position:absolute;right:14px;top:12px;border:0;background:none;font-size:25px;cursor:pointer}@media(max-width:600px){.source-dialog{padding:20px;max-height:88vh}}
</style>
