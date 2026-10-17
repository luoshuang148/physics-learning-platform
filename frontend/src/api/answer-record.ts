import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { AnswerRecordBatchResponse, AnswerRecordResponse, AnswerRecordSubmitPayload, RecordBatchDeletePayload, AnswerStatsResponse } from '@/types/answer-record';

export const submitAnswerApi = async (payload: AnswerRecordSubmitPayload): Promise<AnswerRecordResponse> => {
  const { data } = await request.post<ApiResponse<AnswerRecordResponse>>('/answer-records/student/submit', payload);
  return data.data;
};

export const getMyAnswersApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>('/answer-records/student/my');
  return data.data;
};

export const getMyWrongAnswersApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>('/answer-records/student/my/wrong');
  return data.data;
};

export const getAllAnswerRecordsApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>('/answer-records/teacher/all');
  return data.data;
};

export const getAllWrongAnswerRecordsApi = async (): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>('/answer-records/teacher/wrong/all');
  return data.data;
};

export const getUserAnswerRecordsApi = async (userId: number): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(`/answer-records/user/${userId}`);
  return data.data;
};

export const getUserWrongAnswerRecordsApi = async (userId: number): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(`/answer-records/user/wrong/${userId}`);
  return data.data;
};

export const getSessionAnswerRecordsApi = async (sessionId: string): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(`/answer-records/session/${encodeURIComponent(sessionId)}`);
  return data.data;
};

export const getStudentLatestSessionRecordsApi = async (studentId: number): Promise<AnswerRecordBatchResponse> => {
  const { data } = await request.get<ApiResponse<AnswerRecordBatchResponse>>(`/answer-records/teacher/${studentId}/latest-session`);
  return data.data;
};

export const deleteAnswerRecordApi = async (recordId: string): Promise<void> => {
  await request.delete<ApiResponse<void>>(`/answer-records/teacher/${recordId}`);
};

export const batchDeleteAnswerRecordsApi = async (payload: RecordBatchDeletePayload): Promise<void> => {
  await request.delete<ApiResponse<void>>('/answer-records/teacher/batch', { data: payload });
};

export const getTotalAnswerCountApi = async (): Promise<number> => {
  const { data } = await request.get<ApiResponse<number>>('/answer-records/teacher/count');
  return data.data;
};

export const getAnswerStatsApi = async (): Promise<AnswerStatsResponse> => {
  const { data } = await request.get<ApiResponse<AnswerStatsResponse>>('/answer-records/teacher/stats');
  return data.data;
};


export const getAnswerRecordPageApi = async (params: { page: number; size: number; wrongOnly: boolean; studentName: string }) => {
  const { data } = await request.get<ApiResponse<import('@/types/common').PageResult<AnswerRecordResponse>>>(
    '/answer-records/teacher/page', { params });
  return data.data;
};
