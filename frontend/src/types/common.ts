export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number | string;
  reason?: string | null;
}

export interface PageRequest {
  page?: number;
  size?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export type UserType = 'STUDENT' | 'TEACHER';

export interface SSEMessageEventPayload {
  content: string;
  type?: string;
}

export interface SSEProgressEventPayload {
  progress: number;
  stage?: string;
}

export interface SSECompleteEventPayload {
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
}

export interface SSEErrorEventPayload {
  code: number;
  message: string;
  reason?: string;
}

