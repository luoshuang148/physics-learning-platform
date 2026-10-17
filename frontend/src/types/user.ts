import type { UserType } from './common';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  realName: string;
  className?: string;
  userType: UserType;
}

export interface UpdateCurrentUserRequest {
  username?: string;
  password?: string;
  realName?: string;
  className?: string | null;
  userType?: UserType;
}

export interface UserAdminUpdateRequest {
  userId: number;
  realName?: string;
  className?: string | null;
  active?: boolean;
  userType?: UserType;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  userType: UserType;
  realName: string;
}

export interface UserResponse {
  id: number;
  username: string;
  realName: string;
  className: string | null;
  userType: UserType;
  active: boolean;
  createTime: string;
  lastLogin: string | null;
}
