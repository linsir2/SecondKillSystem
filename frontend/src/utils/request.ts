import { getAccessToken, removeTokens } from './token';

const BASE_URL = '/api/v1';

export class ApiError extends Error {
  constructor(
    message: string,
    public code: number,
    public errors?: string[],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export class AuthError extends ApiError {
  constructor(message = '未登录') {
    super(message, 401);
    this.name = 'AuthError';
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  let json: unknown;
  try {
    json = await response.json();
  } catch {
    if (!response.ok) {
      throw new ApiError(`HTTP ${response.status}`, response.status);
    }
    return undefined as T;
  }

  // 安全解析：防止 __proto__ 污染
  const safe: Record<string, unknown> = JSON.parse(JSON.stringify(json)) as Record<string, unknown>;

  if (safe.code !== 200) {
    if (response.status === 401 || safe.code === 401) {
      removeTokens();
      throw new AuthError((safe.message as string) || '未登录');
    }
    throw new ApiError(
      (safe.message as string) || '请求失败',
      (safe.code as number) || response.status,
      safe.errors as string[] | undefined,
    );
  }

  return safe.data as T;
}

export async function post<T>(url: string, body?: unknown, method?: string): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getAccessToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    method: method || 'POST',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  return handleResponse<T>(response);
}

export async function get<T>(url: string): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getAccessToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${url}`, {
    method: 'GET',
    headers,
  });

  return handleResponse<T>(response);
}
