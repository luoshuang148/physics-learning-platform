export interface AppShellLink {
  label: string;
  to: string;
}

export const teacherNavLinks: AppShellLink[] = [
  { label: '总览', to: '/teacher/dashboard' },
  { label: '学生名册', to: '/teacher/users' },
  { label: '题库管理', to: '/teacher/questions' },
  { label: '学生学情', to: '/teacher/student-analysis' },
  { label: '任务管理', to: '/teacher/event-manage' },
];

