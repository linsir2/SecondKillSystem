import { describe, it, expect, beforeEach, vi } from 'vitest';
import { pay } from '../api/payment';
import * as token from '../utils/token';

describe('api / payment', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /* ─── 工厂 ─── */

  /** 200 成功：支付成功或业务失败 */
  const mockPayResponse = (success: boolean, message: string) =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: { success, message } }),
    });

  /** 业务异常：HTTP 400, code 400 */
  const mockBizError = (message: string) =>
    vi.fn().mockResolvedValue({
      status: 400,
      json: () => Promise.resolve({ code: 400, message, data: null, errors: [] }),
    });

  /* ─── pay ─── */

  describe('pay', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/payment/pay 成功返回 orderNo/userId 字段', async () => {
      token.setTokens('user-token', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      const res = await pay('123456789', '1');

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/payment/pay', {
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer user-token',
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({ orderNo: '123456789', userId: '1' }),
      });
      expect(res.success).toBe(true);
      expect(res.message).toBe('支付成功');
    });

    it('success: true', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      const res = await pay('1', '1');
      expect(res.success).toBe(true);
    });

    it('success: false 含提示信息', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(false, '余额不足');

      const res = await pay('1', '1');
      expect(res.success).toBe(false);
      expect(res.message).toContain('余额不足');
    });

    it('message 字段透传', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(false, '支付失败：账户异常');

      const res = await pay('1', '1');
      expect(res.message).toBe('支付失败：账户异常');
    });

    it('请求体字段完整', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      await pay('999', '42');
      const callArg = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1];
      const body = JSON.parse(callArg.body);
      expect(body.orderNo).toBe('999');
      expect(body.userId).toBe('42');
    });

    /* ─── 边界 ─── */

    it('订单不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('订单不存在');

      const err = await pay('99999', '1').catch(e => e);
      expect(err.code).toBe(400);
      expect(err.message).toContain('不存在');
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await pay('1', '1').catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(pay('1', '1')).rejects.toThrow(TypeError);
    });

    it('500 服务器错误 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 500,
        json: () => Promise.resolve({ code: 500, message: '服务器内部错误', data: null, errors: null }),
      });

      const err = await pay('1', '1').catch(e => e);
      expect(err.code).toBe(500);
    });

    /* ─── 攻击场景 ─── */

    it('orderNo 字符串注入原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      await (pay as (orderNo: unknown, userId: unknown) => Promise<{ success: boolean; message: string }>)(
        '1 OR 1=1',
        '1',
      );
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('1 OR 1=1');
    });

    it('orderNo 负数原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      await pay('-1', '1');
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('-1');
    });

    it('orderNo 零值原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      await pay('0', '1');
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBe('0');
    });

    it('NaN/Infinity 被 JSON.stringify 转为 null 原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPayResponse(true, '支付成功');

      // JSON.stringify(NaN) → 'null'，JSON.stringify(Infinity) → 'null'
      // 后端将会收到 null 并自行处理
      await (pay as (orderNo: unknown, userId: unknown) => Promise<unknown>)(NaN, Infinity);
      const body = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(body.orderNo).toBeNull();
      expect(body.userId).toBeNull();
    });
  });
});
