export interface AIAskPayload {
  question: string;
  questionId?: number;
  imageBase64?: string;
}

export interface StudentReportResponse {
  studentRealName: string;
  latestReportContent: string;
  reportCreateTime: string;
}

export interface ConversationHistory {
  id: number;
  sessionId: string;
  userId: number;
  question: string;
  answer: string;
  createdAt: string;
}

export interface TeacherQuestionErrorSummary {
  questionId: number;
  content: string;
  options: Record<string, string>;
  correctOption: string;
  explanation: string | null;
  difficulty: string | null;
  imageBase64: string | null;
  errorCount: number;
  correctCount: number;
  errorRate: number;
}

export interface QuestionStudentErrorDetail {
  userId: number;
  studentName: string;
  className: string;
  userAnswer: string;
  answerTime: string;
  errorReason: string | null;
}

export interface TeacherQuestionErrorDetails {
  studentList: QuestionStudentErrorDetail[];
  wordCloud: Record<string, number>;
}

