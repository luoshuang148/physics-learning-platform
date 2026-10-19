<template>
  <div class="shell app-shell" :class="{ 'app-shell-wide': route.path === '/student/experiments' }">
    <header class="app-topbar">
      <div class="app-topbar-inner">
        <RouterLink class="app-brand" :to="homePath" aria-label="智学物理首页">
          <span class="app-brand-mark"><AppIcon name="spark" /></span>
          <span><strong>智学物理</strong><small>PHYSICS LAB</small></span>
        </RouterLink>
        <AppNavigation placement="top" :role="role" />
        <div ref="avatarRef" class="app-account">
          <button class="app-account-button" :class="{ open: menuOpen }" aria-label="个人菜单" :aria-expanded="menuOpen" @click="toggleUserMenu">
            <span class="app-avatar">{{ avatarLetter }}</span>
            <span class="app-account-name">{{ auth.user?.realName ?? auth.user?.username }}</span>
            <span class="app-account-chevron" aria-hidden="true">⌄</span>
          </button>
        </div>
      </div>
    </header>
    <main class="shell-main">
      <div v-if="route.path !== '/student/home'" class="app-page-heading">
        <button v-if="showBackButton" class="app-back" @click="goBack">← 返回上一步</button>
        <h1>{{ pageTitle }}</h1>
      </div>
      <slot />
    </main>
    <AppNavigation placement="bottom" :role="role" />
  </div>

  <teleport to="body">
    <transition name="popup">
      <div v-if="menuOpen" class="user-menu user-menu-floating" :style="userMenuStyle">
        <div class="user-menu-header">
          <div class="user-menu-avatar">{{ avatarLetter }}</div>
          <div class="user-menu-info">
            <strong>{{ auth.user?.realName ?? auth.user?.username }}</strong>
            <span>{{ auth.user?.username }}</span>
          </div>
        </div>
        <div class="user-menu-body">
          <div class="user-meta-row">
            <span class="meta-label">角色</span>
            <span class="meta-badge" :class="auth.user?.userType === 'TEACHER' ? 'teacher' : 'student'">
              {{ auth.user?.userType === 'TEACHER' ? '教师' : '学生' }}
            </span>
          </div>
          <div class="user-meta-row" v-if="auth.user?.className">
            <span class="meta-label">班级</span>
            <span>{{ auth.user.className }}</span>
          </div>
        </div>
        <button class="user-menu-action" @click="openSettingsPanel">设置</button>
        <div v-if="settingsOpen" class="user-settings-panel">
          <div class="user-settings-head">
            <span>字号</span>
            <strong>{{ fontSizeLabel }}</strong>
          </div>
          <input
            v-model="fontSize"
            class="font-size-slider"
            type="range"
            :min="FONT_SIZE_MIN"
            :max="FONT_SIZE_MAX"
            step="1"
          />
          <div class="font-size-scale">
            <span>偏小</span>
            <span>标准</span>
            <span>偏大</span>
            <span>最大</span>
          </div>
        </div>
        <button class="user-menu-action" @click="openProfileEditor">修改个人信息</button>
        <button class="user-menu-logout" @click="onLogout">退出登录</button>
      </div>
    </transition>
  </teleport>

  <teleport to="body">
    <div v-if="profileModalOpen" class="profile-modal-mask" @click.self="closeProfileEditor">
      <div class="profile-modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
        <div class="profile-modal-head">
          <div>
            <h3 id="profile-title">修改个人信息</h3>
          </div>
          <button class="profile-close" @click="closeProfileEditor">×</button>
        </div>

        <form class="profile-form" @submit.prevent="submitProfileEditor">
          <label>用户名</label>
          <input v-model="profileForm.username" placeholder="请输入用户名" />

          <label>新密码</label>
          <input v-model="profileForm.password" type="password" placeholder="" />

          <label>真实姓名</label>
          <input v-model="profileForm.realName" placeholder="请输入真实姓名" />

          <label>班级</label>
          <input v-model="profileForm.className" placeholder="" />

          <div class="profile-actions">
            <button type="button" class="secondary-btn" @click="closeProfileEditor">取消</button>
            <button type="submit" class="primary-btn" :disabled="profileSaving">
              {{ profileSaving ? '保存中…' : '保存修改' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </teleport>

</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { useRoute, useRouter } from 'vue-router';
import { updateCurrentUserApi } from '@/api/user';
import AppNavigation from '@/components/AppNavigation.vue';
import AppIcon from '@/components/AppIcon.vue';

const FONT_SIZE_STORAGE_KEY = 'zxwl-font-scale-v2';
const FONT_SIZE_MIN = 13;
const FONT_SIZE_MAX = 18;
const DEFAULT_FONT_SIZE = 15;

const props = defineProps<{ subtitle: string }>();

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const role = computed(() => auth.isTeacher ? 'TEACHER' : 'STUDENT');
const homePath = computed(() => auth.isTeacher ? '/teacher/dashboard' : '/student/home');
const pageTitle = computed(() => route.path === '/student/practice' ? '错题补练'
  : route.path === '/student/task' ? '我的作业' : props.subtitle.split(' · ')[0]);
const menuOpen = ref(false);
const settingsOpen = ref(false);
const avatarRef = ref<HTMLElement | null>(null);
const userMenuStyle = ref<Record<string, string>>({});
const fontSize = ref(DEFAULT_FONT_SIZE);
const profileModalOpen = ref(false);
const profileSaving = ref(false);
const profileForm = reactive({
  username: '',
  password: '',
  realName: '',
  className: '',
});

const avatarLetter = computed(() => {
  const name = auth.user?.realName ?? auth.user?.username ?? '?';
  return name.charAt(0).toUpperCase();
});

const fontSizeLabel = computed(() => `${fontSize.value}px`);

const showBackButton = computed(() => route.path !== homePath.value);

const goBack = () => {
  if (window.history.length > 1) router.back();
  else router.push(homePath.value);
};

const syncProfileForm = () => {
  profileForm.username = auth.user?.username ?? '';
  profileForm.password = '';
  profileForm.realName = auth.user?.realName ?? '';
  profileForm.className = auth.user?.className ?? '';
};

const openProfileEditor = () => {
  syncProfileForm();
  settingsOpen.value = false;
  menuOpen.value = false;
  profileModalOpen.value = true;
};

const openSettingsPanel = () => {
  settingsOpen.value = !settingsOpen.value;
  if (menuOpen.value) {
    nextTick(updateUserMenuPosition);
  }
};

const updateUserMenuPosition = () => {
  if (!menuOpen.value || !avatarRef.value) return;
  const rect = avatarRef.value.getBoundingClientRect();
  userMenuStyle.value = {
    right: `${Math.max(12, window.innerWidth - rect.right)}px`,
    top: `${rect.bottom + 10}px`,
    width: `${Math.min(320, window.innerWidth - 24)}px`,
    maxHeight: `${Math.max(120, window.innerHeight - rect.bottom - 22)}px`,
  };
};

const toggleUserMenu = async () => {
  menuOpen.value = !menuOpen.value;

  if (menuOpen.value) {
    const savedFontSize = Number(window.localStorage.getItem(FONT_SIZE_STORAGE_KEY));
    fontSize.value = Number.isFinite(savedFontSize)
      ? Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, savedFontSize))
      : DEFAULT_FONT_SIZE;
    await nextTick();
    updateUserMenuPosition();
    return;
  }

  settingsOpen.value = false;
};

