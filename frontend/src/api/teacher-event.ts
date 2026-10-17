import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { TeacherEventCreatePayload, TeacherEventResponse } from '@/types/teacher-event';

export const getTeacherEventApi = async (): Promise<TeacherEventResponse | null> => {
  const { data } = await request.get<ApiResponse<TeacherEventResponse | null>>('/teacher/events');
  return data.data;
};

export const createTeacherEventApi = async (payload: TeacherEventCreatePayload): Promise<TeacherEventResponse> => {
  const { data } = await request.post<ApiResponse<TeacherEventResponse>>('/teacher/events', payload);
  return data.data;
};

export const deleteTeacherEventApi = async (): Promise<void> => {
  await request.delete<ApiResponse<void>>('/teacher/events');
};

