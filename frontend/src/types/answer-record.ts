export interface AnswerRecordSubmitPayload {
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

