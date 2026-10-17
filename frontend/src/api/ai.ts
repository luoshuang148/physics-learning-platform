import type { ApiResponse, SSECompleteEventPayload, SSEErrorEventPayload, SSEMessageEventPayload, SSEProgressEventPayload } from '@/types/common';
import type { AIAskPayload, ConversationHistory, StudentReportResponse } from '@/types/ai';
import request, { resolveApiBaseUrl } from '@/utils/request';
import { getSessionId } from '@/utils/session';

export const getSessionHistoryApi = async (sessionType?: string): Promise<ConversationHistory[]> => {
  const sessionId = getSessionId(sessionType);
  const { data } = await request.get<ApiResponse<ConversationHistory[]>>('/ai/session/history', {
    headers: {
      'X-Session-Id': sessionId,
    },
  });
  return data.data;
};

export const clearSessionApi = async (sessionType?: string): Promise<void> => {
  const sessionId = getSessionId(sessionType);
  await request.delete<ApiResponse<void>>('/ai/session', {
    headers: {
      'X-Session-Id': sessionId,
    },
  });
};

export const getStudentLatestReportApi = async (studentId: number): Promise<StudentReportResponse> => {
  const { data } = await request.get<ApiResponse<StudentReportResponse>>(`/ai/teacher/report/${studentId}`);
  return data.data;
};

export interface StreamOptions {
  method?: 'GET' | 'POST';
  body?: AIAskPayload;
  sessionType?: string;
  signal?: AbortSignal;
  onMessage?: (payload: SSEMessageEventPayload) => void;
  onProgress?: (payload: SSEProgressEventPayload) => void;
  onReasoning?: (payload: { reasoning: string }) => void;
  onComplete?: (payload: SSECompleteEventPayload) => void;
  onError?: (payload: SSEErrorEventPayload) => void;
}

interface SSEParseState {
  stripDataLeadingSeparatorSpace: boolean | null;
}

const parseEventBlock = (block: string, parseState: SSEParseState): { type: string; data: string } | null => {
  // 【关键修复】不要丢弃只包含空格的块，它们可能是有效的 Markdown 分隔符
  // 例如："### " 后面的空格用于分隔标题和内容
  if (block === null || block === undefined) return null;
  const lines = block.replace(/\r/g, '').split('\n');

  let eventType = 'message';
  const dataParts: string[] = [];

  for (const line of lines) {
    if (!line) continue;

    if (line.startsWith('event:')) {
      eventType = line.replace('event:', '').trim();
    } else if (line.startsWith('data:')) {
      // 先保留 data: 后的原始内容，避免无条件吞掉首字符空格（如 " s"）
      let dataLine = line.slice('data:'.length);
      // 兼容两类后端编码风格：
      // 1) "data: value"（带协议分隔空格） => 去掉 1 个分隔空格
      // 2) "data:value"（不带分隔空格）   => 不去空格，完整保留 payload
      if (parseState.stripDataLeadingSeparatorSpace === null) {
        parseState.stripDataLeadingSeparatorSpace = dataLine.startsWith(' ');
      }
      if (parseState.stripDataLeadingSeparatorSpace && dataLine.startsWith(' ')) {
        dataLine = dataLine.slice(1);
      }
      dataParts.push(dataLine);
    }
  }

  if (dataParts.length === 0) return null;

  return {
    type: eventType,
    data: dataParts.join('\n'),
  };
};

export const streamAI = async (endpoint: string, options: StreamOptions): Promise<void> => {
  const token = localStorage.getItem('zhixue_token');
  const sessionId = getSessionId(options.sessionType);
  const apiBaseUrl = resolveApiBaseUrl();

  const response = await fetch(`${apiBaseUrl}${endpoint}`, {
    method: options.method ?? 'POST',
    signal: options.signal,
    headers: {
      Authorization: token ? `Bearer ${token}` : '',
      'X-Session-Id': sessionId,
      'Content-Type': 'application/json',
    },
    body: options.method === 'GET' ? undefined : JSON.stringify(options.body ?? {}),
  });

  if (!response.ok || !response.body) {
    const text = await response.text();
    throw new Error(text || `请求失败：${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  const parseState: SSEParseState = { stripDataLeadingSeparatorSpace: null };

  const processBlock = (rawBlock: string) => {
    const normalizedBlock = rawBlock.replace(/\r/g, '');
    // 【关键修复】不要丢弃只包含空格的块，保留有效的 Markdown 分隔符
    if (normalizedBlock === null || normalizedBlock === undefined) return;

    const parsed = parseEventBlock(normalizedBlock, parseState);
    if (!parsed) return;

    console.debug(`[SSE] type: ${parsed.type}, data length: ${parsed.data.length}`);

    let payload: any;
    let parseSuccess = true;
    try {
      payload = JSON.parse(parsed.data);
    } catch {
      parseSuccess = false;
    }

    switch (parsed.type) {
      case 'content':
        options.onMessage?.({ content: parsed.data });
        break;
      case 'message':
        if (parseSuccess && payload?.content) {
          options.onMessage?.(payload);
        } else if (parsed.data && parsed.data !== '连接成功') {
          options.onMessage?.({ content: parsed.data });
        }
        break;
      case 'progress':
        options.onProgress?.(parseSuccess ? payload : { progress: parseInt(parsed.data) || 0 });
        break;
      case 'reasoning':
        options.onReasoning?.({ reasoning: parsed.data });
        break;
      case 'complete':
        options.onComplete?.(parseSuccess ? payload : {});
        break;
      case 'error':
        options.onError?.(parseSuccess ? payload : { message: parsed.data });
        break;
      default:
        if (parseSuccess) {
          if (payload.reasoning) options.onReasoning?.(payload);
          else options.onMessage?.({ content: payload.content ?? payload.data ?? parsed.data });
        } else {
          options.onMessage?.({ content: parsed.data });
        }
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    const normalizedBuffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    const blocks = normalizedBuffer.split('\n\n');
    const hasCompleteBlock = normalizedBuffer.includes('\n\n');

    if (!hasCompleteBlock) {
      buffer = normalizedBuffer;
      continue;
    }

    buffer = blocks.pop() ?? '';
    for (const block of blocks) {
      processBlock(block);
    }
  }

  const remaining = decoder.decode();
  if (remaining) {
    buffer += remaining;
  }

  processBlock(buffer);
};

export const streamRealtimeQA = (body: AIAskPayload, options: Omit<StreamOptions, 'body' | 'method'>) =>
  streamAI('/ai/student/realtimeQA', { ...options, body, method: 'POST', sessionType: options.sessionType ?? 'student_realtime' });

export const streamWrongQuestionAnalysis = (body: AIAskPayload, options: Omit<StreamOptions, 'body' | 'method'>) =>
  streamAI('/ai/student/wrong-question-analysis', { ...options, body, method: 'POST', sessionType: options.sessionType ?? 'student_wrong_analysis' });

export const streamStudentReport = (options: Omit<StreamOptions, 'body'>) =>
  streamAI('/ai/student/report', { ...options, method: 'GET' });

export const streamTeacherReport = (options: Omit<StreamOptions, 'body'>) =>
  streamAI('/ai/teacher/report', { ...options, method: 'GET' });

export const streamTeacherQuestionAnalysis = (
  body: AIAskPayload,
  options: Omit<StreamOptions, 'body' | 'method'>
) => streamAI('/question-error-analysis/stream', {
  ...options,
  body,
  method: 'POST',
  sessionType: options.sessionType ?? 'teacher_question_analysis',
});

