import type { ApiResponse } from '@/types/common';
import request, { resolveApiBaseUrl } from '@/utils/request';

export interface PlatformSource {
  sourceId: string;
  kind: 'PLATFORM_QUESTION';
  questionId: number;
  revision: number;
  part: string;
  ordinal?: number;
  text: string;
  available?: boolean;
}
export interface PlatformSearchRequest {
  query: string;
  eventId?: number;
  knowledgePointCodes?: string[];
  difficulty?: number;
  excludeAttempted?: boolean;
  limit?: number;
}
export interface PlatformQuestionHit {
  questionId: number;
  revision: number;
  content: string;
  difficulty: number | null;
  knowledgePointCodes: string[];
  sourceIds: string[];
}
export interface PlatformSearchResponse {
  status: 'MATCHED' | 'INSUFFICIENT' | 'UNAVAILABLE';
  actualMode: string;
  durationMs: number;
  questions: PlatformQuestionHit[];
  sources: PlatformSource[];
}
export type AssistantIntent = 'GENERAL' | 'PLATFORM' | 'WRONG' | 'PLAN';
export interface AssistantResolution {
  intent: AssistantIntent;
  query: string;
  days: number;
  excludeAttempted: boolean;
}
export interface AssistantMessageRequest {
  question: string;
  mode: 'GENERAL' | 'PLATFORM';
  retrievalQuery?: string;
  excludeAttempted?: boolean;
  conversationId?: number;
  history?: Array<{ role: 'USER' | 'ASSISTANT'; content: string; sourceIds: string[] }>;
  eventId?: number;
  recordId?: number;
  requestId: string;
}
export const resolveAssistantIntent = async (body: { question: string; previousQuestion?: string; previousIntent?: AssistantIntent }, signal?: AbortSignal): Promise<AssistantResolution> => {
  const { data } = await request.post<ApiResponse<AssistantResolution>>('/ai/student/assistant/resolve', body, { signal });
  return data.data;
};
export const platformSearchApi = async (body: PlatformSearchRequest): Promise<PlatformSearchResponse> => {
  const { data } = await request.post<ApiResponse<PlatformSearchResponse>>('/ai/student/platform-search', body);
  return data.data;
};
export const platformSourceApi = async (sourceId: string): Promise<PlatformSource> => {
  const { data } = await request.get<ApiResponse<PlatformSource>>(`/ai/student/platform-sources/${encodeURIComponent(sourceId)}`);
  return data.data;
};
export const listConversationsApi = async () => {
  const { data } = await request.get<ApiResponse<Array<Record<string, unknown>>>>('/ai/student/assistant/conversations');
  return data.data;
};
export const listConversationMessagesApi = async (id: number, page = 0, size = 50) => {
  const { data } = await request.get<ApiResponse<Array<Record<string, unknown>>>>(`/ai/student/assistant/conversations/${id}/messages`, { params: { page, size } });
  return data.data;
};

export const streamAssistantMessage = async (
  body: AssistantMessageRequest,
  handlers: {
    onProgress?: (payload: Record<string, unknown>) => void;
    onSources?: (payload: { sources: PlatformSource[] }) => void;
    onContent?: (payload: { text: string; runId?: string }) => void;
    onComplete?: (payload: Record<string, unknown>) => void;
    onError?: (payload: { message?: string; code?: string }) => void;
  },
  signal?: AbortSignal,
): Promise<void> => {
  const token = localStorage.getItem('zhixue_token');
  const response = await fetch(`${resolveApiBaseUrl()}/ai/student/assistant/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: token ? `Bearer ${token}` : '' },
    body: JSON.stringify(body), signal,
  });
  if (!response.ok || !response.body) {
    if (response.status === 401) window.dispatchEvent(new Event('auth-expired'));
    throw new Error(response.status === 401 ? '登录已过期，请重新登录。' : '服务暂不可用，请稍后重试。');
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let terminal = false;
  const dispatch = (block: string) => {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
    }
    if (!data.length) return;
    const parsed = JSON.parse(data.join('\n'));
    if (event === 'progress') handlers.onProgress?.(parsed);
    else if (event === 'sources') handlers.onSources?.(parsed);
    else if (event === 'content') handlers.onContent?.(parsed);
    else if (event === 'complete') { terminal = true; handlers.onComplete?.(parsed); }
    else if (event === 'error') { terminal = true; handlers.onError?.(parsed); }
  };
  try {
    for (;;) {
      const { done, value } = await reader.read();
      buffer += (done ? decoder.decode() : decoder.decode(value, { stream: true })).replace(/\r/g, '');
      const blocks = buffer.split('\n\n');
      buffer = blocks.pop() ?? '';
      for (const block of blocks) dispatch(block);
      if (done) { if (buffer.trim()) dispatch(buffer); break; }
    }
    if (!terminal && !signal?.aborted) throw new Error('连接中断，回答尚未完成，请重试。');
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
};
