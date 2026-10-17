<template>
  <span class="latex-text" v-html="rendered"></span>
</template>

<script setup lang="ts">
import katex from 'katex';
import { computed } from 'vue';

const props = defineProps<{ content: string | null | undefined }>();

const escapeHtml = (value: string) => value
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

const normalizeSource = (source: string) => source
  .replace(/\\\\\\/g, '\\\\')
  .replace(/\\\[/g, '$$')
  .replace(/\\\]/g, '$$')
  .replace(/\\\((.*?)\\\)/g, '$$$1$$')
  .replace(/\\\((.*?)$/g, '$$$1')
  .replace(/^([^$]*?)\\\)/g, '$1$$')
  .replace(/\u0009/g, '\\\\t')
  .replace(/\u000c/g, '\\\\f')
  .replace(/\u0008/g, '\\\\b')
  .replace(/\u000b/g, '\\\\v');

const renderInlineMath = (text: string) => text.replace(/\$([^$\n]+?)\$/g, (_, expr) => {
  try {
    return katex.renderToString(expr.trim(), { throwOnError: false, displayMode: false });
  } catch {
    return escapeHtml(expr);
  }
});

const renderDisplayMath = (text: string) => text.replace(/\$\$([\s\S]+?)\$\$/g, (_, expr) => {
  try {
    return katex.renderToString(expr.trim(), { throwOnError: false, displayMode: true });
  } catch {
    return `<div class="latex-display-fallback">${escapeHtml(expr)}</div>`;
  }
});

const renderPlainText = (text: string) => escapeHtml(text).replace(/\n/g, '<br>');

const rendered = computed(() => {
  const source = normalizeSource(props.content ?? '');
  if (!source) return '';

  const tokenMap = new Map<string, string>();
  let counter = 0;

  const protectedSource = source.replace(/\$\$([\s\S]+?)\$\$|\$([^$\n]+?)\$/g, (match) => {
    const key = `__LATEX_TOKEN_${counter++}__`;
    tokenMap.set(key, match.startsWith('$$') ? renderDisplayMath(match) : renderInlineMath(match));
    return key;
  });

  let html = renderPlainText(protectedSource);
  tokenMap.forEach((value, key) => {
    html = html.replace(key, value);
  });
  return html;
});
</script>

<style scoped>
.latex-text :deep(.katex-display) {
  margin: 0.35em 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.latex-display-fallback {
  white-space: pre-wrap;
}
</style>

