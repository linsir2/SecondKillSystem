import { describe, it, expect, beforeEach, vi } from 'vitest';
import { getGoodsDetail, listMerchantGoods, createGoods, updateGoods, listGoods, delistGoods } from '../api/goods';
import * as token from '../utils/token';

describe('api / goods', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const goodsInfo = {
    goodsId: 1,
    goodsName: 'iPhone 15',
    price: 6999,
    stock: 100,
  };

  const goodsVO = { goodsId: 1, goodsName: 'iPhone 15', price: 6999, status: 1, stock: 100, createdAt: '2026-07-30T10:00:00' };

  const goodsVOList = [
    goodsVO,
    { goodsId: 2, goodsName: 'MacBook Pro', price: 14999, status: 0, stock: 50, createdAt: '2026-07-29T10:00:00' },
  ];

  /* ─── getGoodsDetail ─── */

  describe('getGoodsDetail', () => {
    /* ─── 正常路径 ─── */

    it('带 token 调用 GET /api/v1/goods/{goodsId}', async () => {
      token.setTokens('my-token', 'my-refresh');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsInfo }),
      });

      await getGoodsDetail(1);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods/1',
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({ Authorization: 'Bearer my-token' }),
        }),
      );
    });

    it('返回完整 GoodsInfo', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsInfo }),
      });

      const result = await getGoodsDetail(1);
      expect(result).toEqual(goodsInfo);
    });

    /* ─── 边界 ─── */

    it('无 token 也能发出请求（后端返回 401）', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await getGoodsDetail(1).catch((e) => e);
      expect(err.code).toBe(401);
      expect(err.message).toMatch(/登录/);
    });

    it('goodsId=0 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsInfo }),
      });

      await getGoodsDetail(0);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/goods/0', expect.anything());
    });

    it('goodsId=-1 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 400,
        json: () => Promise.resolve({ code: 400, message: '参数错误' }),
      });

      const err = await getGoodsDetail(-1).catch((e) => e);
      expect(err.code).toBe(400);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(getGoodsDetail(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 goodsId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsInfo }),
      });

      await (getGoodsDetail as (id: unknown) => Promise<unknown>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/goods/1 OR 1=1', expect.anything());
    });

    it('超长 goodsId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsInfo }),
      });

      const huge = 9999999999999;
      await getGoodsDetail(huge);
      expect(globalThis.fetch).toHaveBeenCalledWith(`/api/v1/goods/${huge}`, expect.anything());
    });

    it('响应 __proto__ 不污染 Object.prototype', async () => {
      token.setTokens('t', 'r');
      const protoBefore = Object.prototype;
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({
          code: 200, message: 'success',
          data: goodsInfo,
          __proto__: { polluted: true },
        }),
      });

      await getGoodsDetail(1);
      expect(({} as Record<string, unknown>).polluted).toBeUndefined();
      expect(Object.prototype).toBe(protoBefore);
    });

    it('响应 XSS goodsName 原样返回', async () => {
      token.setTokens('t', 'r');
      const xss = '<script>alert(1)</script>';
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({
          code: 200, message: 'success',
          data: { ...goodsInfo, goodsName: xss },
        }),
      });

      const result = await getGoodsDetail(1);
      expect(result.goodsName).toBe(xss);
    });
  });

  /* ─── listMerchantGoods ─── */

  describe('listMerchantGoods', () => {
    /* ─── 正常路径 ─── */

    it('带 token 调用 GET /api/v1/goods', async () => {
      token.setTokens('my-token', 'my-refresh');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVOList }),
      });

      await listMerchantGoods();

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods',
        expect.objectContaining({
          method: 'GET',
          headers: expect.objectContaining({ Authorization: 'Bearer my-token' }),
        }),
      );
    });

    it('返回完整 GoodsVO[]（含 status、createdAt）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVOList }),
      });

      const result = await listMerchantGoods();
      expect(result).toHaveLength(2);
      expect(result[0]).toHaveProperty('status', 1);
      expect(result[0]).toHaveProperty('createdAt');
      expect(result[0].goodsName).toBe('iPhone 15');
    });

    it('返回空数组', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: [] }),
      });

      const result = await listMerchantGoods();
      expect(result).toEqual([]);
    });

    /* ─── 边界 ─── */

    it('无 token 发出请求（后端返回 401）', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await listMerchantGoods().catch((e) => e);
      expect(err.code).toBe(401);
      expect(err.message).toMatch(/登录/);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(listMerchantGoods()).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('响应 __proto__ 不污染 Object.prototype', async () => {
      token.setTokens('t', 'r');
      const protoBefore = Object.prototype;
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({
          code: 200, message: 'success',
          data: goodsVOList,
          __proto__: { polluted: true },
        }),
      });

      await listMerchantGoods();
      expect(({} as Record<string, unknown>).polluted).toBeUndefined();
      expect(Object.prototype).toBe(protoBefore);
    });

    it('列表中 XSS goodsName 原样返回', async () => {
      token.setTokens('t', 'r');
      const xss = '<img src=x onerror=alert(1)>';
      const listWithXss = [
        { ...goodsVOList[0], goodsName: xss },
        ...goodsVOList.slice(1),
      ];
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: listWithXss }),
      });

      const result = await listMerchantGoods();
      expect(result[0].goodsName).toBe(xss);
    });

    it('响应 data 为数组时 __proto__ 不污染', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({
          code: 200, message: 'success',
          data: goodsVOList,
          __proto__: { polluted: true },
        }),
      });

      const result = await listMerchantGoods();
      // data 本身是数组，不会被 __proto__ 影响
      expect(Array.isArray(result)).toBe(true);
      expect(result).toHaveLength(2);
    });
  });

  /* ─── createGoods ─── */

  describe('createGoods', () => {
    const createReq = { goodsName: '新商品', price: 1999.99, stock: 50 };

    /* ─── 正常路径 ─── */

    it('POST /api/v1/goods 带 Bearer，body 含 goodsName/price/stock', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await createGoods(createReq);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer t' }),
          body: JSON.stringify(createReq),
        }),
      );
    });

    it('返回完整 GoodsVO（含 goodsId/status/createdAt）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const result = await createGoods(createReq);
      expect(result).toEqual(goodsVO);
      expect(result).toHaveProperty('status');
      expect(result).toHaveProperty('createdAt');
    });

    it('price 为小数正确发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await createGoods({ goodsName: '测试', price: 0.01, stock: 1 });
      const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
      expect(body.price).toBe(0.01);
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await createGoods(createReq).catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(createGoods(createReq)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('goodsName XSS 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const xss = '<script>alert("xss")</script>';
      await createGoods({ ...createReq, goodsName: xss });
      const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
      expect(body.goodsName).toBe(xss);
    });

    it('超长 goodsName(10000 字符)原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const long = 'n'.repeat(10000);
      await createGoods({ ...createReq, goodsName: long });
      const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
      expect(body.goodsName).toBe(long);
      expect(body.goodsName.length).toBe(10000);
    });
  });

  /* ─── updateGoods ─── */

  describe('updateGoods', () => {
    const updateReq = { goodsName: '更名', price: 2999, stock: 30 };

    /* ─── 正常路径 ─── */

    it('PUT /api/v1/goods/{goodsId} 带 Bearer，body 含更新字段', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await updateGoods(1, updateReq);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods/1',
        expect.objectContaining({
          method: 'PUT',
          headers: expect.objectContaining({ Authorization: 'Bearer t' }),
          body: JSON.stringify(updateReq),
        }),
      );
    });

    it('返回更新后 GoodsVO', async () => {
      token.setTokens('t', 'r');
      const updated = { ...goodsVO, goodsName: '更名', price: 2999 };
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: updated }),
      });

      const result = await updateGoods(1, updateReq);
      expect(result.goodsName).toBe('更名');
      expect(result.price).toBe(2999);
    });

    /* ─── 边界 ─── */

    it('goodsId=-1 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 400,
        json: () => Promise.resolve({ code: 400, message: '参数错误' }),
      });

      const err = await updateGoods(-1, updateReq).catch((e) => e);
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await updateGoods(1, updateReq).catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(updateGoods(1, updateReq)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('goodsName XSS 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const xss = '<img src=x onerror=alert(1)>';
      await updateGoods(1, { ...updateReq, goodsName: xss });
      const body = JSON.parse((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body);
      expect(body.goodsName).toBe(xss);
    });

    it('SQL 注入 goodsId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await (updateGoods as (id: unknown, data: unknown) => Promise<unknown>)("1 OR 1=1", updateReq);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/goods/1 OR 1=1', expect.anything());
    });
  });

  /* ─── listGoods ─── */

  describe('listGoods', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/goods/{goodsId}/list 带 Bearer', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await listGoods(1);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods/1/list',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer t' }),
        }),
      );
    });

    it('返回 GoodsVO', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const result = await listGoods(1);
      expect(result).toEqual(goodsVO);
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await listGoods(1).catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(listGoods(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 goodsId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await (listGoods as (id: unknown) => Promise<unknown>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/goods/1 OR 1=1/list', expect.anything());
    });
  });

  /* ─── delistGoods ─── */

  describe('delistGoods', () => {
    /* ─── 正常路径 ─── */

    it('POST /api/v1/goods/{goodsId}/delist 带 Bearer', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await delistGoods(1);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/goods/1/delist',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({ Authorization: 'Bearer t' }),
        }),
      );
    });

    it('返回 GoodsVO', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      const result = await delistGoods(1);
      expect(result).toEqual(goodsVO);
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await delistGoods(1).catch((e) => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(delistGoods(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 goodsId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 200, message: 'success', data: goodsVO }),
      });

      await (delistGoods as (id: unknown) => Promise<unknown>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/goods/1 OR 1=1/delist', expect.anything());
    });
  });
});
