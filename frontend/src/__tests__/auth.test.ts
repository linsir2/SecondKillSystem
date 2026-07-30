import { describe, it, expect, beforeEach, vi } from 'vitest';
import { login, register, logout } from '../api/auth';

describe('api / auth', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const mockOk = (data: unknown) =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data }),
    });

  const loginVO = {
    accessToken: 'at_xxx',
    refreshToken: 'rt_xxx',
    userId: 1,
    userName: 'test',
    role: 'user' as const,
  };

  /* ─── 正常路径（已有） ─── */

  it('login() 调用 POST /api/v1/auth/login', async () => {
    globalThis.fetch = mockOk(loginVO);
    await login({ email: 'a@b.com', password: '12345678' });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ email: 'a@b.com', password: '12345678' }),
      }),
    );
  });

  it('register() 调用 POST /api/v1/auth/register', async () => {
    globalThis.fetch = mockOk(loginVO);
    await register({ userName: 'n', email: 'a@b.com', password: '12345678', role: 'merchant' });
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/auth/register',
      expect.objectContaining({ 
        method: 'POST', 
        body: JSON.stringify({ userName: 'n', email: 'a@b.com', password: '12345678', role: 'merchant' }),
      }),
    );
  });

  it('register() 不传 role 时 body 不含 role', async () => {
    globalThis.fetch = mockOk(loginVO);
    await register({ userName: 'n', email: 'a@b.com', password: '12345678' });
    const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
    expect(body.role).toBeUndefined();
  });

  it('login() 返回正确的 LoginVO', async () => {
    globalThis.fetch = mockOk(loginVO);
    const result = await login({ email: 'a@b.com', password: '12345678' });
    expect(result).toEqual(loginVO);
  });

  /* ─── logout 正常路径 ─── */

  it('logout() 调用 POST /api/v1/auth/logout，body 含 refreshToken', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

    await logout('rt_token_value');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ refreshToken: 'rt_token_value' }),
      }),
    );
  });

  it('logout() 正确携带 Bearer token', async () => {
    const { setTokens } = await import('../utils/token');
    setTokens('my-at', 'my-rt');

    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

    await logout('my-rt');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer my-at' }),
      }),
    );
  });

  it('logout() 成功返回 void', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

    const result = await logout('rt');
    expect(result).toBeNull();
  });

  /* ─── 攻击场景 ─── */

  it('SQL 注入 email 原样发送', async () => {
    globalThis.fetch = mockOk(loginVO);
    await login({ email: "' OR 1=1 --", password: 'x' });
    const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
    expect(body.email).toBe("' OR 1=1 --");
  });

  it('XSS userName 原样发送', async () => {
    globalThis.fetch = mockOk(loginVO);
    await register({ userName: '<img src=x onerror=alert(1)>', email: 'x@b.com', password: '12345678' });
    const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
    expect(body.userName).toBe('<img src=x onerror=alert(1)>');
  });

  it('10000 字符密码原样发送不截断', async () => {
    globalThis.fetch = mockOk(loginVO);
    const long = 'p'.repeat(10000);
    await register({ userName: 'n', email: 'a@b.com', password: long });
    const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
    expect(body.password).toBe(long);
    expect(body.password.length).toBe(10000);
  });

  it('logout() 超长 refreshToken 原样发送', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

    const long = 't'.repeat(10000);
    await logout(long);
    const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
    expect(body.refreshToken).toBe(long);
    expect(body.refreshToken.length).toBe(10000);
  });
});