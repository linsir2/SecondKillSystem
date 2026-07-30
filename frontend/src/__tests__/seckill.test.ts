import { describe, it, expect, beforeEach, vi } from 'vitest';
import { executeSeckill } from '../api/seckill';
import * as token from '../utils/token';
import type { SeckillRequest } from '../types';

describe('api / seckill', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /* ─── 工厂 ─── */

  /** 成功响应：HTTP 200, code 200 */
  const mockSuccess = (orderToken = 'tok_abc123') =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: { orderToken } }),
    });

  /** 业务异常响应：HTTP 400, code 400, data=null, errors=[] */
  const mockBizError = (message: string) =>
    vi.fn().mockResolvedValue({
      status: 400,
      json: () => Promise.resolve({ code: 400, message, data: null, errors: [] }),
    });

  const validReq: SeckillRequest = {
    activityId: 1,
    seckillGoodsId: 10,
    buyCount: 1,
  };

  /* ─── executeSeckill ─── */

  describe('executeSeckill', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/seckill/execute 成功返回 orderToken', async () => {
      token.setTokens('user-token', 'r');
      globalThis.fetch = mockSuccess('tok_seckill_001');

      const res = await executeSeckill(validReq);

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/seckill/execute', {
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer user-token',
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify(validReq),
      });
      expect(res.orderToken).toBe('tok_seckill_001');
    });

    it('body 字段完整传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill(validReq);
      const callBody = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body;
      const parsed = JSON.parse(callBody);
      expect(parsed.activityId).toBe(1);
      expect(parsed.seckillGoodsId).toBe(10);
      expect(parsed.buyCount).toBe(1);
    });

    it('商品已售罄 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('商品已售罄');

      const err = await executeSeckill(validReq).catch(e => e);
      expect(err.message).toContain('售罄');
    });

    it('请勿重复购买 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('请勿重复购买');

      const err = await executeSeckill(validReq).catch(e => e);
      expect(err.message).toContain('重复购买');
    });

    it('超过限购数量 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('超过限购数量');

      const err = await executeSeckill(validReq).catch(e => e);
      expect(err.message).toContain('限购');
    });

    it('系统繁忙 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockBizError('系统繁忙，请重试');

      const err = await executeSeckill(validReq).catch(e => e);
      expect(err.message).toContain('系统繁忙');
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await executeSeckill(validReq).catch(e => e);
      expect(err.code).toBe(401);
    });

    /* ─── 边界 ─── */

    it('buyCount=1（最小值）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, buyCount: 1 });
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.buyCount).toBe(1);
    });

    it('buyCount=999999（超大值）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, buyCount: 999999 });
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.buyCount).toBe(999999);
    });

    /* ─── 攻击场景 ─── */

    it('seckillGoodsId 字符串注入原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, seckillGoodsId: '1 OR 1=1' } as unknown as SeckillRequest);
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.seckillGoodsId).toBe('1 OR 1=1');
    });

    it('activityId 字符串注入原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, activityId: '1; DROP TABLE' } as unknown as SeckillRequest);
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.activityId).toBe('1; DROP TABLE');
    });

    it('buyCount=-1（负数）原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, buyCount: -1 });
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.buyCount).toBe(-1);
    });

    it('buyCount=0（零值）原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, buyCount: 0 });
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.buyCount).toBe(0);
    });

    it('buyCount=null 原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      await executeSeckill({ ...validReq, buyCount: null as unknown as number });
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.buyCount).toBeNull();
    });

    it('额外攻击字段原样传递', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSuccess();

      const malicious = {
        ...validReq,
        __proto__: { admin: true },
        constructor: { prototype: { admin: true } },
      } as unknown as SeckillRequest;
      await executeSeckill(malicious);
      const callBody = JSON.parse(
        (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body,
      );
      expect(callBody.activityId).toBe(1);
    });
  });
});
