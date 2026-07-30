import { post } from '../utils/request';
import type { LoginVO, LoginRequest, RegisterRequest } from '../types';

export async function login(data: LoginRequest): Promise<LoginVO> {
  return post<LoginVO>('/auth/login', data);
}

export async function register(data: RegisterRequest): Promise<LoginVO> {
  return post<LoginVO>('/auth/register', data);
}

export async function logout(refreshToken: string): Promise<void> {
  return post<void>('/auth/logout', { refreshToken });
}
