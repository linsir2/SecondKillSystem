import { getAccessToken, getRefreshToken, removeTokens, setTokens } from './token';

const BASE_URL = '/api/v1';

// ── 错误类型 ──────────────────────────────────────────────

export class ApiError extends Error {
  code: number;
  errors?: string[];
  constructor(message: string, code: number, errors?: string[]) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.errors = errors;
  }
}

export class AuthError extends ApiError {
  constructor(message = '登录已过期，请重新登录') {
    super(message, 401);
    this.name = 'AuthError';
  }
}

// ── 静默 token 刷新 ──────────────────────────────────────

let refreshPromise: Promise<boolean> | null = null;

/**
 * 用 refreshToken 换取新 token（直接 fetch，不经过业务拦截）。
 */
async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;

  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    const json: unknown = await res.json();
    const safe = JSON.parse(JSON.stringify(json)) as Record<string, unknown>;
    if (safe.code === 200) {
      const data = safe.data as Record<string, unknown>;
      setTokens(data.accessToken as string, data.refreshToken as string);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

/** 获取/等待刷新 Promise，并发 401 只触发一次刷新。 */
function acquireRefreshPromise(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

// ── HTTP 底层 ────────────────────────────────────────────

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
    throw new ApiError(
      (safe.message as string) || '请求失败',
      (safe.code as number) || response.status,
      safe.errors as string[] | undefined,
    );
  }

  return safe.data as T;
}

function buildHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = getAccessToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/**
 * fetch + 401 自动刷新重试。
 * 注意：仅对 accessToken 过期导致的 401 生效。
 * 若 refreshToken 也过期 → 清除 token + 抛出 AuthError。
 */
async function fetchWithRetry<T>(url: string, options: RequestInit): Promise<T> {
  let resp = await fetch(url, options);

  if (resp.status === 401) {
    const refreshed = await acquireRefreshPromise();
    if (refreshed) {
      // 用新 token 重试原请求
      const token = getAccessToken();
      const headers = options.headers as Record<string, string>;
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      } else {
        delete headers['Authorization'];
      }
      resp = await fetch(url, options);
    } else {
      removeTokens();
      throw new AuthError();
    }
  }

  return handleResponse<T>(resp);
}

// ── 对外接口 ──────────────────────────────────────────────

export async function post<T>(url: string, body?: unknown, method?: string): Promise<T> {
  return fetchWithRetry<T>(`${BASE_URL}${url}`, {
    method: method || 'POST',
    headers: buildHeaders(),
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export async function get<T>(url: string): Promise<T> {
  return fetchWithRetry<T>(`${BASE_URL}${url}`, {
    method: 'GET',
    headers: buildHeaders(),
  });
}
