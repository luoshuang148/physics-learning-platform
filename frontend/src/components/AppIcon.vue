<template>
  <svg class="app-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="paths[name] ?? paths.home" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string> = {
  home: 'M3 10 12 3 21 10V20A1 1 0 0 1 20 21H15V14H9V21H4A1 1 0 0 1 3 20Z',
  task: 'M7 4H5V21H19V4H17M9 2H15V6H9ZM9 11H15M9 15H15M9 18H12',
  'wrong-book': 'M12 5C9 3 6 3 3 4V19C6 18 9 18 12 20 15 18 18 18 21 19V4C18 3 15 3 12 5ZM12 5V20M6 8H9M6 12H9M15 8H18M15 12H18',
  ai: 'M12 3A8 8 0 0 0 5.2 15.2L4 20 8.8 18.8A8 8 0 1 0 12 3ZM12 7V12L15 14M18.5 3.5 19 5 20.5 5.5 19 6 18.5 7.5 18 6 16.5 5.5 18 5Z',
  report: 'M4 3V21H21M8 16V11M13 16V7M18 16V4',
  arrow: 'M4 12H20M14 6 20 12 14 18',
  spark: 'M12 3 14.5 9.5 21 12 14.5 14.5 12 21 9.5 14.5 3 12 9.5 9.5Z',
  dashboard: 'M3 3H10V10H3ZM14 3H21V10H14ZM3 14H10V21H3ZM14 14H21V21H14Z',
  users: 'M16 21V19A4 4 0 0 0 12 15H6A4 4 0 0 0 2 19V21M22 21V19A4 4 0 0 0 19 15.13M15 3.13A4 4 0 0 1 15 10.87M13 7A4 4 0 1 1 5 7 4 4 0 0 1 13 7Z',
  questions: 'M8 3H19V21H5V6M3 3H15M3 7H6M3 11H6M3 15H6M10 8H15M10 12H15M10 16H13',
  'student-analysis': 'M4 3V21H21M8 16V11M13 16V7M18 16V4',
  'event-manage': 'M4 5H20V21H4ZM8 2V8M16 2V8M4 10H20M8 14H11M8 17H15',
};
</script>

<style scoped>
.app-icon { width: 20px; height: 20px; flex-shrink: 0; }
</style>
