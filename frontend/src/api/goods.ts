import { get, post } from '../utils/request';
import type { GoodsInfo, GoodsVO, CreateGoodsRequest, UpdateGoodsRequest } from '../types';

export async function getGoodsDetail(goodsId: string): Promise<GoodsInfo> {
  return get<GoodsInfo>(`/goods/${goodsId}`);
}

export async function listMerchantGoods(): Promise<GoodsVO[]> {
  return get<GoodsVO[]>('/goods');
}

export async function createGoods(data: CreateGoodsRequest): Promise<GoodsVO> {
  return post<GoodsVO>('/goods', data);
}

export async function updateGoods(goodsId: string, data: UpdateGoodsRequest): Promise<GoodsVO> {
  return post<GoodsVO>(`/goods/${goodsId}`, data, 'PUT');
}

export async function listGoods(goodsId: string): Promise<GoodsVO> {
  return post<GoodsVO>(`/goods/${goodsId}/list`);
}

export async function delistGoods(goodsId: string): Promise<GoodsVO> {
  return post<GoodsVO>(`/goods/${goodsId}/delist`);
}