const closeProfileEditor = () => {
  profileModalOpen.value = false;
  profileSaving.value = false;
};

const submitProfileEditor = async () => {
  if (!profileForm.username.trim() || !profileForm.realName.trim()) {
    window.alert('用户名和真实姓名不能为空');
    return;
  }

  profileSaving.value = true;
  try {
    const updatedUser = await updateCurrentUserApi({
      username: profileForm.username.trim(),
      password: profileForm.password.trim() || undefined,
      realName: profileForm.realName.trim(),
      className: profileForm.className.trim() || null,
    });
    if(profileForm.password.trim()) {
      await auth.clearAuth();profileModalOpen.value=false;await router.replace('/auth/login');return;
    }
    auth.user = updatedUser;
    profileModalOpen.value = false;

    if (updatedUser.userType === 'TEACHER') {
      await router.push('/teacher/dashboard');
    } else {
      await router.push('/student/home');
    }
  } catch (error: any) {
    window.alert(error?.response?.data?.message ?? '保存失败');
  } finally {
    profileSaving.value = false;
  }
};

const onLogout = async () => {
  menuOpen.value = false;
  await auth.logout();
  await router.push('/auth/login');
};

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as Node | null;
  if (!target) return;
  const clickedInsideAvatar = avatarRef.value?.contains(target);
  const clickedInsideMenu = target instanceof Element && Boolean(target.closest('.user-menu'));

  if (!clickedInsideAvatar && !clickedInsideMenu) {
    menuOpen.value = false;
    settingsOpen.value = false;
  }
};

