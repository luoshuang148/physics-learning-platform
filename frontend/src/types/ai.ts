export interface AIAskPayload {
  question: string;
  questionId?: number;
  recordId?: string;
  imageBase64?: string;
}

export interface AISource {
  sourceId: string;
  chunkId: number;
  documentId: number;
  revisionId: number;
  title: string;
  page: number | null;
  section: string | null;
  text: string;
  available?: boolean;
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
  aiRunId?: number | null;
  sources?: AISource[];
}
