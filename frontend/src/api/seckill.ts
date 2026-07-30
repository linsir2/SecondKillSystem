import { post } from '../utils/request';
import type { SeckillRequest, SeckillResponse } from '../types';

export async function executeSeckill(data: SeckillRequest): Promise<SeckillResponse> {
  return post<SeckillResponse>('/seckill/execute', data);
}