const handleWindowChange = () => {
  updateUserMenuPosition();
};

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false;
    settingsOpen.value = false;
  },
);

watch(fontSize, (value) => {
  const normalized = Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, value));
  document.documentElement.style.setProperty('--app-font-size', `${normalized}px`);
  window.localStorage.setItem(FONT_SIZE_STORAGE_KEY, String(normalized));
  nextTick(updateUserMenuPosition);
});

onMounted(() => {
  const savedFontSize = Number(window.localStorage.getItem(FONT_SIZE_STORAGE_KEY));
  fontSize.value = Number.isFinite(savedFontSize)
    ? Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, savedFontSize))
    : DEFAULT_FONT_SIZE;
  document.documentElement.style.setProperty('--app-font-size', `${fontSize.value}px`);
  document.addEventListener('click', handleClickOutside);
  window.addEventListener('resize', handleWindowChange);
  window.addEventListener('scroll', handleWindowChange, true);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside);
  window.removeEventListener('resize', handleWindowChange);
  window.removeEventListener('scroll', handleWindowChange, true);
});
</script>

<style scoped>

:deep(*) { font-size: inherit; }
.shell.app-shell { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg); padding: 0; }
.app-topbar { background: #fff; border-bottom: 1px solid #e5e9ed; }
.app-topbar-inner { max-width: 1244px; min-height: 76px; margin: 0 auto; padding: 0 32px; display: flex; align-items: center; gap: 24px; }
.app-brand { display: flex; align-items: center; gap: 10px; color: var(--text); text-decoration: none; flex-shrink: 0; }
.app-brand-mark { width: 34px; height: 34px; border: 1px solid #1d715b; border-radius: 10px; color: #1d715b; display: grid; place-items: center; }
.app-brand strong { font-size: 1.125rem; letter-spacing: .03em; }
.app-brand small { display: block; margin-top: 1px; color: #94a3b8; font-size: .6rem; letter-spacing: .17em; }
.app-topbar-inner > :deep(.app-navigation-top) { flex: 1; }
.app-account { margin-left: auto; min-width: 0; }
.app-account-button { border: 1px solid transparent; background: none; padding: 5px 0 5px 5px; display: flex; align-items: center; gap: 9px; cursor: pointer; border-radius: 7px; color: var(--text); }
.app-account-button:hover, .app-account-button.open { background: #f4f8f5; }
.app-avatar { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: #e5eeeb; color: #326d5a; font-size: .8125rem; flex-shrink: 0; }
.app-account-name { font-size: .8125rem; max-width: 120px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.app-account-chevron { color: #94a3b8; font-size: .8125rem; }
.app-shell > .shell-main { width: 100%; max-width: 1244px; margin: 0 auto; padding: 36px 32px 48px; gap: 24px; overflow: visible; animation: none; }
.app-shell-wide > .shell-main { max-width: none; }
.app-page-heading { display: grid; gap: 18px; }
.app-back { justify-self: start; border: 0; background: none; padding: 0; color: #64748b; font-size: .8125rem; cursor: pointer; }
.app-page-heading h1 { font-size: 1.75rem; font-weight: 600; letter-spacing: -.03em; overflow-wrap: anywhere; }
.app-brand:focus-visible, .app-account-button:focus-visible, .app-back:focus-visible { outline: 2px solid #1d715b; outline-offset: 3px; }
.user-menu { position: fixed; z-index: 1000; border: 1px solid #e2e8f0; border-radius: 12px; box-shadow: 0 16px 40px #0f172a1f; overflow-y: auto; background: #fff; color: var(--text); }
.user-menu-header { display: flex; align-items: center; gap: 10px; padding: 14px; border-bottom: 1px solid #eef2f7; }
.user-menu-avatar { width: 34px; height: 34px; border-radius: 10px; background: #e5eeeb; color: #326d5a; display: grid; place-items: center; flex-shrink: 0; }
.user-menu-info { display: grid; min-width: 0; }
.user-menu-info strong { font-size: .875rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-menu-info span { color: var(--text-muted); font-size: .75rem; overflow-wrap: anywhere; }
.user-menu-body { padding: 12px 14px; display: grid; gap: 8px; }
.user-meta-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: .75rem; color: var(--text-muted); }
.meta-badge { padding: 2px 7px; border-radius: 5px; color: #1d715b; background: #edf6f2; font-size: .75rem; }
.user-menu-action, .user-menu-logout { width: 100%; padding: 12px 14px; border: 0; border-top: 1px solid #eef2f7; background: #fff; text-align: left; font-size: .8125rem; cursor: pointer; }
.user-menu-action { color: #475569; }
.user-menu-action:hover { background: #f5f8f6; }
.user-menu-logout { color: #b91c1c; }
.user-menu-logout:hover { background: #fef2f2; }
.user-settings-panel { padding: 14px; border-top: 1px solid #eef2f7; background: #f8fafc; display: grid; gap: 10px; }
.user-settings-head { display: flex; align-items: center; justify-content: space-between; font-size: .8125rem; }
.user-settings-head strong { color: #1d715b; }
.font-size-slider { width: 100%; accent-color: #1d715b; cursor: pointer; }
.font-size-scale { display: flex; justify-content: space-between; font-size: .6875rem; color: var(--text-muted); }
.profile-modal-mask { position: fixed; inset: 0; z-index: 1100; display: grid; place-items: center; background: #0f172a66; padding: 20px; }
.profile-modal { width: min(480px, 100%); max-height: calc(100dvh - 40px); overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 12px; background: #fff; box-shadow: 0 16px 40px #0f172a1f; color: var(--text); }
.profile-modal-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 18px 20px 14px; border-bottom: 1px solid #eef2f7; }
.profile-modal-head h3 { margin: 0; font-size: 1.25rem; }
.profile-close { width: 32px; height: 32px; border: 1px solid #e2e8f0; border-radius: 8px; background: #fff; color: var(--text-muted); font-size: 1.25rem; cursor: pointer; }
.profile-form { display: grid; gap: 10px; padding: 18px 20px 20px; }
.profile-form label { font-size: .8125rem; color: var(--text-muted); }
.profile-form input { width: 100%; padding: 11px 12px; border-radius: 8px; border: 1px solid #e2e8f0; background: #fff; color: var(--text); font: inherit; }
.profile-form input:focus { outline: 2px solid #1d715b; outline-offset: 1px; }
.profile-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.secondary-btn, .primary-btn { padding: 10px 16px; border-radius: 8px; font: inherit; font-size: .8125rem; cursor: pointer; }
.secondary-btn { border: 1px solid #e2e8f0; background: #fff; color: #475569; }
.primary-btn { border: 1px solid #1d715b; background: #1d715b; color: #fff; }
.primary-btn:disabled { opacity: .65; cursor: not-allowed; }
.popup-enter-active, .popup-leave-active { transition: opacity .15s, transform .15s; }
.popup-enter-from, .popup-leave-to { opacity: 0; transform: translateY(-5px); }
@media (max-width: 1000px) {
  .app-topbar-inner { gap: 12px; padding: 0 24px; }
  .app-account-name, .app-brand small { display: none; }
  .app-brand strong { font-size: 1rem; }
}
@media (max-width: 760px) {
  .app-topbar-inner { min-height: 65px; padding: 0 20px; }
  .app-brand-mark { width: 30px; height: 30px; border-radius: 8px; }
  .app-avatar { width: 30px; height: 30px; }
  .app-shell > .shell-main { padding: 26px 20px calc(100px + env(safe-area-inset-bottom, 0px)); gap: 20px; }
  .app-page-heading h1 { font-size: 1.5rem; }
  .app-back { font-size: .75rem; }
}
</style>
