import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { QuestionPayload, QuestionResponse, QuestionUpdatePayload } from '@/types/question';

export const getQuestionCatalogApi = async (params: { page: number; size: number; search: string; difficulty?: number }) => {
  const { data } = await request.get<ApiResponse<{ records: import('@/types/question').QuestionCatalogRow[]; total: number; pages: number }>>('/questions/teacher/page', { params });
  return data.data;
};

export const createQuestionApi = async (payload: QuestionPayload): Promise<QuestionResponse> => {
  const { data } = await request.post<ApiResponse<QuestionResponse>>('/questions/teacher/create', payload);
  return data.data;
};

export const updateQuestionApi = async (payload: QuestionUpdatePayload): Promise<QuestionResponse> => {
  const { data } = await request.put<ApiResponse<QuestionResponse>>('/questions/teacher/update', payload);
  return data.data;
};

export const getQuestionByIdApi = async (id: number): Promise<QuestionResponse> => {
  const { data } = await request.get<ApiResponse<QuestionResponse>>(`/questions/teacher/get/${id}`);
  return data.data;
};

export const getAllQuestionsApi = async (): Promise<QuestionResponse[]> => {
  const { data } = await request.get<ApiResponse<QuestionResponse[]>>('/questions/teacher/get/all');
  return data.data;
};

export const deleteQuestionApi = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<void>>(`/questions/teacher/delete/${id}`);
};

