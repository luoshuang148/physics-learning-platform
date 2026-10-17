<template>
  <RouterView />
  <AIFloatWidget v-if="auth.isAuthed" />
  <AppToast :visible="visible" :type="type" :message="message" />
</template>

<script setup lang="ts">
import AIFloatWidget from '@/components/AIFloatWidget.vue';
import AppToast from '@/components/AppToast.vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from '@/composables/useToast';

const auth = useAuthStore();
const { visible, type, message } = useToast();
</script>
