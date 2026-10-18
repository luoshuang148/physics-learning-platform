export type TeacherEventStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED';
export type TeacherEventAvailability = 'DRAFT' | 'SCHEDULED' | 'OPEN' | 'ENDED' | 'CLOSED' | 'ARCHIVED';

export interface TeacherEventCreatePayload {
  eventTitle: string;
  eventDescription?: string;
  questionIds?: number[];
  eventTimestamp: string;
  status?: TeacherEventStatus;
  startAt?: string | null;
  dueAt?: string | null;
}

export interface TeacherEventResponse {
  id: number;
  teacherId: number;
  questionIds: number[];
  questionCount?: number;
  isGlobal: boolean;
  teacherName: string;
  eventTitle: string;
  eventDescription: string | null;
  eventTimestamp: string;
  status: TeacherEventStatus;
  availability?: TeacherEventAvailability;
  canAnswer?: boolean;
  startAt: string | null;
  dueAt: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface TaskStudentRow {
  userId: number;
  username: string;
  realName: string;
  className: string | null;
  status: 'NOT_STARTED' | 'PARTIAL' | 'COMPLETED';
  answeredQuestions: number;
  correctQuestions: number;
  missingQuestions: number;
  lastAnsweredAt: string | null;
}

export interface TaskQuestionRow {
  questionId: number;
  contentPreview: string;
  answeredStudents: number;
  correctStudents: number;
  unansweredStudents: number;
  accuracy: number;
}

export interface TaskDetailResponse {
  taskId: number;
  title: string;
  status: TeacherEventStatus;
  availability: string;
  startAt: string | null;
  dueAt: string | null;
  questionCount: number;
  totalStudents: number;
  participatedStudents: number;
  completedStudents: number;
  notStartedStudents: number;
  partialStudents: number;
  completionRate: number;
  participationRate: number;
  accuracy: number;
  answeredQuestions: number;
  correctQuestions: number;
  students: TaskStudentRow[];
  questions: TaskQuestionRow[];
}
