import { describe, it, expect, beforeEach, vi } from 'vitest';
import { getOrderStatus, cancelOrder } from '../api/order';
import * as token from '../utils/token';

describe('api / order', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /* ─── 工厂 ─── */

  /** GET /status 成功响应 */
  const mockStatus = (status: string | null, orderNo: string | null) =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: { status, orderNo } }),
    });

  /** POST /cancel 成功响应 (data: null) */
  const mockCancelOk = () =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

  /** 业务异常 */
  const mockBizError = (message: string) =>
    vi.fn().mockResolvedValue({
      status: 400,
      json: () => Promise.resolve({ code: 400, message, data: null, errors: [] }),
    });

  /* ─── getOrderStatus ─── */

  describe('getOrderStatus', () => {
    /* ─── Normal ─── */

    it('GET /api/v1/order/status?token=xxx 返回 status 和 orderNo', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockStatus('PAID', '123456789');

      const res = await getOrderStatus('tok_seckill_001');

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/order/status?token=tok_seckill_001',
        expect.objectContaining({ method: 'GET' }),
      );
      expect(res.status).toBe('PAID');
      expect(res.orderNo).toBe('123456789');
    });

    it('status 各状态原文透传 (UNPAID / PAID / CANCELLED)', async () => {
      token.setTokens('t', 'r');

      for (const s of ['UNPAID', 'PAID', 'CANCELLED'] as const) {
        globalThis.fetch = mockStatus(s, '1');
        const res = await getOrderStatus('tok');
        expect(res.status).toBe(s);
      }
    });

    it('status 和 orderNo 同时为 null（尚未查到）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockStatus(null, null);

      const res = await getOrderStatus('tok');
      expect(res.status).toBeNull();
      expect(res.orderNo).toBeNull();
    });

    /* ─── Boundary ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await getOrderStatus('tok').catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(getOrderStatus('tok')).rejects.toThrow(TypeError);
    });

    /* ─── Attack ─── */

    it('token SQL 注入原样拼接 URL', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockStatus(null, null);

      await getOrderStatus('1 OR 1=1');
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/order/status?token=1 OR 1=1',
        expect.anything(),
      );
    });

    it('token 含 URL 特殊字符原样拼接', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockStatus(null, null);

      await getOrderStatus('a&b?c=d');
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/order/status?token=a&b?c=d',
        expect.anything(),
      );
    });
  });

  /* ─── cancelOrder ─── */

  describe('cancelOrder', () => {
    /* ─── Normal ─── */

    it('POST /api/v1/order/cancel 正确调用', async () => {
      token.setTokens('user-token', 'r');
      globalThis.fetch = mockCancelOk();

      const res = await cancelOrder('123456789');

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/order/cancel', {
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer user-token',
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({ orderNo: '123456789' }),
      });
      expect(res).toBeNull();
    });

    it('返回 data: null', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCancelOk();

      const res = await cancelOrder('1');
      expect(res).toBeNull();
    });

    /* ─── Boundary ─── */

    it('订单不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('订单不存在');

      const err = await cancelOrder('99999').catch(e => e);
      expect(err.code).toBe(400);
      expect(err.message).toContain('不存在');
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await cancelOrder('1').catch(e => e);
      expect(err.code).toBe(401);
    });

    /* ─── Attack ─── */

    it('orderNo 字符串注入原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCancelOk();

      await (cancelOrder as (o: unknown) => Promise<null>)('1 OR 1=1');
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('1 OR 1=1');
    });

    it('orderNo 负数原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCancelOk();

      await cancelOrder('-1');
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('-1');
    });

    it('orderNo 零值原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCancelOk();

      await cancelOrder('0');
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('0');
    });
  });
});
