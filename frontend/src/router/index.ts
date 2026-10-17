import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { getTeacherEventApi } from '@/api/teacher-event';

const LoginView = () => import('@/views/auth/LoginView.vue');
const RegisterView = () => import('@/views/auth/RegisterView.vue');
const StudentHomeView = () => import('@/views/student/StudentHomeView.vue');
const StudentTaskView = () => import('@/views/student/StudentTaskView.vue');
const StudentQuestionView = () => import('@/views/student/StudentQuestionView.vue');
const StudentQuestionVideoView = () => import('@/views/student/StudentQuestionVideoView.vue');
const StudentExperimentView = () => import('@/views/student/StudentExperimentView.vue');
const StudentWrongBookView = () => import('@/views/student/StudentWrongBookView.vue');
const StudentAIView = () => import('@/views/student/StudentAIView.vue');
const StudentReportView = () => import('@/views/student/StudentReportView.vue');
const TeacherDashboardView = () => import('@/views/teacher/TeacherDashboardView.vue');
const TeacherUserManageView = () => import('@/views/teacher/TeacherUserManageView.vue');
const TeacherRecordManageView = () => import('@/views/teacher/TeacherRecordManageView.vue');
const TeacherExperimentManageView = () => import('@/views/teacher/TeacherExperimentManageView.vue');
const TeacherQuestionManageView = () => import('@/views/teacher/TeacherQuestionManageView.vue');
const TeacherVideoManageView = () => import('@/views/teacher/TeacherVideoManageView.vue');
const TeacherErrorAnalysisView = () => import('@/views/teacher/TeacherErrorAnalysisView.vue');
const TeacherStudentAnalysisView = () => import('@/views/teacher/TeacherStudentAnalysisView.vue');
const TeacherEventView = () => import('@/views/teacher/TeacherEventView.vue');

const TASK_ENTRY_EVENT_ID_KEY_PREFIX = 'student_task_entry_event_id';
const STUDENT_LEARNING_PATHS = new Set([
  '/student/experiment',
  '/student/question',
]);

const getTaskEntryEventIdKey = (userId?: number) => `${TASK_ENTRY_EVENT_ID_KEY_PREFIX}_${userId ?? 'anonymous'}`;

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/auth/login' },
    { path: '/auth/login', component: LoginView, meta: { guestOnly: true } },
    { path: '/auth/register', component: RegisterView, meta: { guestOnly: true } },

    { path: '/student/home', component: StudentHomeView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'expanded' } },
    { path: '/student/task', component: StudentTaskView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/experiment', component: StudentExperimentView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/question', component: StudentQuestionView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/question/video', component: StudentQuestionVideoView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/wrong-book', component: StudentWrongBookView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/ai', component: StudentAIView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },
    { path: '/student/report', component: StudentReportView, meta: { requiresAuth: true, role: 'STUDENT', sidebarDefault: 'collapsed' } },


    { path: '/teacher/dashboard', component: TeacherDashboardView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/users', component: TeacherUserManageView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/records', component: TeacherRecordManageView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/experiments', component: TeacherExperimentManageView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/questions', component: TeacherQuestionManageView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/videos', component: TeacherVideoManageView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/errors', component: TeacherErrorAnalysisView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/student-analysis', component: TeacherStudentAnalysisView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },
    { path: '/teacher/event-manage', component: TeacherEventView, meta: { requiresAuth: true, role: 'TEACHER', sidebarDefault: 'expanded' } },

    { path: '/:pathMatch(.*)*', redirect: '/auth/login' },
  ],
});

let initialized = false;
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!initialized) {
    initialized = true;
    await auth.bootstrap();
  }

  const requiresAuth = Boolean(to.meta.requiresAuth);
  const guestOnly = Boolean(to.meta.guestOnly);
  const role = to.meta.role as 'STUDENT' | 'TEACHER' | undefined;

  if (requiresAuth && !auth.isAuthed) {
    return '/auth/login';
  }

  if (guestOnly && auth.isAuthed) {
    return auth.isTeacher ? '/teacher/dashboard' : '/student/home';
  }

  if (requiresAuth && role && auth.user?.userType !== role) {
    return auth.user?.userType === 'TEACHER' ? '/teacher/dashboard' : '/student/home';
  }

  const shouldCheckStudentTask =
    auth.user?.userType === 'STUDENT' &&
    requiresAuth &&
    STUDENT_LEARNING_PATHS.has(to.path);

  if (shouldCheckStudentTask) {
    try {
      const experimentTask = await getTeacherEventApi();
      if (!experimentTask) {
        return '/student/home';
      }

      const enteredEventId = sessionStorage.getItem(getTaskEntryEventIdKey(auth.user?.id));
      if (enteredEventId !== String(experimentTask.id)) {
        return '/student/home';
      }
    } catch {
      return '/student/home';
    }
  }

  return true;
});

export default router;
