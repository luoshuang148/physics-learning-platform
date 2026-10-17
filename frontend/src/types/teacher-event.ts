export interface TeacherEventCreatePayload {
  eventTitle: string;
  eventDescription?: string;
  eventTimestamp: string;
}

export interface TeacherEventResponse {
  id: number;
  teacherId: number;
  teacherName: string;
  eventTitle: string;
  eventDescription: string | null;
  eventTimestamp: string;
  createdAt: string;
  updatedAt: string;
}

