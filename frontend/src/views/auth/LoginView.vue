<template>
  <AuthLayout>
    <header class="auth-heading auth-login-heading">
      <h2 class="auth-title">登录</h2>
    </header>

    <form class="auth-form" novalidate :aria-busy="loading" @submit.prevent="onSubmit" @input="loginError = ''">
      <div class="auth-field">
        <label for="login-username">用户名</label>
        <input
          id="login-username"
          v-model="form.username"
          placeholder="请输入用户名"
          autocomplete="username"
          autocapitalize="none"
          :spellcheck="false"
          :disabled="loading"
          :aria-invalid="(attempted && !form.username.trim()) || undefined"
          :aria-describedby="loginError ? 'login-error' : undefined"
          required
        />
      </div>

      <div class="auth-field">
        <label for="login-password">密码</label>
        <AuthPasswordInput
          id="login-password"
          v-model="form.password"
          :disabled="loading"
          :invalid="attempted && !form.password"
          :describedby="loginError ? 'login-error' : undefined"
        />
      </div>

      <p v-if="loginError" id="login-error" class="auth-inline-error" role="alert">{{ loginError }}</p>

      <button class="auth-submit" type="submit" :disabled="loading">
        {{ loading ? '正在登录…' : '登录' }}
      </button>
    </form>

    <div class="auth-account-switch">
      <span>还没有账号？</span><RouterLink class="auth-link" to="/auth/register">注册账号</RouterLink>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthLayout from '@/layouts/AuthLayout.vue';
import AuthPasswordInput from '@/components/AuthPasswordInput.vue';
import { useAuthStore } from '@/stores/auth';
import { useToast } from '@/composables/useToast';

const auth = useAuthStore();
const router = useRouter();
const { show } = useToast();

const loading = ref(false);
const attempted = ref(false);
const loginError = ref('');
const form = reactive({
  username: '',
  password: '',
});

const onSubmit = async () => {
  if (loading.value) return;
  attempted.value = true;
  loginError.value = '';
  if (!form.username.trim() || !form.password) {
    loginError.value = '请填写用户名和密码后登录。';
    return;
  }

  loading.value = true;
  try {
    await auth.login({ username: form.username.trim(), password: form.password });
    show('登录成功', 'success');
    await router.push(auth.isTeacher ? '/teacher/dashboard' : '/student/home');
  } catch (error: any) {
    const hint = error?.response?.data?.message ?? error?.message ?? '登录失败';
    loginError.value = hint;
  } finally {
    loading.value = false;
  }
};
</script>
