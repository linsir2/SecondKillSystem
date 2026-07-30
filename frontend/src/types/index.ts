export type UserRole = 'user' | 'merchant' | 'admin';
export type BanStatus = 'NORMAL' | 'BANNED';

export interface LoginVO {
  accessToken: string;
  refreshToken: string;
  userId: number;
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
  userId: number;
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

