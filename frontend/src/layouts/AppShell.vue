<template>
  <div class="shell">
    <div class="shell-backdrop" :class="{ visible: !sidebarCollapsed }" @click="sidebarCollapsed = true" />
    <aside
      class="sidebar"
      :class="{ collapsed: sidebarCollapsed, 'sidebar-auto-transition': autoSidebarTransition }"
      @transitionend="handleSidebarTransitionEnd"
    >
      <div class="sidebar-brand">
        <div class="brand-orbit">
          <div class="brand-core" />
          <div class="brand-ring brand-ring-a" />
          <div class="brand-ring brand-ring-b" />
        </div>
        <div class="brand-text">
          <span class="brand-name">智学物理</span>
          <span class="brand-sub">物理教学工作台</span>
        </div>
        <button
          class="sidebar-toggle"
          type="button"
          :aria-label="sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="toggleSidebar"
        >
          {{ sidebarCollapsed ? '›' : '‹' }}
        </button>
      </div>

      <nav class="sidebar-nav">
        <RouterLink
          v-for="item in links"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          active-class="is-active"
          :title="sidebarCollapsed ? item.label : ''"
        >
          <span class="nav-glow" />
          <span class="nav-short">{{ item.label.slice(0, 1) }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-footer sidebar-footer-anchor">
        <div class="status-card">
          <span class="status-kicker">当前空间</span>
          <strong class="status-title">{{ auth.user?.userType === 'TEACHER' ? '教师控制台' : '学生学习台' }}</strong>
          <span class="status-meta">{{ subtitle }}</span>
        </div>

        <div class="avatar-wrap" ref="avatarRef">
          <button class="avatar-btn" @click="toggleUserMenu" :class="{ open: menuOpen }">
            <span class="avatar-letter">{{ avatarLetter }}</span>
            <div class="avatar-info">
              <span class="avatar-name">{{ auth.user?.realName ?? auth.user?.username }}</span>
              <span class="avatar-role">{{ auth.user?.userType === 'TEACHER' ? '教师' : '学生' }}</span>
            </div>
            <span class="avatar-chevron" :class="{ open: menuOpen }">›</span>
          </button>
        </div>
      </div>
    </aside>

    <main class="shell-main">
      <div class="main-header">
        <div class="main-header-left">
          <button class="mobile-menu-btn" type="button" aria-label="打开导航" @click="sidebarCollapsed = !sidebarCollapsed">菜单</button>
          <button v-if="showBackButton" class="back-btn" @click="goBack">
            <span class="back-btn-arrow">←</span>
            <span>返回上一步</span>
          </button>
          <div class="main-header-copy">
            <div class="shell-subtitle">{{ subtitle }}</div>
          </div>
        </div>
        <div class="main-header-right">
          <div class="main-badge">{{ auth.user?.userType === 'TEACHER' ? '教师端' : '学生端' }}</div>
        </div>
      </div>
      <slot />
    </main>
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
      <div class="profile-modal">
        <div class="profile-modal-head">
          <div>
            <h3>修改个人信息</h3>
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
interface Link {
  label: string;
  to: string;
}

const FONT_SIZE_STORAGE_KEY = 'zxwl-font-scale-v2';
const FONT_SIZE_MIN = 13;
const FONT_SIZE_MAX = 18;
const DEFAULT_FONT_SIZE = 15;

const props = defineProps<{
  subtitle: string;
  links: Link[];
}>();
void props;

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const menuOpen = ref(false);
const settingsOpen = ref(false);
const avatarRef = ref<HTMLElement | null>(null);
const userMenuStyle = ref<Record<string, string>>({});
const fontSize = ref(DEFAULT_FONT_SIZE);
const profileModalOpen = ref(false);
const profileSaving = ref(false);
const sidebarCollapsed = ref(false);
const autoSidebarTransition = ref(false);
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




const showBackButton = computed(() => {
  const path = route.path;
  return ![
    '/student/home',
    '/teacher/dashboard',
    '/preview/student/home',
    '/preview/teacher/dashboard',
  ].includes(path);
});

const goBack = () => {
  if (window.history.length > 1) {
    router.back();
    return;
  }

  if (route.path.startsWith('/teacher') || route.path.startsWith('/preview/teacher')) {
    router.push('/teacher/dashboard');
    return;
  }

  if (route.path.startsWith('/student') || route.path.startsWith('/preview/student')) {
    router.push('/student/home');
  }
};

const syncViewportAfterLayoutChange = () => {
  if (typeof window === 'undefined') {
    return;
  }

  window.requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'));
  });
};

