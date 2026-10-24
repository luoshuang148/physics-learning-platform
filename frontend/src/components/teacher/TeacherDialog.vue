<template>
  <AppDialog
    :open="open"
    :title="title"
    :drawer="drawer"
    :wide="wide"
    :busy="busy"
    @close="$emit('close')"
    ><slot
  /></AppDialog>
</template>
<script setup lang="ts">
import AppDialog from '@/components/AppDialog.vue';
withDefaults(
  defineProps<{ open: boolean; title: string; drawer?: boolean; wide?: boolean; busy?: boolean }>(),
  { drawer: false, wide: false, busy: false },
);
defineEmits<{ close: [] }>();
</script>
