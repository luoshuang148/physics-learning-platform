export interface QuestionPayload {
  content: string;
  options: string;
  correctOption: 'A' | 'B' | 'C' | 'D';
  explanation?: string;
  difficulty?: number | null;
  imageBase64?: string;
  knowledgePointCodes?: string[];
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

