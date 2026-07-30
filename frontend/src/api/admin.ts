import { post } from '../utils/request';

export async function banUser(userId: string): Promise<null> {
  return post<null>(`/admin/users/${userId}/ban`);
}

export async function unbanUser(userId: string): Promise<null> {
  return post<null>(`/admin/users/${userId}/unban`);
}
