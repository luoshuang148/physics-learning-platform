<template>
  <div class="toast" :class="[`type-${type}`, { visible }]">
    {{ message }}
  </div>
</template>

<script setup lang="ts">
defineProps<{
  visible: boolean;
  type: 'success' | 'error' | 'info';
  message: string;
}>();
</script>

