export interface TeacherEventCreatePayload {
  eventTitle: string;
  eventDescription?: string;
  questionIds?: number[];
  eventTimestamp: string;
}

export interface TeacherEventResponse {
  id: number;
  teacherId: number;
  questionIds: number[];
  isGlobal: boolean;
  teacherName: string;
  eventTitle: string;
  eventDescription: string | null;
  eventTimestamp: string;
  createdAt: string;
  updatedAt: string;
}

