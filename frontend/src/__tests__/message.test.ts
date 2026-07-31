import { describe, it, expect, beforeEach, vi } from 'vitest';
import { listMessages, countUnread, markAsRead } from '../api/message';
import * as token from '../utils/token';

import type { MessageVO } from '../types';

describe('api / message', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  /* ─── 工厂 ─── */

  const mockMsgs = (msgs: MessageVO[]) =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: msgs }),
    });

  const mockMsg = (overrides?: Partial<MessageVO>): MessageVO => ({
    messageId: '1',
    type: 'approval_result',
    content: '您的活动「618秒杀」已通过审核',
    activityId: '100',
    read: false,
    createdAt: '2026-07-30T10:00:00',
    ...overrides,
  });

  const mockNumber = (n: number) =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: n }),
    });

  const mockNull = () =>
    vi.fn().mockResolvedValue({
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'success', data: null }),
    });

  /* ─── listMessages ─── */

  describe('listMessages', () => {
    /* ─── 正常路径 ─── */

    it('GET /api/v1/messages 默认分页参数', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([mockMsg()]);

      const res = await listMessages();
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages?page=1&size=20',
        expect.objectContaining({ method: 'GET' }),
      );
      expect(res).toHaveLength(1);
    });

    it('自定义分页参数 (page=2, size=5)', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([]);

      await listMessages(2, 5);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages?page=2&size=5',
        expect.anything(),
      );
    });

    it('返回多条消息含所有字段', async () => {
      token.setTokens('t', 'r');
      const msgs = [
        mockMsg({ messageId: '1', type: 'approval_result', content: '已通过', activityId: '100', read: true }),
        mockMsg({ messageId: '2', type: 'ban_info', content: '您已被封禁', activityId: null, read: false }),
      ];
      globalThis.fetch = mockMsgs(msgs);

      const res = await listMessages();
      expect(res).toHaveLength(2);
      expect(res[0].type).toBe('approval_result');
      expect(res[0].content).toBe('已通过');
      expect(res[1].type).toBe('ban_info');
      expect(res[1].activityId).toBeNull();
    });

    it('read: false 区分未读', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([mockMsg({ read: false })]);

      const res = await listMessages();
      expect(res[0].read).toBe(false);
    });

    it('read: true 区分已读', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([mockMsg({ read: true })]);

      const res = await listMessages();
      expect(res[0].read).toBe(true);
    });

    it('activityId 可为 null', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([mockMsg({ activityId: null })]);

      const res = await listMessages();
      expect(res[0].activityId).toBeNull();
    });

    /* ─── 边界 ─── */

    it('空列表', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([]);

      const res = await listMessages();
      expect(res).toHaveLength(0);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await listMessages().catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(listMessages()).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('超大分页参数原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([]);

      await listMessages(999999, 999999);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('page=999999'),
        expect.anything(),
      );
    });

    it('负数分页参数原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockMsgs([]);

      await listMessages(-1, -1);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining('page=-1'),
        expect.anything(),
      );
    });
  });

  /* ─── countUnread ─── */

  describe('countUnread', () => {
    /* ─── 正常路径 ─── */

    it('GET /api/v1/messages/unread/count 有未读', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNumber(5);

      const res = await countUnread();
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages/unread/count',
        expect.objectContaining({ method: 'GET' }),
      );
      expect(res).toBe(5);
    });

    it('返回 0（无未读）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNumber(0);

      const res = await countUnread();
      expect(res).toBe(0);
    });

    /* ─── 边界 ─── */

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await countUnread().catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(countUnread()).rejects.toThrow(TypeError);
    });
  });

  /* ─── markAsRead ─── */

  describe('markAsRead', () => {
    /* ─── 正常路径 ─── */

    it('PUT /api/v1/messages/{messageId}/read 无请求体', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      await markAsRead('1');
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages/1/read',
        expect.objectContaining({
          method: 'PUT',
          headers: expect.objectContaining({ Authorization: 'Bearer t' }),
        }),
      );
      // body 应被省略（undefined）
      const callArg = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0][1];
      expect(callArg.body).toBeUndefined();
    });

    it('返回 null（data: null）', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      const res = await markAsRead('1');
      expect(res).toBeNull();
    });

    /* ─── 边界 ─── */

    it('消息不存在 → ApiError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        json: () => Promise.resolve({ code: 400, message: '消息不存在' }),
      });

      const err = await markAsRead('99999').catch(e => e);
      expect(err.code).toBe(400);
    });

    it('无 token → 401 AuthError', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 401,
        json: () => Promise.resolve({ code: 401, message: '未登录' }),
      });

      const err = await markAsRead('1').catch(e => e);
      expect(err.code).toBe(401);
    });

    it('网络异常 → TypeError', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

      await expect(markAsRead('1')).rejects.toThrow(TypeError);
    });

    /* ─── 攻击场景 ─── */

    it('SQL 注入 messageId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      await (markAsRead as (id: unknown) => Promise<null>)("1 OR 1=1");
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages/1 OR 1=1/read',
        expect.anything(),
      );
    });

    it('超长 messageId 原样发送', async () => {
      token.setTokens('t', 'r');
      globalThis.fetch = mockNull();

      await markAsRead('999999999999999');
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/messages/999999999999999/read',
        expect.anything(),
      );
    });
  });
});
