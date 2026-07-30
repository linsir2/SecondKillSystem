import { post } from '../utils/request';

export async function banUser(userId: number): Promise<null> {
  return post<null>(`/admin/users/${userId}/ban`);
}

export async function unbanUser(userId: number): Promise<null> {
  return post<null>(`/admin/users/${userId}/unban`);
}
