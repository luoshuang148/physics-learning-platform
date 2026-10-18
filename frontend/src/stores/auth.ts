import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { UserResponse } from '@/types/user';
import { loginApi, logoutApi } from '@/api/auth';
import { clearSessionId } from '@/utils/session';
import { getCurrentUserApi } from '@/api/user';
import type { LoginRequest } from '@/types/user';

const TOKEN_KEY = 'zhixue_token';
// Delete credentials left by older debug/account-history implementations.
localStorage.removeItem('zhixue_account_db');
localStorage.removeItem('zhixue_account_db_version');
const STUDENT_SESSION_STATE_KEY_PREFIXES = [
  'student_task_entry_event_id',
  'student_experiment_view_state',
  'student_question_view_state',
  'student_question_task_event_id',
];

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY));
  const user = ref<UserResponse | null>(null);
  const loading = ref(false);

  const isAuthed = computed(() => Boolean(token.value));
  const isTeacher = computed(() => user.value?.userType === 'TEACHER');
  const isStudent = computed(() => user.value?.userType === 'STUDENT');

  const login = async (payload: LoginRequest) => {
    loading.value = true;
    try {
      const data = await loginApi(payload);
      // 避免账号切换后复用旧会话ID，触发会话归属校验失败
      clearAllAISessionIds();
      clearStudentReportCache();
      clearTeacherReportCache();
      clearStudentSessionState();
      token.value = data.token;
      localStorage.setItem(TOKEN_KEY, data.token);
      await fetchMe();
      return data;
    } finally {
      loading.value = false;
    }
  };

  const fetchMe = async () => {
    if (!token.value) return;
    user.value = await getCurrentUserApi();
  };

  const bootstrap = async () => {
    if (!token.value) return;
    try {
      await fetchMe();
    } catch {
      await clearAuth();
    }
  };

  const clearAllAISessionIds = () => {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i);
      if (key?.startsWith('zhixue_session_id')) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => clearSessionId(key.replace(/^zhixue_session_id_?/, '') || undefined));
  };

  const clearStudentReportCache = () => {
    localStorage.removeItem('student_report_cache');
    sessionStorage.removeItem('student_report_cache');
  };

  const clearTeacherReportCache = () => {
    localStorage.removeItem('teacher_event_report_cache');
    sessionStorage.removeItem('teacher_event_report_cache');
  };

  const clearStudentSessionState = () => {
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i);
      if (!key) continue;
      if (STUDENT_SESSION_STATE_KEY_PREFIXES.some((prefix) => key === prefix || key.startsWith(`${prefix}_`))) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((key) => sessionStorage.removeItem(key));
  };

  const clearAuth = async () => {
    token.value = null;
    user.value = null;
    localStorage.removeItem(TOKEN_KEY);
    clearAllAISessionIds();
    clearStudentReportCache();
    clearTeacherReportCache();
    clearStudentSessionState();
  };

  const logout = async () => {
    try {
      if (token.value) {
        await logoutApi();
      }
    } finally {
      await clearAuth();
    }
  };

  return {
    token,
    user,
    loading,
    isAuthed,
    isTeacher,
    isStudent,
    login,
    logout,
    bootstrap,
    fetchMe,
    clearAuth,
  };
});

