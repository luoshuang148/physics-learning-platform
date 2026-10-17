import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { ExperimentResponse } from '@/types/experiment';

export interface ExperimentMutationPayload {
  id?: number;
  name: string;
  questionIds: number[];
  description?: string;
  orderNumber: number;
}

export const getAllExperimentsApi = async (): Promise<ExperimentResponse[]> => {
  const { data } = await request.get<ApiResponse<ExperimentResponse[]>>('/experiments/all');
  return data.data;
};

export const createExperimentApi = async (payload: ExperimentMutationPayload): Promise<ExperimentResponse> => {
  const { data } = await request.post<ApiResponse<ExperimentResponse>>('/experiments/teacher/create', payload);
  return data.data;
};

export const updateExperimentApi = async (payload: ExperimentMutationPayload): Promise<ExperimentResponse> => {
  const { data } = await request.put<ApiResponse<ExperimentResponse>>('/experiments/teacher/update', payload);
  return data.data;
};

export const deleteExperimentApi = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<void>>(`/experiments/teacher/delete/${id}`);
};
