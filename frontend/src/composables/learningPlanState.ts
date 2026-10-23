import type { LearningAssistantStep, LearningPlanPreview, LearningPlanView } from '@/types/learning-plan';

export interface LearningPlanState {
  runId: string | null;
  lastSeq: number;
  preview: LearningPlanPreview | null;
  savedPlan: LearningPlanView | null;
  steps: LearningAssistantStep[];
  error: string;
}

export const emptyLearningPlanState = (): LearningPlanState => ({
  runId: null, lastSeq: 0, preview: null, savedPlan: null, steps: [], error: '',
});

export const startLearningPlanRun = (runId: string): LearningPlanState => ({
  ...emptyLearningPlanState(), runId,
});

export const acceptLearningPlanEvent = <T extends { runId?: string; seq?: number }>(state: LearningPlanState, event: T,
                                                                                   apply: (current: LearningPlanState) => LearningPlanState): LearningPlanState => {
  if (!event.runId || event.runId !== state.runId || typeof event.seq !== 'number' || event.seq <= state.lastSeq) return state;
  return { ...apply(state), lastSeq: event.seq };
};
