import type { ApiResponse, SSECompleteEventPayload, SSEErrorEventPayload, SSEMessageEventPayload, SSEProgressEventPayload } from '@/types/common';
import type { AIAskPayload, ConversationHistory, StudentReportResponse } from '@/types/ai';
import request, { resolveApiBaseUrl } from '@/utils/request';
import { getSessionId } from '@/utils/session';
import { requiredAICapability } from '@/utils/aiCapabilities';
import { dispatchAIStreamEvent } from '@/utils/aiEventDispatch';

export const getSessionHistoryApi = async (sessionType?: string, recordId?: string): Promise<ConversationHistory[]> => {
  const sessionId = getSessionId(sessionType);
  const { data } = await request.get<ApiResponse<ConversationHistory[]>>('/ai/session/history', {
    headers: {
      'X-Session-Id': sessionId,
    },
    params: recordId ? {recordId} : undefined,
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

export const getStudentLatestReportApi = async (studentId: number, eventId?: number): Promise<StudentReportResponse> => {
  const { data } = await request.get<ApiResponse<StudentReportResponse>>(`/ai/teacher/report/${studentId}`, { params: { eventId } });
  return data.data;
};

export interface StreamOptions {
  method?: 'GET' | 'POST';
  body?: AIAskPayload;
  sessionType?: string;
  signal?: AbortSignal;
  onEvidence?: (payload: Record<string, unknown>) => void;
  onMessage?: (payload: SSEMessageEventPayload) => void;
  onProgress?: (payload: SSEProgressEventPayload) => void;
  onReasoning?: (payload: { reasoning: string }) => void;
  onComplete?: (payload: SSECompleteEventPayload) => void;
  onError?: (payload: SSEErrorEventPayload) => void;
  onSources?: (payload: import('@/types/ai').AISource[]) => void;
  onToolStep?: (payload: Record<string, unknown>) => void;
  onPlan?: (payload: Record<string, unknown>) => void;
  onKnowledgeStatus?: (payload: Record<string, unknown>) => void;
  onCitationValidation?: (payload: string[]) => void;
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
  const capability = await getAICapabilitiesApi();
  const available = capability[requiredAICapability(endpoint)];
  if (!available) throw new Error('AI 暂不可用，请稍后重试或联系教师。');
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
    if (response.status === 401) window.dispatchEvent(new CustomEvent('auth-expired'));
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

    dispatchAIStreamEvent(parsed.type, parsed.data, options);
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

export interface LearningPlanPreviewPayload { eventId: number; days: number; question: string; requestId: string; }
export const streamLearningPlanPreview = (body: LearningPlanPreviewPayload, options: Omit<StreamOptions, 'body' | 'method'>) =>
  streamAI('/ai/student/learning-plan/preview', { ...options, body, method: 'POST', sessionType: options.sessionType ?? 'student_learning_assistant' });

export const streamStudentReport = (options: Omit<StreamOptions, 'body'> & { eventId?: number }) =>
  streamAI(`/ai/student/report${options.eventId == null ? '' : `?eventId=${options.eventId}`}`, { ...options, method: 'GET' });

export const streamTeacherReport = (options: Omit<StreamOptions, 'body'> & { eventId?: number }) =>
  streamAI(`/ai/teacher/report${options.eventId == null ? '' : `?eventId=${options.eventId}`}`, { ...options, method: 'GET' });

export interface AICapabilities { reports: boolean; tutoring: boolean; knowledgeReady?: boolean; workflowReady?: boolean; agentReady?: boolean; notice: string; reportStatus?: { state: string; checkedAt: string | null }; tutoringStatus?: { state: string; checkedAt: string | null }; }
export const getAICapabilitiesApi = async (): Promise<AICapabilities> => {
  const {data}=await request.get('/ai/capabilities'); return data.data;
};
