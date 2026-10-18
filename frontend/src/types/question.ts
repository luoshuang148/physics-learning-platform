export interface QuestionPayload {
  content: string;
  options: string;
  correctOption: 'A' | 'B' | 'C' | 'D';
  explanation?: string;
  difficulty?: number | null;
  imageBase64?: string;
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
}

