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
