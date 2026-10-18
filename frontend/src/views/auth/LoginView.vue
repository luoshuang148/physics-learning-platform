<template>
  <AuthLayout>
    <h2 class="auth-title">欢迎回来</h2>


    <form class="form" @submit.prevent="onSubmit">
      <label>用户名</label>
      <input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />

      <label>密码</label>
      <input v-model="form.password" type="password" placeholder="请输入密码" />

      <button class="neon-btn primary" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </form>

    <RouterLink class="auth-link" to="/auth/register">还没有账号？去注册</RouterLink>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthLayout from '@/layouts/AuthLayout.vue';
import AppToast from '@/components/AppToast.vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from '@/composables/useToast';


const auth = useAuthStore();
const router = useRouter();
const { visible, type, message, show } = useToast();

const loading = ref(false);
const form = reactive({
  username: '',
  password: '',
});

const onSubmit = async () => {
  if (!form.username || !form.password) {
    show('请完整填写登录信息', 'error');
    return;
  }

  loading.value = true;
  try {
    await auth.login(form);
    show('登录成功', 'success');
    await router.push(auth.isTeacher ? '/teacher/dashboard' : '/student/home');
  } catch (error: any) {
    const hint = error?.response?.data?.message ?? error?.message ?? '登录失败';
    show(hint, 'error');
  } finally {
    loading.value = false;
  }
};
</script>

