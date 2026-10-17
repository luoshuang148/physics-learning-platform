import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { TeacherQuestionErrorDetails, TeacherQuestionErrorSummary } from '@/types/ai';

export const getQuestionErrorStatisticsApi = async (): Promise<TeacherQuestionErrorSummary[]> => {
  const { data } = await request.get<ApiResponse<TeacherQuestionErrorSummary[]>>('/question-error-analysis/statistics');
  return data.data;
};

export const getQuestionErrorDetailsApi = async (questionId: number): Promise<TeacherQuestionErrorDetails> => {
  const { data } = await request.get<ApiResponse<TeacherQuestionErrorDetails>>(`/question-error-analysis/${questionId}/details`);
  return data.data;
};

