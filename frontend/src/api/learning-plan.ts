import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { LearningPlanView } from '@/types/learning-plan';

export const saveLearningPlanApi = async (
  runId: string,
  requestId: string,
): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>('/learning-plans', {
    runId,
    requestId,
  });
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

export const activateLearningPlanApi = async (
  id: number,
  expectedVersion: number,
): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>(
    `/learning-plans/${id}/activate`,
    { expectedVersion },
  );
  return data.data;
};

export const cancelLearningPlanApi = async (
  id: number,
  expectedVersion: number,
): Promise<LearningPlanView> => {
  const { data } = await request.post<ApiResponse<LearningPlanView>>(
    `/learning-plans/${id}/cancel`,
    { expectedVersion },
  );
  return data.data;
};

/** 计划条目题干与选项（不含正确答案）。 */
export const getPlanItemQuestionApi = async (planId: number, itemId: number) => {
  const { data } = await request.get<
    ApiResponse<{
      id: number;
      content: string;
      options: Record<string, string>;
      difficulty?: number;
      imageBase64?: string | null;
      knowledgePointCodes?: string[];
    }>
  >(`/learning-plans/${planId}/items/${itemId}/question`);
  return data.data;
};

/** 提交计划条目补练；返回计分与进度。 */
export const submitPlanItemAnswerApi = async (
  planId: number,
  itemId: number,
  userAnswer: string,
  requestId: string,
) => {
  const { data } = await request.post<
    ApiResponse<{
      planId: number;
      itemId: number;
      correct: boolean;
      attempts: number;
      correctCount: number;
      progressStatus: string;
    }>
  >(`/learning-plans/${planId}/items/${itemId}/answers`, { userAnswer, requestId });
  return data.data;
};
