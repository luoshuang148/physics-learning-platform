<template>
  <AuthLayout>
    <h2 class="auth-title">创建账号</h2>


    <form class="form" @submit.prevent="onSubmit">
      <label>用户名</label>
      <input v-model="form.username" placeholder="仅字母/数字/下划线" />

      <label>密码</label>
      <input v-model="form.password" type="password" placeholder="至少 6 位" />

      <label>真实姓名</label>
      <input v-model="form.realName"  />

      <label>班级（可选）</label>
      <input v-model="form.className"  />



      <button class="neon-btn primary" type="submit" :disabled="loading">
        {{ loading ? '注册中…' : '注册' }}
      </button>
    </form>

    <RouterLink class="auth-link" to="/auth/login">已有账号？去登录</RouterLink>

    <AppToast :visible="visible" :type="type" :message="message" />
  </AuthLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthLayout from '@/layouts/AuthLayout.vue';
import AppToast from '@/components/AppToast.vue';
import { registerApi } from '@/api/auth';
import { useToast } from '@/composables/useToast';



const router = useRouter();
const { visible, type, message, show } = useToast();

const loading = ref(false);
const form = reactive({
  username: '',
  password: '',
  realName: '',
  className: '',
  userType: 'STUDENT' as 'STUDENT' | 'TEACHER',
});

const onSubmit = async () => {
  if (!form.username || !form.password || !form.realName) {
    show('请填写必要信息', 'error');
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
    show(hint, 'error');
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.teacher-code-tip {
  margin: -4px 0 2px;
  font-size: var(--font-size-sm);
  color: rgba(151, 172, 249, 0.78);
}
</style>
