export interface AppShellLink {
  label: string;
  to: string;
}

export const teacherNavLinks: AppShellLink[] = [
  { label: '总览', to: '/teacher/dashboard' },
  { label: '用户管理', to: '/teacher/users' },
  { label: '答题记录', to: '/teacher/records' },
  { label: '题库管理', to: '/teacher/questions' },
  { label: '题目分析', to: '/teacher/errors' },
  { label: '学生学情', to: '/teacher/student-analysis' },
  { label: '任务管理', to: '/teacher/event-manage' },
];

