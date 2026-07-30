export type UserRole = 'user' | 'merchant' | 'admin';
export type BanStatus = 'NORMAL' | 'BANNED';

export interface LoginVO {
  accessToken: string;
  refreshToken: string;
  userId: string;
  userName: string;
  role: UserRole;
}

export interface Result<T> {
  code: number;
  message: string;
  data: T;
  errors?: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserInfoVO {
  userId: string;
  userName: string;
  email: string;
  role: UserRole;
  banStatus: BanStatus;
}

export interface RegisterRequest {
  userName: string;
  email: string;
  password: string;
  role?: UserRole;
}

export interface GoodsInfo {
  goodsId: number;
  goodsName: string;
  price: number;
  stock: number;
}

export interface GoodsVO {
  goodsId: number;
  goodsName: string;
  price: number;
  status: number;
  stock: number;
  createdAt: string;
}

export interface CreateGoodsRequest {
  goodsName: string;
  price: number;
  stock: number;
}

export interface UpdateGoodsRequest {
  goodsName: string;
  price: number;
  stock: number;
}

/* ─── Activity ─── */

export interface SeckillGoodsVO {
  seckillGoodsId: number;
  goodsId: number;
  goodsName: string;
  seckillPrice: number;
  stock: number;
  limitNum: number;
}

export interface ActivityVO {
  activityId: number;
  activityName: string;
  merchantId: number;
  status: string;
  startTime: string;
  endTime: string;
  description: string;
  seckillGoodsList: SeckillGoodsVO[];
  createdAt: string;
  rejectReason: string | null;
}

export interface PageVO<T> {
  records: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateSeckillGoodsItem {
  goodsId: number;
  seckillPrice: number;
  stock: number;
  limitNum: number;
}

export interface CreateActivityRequest {
  activityName: string;
  startTime: string;
  endTime: string;
  description: string;
  seckillGoodsList: CreateSeckillGoodsItem[];
}

/* ─── Seckill ─── */

export interface SeckillRequest {
  activityId: number;
  seckillGoodsId: number;
  buyCount: number;
}

export interface SeckillResponse {
  orderToken: string;
}

/* ─── Order ─── */

export interface OrderStatusVO {
  status: string | null;
  orderNo: string | null;
}

/* ─── Payment ─── */

export interface PayResponse {
  success: boolean;
  message: string;
}

/* ─── Message ─── */

export interface MessageVO {
  messageId: number;
  type: string;
  content: string;
  activityId: number | null;
  read: boolean;
  createdAt: string;
}

