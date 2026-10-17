<template>
  <div class="panel" :class="{ compact }">
    <div v-if="title" class="panel-head">
      <h3>{{ title }}</h3>
      <slot name="head-extra" />
    </div>
    <div class="panel-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string;
  compact?: boolean;
}>();
</script>

