<template>
  <button class="neon-btn" :class="variant">
    <slot />
  </button>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary';
  }>(),
  {
    variant: 'primary',
  }
);
</script>

