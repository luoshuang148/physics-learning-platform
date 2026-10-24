export interface LearningPlanItem {
  id?: number;
  day: number;
  knowledgePointCode: string;
  questionId: number;
  reasonSourceIds: string[];
}

export interface LearningPlanPreview {
  days: number;
  sourceEventId: number;
  sourceAnswerRevision: string;
  executionMode: 'WORKFLOW' | 'AGENT';
  items: LearningPlanItem[];
  evidenceSummary: string[];
  partial: boolean;
}

export interface LearningPlanView {
  id: number;
  eventId: number;
  status: 'DRAFT' | 'ACTIVE' | 'CANCELLED' | 'COMPLETED';
  version: number;
  days: number;
  items: LearningPlanItem[];
  sourceAnswerRevision: string;
  practiceAttempts: number;
  practiceCorrect: number;
  progressStatus: 'NOT_STARTED' | 'ON_TRACK' | 'IN_PROGRESS' | 'CANCELLED';
  executionMode: 'WORKFLOW' | 'AGENT';
  evidenceSummary: string[];
}

export interface LearningAssistantStep {
  runId: string;
  seq: number;
  toolName: string;
  status: 'SUCCEEDED' | 'FAILED';
  summary: string;
}
