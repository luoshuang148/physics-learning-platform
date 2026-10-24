export interface QuestionPayload {
  content: string;
  options: string;
  correctOption: 'A' | 'B' | 'C' | 'D';
  explanation?: string;
  difficulty?: number | null;
  imageBase64?: string;
  knowledgePointCodes?: string[];
  aiPracticeEnabled?: boolean;
}

export interface QuestionUpdatePayload extends QuestionPayload {
  id: number;
}

export interface QuestionResponse {
  snapshotOrigin?: string;
  id: number;
  content: string;
  options: Record<string, string>;
  correctOption: 'A' | 'B' | 'C' | 'D';
  explanation: string | null;
  difficulty: number | null;
  imageBase64: string | null;
  createdAt: string;
  updatedAt: string;
  knowledgePointCodes?: string[];
  aiPracticeEnabled?: boolean;
  aiContentRevision?: number;
}

export interface QuestionIndexStatus {
  questionId: number;
  revision: number;
  status: 'PENDING' | 'INDEXING' | 'READY' | 'FAILED' | 'OBSOLETE' | 'DELETED' | 'DISABLED';
  failureCode: string | null;
}

export interface KnowledgePoint {
  code: string;
  name: string;
  topic: string;
}

export interface QuestionCatalogRow {
  id: number;
  content: string;
  correctOption: 'A' | 'B' | 'C' | 'D';
  difficulty: number | null;
  hasImage: boolean;
}

