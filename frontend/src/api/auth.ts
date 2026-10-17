import request from '@/utils/request';
import type { ApiResponse } from '@/types/common';
import type { LoginRequest, LoginResponse, RegisterRequest } from '@/types/user';

export const loginApi = async (payload: LoginRequest): Promise<LoginResponse> => {
  const { data } = await request.post<ApiResponse<LoginResponse>>('/auth/login', payload);
  return data.data;
};

export const registerApi = async (payload: RegisterRequest): Promise<void> => {
  await request.post<ApiResponse<{ username: string; userType: string }>>('/auth/register', payload);
};

export const logoutApi = async (): Promise<void> => {
  await request.post<ApiResponse<string>>('/auth/logout');
};