let lastMobileViewport = typeof window !== 'undefined' && window.innerWidth <= 1100;
const initializeSidebarState = () => {
  autoSidebarTransition.value = false;
  lastMobileViewport = window.innerWidth <= 1100;
  sidebarCollapsed.value = lastMobileViewport;
};
const handleViewportResize = () => {
  const mobile = window.innerWidth <= 1100;
  if (mobile === lastMobileViewport) return;
  lastMobileViewport = mobile;
  sidebarCollapsed.value = mobile;
};

const toggleSidebar = () => {
  autoSidebarTransition.value = false;
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const handleSidebarTransitionEnd = (event: TransitionEvent) => {
  if (event.target !== event.currentTarget) {
    return;
  }
  if (event.propertyName !== 'width' && event.propertyName !== 'max-height') {
    return;
  }
  autoSidebarTransition.value = false;
  syncViewportAfterLayoutChange();
  updateUserMenuPosition();
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
  if (!menuOpen.value || !avatarRef.value) {
    return;
  }

  const rect = avatarRef.value.getBoundingClientRect();
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;
  const menuWidth = Math.min(320, viewportWidth - 24);
  const gap = viewportWidth <= 1100 ? 10 : 12;
  const isCompact = viewportWidth <= 1100;

  let left = isCompact ? rect.left : rect.right + gap;
  left = Math.max(12, Math.min(left, viewportWidth - menuWidth - 12));

  let top = isCompact ? rect.bottom + gap : rect.bottom - (settingsOpen.value ? 320 : 248);
  const estimatedHeight = settingsOpen.value ? 320 : 248;

  if (isCompact) {
    if (top + estimatedHeight > viewportHeight - 12) {
      top = Math.max(12, rect.top - estimatedHeight - gap);
    }
  } else {
    top = Math.max(24, Math.min(top, viewportHeight - estimatedHeight - 24));
  }

  userMenuStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    width: `${menuWidth}px`,
    transform: 'none',
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
    if (window.innerWidth <= 1100) sidebarCollapsed.value = true;
  },
);

watch(sidebarCollapsed, () => {
  nextTick(() => {
    updateUserMenuPosition();
    syncViewportAfterLayoutChange();
  });
});

watch(fontSize, (value) => {
  const normalized = Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, value));
  document.documentElement.style.setProperty('--app-font-size', `${normalized}px`);
  window.localStorage.setItem(FONT_SIZE_STORAGE_KEY, String(normalized));
  nextTick(updateUserMenuPosition);
});

onMounted(() => {
  initializeSidebarState();
  const savedFontSize = Number(window.localStorage.getItem(FONT_SIZE_STORAGE_KEY));
  fontSize.value = Number.isFinite(savedFontSize)
    ? Math.min(FONT_SIZE_MAX, Math.max(FONT_SIZE_MIN, savedFontSize))
    : DEFAULT_FONT_SIZE;
  document.documentElement.style.setProperty('--app-font-size', `${fontSize.value}px`);
  document.addEventListener('click', handleClickOutside);
  window.addEventListener('resize', handleWindowChange);
  window.addEventListener('resize', handleViewportResize);
  window.addEventListener('scroll', handleWindowChange, true);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside);
  window.removeEventListener('resize', handleWindowChange);
  window.removeEventListener('resize', handleViewportResize);
  window.removeEventListener('scroll', handleWindowChange, true);
});
</script>

<style scoped>
:deep(*) {
  font-size: inherit;
}

