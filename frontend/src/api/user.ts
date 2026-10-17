import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { UpdateCurrentUserRequest, UserAdminUpdateRequest, UserResponse } from '@/types/user';

export const getCurrentUserApi = async (): Promise<UserResponse> => {
  const { data } = await request.get<ApiResponse<UserResponse>>('/user/me');
  return data.data;
};

export const updateCurrentUserApi = async (payload: UpdateCurrentUserRequest): Promise<UserResponse> => {
  const { data } = await request.put<ApiResponse<UserResponse>>('/user/me', payload);
  return data.data;
};

export const getUserByIdApi = async (id: number): Promise<UserResponse> => {
  const { data } = await request.get<ApiResponse<UserResponse>>(`/user/teacher/${id}`);
  return data.data;
};

export const getUserByRealNameApi = async (realName: string): Promise<UserResponse> => {
  const { data } = await request.get<ApiResponse<UserResponse>>(`/user/teacher/realname/${encodeURIComponent(realName)}`);
  return data.data;
};

export const getAllUsersApi = async (): Promise<UserResponse[]> => {
  const { data } = await request.get<ApiResponse<UserResponse[]>>('/user/teacher/all');
  return data.data;
};

export const updateUserByAdminApi = async (payload: UserAdminUpdateRequest): Promise<UserResponse> => {
  const { data } = await request.put<ApiResponse<UserResponse>>('/user/teacher/update', payload);
  return data.data;
};

export const deleteUserByAdminApi = async (id: number): Promise<void> => {
  await request.delete<ApiResponse<void>>(`/user/teacher/${id}`);
};
