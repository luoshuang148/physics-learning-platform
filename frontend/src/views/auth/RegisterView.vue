<template>
  <AuthLayout>
    <h2 class="auth-title">创建账号</h2>
    <p class="auth-sub">接入学生与教师双角色体验</p>

    <form class="form" @submit.prevent="onSubmit">
      <label>用户名</label>
      <input v-model="form.username" placeholder="仅字母/数字/下划线" />

      <label>密码</label>
      <input v-model="form.password" type="password" placeholder="至少 6 位" />

      <label>真实姓名</label>
      <input v-model="form.realName" placeholder="例如：张三" />

      <label>班级（可选）</label>
      <input v-model="form.className" placeholder="例如：高三 (1) 班" />

      <label>角色</label>
      <select v-model="form.userType">
        <option value="STUDENT">学生</option>
        <option value="TEACHER">教师</option>
      </select>

      <template v-if="form.userType === 'TEACHER'">
        <label>教师口令</label>
        <input
          v-model="form.teacherCode"
          inputmode="numeric"
          maxlength="6"
          placeholder="请输入 6 位教师口令"
        />
        <p class="teacher-code-tip">注册教师账号需输入教师口令</p>
      </template>

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
import { upsertAccount } from '@/db/accountDb';

const TEACHER_REGISTER_CODE = '786526';

const router = useRouter();
const { visible, type, message, show } = useToast();

const loading = ref(false);
const form = reactive({
  username: '',
  password: '',
  realName: '',
  className: '',
  userType: 'STUDENT' as 'STUDENT' | 'TEACHER',
  teacherCode: '',
});

const onSubmit = async () => {
  if (!form.username || !form.password || !form.realName) {
    show('请填写必要信息', 'error');
    return;
  }

  if (form.userType === 'TEACHER') {
    const teacherCode = form.teacherCode.trim();
    if (!/^\d{6}$/.test(teacherCode)) {
      show('教师口令必须为 6 位数字', 'error');
      return;
    }
    if (teacherCode !== TEACHER_REGISTER_CODE) {
      show('教师口令错误，无法注册教师账号', 'error');
      return;
    }
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
    upsertAccount({
      userId: 0,
      username: form.username,
      userType: form.userType,
      realName: form.realName,
      className: form.className || null,
      active: true,
      createdAt: new Date().toISOString(),
      lastLogin: null,
      cachedToken: null,
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