.shell {
  min-height: 100vh;
  display: flex;
  position: relative;
  background:
    radial-gradient(circle at 18% 12%, rgba(79, 232, 191, 0.08), transparent 18%),
    radial-gradient(circle at 88% 10%, rgba(255, 150, 106, 0.08), transparent 18%),
    linear-gradient(180deg, #050814 0%, #08101d 46%, #060a16 100%);
}

.shell-backdrop {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(rgba(120, 146, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(120, 146, 255, 0.05) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(circle at center, black 42%, transparent 88%);
  opacity: 0.35;
}

.sidebar {
  width: 260px;
  flex-shrink: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 28px 18px 20px;
  background: linear-gradient(180deg, rgba(9, 14, 28, 0.96) 0%, rgba(6, 10, 22, 0.98) 100%);
  border-right: 1px solid rgba(127, 153, 255, 0.12);
  box-shadow: inset -1px 0 0 rgba(255,255,255,0.03);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  backdrop-filter: blur(16px);
  transition: width 0.36s cubic-bezier(0.22, 1, 0.36, 1), padding 0.36s cubic-bezier(0.22, 1, 0.36, 1);
}

.sidebar.sidebar-auto-transition {
  transition: width 0.56s cubic-bezier(0.22, 1, 0.36, 1), padding 0.56s cubic-bezier(0.22, 1, 0.36, 1);
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top, rgba(87, 240, 207, 0.13), transparent 22%),
    linear-gradient(180deg, transparent, rgba(126, 87, 255, 0.08));
  pointer-events: none;
}

.sidebar-brand {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 20px;
  height: 66px;
  min-height: 66px;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
  margin-bottom: 18px;
}

.sidebar-toggle {
  margin-left: auto;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  border: 1px solid rgba(151, 172, 249, 0.2);
  background: rgba(255, 255, 255, 0.04);
  color: #dce7ff;
  font-size: 22px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.sidebar-toggle:hover {
  border-color: rgba(90, 243, 211, 0.35);
  color: #8ef6df;
  background: rgba(90, 243, 211, 0.12);
}

.brand-orbit {
  position: relative;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
}

.brand-core {
  position: absolute;
  inset: 11px;
  border-radius: 999px;
  background: linear-gradient(135deg, #61f7d0, #7da8ff);
  box-shadow: 0 0 20px rgba(97, 247, 208, 0.45);
}

.brand-ring {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  border: 1px solid rgba(125, 168, 255, 0.32);
}

.brand-ring-a {
  transform: rotate(18deg) scaleX(1.12);
}

.brand-ring-b {
  transform: rotate(-28deg) scaleY(0.72);
  border-color: rgba(97, 247, 208, 0.35);
}

.brand-text {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  gap: 2px;
  opacity: 1;
  transform: translateX(0);
  transition: opacity 0.14s ease 0.14s, transform 0.14s ease 0.14s;
}

.brand-name {
  font-size: var(--font-size-lg);
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #f5fbff;
  line-height: 1;
}

.brand-sub {
  font-size: var(--font-size-xs);
  color: #8ba0d4;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  position: relative;
}

.nav-item {
  text-decoration: none;
  display: flex;
  align-items: center;
  height: 50px;
  min-height: 50px;
  padding: 0 16px;
  border-radius: 14px;
  border: 1px solid transparent;
  color: #95a8d6;
  font-size: var(--font-size-md);
  font-weight: 600;
  transition: all 0.22s ease;
  position: relative;
  overflow: hidden;
  background: rgba(255,255,255,0.015);
}

.nav-glow {
  position: absolute;
  inset: -40% auto -40% -30%;
  width: 60px;
  background: linear-gradient(90deg, transparent, rgba(97,247,208,0.18), transparent);
  opacity: 0;
  transition: opacity 0.22s ease;
}

.nav-item:hover {
  color: #f3f8ff;
  border-color: rgba(116, 145, 255, 0.12);
  transform: translateX(2px);
}

.nav-item:hover .nav-glow,
.nav-item.is-active .nav-glow {
  opacity: 1;
}

.nav-item.is-active {
  color: #67f2d1;
  background: linear-gradient(90deg, rgba(97,247,208,0.13), rgba(109,138,255,0.08));
  border-color: rgba(97, 247, 208, 0.26);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.02), 0 10px 20px rgba(4, 10, 24, 0.28);
  font-weight: 700;
}

.nav-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 4px 4px 0;
  background: linear-gradient(180deg, #6ff9d7, #7e8dff);
}

.nav-label {
  position: relative;
  z-index: 1;
}

.nav-short {
  display: none;
  position: relative;
  z-index: 1;
}

.sidebar.collapsed {
  width: 96px;
  padding: 28px 10px 16px;
}

.sidebar.collapsed .sidebar-brand {
  justify-content: center;
  gap: 0;
  padding: 8px 8px 20px;
  margin-bottom: 18px;
}

.sidebar.collapsed .brand-orbit {
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
}

.sidebar.collapsed .brand-text {
  visibility: hidden;
  width: 0;
  min-width: 0;
  flex: 0 0 0;
  opacity: 0;
  pointer-events: none;
  transform: translateX(-4px);
  transition: opacity 0.08s ease, transform 0.08s ease, visibility 0s linear 0.08s;
}

.sidebar.collapsed .sidebar-toggle {
  margin-left: 0;
  margin-top: 0;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  font-size: 26px;
}

.sidebar.collapsed .sidebar-nav {
  gap: 6px;
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 0;
}

.sidebar.collapsed .nav-label {
  display: none;
}

.sidebar.collapsed .nav-short {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: inherit;
}

.sidebar.collapsed .guide-entry-btn,
.sidebar.collapsed .status-card {
  display: none;
}

.sidebar.collapsed .sidebar-footer {
  border-top: none;
  padding-top: 4px;
}

.sidebar.collapsed .avatar-btn {
  justify-content: center;
  padding: 8px;
}

.sidebar.collapsed .avatar-info,
.sidebar.collapsed .avatar-chevron {
  display: none;
}

.sidebar-footer {
  border-top: 1px solid rgba(151, 172, 249, 0.1);
  padding-top: 14px;
  position: relative;
  display: grid;
  gap: 12px;
}

.guide-entry-btn {
  width: 100%;
  min-height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(97, 247, 208, 0.2);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.12), rgba(125, 168, 255, 0.08));
  color: #86f7e1;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.guide-entry-btn:hover {
  border-color: rgba(97, 247, 208, 0.35);
  color: #c4fff3;
  box-shadow: 0 10px 22px rgba(5, 12, 28, 0.35);
  transform: translateY(-1px);
}

.status-card {
  padding: 12px 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(123, 148, 255, 0.14);
  background: linear-gradient(180deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02));
  display: grid;
  gap: 4px;
}

