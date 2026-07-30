import { get, post } from '../utils/request';
import type { OrderStatusVO } from '../types';

export async function getOrderStatus(token: string): Promise<OrderStatusVO> {
  return get<OrderStatusVO>(`/order/status?token=${token}`);
}

export async function cancelOrder(orderNo: number): Promise<null> {
  return post<null>('/order/cancel', { orderNo });
}
