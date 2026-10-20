import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { TeacherEventCreatePayload, TeacherEventResponse } from '@/types/teacher-event';

export const getTeacherEventApi = async (): Promise<TeacherEventResponse | null> => {
  const { data } = await request.get<ApiResponse<TeacherEventResponse | null>>('/teacher/events');
  return data.data;
};

export const getTeacherTaskOverviewApi = async (): Promise<import('@/types/teacher-event').TaskOverviewRow[]> => {
  const { data } = await request.get<ApiResponse<import('@/types/teacher-event').TaskOverviewRow[]>>('/teacher/events/overview');
  return data.data;
};

export const getTeacherTasksApi = async (): Promise<TeacherEventResponse[]> => {
  const { data } = await request.get<ApiResponse<TeacherEventResponse[]>>('/teacher/events/all');
  return data.data;
};

export const getStudentTasksApi = async (): Promise<TeacherEventResponse[]> => {
  const { data } = await request.get<ApiResponse<TeacherEventResponse[]>>('/teacher/events/available');
  return data.data;
};

export interface StudentTaskResult {
  task: TeacherEventResponse;
  answeredQuestions: number;
  correctQuestions: number;
  answerRevision: string;
  savedReport?: { content: string; answerRevision: string; generatedAt: string } | null;
}

export const getStudentTaskResultsApi = async (): Promise<StudentTaskResult[]> => {
  const { data } = await request.get<ApiResponse<StudentTaskResult[]>>('/teacher/events/student-tasks');
  return data.data;
};

export const getStudentTaskResultApi = async (eventId: number): Promise<StudentTaskResult> => {
  const { data } = await request.get<ApiResponse<StudentTaskResult>>(`/teacher/events/${eventId}/my-result`);
  return data.data;
};

export const createTeacherEventApi = async (payload: TeacherEventCreatePayload): Promise<TeacherEventResponse> => {
  const { data } = await request.post<ApiResponse<TeacherEventResponse>>('/teacher/events', payload);
  return data.data;
};

export const updateDraftTaskApi = async (eventId: number, payload: TeacherEventCreatePayload): Promise<TeacherEventResponse> => {
  const { data } = await request.put<ApiResponse<TeacherEventResponse>>('/teacher/events/' + eventId + '/draft', payload);
  return data.data;
};

export const publishDraftTaskApi = async (eventId: number): Promise<TeacherEventResponse> => {
  const { data } = await request.post<ApiResponse<TeacherEventResponse>>('/teacher/events/' + eventId + '/publish');
  return data.data;
};

export const closeTaskApi = async (eventId: number): Promise<TeacherEventResponse> => {
  const { data } = await request.post<ApiResponse<TeacherEventResponse>>('/teacher/events/' + eventId + '/close');
  return data.data;
};

export const deleteTeacherEventApi = async (eventId: number): Promise<void> => {
  await request.delete<ApiResponse<void>>('/teacher/events/' + eventId);
};

export const getTeacherEventHistoryApi = async (page = 1) => {
  const { data } = await request.get<ApiResponse<import('@/types/common').PageResult<TeacherEventResponse>>>(
    '/teacher/events/history', { params: { page, size: 10 } });
  return data.data;
};

export const replaceTeacherEventApi = async (eventId: number, payload: TeacherEventCreatePayload): Promise<TeacherEventResponse> => {
  const { data } = await request.put<ApiResponse<TeacherEventResponse>>('/teacher/events/' + eventId + '/replace', payload);
  return data.data;
};

export const getTaskQuestionsApi = async (eventId: number) => {
  const { data } = await request.get<ApiResponse<import('@/types/question').QuestionResponse[]>>('/teacher/events/' + eventId + '/questions');
  return data.data;
};

export const getTaskDetailApi = async (eventId: number): Promise<import('@/types/teacher-event').TaskDetailResponse> => {
  const { data } = await request.get<ApiResponse<import('@/types/teacher-event').TaskDetailResponse>>('/teacher/events/' + eventId + '/stats');
  return data.data;
};

export const getTaskApi = async (eventId: number) => {
  const { data } = await request.get<ApiResponse<TeacherEventResponse>>('/teacher/events/' + eventId);
  return data.data;
};
