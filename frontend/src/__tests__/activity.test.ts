import { describe, it, expect, beforeEach, vi } from 'vitest';
import { listActivities, getActivityDetail, createActivity, submitForReview, approveActivity, rejectActivity } from '../api/activity';
import * as token from '../utils/token';

import type { ActivityVO, PageVO } from '../types';

describe('api / activity', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /* ─── 工厂 ─── */

  const mockPage = (overrides?: Partial<PageVO<ActivityVO>>) => {
    const page: PageVO<ActivityVO> = {
      records: [],
      total: 0,
      page: 1,
      pageSize: 10,
      ...overrides,
    };
    return vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: page }),
    });
  };

  const mockActivity = (overrides?: Partial<ActivityVO>) => {
    const activity: ActivityVO = {
      activityId: 1,
      activityName: '618秒杀',
      merchantId: 100,
      status: 'running',
      startTime: '2026-07-30T10:00:00',
      endTime: '2026-07-30T12:00:00',
      description: '618大促',
      seckillGoodsList: [
        { seckillGoodsId: 1, goodsId: 10, goodsName: '手机', seckillPrice: 1999, stock: 100, limitNum: 1 },
      ],
      createdAt: '2026-07-01T08:00:00',
      rejectReason: null,
      ...overrides,
    };
    return vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: activity }),
    });
  };

  /* ─── listActivities ─── */

  describe('listActivities', () => {
    /* ─── 正常路径 ─── */

    it('GET /api/v1/activity 默认分页参数', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPage();

      const res = await listActivities();
      expect(res.page).toBe(1);
      expect(res.pageSize).toBe(10);
    });

    it('GET /api/v1/activity 自定义分页参数', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPage({ page: 2, pageSize: 5 });

      const res = await listActivities(2, 5);
      expect(res.page).toBe(2);
      expect(res.pageSize).toBe(5);
    });

    it('返回空列表', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPage({ records: [], total: 0 });

      const res = await listActivities();
      expect(res.records).toHaveLength(0);
      expect(res.total).toBe(0);
    });

    it('返回多条活动记录', async () => {
      token.setTokens('t', 'r');
      const records: ActivityVO[] = [
        { activityId: 1, activityName: '活动A', merchantId: 100, status: 'running', startTime: '', endTime: '', description: '', seckillGoodsList: [], createdAt: '', rejectReason: null },
        { activityId: 2, activityName: '活动B', merchantId: 100, status: 'preheating', startTime: '', endTime: '', description: '', seckillGoodsList: [], createdAt: '', rejectReason: null },
      ];
      globalThis.fetch = mockPage({ records, total: 2 });

      const res = await listActivities();
      expect(res.records).toHaveLength(2);
      expect(res.total).toBe(2);
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await listActivities().catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(listActivities()).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('超大分页参数原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPage();

      await listActivities(999999, 999999);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('page=999999'),
        expect.anything(),
      );
    });

    it('负数分页参数原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockPage();

      await listActivities(-1, -1);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('page=-1'),
        expect.anything(),
      );
    });
  });

  /* ─── getActivityDetail ─── */

  describe('getActivityDetail', () => {
    /* ─── 正常路径 ─── */

    it('GET /api/v1/activity/{activityId} 带 Bearer', async () => {
      token.setTokens('user-token', 'r');
      globalThis.fetch = mockActivity();

      const res = await getActivityDetail(1);
      expect(res.activityId).toBe(1);
      expect(res.activityName).toBe('618秒杀');
    });

    it('返回秒杀商品列表', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockActivity();

      const res = await getActivityDetail(1);
      expect(res.seckillGoodsList).toHaveLength(1);
      expect(res.seckillGoodsList[0].goodsName).toBe('手机');
      expect(res.seckillGoodsList[0].seckillPrice).toBe(1999);
    });

    it('活动含驳回理由', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockActivity({ rejectReason: '商品库存不足' });

      const res = await getActivityDetail(1);
      expect(res.rejectReason).toBe('商品库存不足');
    });

    it('活动无驳回理由为 null', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockActivity({ rejectReason: null });

      const res = await getActivityDetail(1);
      expect(res.rejectReason).toBeNull();
    });

    /* ─── 边界 ─── */

    it('活动不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 400, message: '活动不存在' }),
      });

      const err = await getActivityDetail(99999).catch(e => e);
      expect(err.message).toBe('活动不存在');
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await getActivityDetail(1).catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(getActivityDetail(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockActivity();

      await (getActivityDetail as (id: unknown) => Promise<ActivityVO>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1 OR 1=1', expect.anything());
    });

    it('超长 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockActivity();

      await getActivityDetail(999999999999999);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/999999999999999', expect.anything());
    });
  });

  /* ─── createActivity ─── */

  describe('createActivity', () => {
    const validReq = {
      activityName: '618秒杀',
      startTime: '2026-07-30T10:00:00',
      endTime: '2026-07-30T12:00:00',
      description: '大促销',
      seckillGoodsList: [{ goodsId: 1, seckillPrice: 1999, stock: 100, limitNum: 1 }],
    };

    const mockCreated = (overrides?: Partial<ActivityVO>) =>
      vi.fn().mockResolvedValue({
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            message: 'success',
            data: {
              activityId: 1,
              activityName: '618秒杀',
              merchantId: 100,
              status: 'draft',
              startTime: '2026-07-30T10:00:00',
              endTime: '2026-07-30T12:00:00',
              description: '大促销',
              seckillGoodsList: [{ seckillGoodsId: 1, goodsId: 1, goodsName: '手机', seckillPrice: 1999, stock: 100, limitNum: 1 }],
              createdAt: '2026-07-01T08:00:00',
              rejectReason: null,
              ...overrides,
            },
          }),
      });

    /* ─── 正常路径 ─── */

    it('POST /api/v1/activity 完整创建含秒杀商品列表', async () => {
      token.setTokens('merchant-token', 'r');
      globalThis.fetch = mockCreated();

      const res = await createActivity(validReq);

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity', {
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer merchant-token', 'Content-Type': 'application/json' }),
        body: JSON.stringify(validReq),
      });
      expect(res.activityId).toBe(1);
      expect(res.status).toBe('draft');
    });

    it('返回含嵌套 seckillGoodsList', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCreated();

      const res = await createActivity(validReq);
      expect(res.seckillGoodsList).toHaveLength(1);
      expect(res.seckillGoodsList[0].goodsName).toBe('手机');
      expect(res.seckillGoodsList[0].seckillPrice).toBe(1999);
    });

    /* ─── 边界 ─── */

    it('无秒杀商品（空数组）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCreated();

      const req = { ...validReq, seckillGoodsList: [] };
      await createActivity(req);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity', {
        method: 'POST',
        headers: expect.anything(),
        body: JSON.stringify(req),
      });
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await createActivity(validReq).catch(e => e);
      expect(err.code).toBe(401);
    });

    it('角色不足 → ApiError', async () => {
      token.setTokens('user-token', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 403, message: '仅商家可创建活动' }),
      });

      const err = await createActivity(validReq).catch(e => e);
      expect(err.code).toBe(403);
      expect(err.message).toContain('商家');
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(createActivity(validReq)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('超长 activityName 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCreated();

      const longName = 'A'.repeat(1000);
      await createActivity({ ...validReq, activityName: longName });
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/activity',
        expect.objectContaining({ body: expect.stringContaining(longName) }),
      );
    });

    it('负数 price/stock/limitNum 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockCreated();

      const req = { ...validReq, seckillGoodsList: [{ goodsId: 1, seckillPrice: -1, stock: -1, limitNum: -1 }] };
      await createActivity(req);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/activity',
        expect.objectContaining({ body: JSON.stringify(req) }),
      );
    });
  });

  /* ─── submitForReview ─── */

  describe('submitForReview', () => {
    const mockSubmitted = () =>
      vi.fn().mockResolvedValue({
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200,
            message: 'success',
            data: {
              activityId: 1, activityName: '618秒杀', merchantId: 100, status: 'pending',
              startTime: '2026-07-30T10:00:00', endTime: '2026-07-30T12:00:00',
              description: '', seckillGoodsList: [], createdAt: '', rejectReason: null,
            },
          }),
      });

    /* ─── 正常路径 ─── */

    it('POST /api/v1/activity/{id}/submit 无请求体', async () => {
      token.setTokens('merchant-token', 'r');
      globalThis.fetch = mockSubmitted();

      const res = await submitForReview(1);

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1/submit', {
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer merchant-token' }),
        body: undefined,
      });
      expect(res.activityId).toBe(1);
    });

    it('返回 status="pending"', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSubmitted();

      const res = await submitForReview(1);
      expect(res.status).toBe('pending');
    });

    /* ─── 边界 ─── */

    it('活动不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 400, message: '活动不存在' }),
      });

      const err = await submitForReview(99999).catch(e => e);
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401, json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await submitForReview(1).catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
      await expect(submitForReview(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSubmitted();

      await (submitForReview as (id: unknown) => Promise<ActivityVO>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1 OR 1=1/submit', expect.anything());
    });

    it('超长 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockSubmitted();

      await submitForReview(999999999999999);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/999999999999999/submit', expect.anything());
    });
  });

  /* ─── approveActivity ─── */

  describe('approveActivity', () => {
    const mockApproved = () =>
      vi.fn().mockResolvedValue({
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200, message: 'success',
            data: {
              activityId: 1, activityName: '618秒杀', merchantId: 100, status: 'preheating',
              startTime: '2026-07-30T10:00:00', endTime: '2026-07-30T12:00:00',
              description: '', seckillGoodsList: [], createdAt: '', rejectReason: null,
            },
          }),
      });

    /* ─── 正常路径 ─── */

    it('POST /api/v1/activity/{id}/approve 无请求体', async () => {
      token.setTokens('admin-token', 'r');
      globalThis.fetch = mockApproved();

      const res = await approveActivity(1);

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1/approve', {
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer admin-token' }),
        body: undefined,
      });
      expect(res.activityId).toBe(1);
    });

    it('返回 status="preheating"', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockApproved();

      const res = await approveActivity(1);
      expect(res.status).toBe('preheating');
    });

    /* ─── 边界 ─── */

    it('活动不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200, json: () => Promise.resolve({ code: 400, message: '活动不存在' }),
      });

      const err = await approveActivity(99999).catch(e => e);
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401, json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await approveActivity(1).catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
      await expect(approveActivity(1)).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockApproved();

      await (approveActivity as (id: unknown) => Promise<ActivityVO>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1 OR 1=1/approve', expect.anything());
    });

    it('超长 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockApproved();

      await approveActivity(999999999999999);
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/999999999999999/approve', expect.anything());
    });
  });

  /* ─── rejectActivity ─── */

  describe('rejectActivity', () => {
    const mockRejected = (reason: string | null) =>
      vi.fn().mockResolvedValue({
        status: 200,
        json: () =>
          Promise.resolve({
            code: 200, message: 'success',
            data: {
              activityId: 1, activityName: '618秒杀', merchantId: 100, status: 'draft',
              startTime: '2026-07-30T10:00:00', endTime: '2026-07-30T12:00:00',
              description: '', seckillGoodsList: [], createdAt: '', rejectReason: reason,
            },
          }),
      });

    /* ─── 正常路径 ─── */

    it('POST /api/v1/activity/{id}/reject 带 reason', async () => {
      token.setTokens('admin-token', 'r');
      globalThis.fetch = mockRejected('商品库存不足');

      const res = await rejectActivity(1, '商品库存不足');

      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1/reject', {
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer admin-token', 'Content-Type': 'application/json' }),
        body: JSON.stringify({ reason: '商品库存不足' }),
      });
      expect(res.activityId).toBe(1);
    });

    it('返回 rejectReason 非空', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockRejected('库存不足');

      const res = await rejectActivity(1, '库存不足');
      expect(res.rejectReason).toBe('库存不足');
      expect(res.status).toBe('draft');
    });

    /* ─── 边界 ─── */

    it('reason 为空字符串', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockRejected('');

      await rejectActivity(1, '');
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1/reject', {
        method: 'POST',
        headers: expect.anything(),
        body: JSON.stringify({ reason: '' }),
      });
    });

    it('活动不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200, json: () => Promise.resolve({ code: 400, message: '活动不存在' }),
      });

      const err = await rejectActivity(99999, 'reason').catch(e => e);
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401, json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await rejectActivity(1, 'r').catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
      await expect(rejectActivity(1, 'r')).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('XSS payload in reason 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockRejected('<script>alert(1)</script>');

      await rejectActivity(1, '<script>alert(1)</script>');
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/activity/1/reject',
        expect.objectContaining({ body: expect.stringContaining('<script>alert(1)</script>') }),
      );
    });

    it('SQL 注入 activityId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockRejected('r');

      await (rejectActivity as (id: unknown, reason: string) => Promise<ActivityVO>)("1 OR 1=1", 'r');
      expect(globalThis.fetch).toHaveBeenCalledWith('/api/v1/activity/1 OR 1=1/reject', expect.anything());
    });
  });
});