.status-kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7e92c8;
}

.status-title {
  color: #f4f8ff;
  font-size: var(--font-size-md);
}

.status-meta {
  color: #9db0db;
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.avatar-wrap {
  position: relative;
}

.avatar-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid transparent;
  background: rgba(255,255,255,0.025);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.avatar-btn:hover,
.avatar-btn.open {
  background: linear-gradient(90deg, rgba(97,247,208,0.09), rgba(125,168,255,0.05));
  border-color: rgba(97, 247, 208, 0.18);
}

.avatar-letter {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(97,247,208,0.28), rgba(125,168,255,0.28));
  border: 1px solid rgba(97,247,208,0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-md);
  font-weight: 800;
  color: #73f6d8;
  flex-shrink: 0;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.12);
}

.avatar-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  min-width: 0;
}

.avatar-name {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: #f4f9ff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.avatar-role {
  font-size: var(--font-size-xs);
  color: #8ea2d2;
}

.avatar-chevron {
  font-size: 16px;
  color: #8ea2d2;
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.avatar-chevron.open { transform: rotate(90deg); }

.user-menu {
  background: linear-gradient(160deg, rgba(13, 19, 38, 0.99), rgba(9, 13, 28, 0.99));
  border: 1px solid rgba(151, 172, 249, 0.22);
  border-radius: 16px;
  box-shadow: 16px 0 40px rgba(4, 6, 18, 0.52);
  overflow: hidden;
  z-index: 1000;
  backdrop-filter: blur(14px);
  max-height: min(80vh, 560px);
  overflow-y: auto;
}

.user-menu-floating {
  position: fixed;
}

.user-menu-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 12px 8px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.1);
}

.user-menu-avatar {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(97,247,208,0.3), rgba(125,168,255,0.3));
  border: 1px solid rgba(97,247,208,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 800;
  color: #6af2d2;
  flex-shrink: 0;
}

.user-menu-info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
}

