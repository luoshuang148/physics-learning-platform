export interface AnswerRecordSubmitPayload {
  eventId?: number;
  requestId?: string;
  intent?: 'ASSIGNMENT' | 'PRACTICE';
  questionId: number;
  userAnswer: string;
  answeredAt?: string;
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
  questionContent: string;
  questionOptions: Record<string, string>;
  correctAnswer: string;
  questionAnalysis: string | null;
  difficulty: string | null;
  imageBase64: string | null;
  sessionId: string;
}

export interface AnswerRecordSummary {
  recordId: string;
  eventId: number | null;
  practice: boolean;
  attemptOrder: number | null;
  userId: number;
  questionId: number;
  sessionId: string;
  userAnswer: string;
  correct: boolean;
  questionPreview: string;
  answeredAt: string;
}

export interface StudentAnswerSummary {
  totalAttempts: number;
  correctAttempts: number;
  wrongQuestions: number;
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

