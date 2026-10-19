export interface AppShellLink {
  label: string;
  to: string;
}

export const studentNavLinks: AppShellLink[] = [
  { label: '首页', to: '/student/home' },
  { label: '作业', to: '/student/task' },
  { label: '错题本', to: '/student/wrong-book' },
  { label: '3D 实验', to: '/student/experiments' },
];

export const teacherNavLinks: AppShellLink[] = [
  { label: '总览', to: '/teacher/dashboard' },
  { label: '学生名册', to: '/teacher/users' },
  { label: '题库管理', to: '/teacher/questions' },
  { label: '学生学情', to: '/teacher/student-analysis' },
  { label: '任务管理', to: '/teacher/event-manage' },
];

