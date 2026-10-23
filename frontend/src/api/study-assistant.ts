import type { ApiResponse } from '@/types/common';
import request, { resolveApiBaseUrl } from '@/utils/request';

/** 平台题库来源（区别于旧文档来源 documentId/chunkId）。 */
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

export interface AssistantMessageRequest {
  question: string;
  mode: 'GENERAL' | 'PLATFORM';
  conversationId?: number;
  eventId?: number;
  recordId?: number;
  requestId: string;
}

export const platformSearchApi = async (body: PlatformSearchRequest): Promise<PlatformSearchResponse> => {
  const { data } = await request.post<ApiResponse<PlatformSearchResponse>>('/ai/student/platform-search', body);
  return data.data;
};

export const platformSourceApi = async (sourceId: string): Promise<PlatformSource> => {
  const { data } = await request.get<ApiResponse<PlatformSource>>(
    `/ai/student/platform-sources/${encodeURIComponent(sourceId)}`,
  );
  return data.data;
};

export const listConversationsApi = async () => {
  const { data } = await request.get<ApiResponse<Array<Record<string, unknown>>>>(
    '/ai/student/assistant/conversations',
  );
  return data.data;
};

export const listConversationMessagesApi = async (id: number, page = 0, size = 50) => {
  const { data } = await request.get<ApiResponse<Array<Record<string, unknown>>>>(
    `/ai/student/assistant/conversations/${id}/messages`,
    { params: { page, size } },
  );
  return data.data;
};

/**
 * SSE：POST /ai/student/assistant/messages
 * 事件：progress / sources / content / complete / error
 */
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
  const base = resolveApiBaseUrl();
  const token = localStorage.getItem('token') || '';
  const response = await fetch(`${base}/ai/student/assistant/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      Authorization: token ? `Bearer ${token}` : '',
    },
    body: JSON.stringify(body),
    signal,
  });
  if (!response.ok || !response.body) {
    handlers.onError?.({ message: '请求失败', code: String(response.status) });
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split('\n\n');
    buffer = blocks.pop() ?? '';
    for (const block of blocks) {
      const lines = block.split('\n');
      let event = 'message';
      let data = '';
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        else if (line.startsWith('data:')) data += line.slice(5).trim();
      }
      if (!data) continue;
      try {
        const parsed = JSON.parse(data);
        if (event === 'progress') handlers.onProgress?.(parsed);
        else if (event === 'sources') handlers.onSources?.(parsed as { sources: PlatformSource[] });
        else if (event === 'content') handlers.onContent?.(parsed);
        else if (event === 'complete') handlers.onComplete?.(parsed);
        else if (event === 'error') handlers.onError?.(parsed);
      } catch {
        // ignore malformed frame
      }
    }
  }
};
