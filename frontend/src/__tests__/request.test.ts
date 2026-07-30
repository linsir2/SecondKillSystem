import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { post, get } from '../utils/request';
import * as token from '../utils/token';

describe('utils / request', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  /* ─── 正常路径 ─── */

  it('POST 请求发送正确 JSON body 和 Content-Type', async () => {
    const mock = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: { id: 1 } }),
    });
    globalThis.fetch = mock;

    const result = await post<{ id: number }>('/test', { name: 'foo' });

    expect(mock).toHaveBeenCalledWith(
      '/api/v1/test',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ name: 'foo' }),
      }),
    );
    expect(result).toEqual({ id: 1 });
  });

  it('GET 请求不发送 body', async () => {
    const mock = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: [] }),
    });
    globalThis.fetch = mock;

    await get('/test');

    expect(mock).toHaveBeenCalledWith(
      '/api/v1/test',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('有 token 时注入 Authorization header', async () => {
    token.setTokens('my-token', 'my-refresh');
    const mock = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });
    globalThis.fetch = mock;

    await post('/test');

    expect(mock).toHaveBeenCalledWith(
      '/api/v1/test',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer my-token' }),
      }),
    );
  });

  /* ─── 边界 ─── */

  it('无 token 时不带 Authorization header', async () => {
    const mock = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });
    globalThis.fetch = mock;

    await post('/test');

    const headers: Record<string, string> = mock.mock.calls[0][1].headers;
    expect(headers.Authorization).toBeUndefined();
  });

  it('204 响应不崩溃', async () => {
    const mock = vi.fn().mockResolvedValue({ status: 204 });
    globalThis.fetch = mock;

    const result = await post<void>('/test');
    expect(result).toBeUndefined();
  });

  it('网络异常抛可捕获错误', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(post('/test')).rejects.toThrow(TypeError);
  });

  /* ─── 攻击场景 ─── */

  it('401 时尝试刷新 token，刷新失败则清除 token 并抛 AuthError', async () => {
    const spy = vi.spyOn(token, 'removeTokens');
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 401,
      json: () => Promise.resolve({ code: 401, message: '登录已过期' }),
    });

    await expect(post('/test')).rejects.toThrow(/登录/);
    expect(spy).toHaveBeenCalledOnce();
  });

  it('响应 __proto__ 字段不污染 Object.prototype', async () => {
    const protoBefore = Object.prototype;
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({
        code: 200,
        message: 'success',
        data: { userId: 1 },
        __proto__: { polluted: true },
      }),
    });

    await post('/test');
    expect(({} as Record<string, unknown>).polluted).toBeUndefined();
    expect(Object.prototype).toBe(protoBefore);
  });

  it('非 200 code 抛 ApiError', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 400,
      json: () => Promise.resolve({
        code: 400,
        message: '参数错误',
        errors: ['email 格式不正确'],
      }),
    });

    const err = await post('/test').catch((e) => e);
    expect(err.code).toBe(400);
    expect(err.message).toBe('参数错误');
    expect(err.errors).toEqual(['email 格式不正确']);
  });

  it('路径穿越原样发送', async () => {
    const mock = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });
    globalThis.fetch = mock;

    await post('/../secret');
    expect(mock).toHaveBeenCalledWith('/api/v1/../secret', expect.anything());
  });
});
