import { get, post } from '../utils/request';
import type { ActivityVO, CreateActivityRequest, PageVO } from '../types';

export async function listActivities(page = 1, pageSize = 10): Promise<PageVO<ActivityVO>> {
  return get<PageVO<ActivityVO>>(`/activity?page=${page}&pageSize=${pageSize}`);
}

export async function getActivityDetail(activityId: string): Promise<ActivityVO> {
  return get<ActivityVO>(`/activity/${activityId}`);
}

export async function createActivity(data: CreateActivityRequest): Promise<ActivityVO> {
  return post<ActivityVO>('/activity', data);
}

export async function submitForReview(activityId: string): Promise<ActivityVO> {
  return post<ActivityVO>(`/activity/${activityId}/submit`);
}

export async function approveActivity(activityId: string): Promise<ActivityVO> {
  return post<ActivityVO>(`/activity/${activityId}/approve`);
}

export async function rejectActivity(activityId: string, reason: string): Promise<ActivityVO> {
  return post<ActivityVO>(`/activity/${activityId}/reject`, { reason });
}
