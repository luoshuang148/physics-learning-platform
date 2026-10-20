<template>
  <nav class="app-navigation" :class="`app-navigation-${placement}`" :aria-label="`${role === 'TEACHER' ? '教师' : '学生'}${placement === 'top' ? '主导航' : '底部导航'}`">
    <RouterLink v-for="item in links" :key="item.to" :to="item.to" class="app-nav-link" :class="{ 'is-current': isCurrent(item.to) }" :aria-current="isCurrent(item.to) ? 'page' : undefined">
      <AppIcon :name="item.to.split('/').pop()!" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import { computed } from 'vue';
import { studentNavLinks, teacherNavLinks } from '@/constants/navigation';
import AppIcon from '@/components/AppIcon.vue';

const props = defineProps<{ placement: 'top' | 'bottom'; role: 'STUDENT' | 'TEACHER' }>();
const links = computed(() => props.role === 'TEACHER' ? teacherNavLinks : studentNavLinks);
const route = useRoute();
const isCurrent = (path: string) => {
  const parent = ['/student/question', '/student/report'].includes(route.path) ? '/student/task'
    : route.path === '/student/practice' ? '/student/wrong-book'
    : route.path.startsWith('/teacher/assignments/') ? '/teacher/event-manage' : route.path;
  return parent === path;
};
</script>

<style scoped>
.app-navigation-top { display: flex; justify-content: center; align-items: center; gap: 6px; }
.app-navigation-bottom { display: none; }
.app-nav-link { display: flex; align-items: center; justify-content: center; gap: 7px; padding: 11px 14px; border-radius: 7px; color: #64748b; text-decoration: none; font-size: .875rem; white-space: nowrap; transition: background .15s, color .15s; }
.app-nav-link:hover { background: #f1f6f3; color: #1d715b; }
.app-nav-link.is-current { color: #1d715b; background: #edf6f2; font-weight: 600; }
.app-nav-link:focus-visible { outline: 2px solid #1d715b; outline-offset: 2px; }
.app-nav-link .app-icon { width: 18px; height: 18px; }
@media (max-width: 1000px) {
  .app-navigation-top { gap: 0; }
  .app-navigation-top .app-icon { display: none; }
  .app-nav-link { padding: 10px; gap: 5px; }
}
@media (max-width: 760px) {
  .app-navigation-top { display: none; }
  .app-navigation-bottom { position: fixed; inset: auto 0 0; z-index: 220; display: flex; min-height: 66px; padding: 7px 6px calc(7px + env(safe-area-inset-bottom, 0px)); border-top: 1px solid #e2e8e5; background: #fffffff5; backdrop-filter: blur(12px); }
  .app-navigation-bottom .app-nav-link { flex: 1; min-width: 0; flex-direction: column; gap: 4px; padding: 3px 1px; font-size: .6875rem; }
  .app-navigation-bottom .app-nav-link.is-current { background: transparent; }
  .app-navigation-bottom .app-icon { width: 20px; height: 20px; }
}
</style>
