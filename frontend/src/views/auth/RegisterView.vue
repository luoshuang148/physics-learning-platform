<template>
  <AuthLayout>
    <header class="auth-heading">
      <h2 class="auth-title">创建账号</h2>
    </header>

    <form class="auth-form" novalidate :aria-busy="loading" @submit.prevent="onSubmit" @input="registerError = ''">
      <div class="auth-field">
        <label for="register-username">用户名</label>
        <input id="register-username" v-model="form.username" placeholder="字母、数字或下划线" autocomplete="username" autocapitalize="none" :spellcheck="false" :disabled="loading" required />
      </div>
      <div class="auth-field">
        <label for="register-password">密码</label>
        <AuthPasswordInput id="register-password" v-model="form.password" placeholder="设置密码，至少 6 位" autocomplete="new-password" :disabled="loading" />
      </div>
      <div class="auth-field">
        <label for="register-confirm-password">确认密码</label>
        <AuthPasswordInput id="register-confirm-password" v-model="confirmPassword" placeholder="再次输入密码" autocomplete="new-password" :disabled="loading" :invalid="passwordMismatch" :describedby="passwordMismatch ? 'register-password-error' : undefined" />
      </div>
      <div class="auth-field-pair">
        <div class="auth-field">
          <label for="register-name">真实姓名</label>
          <input id="register-name" v-model="form.realName" placeholder="你的姓名" autocomplete="name" :disabled="loading" required />
        </div>
        <div class="auth-field">
          <label for="register-class">班级 <span class="auth-optional">选填</span></label>
          <input id="register-class" v-model="form.className" placeholder="如：高一（1）班" :disabled="loading" />
        </div>
      </div>

      <p v-if="passwordMismatch" id="register-password-error" class="auth-inline-error" role="alert">
        两次输入的密码不一致，请再检查一下。
      </p>
      <p v-else-if="registerError" class="auth-inline-error" role="alert">{{ registerError }}</p>

      <button class="auth-submit" type="submit" :disabled="loading">
        {{ loading ? '正在创建…' : '创建学生账号' }}
      </button>
    </form>

    <div class="auth-account-switch">
      <span>已有账号？</span><RouterLink class="auth-link" to="/auth/login">返回登录</RouterLink>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthLayout from '@/layouts/AuthLayout.vue';
import AuthPasswordInput from '@/components/AuthPasswordInput.vue';
import { registerApi } from '@/api/auth';
import { useToast } from '@/composables/useToast';

const router = useRouter();
const { show } = useToast();

const loading = ref(false);
const registerError = ref('');
const confirmPassword = ref('');
const form = reactive({
  username: '',
  password: '',
  realName: '',
  className: '',
  userType: 'STUDENT' as 'STUDENT' | 'TEACHER',
});

const passwordMismatch = computed(
  () => confirmPassword.value.length > 0 && form.password !== confirmPassword.value,
);

const onSubmit = async () => {
  if (loading.value) return;
  registerError.value = '';
  if (!form.username || !form.password || !form.realName) {
    registerError.value = '请填写用户名、密码和真实姓名。';
    return;
  }
  if (form.password !== confirmPassword.value) {
    registerError.value = '两次输入的密码不一致，请再检查一下。';
    return;
  }

  loading.value = true;
  try {
    await registerApi({
      username: form.username,
      password: form.password,
      realName: form.realName,
      className: form.className || undefined,
      userType: form.userType,
    });
    show('注册成功，请登录', 'success');
    await router.push('/auth/login');
  } catch (error: any) {
    const hint = error?.response?.data?.message ?? error?.message ?? '注册失败';
    registerError.value = hint;
  } finally {
    loading.value = false;
  }
};
</script>