.user-menu-info strong {
  font-size: var(--font-size-sm);
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-menu-info span {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.user-menu-body {
  padding: 10px 14px;
  display: grid;
  gap: 8px;
}

.user-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.meta-badge {
  padding: 2px 7px;
  border-radius: 999px;
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.meta-badge.teacher {
  background: rgba(142, 123, 255, 0.2);
  border: 1px solid rgba(142, 123, 255, 0.45);
  color: #b8a9ff;
}

.meta-badge.student {
  background: rgba(90, 243, 211, 0.12);
  border: 1px solid rgba(90, 243, 211, 0.35);
  color: var(--brand);
}

.user-menu-action,
.user-menu-logout {
  width: 100%;
  padding: 12px 14px;
  border: none;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s ease;
}

.user-menu-action {
  background: rgba(90, 243, 211, 0.06);
  border-top: 1px solid rgba(151, 172, 249, 0.08);
  color: var(--brand);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.user-menu-action:hover {
  background: rgba(90, 243, 211, 0.13);
}

.user-settings-panel {
  padding: 14px;
  border-top: 1px solid rgba(151, 172, 249, 0.08);
  background: linear-gradient(180deg, rgba(97, 247, 208, 0.05), rgba(125, 168, 255, 0.03));
  display: grid;
  gap: 10px;
}

.user-settings-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--font-size-sm);
  color: var(--text);
}

.user-settings-head strong {
  color: var(--brand);
  font-size: var(--font-size-md);
}

.font-size-slider {
  width: 100%;
  accent-color: #5af3d3;
  cursor: pointer;
}

.font-size-scale {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.font-size-scale span:nth-child(2),
.font-size-scale span:nth-child(3) {
  text-align: center;
}

.font-size-scale span:last-child {
  text-align: right;
}

.user-menu-logout {
  background: rgba(255, 107, 145, 0.06);
  border-top: 1px solid rgba(151, 172, 249, 0.08);
  color: var(--danger);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.user-menu-logout:hover {
  background: rgba(255, 107, 145, 0.13);
}

.shell-main {
  flex: 1;
  min-width: 0;
  padding: 26px 28px 30px;
  position: relative;
  overflow: hidden;
  transition: padding 0.36s cubic-bezier(0.22, 1, 0.36, 1);
}

.main-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.main-header-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(151, 172, 249, 0.18);
  background: rgba(255, 255, 255, 0.04);
  color: #dce7ff;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.back-btn:hover {
  border-color: rgba(90, 243, 211, 0.3);
  color: #8ef6df;
  background: rgba(90, 243, 211, 0.08);
}

.back-btn-arrow {
  font-size: 14px;
  line-height: 1;
}

.main-kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #7f96ce;
}

.shell-subtitle {
  margin-top: 6px;
  font-size: clamp(1.9rem, 3vw, 2.2rem);
  font-weight: 800;
  color: #f4f8ff;
  letter-spacing: 0.02em;
}

.main-badge {
  padding: 9px 14px;
  border-radius: 999px;
  border: 1px solid rgba(97, 247, 208, 0.18);
  background: rgba(97,247,208,0.08);
  color: #73f6d8;
  font-size: var(--font-size-xs);
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .shell {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    position: relative;
    border-right: none;
    border-bottom: 1px solid rgba(127, 153, 255, 0.12);
    transition: max-height 0.36s cubic-bezier(0.22, 1, 0.36, 1), padding 0.36s cubic-bezier(0.22, 1, 0.36, 1);
    max-height: 100vh;
  }

  .sidebar.sidebar-auto-transition {
    transition: max-height 0.56s cubic-bezier(0.22, 1, 0.36, 1), padding 0.56s cubic-bezier(0.22, 1, 0.36, 1);
  }

  .sidebar.collapsed {
    width: 100%;
    max-height: 96px;
    padding: 18px 16px 12px;
  }

  .sidebar.collapsed .sidebar-brand {
    justify-content: space-between;
    margin-bottom: 0;
    border-bottom: none;
    padding: 0;
  }

  .sidebar.collapsed .brand-text {
    display: flex;
  }

  .sidebar.collapsed .sidebar-toggle {
    margin: 0;
  }

  .sidebar.collapsed .sidebar-nav,
  .sidebar.collapsed .sidebar-footer {
    display: none;
  }

  .nav-short {
    display: none !important;
  }

  .main-header,
  .main-header-left {
    flex-direction: column;
    align-items: stretch;
  }

  .main-badge {
    align-self: flex-start;
  }
}

.profile-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
  background: rgba(4, 6, 18, 0.72);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.profile-modal {
  width: min(100%, 460px);
  border-radius: 18px;
  border: 1px solid rgba(90, 243, 211, 0.2);
  background: linear-gradient(160deg, rgba(20, 25, 50, 0.98), rgba(12, 16, 34, 0.98));
  box-shadow: 0 24px 70px rgba(4, 6, 18, 0.55);
  overflow: hidden;
}

.guide-modal {
  width: min(100%, 980px);
  max-height: min(88vh, 920px);
  display: flex;
  flex-direction: column;
}

.guide-modal-head {
  flex-shrink: 0;
}

.guide-modal-body {
  padding: 18px 20px 20px;
  overflow-y: auto;
}

.guide-markdown {
  color: #dbe7ff;
  font-size: var(--font-size-sm);
  line-height: 1.75;
}

.guide-markdown :deep(h1),
.guide-markdown :deep(h2),
.guide-markdown :deep(h3),
.guide-markdown :deep(h4) {
  color: #f4f8ff;
  margin: 1.8rem 0 0.8rem;
  line-height: 1.4;
}

.guide-markdown :deep(h1),
.guide-markdown :deep(h2) {
  text-align: center;
  margin-top: 2.2rem;
  margin-bottom: 2rem;
}

.guide-markdown :deep(h3) {
  margin-top: 1.6rem;
  margin-bottom: 0.8rem;
}

.guide-markdown :deep(h1) {
  font-size: 2.2rem;
  background: linear-gradient(135deg, #f4f8ff, #7da8ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.guide-markdown :deep(h2) {
  font-size: 1.8rem;
  color: #86f7e1;
}

.guide-markdown :deep(p) {
  margin: 0.45rem 0;
}

.guide-markdown :deep(ul),
.guide-markdown :deep(ol) {
  padding-left: 1.4rem;
  margin: 0.5rem 0;
}

.guide-markdown :deep(li) {
  margin: 0.2rem 0;
}

.guide-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 0.7rem 0;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(151, 172, 249, 0.14);
}

.guide-markdown :deep(th),
.guide-markdown :deep(td) {
  border: 1px solid rgba(151, 172, 249, 0.14);
  padding: 8px 10px;
  text-align: left;
}

.guide-markdown :deep(code) {
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(125, 168, 255, 0.13);
  color: #c4d8ff;
}

.guide-markdown :deep(strong) {
  color: #f6fbff;
}

/* 教师端特有的警示红样式 */
.is-teacher-guide .guide-markdown :deep(blockquote) {
  border-left: 4px solid #ff6b91;
  background: rgba(255, 107, 145, 0.1);
  color: #ff8fa3;
  padding: 12px 16px;
  border-radius: 8px;
  margin: 1.5rem 0;
}

.is-teacher-guide .guide-markdown :deep(blockquote p) {
  color: #ff8fa3 !important;
  font-weight: 700;
  margin: 0;
}

.is-teacher-guide :deep(blockquote h3) {
  color: #ff6b91 !important;
  margin-top: 0 !important;
  border-bottom: none !important;
}

.profile-modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(151, 172, 249, 0.12);
}

.profile-modal-head h3 {
  margin: 0;
  font-size: 20px;
  color: var(--text);
}

.profile-modal-head p {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  line-height: 1.6;
}

.profile-close {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid rgba(151, 172, 249, 0.15);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-muted);
  font-size: 20px;
  cursor: pointer;
}

