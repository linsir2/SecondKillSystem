import { get, post } from '../utils/request';
import type { MessageVO } from '../types';

export async function listMessages(page = 1, size = 20): Promise<MessageVO[]> {
  return get<MessageVO[]>(`/messages?page=${page}&size=${size}`);
}

export async function countUnread(): Promise<number> {
  return get<number>('/messages/unread/count');
}

export async function markAsRead(messageId: number): Promise<null> {
  return post<null>(`/messages/${messageId}/read`, undefined, 'PUT');
}
