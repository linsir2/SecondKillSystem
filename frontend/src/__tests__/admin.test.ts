import { describe, it, expect, beforeEach, vi } from 'vitest';
import { banUser, unbanUser } from '../api/admin';
import * as token from '../utils/token';

describe('api / admin', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const mockNull = () =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

  /* ─── banUser ─── */

  describe('banUser', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/admin/users/{userId}/ban 带 Bearer', async () => {
      token.setTokens('admin-token', 'admin-refresh');
      globalThis.fetch = mockNull();

      await banUser('1');

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/admin/users/1/ban',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
        }),
      );
    });

    it('返回 null（data: null）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      const result = await banUser('1');
      expect(result).toBeNull();
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await banUser('1').catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(banUser('1')).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 userId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      await (banUser as (id: unknown) => Promise<null>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/admin/users/1 OR 1=1/ban', expect.anything());
    });

    it('超长 userId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      const huge = '9999999999999';
      await banUser(huge);
      expect(globalThis.fetch).toHaveBeenCalledWith(`/api/v1/admin/users/${huge}/ban`, expect.anything());
    });
  });

  /* ─── unbanUser ─── */

  describe('unbanUser', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/admin/users/{userId}/unban 带 Bearer', async () => {
      token.setTokens('admin-token', 'admin-refresh');
      globalThis.fetch = mockNull();

      await unbanUser('1');

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/admin/users/1/unban',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
        }),
      );
    });

    it('返回 null（data: null）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      const result = await unbanUser('1');
      expect(result).toBeNull();
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await unbanUser('1').catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(unbanUser('1')).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 userId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      await (unbanUser as (id: unknown) => Promise<null>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/admin/users/1 OR 1=1/unban', expect.anything());
    });

    it('超长 userId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      const huge = '9999999999999';
      await unbanUser(huge);
      expect(globalThis.fetch).toHaveBeenCalledWith(`/api/v1/admin/users/${huge}/unban`, expect.anything());
    });
  });
});
