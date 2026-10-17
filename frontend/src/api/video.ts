import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { VideoCreatePayload, VideoResponse, VideoUpdatePayload } from '@/types/video';

export const createVideoApi = async (payload: VideoCreatePayload): Promise<VideoResponse> => {
  const { data } = await request.post<ApiResponse<VideoResponse>>('/video/teacher', payload);
  return data.data;
};

export const updateVideoApi = async (id: number, payload: VideoUpdatePayload): Promise<VideoResponse> => {
  const { data } = await request.put<ApiResponse<VideoResponse>>(`/video/teacher/${id}`, payload);
  return data.data;
};

export const getAllVideosApi = async (): Promise<VideoResponse[]> => {
  const { data } = await request.get<ApiResponse<VideoResponse[]>>('/video/teacher/all');
  return data.data;
};

export const deleteVideoApi = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<void>>(`/video/teacher/${id}`);
};

