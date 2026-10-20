<template>
  <RouterView />
  <AppToast :visible="visible" :type="type" :message="message" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import AppToast from '@/components/AppToast.vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from '@/composables/useToast';

const auth = useAuthStore();
const router=useRouter();
const expired=()=>{void auth.clearAuth().then(()=>router.replace('/auth/login'));};
onMounted(()=>window.addEventListener('auth-expired',expired));
onUnmounted(()=>window.removeEventListener('auth-expired',expired));
const { visible, type, message } = useToast();
</script>
