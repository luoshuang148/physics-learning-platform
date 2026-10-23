export interface AIEventHandlers {
  onEvidence?: (payload: Record<string, unknown>) => void;
  onMessage?: (payload: { content: string; type?: string }) => void;
  onProgress?: (payload: {progress: number; stage?: string}) => void;
  onReasoning?: (payload: { reasoning: string }) => void;
  onComplete?: (payload: {promptTokens?: number; completionTokens?: number; totalTokens?: number; [key: string]: unknown}) => void;
  onError?: (payload: {code: number; message: string; reason?: string}) => void;
  onSources?: (payload: any[]) => void;
  onToolStep?: (payload: Record<string, unknown>) => void;
  onPlan?: (payload: Record<string, unknown>) => void;
  onKnowledgeStatus?: (payload: Record<string, unknown>) => void;
  onCitationValidation?: (payload: string[]) => void;
}

const parseJson = (data: string): { success: boolean; value?: any } => {
  try { return {success: true, value: JSON.parse(data)}; }
  catch { return {success: false}; }
};

/** Routes only recognized SSE event types. New run events must never become answer prose. */
export const dispatchAIStreamEvent = (type: string, data: string, handlers: AIEventHandlers) => {
  const parsed = parseJson(data);
  switch (type) {
    case 'evidence': handlers.onEvidence?.(parsed.success ? parsed.value : {}); break;
    case 'content': handlers.onMessage?.({content: data}); break;
    case 'message':
      if (parsed.success && parsed.value?.content) handlers.onMessage?.(parsed.value as {content: string; type?: string});
      else if (data && data !== '连接成功') handlers.onMessage?.({content: data});
      break;
    case 'progress': handlers.onProgress?.(parsed.success ? parsed.value as {progress: number; stage?: string} : {progress: parseInt(data) || 0}); break;
    case 'reasoning': handlers.onReasoning?.({reasoning: data}); break;
    case 'complete': handlers.onComplete?.(parsed.success ? parsed.value : {}); break;
    case 'error': handlers.onError?.(parsed.success ? parsed.value as {code: number; message: string; reason?: string} : {code: 0, message: data}); break;
    case 'sources': handlers.onSources?.(parsed.success && Array.isArray(parsed.value) ? parsed.value : []); break;
    case 'tool-step': handlers.onToolStep?.(parsed.success ? parsed.value : {}); break;
    case 'plan': handlers.onPlan?.(parsed.success ? parsed.value : {}); break;
    case 'knowledge-status': handlers.onKnowledgeStatus?.(parsed.success ? parsed.value : {message: data}); break;
    case 'citation-validation': handlers.onCitationValidation?.(parsed.success && Array.isArray(parsed.value) ? parsed.value : []); break;
    default: break;
  }
};