.profile-form {
  display: grid;
  gap: 10px;
  padding: 18px 20px 20px;
}

.profile-form label {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.profile-form input,
.profile-form select {
  width: 100%;
  padding: 11px 12px;
  border-radius: 10px;
  border: 1px solid rgba(151, 172, 249, 0.16);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  outline: none;
}

.profile-form input:focus,
.profile-form select:focus {
  border-color: rgba(90, 243, 211, 0.45);
  box-shadow: 0 0 0 3px rgba(90, 243, 211, 0.12);
}

.profile-tip {
  margin: -4px 0 2px;
  font-size: var(--font-size-sm);
  color: rgba(151, 172, 249, 0.78);
}

.profile-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.secondary-btn,
.primary-btn {
  padding: 11px 18px;
  border-radius: 10px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  cursor: pointer;
}

.secondary-btn {
  border: 1px solid rgba(151, 172, 249, 0.18);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-muted);
}

.primary-btn {
  border: 1px solid rgba(90, 243, 211, 0.5);
  background: linear-gradient(120deg, rgba(90, 243, 211, 0.9), rgba(124, 245, 221, 0.85));
  color: #061016;
}

.primary-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
@media (max-width: 640px) {
  .shell-main {
    padding: 20px 16px 24px;
  }

  .user-menu {
    width: min(100vw - 24px, 320px);
  }

  .profile-modal-mask {
    padding: 16px;
  }

  .profile-modal-head,
  .profile-form {
    padding-left: 16px;
    padding-right: 16px;
  }

  .guide-modal-body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .profile-actions {
    flex-direction: column-reverse;
  }

  .secondary-btn,
  .primary-btn {
    width: 100%;
  }
}
/* --- Light teaching-workspace theme --- */
.sidebar {
  width: 236px !important;
  padding: 18px 12px 14px !important;
  background: #ffffff !important;
  border-right: 1px solid #e2e8f0 !important;
  box-shadow: none !important;
  backdrop-filter: none !important;
}
.sidebar::before { display: none !important; }
.sidebar-brand {
  height: 54px !important;
  min-height: 54px !important;
  padding: 0 6px 12px !important;
  margin-bottom: 10px !important;
  border-bottom: 1px solid #eef2f7 !important;
  gap: 10px !important;
}
.brand-orbit { width: 32px !important; height: 32px !important; }
.brand-core {
  inset: 13px !important;
  background: #0f766e !important;
  box-shadow: none !important;
}
.brand-ring { display: none !important; }
.brand-name { color: #172033 !important; font-size: 15px !important; font-weight: 700 !important; letter-spacing: 0 !important; }
.brand-sub { color: #94a3b8 !important; font-size: 10px !important; letter-spacing: 0.04em !important; text-transform: none !important; }
.sidebar-toggle {
  display: none !important;
  width: 32px !important;
  height: 32px !important;
  border-radius: 8px !important;
  border: 1px solid #e2e8f0 !important;
  background: #ffffff !important;
  color: #475569 !important;
  font-size: 18px !important;
}
.sidebar-nav { gap: 3px !important; }
.nav-item {
  min-height: 40px !important;
  padding: 0 10px !important;
  border-radius: 8px !important;
  border: 1px solid transparent !important;
  color: #475569 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  background: transparent !important;
}
.nav-item:hover { background: #f8fafc !important; color: #172033 !important; }
.nav-item.is-active {
  background: #ecfdf5 !important;
  border-color: #d1fae5 !important;
  color: #0f766e !important;
  font-weight: 600 !important;
}
.nav-glow { width: 6px !important; height: 6px !important; border-radius: 50% !important; background: #cbd5e1 !important; box-shadow: none !important; opacity: 1 !important; }
.nav-item.is-active .nav-glow { background: #0f766e !important; }
.nav-short { color: inherit !important; }
.sidebar-footer { gap: 10px !important; }
.guide-entry-btn {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  color: #475569 !important;
  border-radius: 8px !important;
  min-height: 34px !important;
  font-size: 12px !important;
  box-shadow: none !important;
}
.guide-entry-btn:hover { background: #f8fafc !important; border-color: #cbd5e1 !important; }
.status-card {
  background: #f8fafc !important;
  border: 1px solid #eef2f7 !important;
  border-radius: 10px !important;
  padding: 10px 12px !important;
  box-shadow: none !important;
}
.status-kicker { color: #94a3b8 !important; font-size: 9px !important; letter-spacing: 0.08em !important; }
.status-title { color: #334155 !important; font-size: 12px !important; }
.status-meta { color: #94a3b8 !important; font-size: 10px !important; }
.avatar-btn {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 10px !important;
  padding: 7px 8px !important;
  box-shadow: none !important;
}
.avatar-letter { background: #ecfdf5 !important; color: #0f766e !important; box-shadow: none !important; }
.avatar-name { color: #172033 !important; font-size: 12px !important; }
.avatar-role { color: #94a3b8 !important; font-size: 10px !important; }
.avatar-chevron { color: #94a3b8 !important; }
.shell-main {
  background: #f5f7fa !important;
  padding: 20px 28px 32px !important;
  gap: 14px !important;
}
.main-header {
  padding-bottom: 14px !important;
  border-bottom: 1px solid #e2e8f0 !important;
  margin-bottom: 0 !important;
}
.main-kicker { color: #94a3b8 !important; font-size: 10px !important; letter-spacing: 0.08em !important; }
.shell-subtitle { color: #172033 !important; font-size: 22px !important; font-weight: 700 !important; font-family: inherit !important; letter-spacing: -0.02em !important; }
.main-badge {
  background: #f1f5f9 !important;
  border: 1px solid #e2e8f0 !important;
  color: #475569 !important;
  border-radius: 6px !important;
  padding: 4px 9px !important;
  font-size: 11px !important;
  font-weight: 500 !important;
  letter-spacing: 0 !important;
}
.ai-capability-notice {
  background: #fffbeb !important;
  border: 1px solid #fde68a !important;
  color: #92400e !important;
  border-radius: 8px !important;
  padding: 7px 10px !important;
  font-size: 12px !important;
  line-height: 1.5 !important;
}
.back-btn {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  color: #475569 !important;
  border-radius: 8px !important;
  box-shadow: none !important;
}
.back-btn:hover { background: #f8fafc !important; border-color: #cbd5e1 !important; }
.mobile-menu-btn {
  display: none;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  font-size: 17px;
  cursor: pointer;
}
.user-menu, .profile-modal, .guide-modal {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12) !important;
  color: #172033 !important;
}
.user-menu-action, .secondary-btn {
  background: #ffffff !important;
  border: 1px solid #e2e8f0 !important;
  color: #475569 !important;
}
.primary-btn {
  background: #0f766e !important;
  border: 1px solid #0f766e !important;
  color: #ffffff !important;
}
.guide-modal-body { color: #334155 !important; }

@media (max-width: 1100px) {
  .mobile-menu-btn { display: inline-flex; }
  .sidebar {
    position: fixed !important;
    top: 0 !important;
    left: 0 !important;
    bottom: 0 !important;
    z-index: 300 !important;
    width: min(82vw, 280px) !important;
    height: 100dvh !important;
    max-height: none !important;
    padding: 18px 14px !important;
    transform: translateX(-100%);
    transition: transform 0.2s ease !important;
    border-right: 1px solid #e2e8f0 !important;
    border-bottom: none !important;
    box-shadow: 12px 0 32px rgba(15, 23, 42, 0.12) !important;
    overflow-y: auto !important;
  }
  .sidebar:not(.collapsed) { transform: translateX(0); }
  .sidebar.collapsed {
    width: min(82vw, 280px) !important;
    max-height: none !important;
    padding: 18px 14px !important;
  }
  .sidebar .sidebar-brand,
  .sidebar.collapsed .sidebar-brand {
    display: flex !important;
    justify-content: space-between !important;
    margin-bottom: 10px !important;
    padding: 0 4px 12px !important;
  }
  .sidebar .brand-text,
  .sidebar.collapsed .brand-text { display: flex !important; }
  .sidebar .sidebar-nav,
  .sidebar .sidebar-footer,
  .sidebar.collapsed .sidebar-nav,
  .sidebar.collapsed .sidebar-footer { display: flex !important; }
  .sidebar-toggle { display: inline-flex !important; }
  .shell-backdrop {
    display: block !important;
    position: fixed !important;
    inset: 0 !important;
    z-index: 250 !important;
    background: rgba(15, 23, 42, 0.28) !important;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.2s ease;
  }
  .shell-backdrop.visible { opacity: 1; pointer-events: auto; }
  .shell-main { padding: 16px 14px 28px !important; }
  .main-header, .main-header-left {
    flex-direction: row !important;
    align-items: center !important;
  }
  .main-header-left { gap: 8px !important; min-width: 0; }
  .main-kicker { display: none !important; }
  .shell-subtitle { font-size: 18px !important; }
  .main-badge { align-self: center !important; flex-shrink: 0; }
}
</style>
