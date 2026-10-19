import type { StudentTaskResult } from '../api/teacher-event';

const availabilityLabels: Record<string, string> = {
  DRAFT: '草稿', SCHEDULED: '未开放', OPEN: '开放中', PUBLISHED: '开放中',
  ENDED: '已截止', CLOSED: '已截止', ARCHIVED: '已归档',
};
export const taskAvailabilityText = (availability?: string) => availabilityLabels[availability ?? ''] ?? '';

const isClosed = (availability?: string) => ['ENDED', 'CLOSED', 'ARCHIVED'].includes(availability ?? '');

export const studentProgressText = (status: string, availability?: string) => {
  if (status === 'COMPLETED') return '已完成';
  if (status === 'NOT_STARTED') return isClosed(availability) ? '未交' : '未开始';
  if (status === 'PARTIAL') return isClosed(availability) ? '未完成' : '进行中';
  return status;
};

export const assignmentProgress = (answeredQuestions: number, totalQuestions: number, availability?: string) => {
  const total = Number.isFinite(totalQuestions) ? Math.max(0, Math.floor(totalQuestions)) : 0;
  const answered = Number.isFinite(answeredQuestions) ? Math.min(total, Math.max(0, Math.floor(answeredQuestions))) : 0;
  const completed = total > 0 && answered === total;
  const status = completed ? 'COMPLETED' : answered > 0 ? 'PARTIAL' : 'NOT_STARTED';
  return {
    total, answered, completed, remaining: total - answered,
    percent: total ? Math.round(answered / total * 100) : 0,
    label: total ? studentProgressText(status, availability) : '暂无题目',
  };
};

export const studentTaskProgress = (result: StudentTaskResult) => {
  const progress = assignmentProgress(result.answeredQuestions, result.task.questionCount ?? result.task.questionIds.length, result.task.availability ?? result.task.status);
  return { ...result, progress, canResume: Boolean(result.task.canAnswer && progress.total && !progress.completed) };
};

// Continue saved work first, then new work. Closed and completed tasks remain available for review.
export const sortStudentTasks = (results: StudentTaskResult[]) => {
  const rank = (item: ReturnType<typeof studentTaskProgress>) => item.canResume ? (item.progress.answered ? 0 : 1)
    : item.task.availability === 'SCHEDULED' ? 2 : !item.progress.completed ? 3 : 4;
  return results.map(studentTaskProgress).sort((a, b) => rank(a) - rank(b));
};
