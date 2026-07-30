import { post } from '../utils/request';
import type { PayResponse } from '../types';

export async function pay(orderNo: number, userId: number): Promise<PayResponse> {
  return post<PayResponse>('/payment/pay', { orderNo, userId });
}
