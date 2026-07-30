import { get } from '../utils/request';
import type { UserInfoVO } from '../types';

export async function getMe(): Promise<UserInfoVO> {
  return get<UserInfoVO>('/user/me');
}
