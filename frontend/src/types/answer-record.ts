export interface AnswerRecordSubmitPayload {
  eventId?: number;
  requestId?: string;
  intent?: 'ASSIGNMENT' | 'PRACTICE';
  questionId: number;
  userAnswer: string;
  errorReason?: string;
  answeredAt?: string;
}

export interface RecordBatchDeletePayload {
  startTime: string;
  endTime: string;
}

export interface AnswerRecordResponse {
  eventId: number | null;
  practice?: boolean;
  requestId?: string;
  attemptOrder?: number;
  studentName?: string;
  recordId: string;
  userId: number;
  questionId: number;
  userAnswer: string;
  correct: boolean;
  answeredAt: string;
  errorReason: string | null;
  questionContent: string;
  questionOptions: Record<string, string>;
  correctAnswer: string;
  questionAnalysis: string | null;
  difficulty: string | null;
  imageBase64: string | null;
  sessionId: string;
}

export interface AnswerRecordBatchResponse {
  records: AnswerRecordResponse[];
  totalCount: number;
  currentPage: number | null;
  pageSize: number | null;
  totalPages: number | null;
}

export interface AnswerStatsResponse {
  answeredStudentCount: number;
  totalStudentCount: number;
  coverageRate: number;
}

