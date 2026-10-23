import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { LearningPlanView } from '@/types/learning-plan';

export const saveLearningPlanApi = async (runId: string, requestId: string): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>('/learning-plans', { runId, requestId });
  return data.data;
};

export const getLearningPlanApi = async (id: number): Promise<LearningPlanView> => {
  const { data } = await request.get<ApiResponse<LearningPlanView>>(`/learning-plans/${id}`);
  return data.data;
};

export const getLearningPlansApi = async (): Promise<LearningPlanView[]> => {
  const { data } = await request.get<ApiResponse<LearningPlanView[]>>('/learning-plans');
  return data.data;
};

export const activateLearningPlanApi = async (id: number, expectedVersion: number): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>(`/learning-plans/${id}/activate`, { expectedVersion });
  return data.data;
};

export const cancelLearningPlanApi = async (id: number, expectedVersion: number): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>(`/learning-plans/${id}/cancel`, { expectedVersion });
  return data.data;
};
