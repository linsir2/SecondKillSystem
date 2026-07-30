import { describe, it, expect, beforeEach, vi } from 'vitest';
import { getMe } from '../api/user';
import * as token from '../utils/token';

describe('api / user', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const userInfoVO = {
    userId: 1,
    userName: 'test',
    email: 'a@b.com',
    role: 'user' as const,
    banStatus: 'NORMAL' as const,
  };

  /* ─── 正常路径 ─── */

  it('getMe() 带 token 调用 GET /api/v1/user/me', async () => {
    token.setTokens('my-token', 'my-refresh');
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: userInfoVO }),
    });

    await getMe();

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/user/me',
      expect.objectContaining({
        method: 'GET',
        headers: expect.objectContaining({ Authorization: 'Bearer my-token' }),
      }),
    );
  });

  it('getMe() 返回完整 UserInfoVO', async () => {
    token.setTokens('t', 'r');
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: userInfoVO }),
    });

    const result = await getMe();
    expect(result).toEqual(userInfoVO);
  });

  /* ─── 边界 ─── */

  it('getMe() 无 token 也能发出请求（后端会返回 401）', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 401,
      json: () => Promise.resolve({ code: 401, message: '未登录' }),
    });

    const err = await getMe().catch((e) => e);
    expect(err.code).toBe(401);
    expect(err.message).toMatch(/登录/);
  });

  it('getMe() 网络异常 → TypeError', async () => {
    token.setTokens('t', 'r');
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(getMe()).rejects.toThrow(TypeError);
  });

  /* ─── 攻击场景 ─── */

  it('响应 __proto__ 不污染 Object.prototype', async () => {
    token.setTokens('t', 'r');
    const protoBefore = Object.prototype;
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({
        code: 200, message: 'success',
        data: userInfoVO,
        __proto__: { polluted: true },
      }),
    });

    await getMe();
    expect(({} as Record<string, unknown>).polluted).toBeUndefined();
    expect(Object.prototype).toBe(protoBefore);
  });

  it('响应 XSS userName 原样返回', async () => {
    token.setTokens('t', 'r');
    const xss = '<script>alert(1)</script>';
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({
        code: 200, message: 'success',
        data: { ...userInfoVO, userName: xss },
      }),
    });

    const result = await getMe();
    expect(result.userName).toBe(xss);
  });
});
