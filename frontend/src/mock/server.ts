/**
 * 轻量 Mock Server —— 仅用于无后端时的前端演示与自测。
 *
 * 启用方式（任一）：
 *  - 构建期：VITE_USE_MOCK=true
 *  - 运行期：localStorage.setItem('seckill_mock','1')
 *  - 运行期：URL 追加 ?mock=1
 *
 * 拦截 window.fetch 的 /api/v1/* 请求，返回与后端 Result 结构一致的响应，
 * 并模拟秒杀核心链路：预扣 → 排队 → 异步建单（轮询）→ 支付/取消。
 * 默认关闭，不影响真实后端对接与单元测试。
 */
import type {
  LoginVO, LoginRequest, RegisterRequest, UserInfoVO, UserRole, BanStatus,
  ActivityVO, CreateActivityRequest, PageVO, SeckillGoodsVO,
  GoodsVO, CreateGoodsRequest, UpdateGoodsRequest,
  SeckillRequest, SeckillResponse, OrderStatusVO, PayResponse, MessageVO,
} from '../types';

/* ------------------------------------------------------------------ */
/*  状态                                                               */
/* ------------------------------------------------------------------ */

interface MockUser {
  userId: string; userName: string; email: string; password: string;
  role: UserRole; banStatus: BanStatus;
}
interface MockGoods {
  goodsId: number; goodsName: string; price: number; status: number; stock: number; createdAt: string; merchantId: number;
}
interface MockSeckillGoods {
  seckillGoodsId: number; goodsId: number; goodsName: string; seckillPrice: number;
  stock: number; origStock: number; limitNum: number; merchantId: number;
}
interface MockActivity {
  activityId: number; activityName: string; merchantId: number; status: string;
  startTime: string; endTime: string; description: string; createdAt: string;
  rejectReason: string | null; goods: MockSeckillGoods[];
}
interface MockOrder {
  orderNo: string; orderToken: string; userId: string; activityId: number;
  seckillGoodsId: number; buyCount: number; totalAmount: number;
  status: 'UNPAID' | 'PAID' | 'CANCELLED' | null; createdAt: string;
  polls: number;
}
interface MockMessage {
  messageId: number; userId: string; type: string; content: string;
  activityId: number | null; isRead: boolean; createdAt: string;
}

const now = () => new Date();
const isoStr = (d: Date) => {
  const p = (n: number) => String(n).padStart(2, '0');
  // LocalDateTime 风格（无时区后缀）
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};
const tIso = (offsetMs: number) => isoStr(new Date(Date.now() + offsetMs));

let seqUser = 100;
let seqGoods = 5000;
let seqSg = 9000;
let seqAct = 100;
let seqOrderNo = 7000000000000000;
let seqMsg = 1;

const users: MockUser[] = [
  { userId: '1001', userName: '张买家', email: 'user@seckill.com', password: '123456', role: 'user', banStatus: 'NORMAL' },
  { userId: '1002', userName: '极客数码', email: 'merchant@seckill.com', password: '123456', role: 'merchant', banStatus: 'NORMAL' },
  { userId: '1003', userName: '管理员', email: 'admin@seckill.com', password: '123456', role: 'admin', banStatus: 'NORMAL' },
];

const goods: MockGoods[] = [
  { goodsId: 5001, goodsName: 'iPhone 16 Pro 256G', price: 8999, status: 1, stock: 500, createdAt: tIso(-86400000 * 9), merchantId: 1002 },
  { goodsId: 5002, goodsName: 'Sony WH-1000XM5 头戴耳机', price: 2899, status: 1, stock: 300, createdAt: tIso(-86400000 * 7), merchantId: 1002 },
  { goodsId: 5003, goodsName: '客制化机械键盘', price: 699, status: 1, stock: 1000, createdAt: tIso(-86400000 * 5), merchantId: 1002 },
  { goodsId: 5004, goodsName: '4K 显示器 27寸', price: 1799, status: 1, stock: 200, createdAt: tIso(-86400000 * 3), merchantId: 1002 },
];

const mkSg = (g: MockGoods, sp: number, stock: number, limit: number): MockSeckillGoods => ({
  seckillGoodsId: ++seqSg, goodsId: g.goodsId, goodsName: g.goodsName,
  seckillPrice: sp, stock, origStock: stock, limitNum: limit, merchantId: g.merchantId,
});

const activities: MockActivity[] = [
  {
    activityId: 101, activityName: '618 数码狂欢夜', merchantId: 1002, status: 'running',
    startTime: tIso(-10 * 60000), endTime: tIso(50 * 60000), description: '年度最低价，全场数码爆款限时秒杀，手慢无！',
    createdAt: tIso(-86400000 * 2), rejectReason: null,
    goods: [mkSg(goods[0], 6999, 50, 1), mkSg(goods[1], 1999, 80, 2)],
  },
  {
    activityId: 102, activityName: '午间限时秒杀', merchantId: 1002, status: 'preheating',
    startTime: tIso(2 * 60000), endTime: tIso(32 * 60000), description: '午休时段精选好物，整点开抢。',
    createdAt: tIso(-86400000), rejectReason: null,
    goods: [mkSg(goods[2], 399, 100, 1)],
  },
  {
    activityId: 103, activityName: '开学季特惠', merchantId: 1002, status: 'ended',
    startTime: tIso(-120 * 60000), endTime: tIso(-60 * 60000), description: '开学装备一站式补齐。',
    createdAt: tIso(-86400000 * 4), rejectReason: null,
    goods: [mkSg(goods[1], 2099, 0, 1)],
  },
  {
    activityId: 104, activityName: '双11 预热专场', merchantId: 1002, status: 'draft',
    startTime: tIso(3 * 86400000), endTime: tIso(3 * 86400000 + 2 * 3600000), description: '双11 筹备中，敬请期待。',
    createdAt: tIso(-86400000 * 1.2), rejectReason: null,
    goods: [mkSg(goods[3], 1299, 150, 1)],
  },
  {
    activityId: 105, activityName: '夏日清仓甩卖', merchantId: 1002, status: 'pending',
    startTime: tIso(1 * 86400000), endTime: tIso(1 * 86400000 + 3600000), description: '清凉一夏，库存清仓。',
    createdAt: tIso(-3600000 * 5), rejectReason: null,
    goods: [mkSg(goods[2], 299, 60, 2)],
  },
];

const orders: MockOrder[] = [];
const messages: MockMessage[] = [
  { messageId: 1, userId: '1001', type: 'welcome', content: '欢迎来到秒杀系统！立即去活动广场参与抢购吧。', activityId: null, isRead: false, createdAt: tIso(-3600000) },
  { messageId: 2, userId: '1001', type: 'approval_result', content: '极客数码将于 10 分钟前创办"618 数码狂欢夜"秒杀活动，包含以下商品：iPhone 16 Pro 256G、Sony WH-1000XM5 头戴耳机。', activityId: 101, isRead: false, createdAt: tIso(-9 * 60000) },
  { messageId: 3, userId: '1002', type: 'welcome', content: '欢迎商家入驻！可前往「创建活动」发布秒杀。', activityId: null, isRead: true, createdAt: tIso(-86400000) },
  { messageId: 4, userId: '1002', type: 'approval_result', content: '您于早些时候创建的"618 数码狂欢夜"活动已通过审批，将按时开始秒杀。', activityId: 101, isRead: true, createdAt: tIso(-11 * 60000) },
  { messageId: 5, userId: '1003', type: 'welcome', content: '管理员后台已就绪，可审核活动与管理用户。', activityId: null, isRead: true, createdAt: tIso(-86400000) },
];

// token -> userId
const sessions = new Map<string, string>();
const buyers = new Set<string>(); // `${activityId}:${seckillGoodsId}:${userId}`

/* ------------------------------------------------------------------ */
/*  辅助                                                               */
/* ------------------------------------------------------------------ */

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

function ok<T>(data: T, status = 200) {
  return { status, json: async () => ({ code: 200, message: 'success', data }) };
}
function err(code: number, message: string, status = 200) {
  return { status, json: async () => ({ code, message }) };
}

function userOf(req: { headers?: Record<string, string> }): MockUser | null {
  const h = req.headers ?? {};
  const auth = h['Authorization'] ?? h['authorization'];
  if (!auth) return null;
  const token = auth.replace(/^Bearer\s+/i, '');
  const uid = sessions.get(token);
  if (!uid) return null;
  return users.find((u) => u.userId === uid) ?? null;
}

function toActivityVO(a: MockActivity): ActivityVO {
  return {
    activityId: a.activityId, activityName: a.activityName, merchantId: a.merchantId,
    status: a.status, startTime: a.startTime, endTime: a.endTime, description: a.description,
    createdAt: a.createdAt, rejectReason: a.rejectReason,
    seckillGoodsList: a.goods.map((g) => ({
      seckillGoodsId: g.seckillGoodsId, goodsId: g.goodsId, goodsName: g.goodsName,
      seckillPrice: g.seckillPrice, stock: g.stock, limitNum: g.limitNum,
    } as SeckillGoodsVO)),
  };
}

function toGoodsVO(g: MockGoods): GoodsVO {
  return { goodsId: g.goodsId, goodsName: g.goodsName, price: g.price, status: g.status, stock: g.stock, createdAt: g.createdAt };
}

function toMessageVO(m: MockMessage): MessageVO {
  return { messageId: m.messageId, type: m.type, content: m.content, activityId: m.activityId, read: m.isRead, createdAt: m.createdAt };
}

function uidNum(u: MockUser): number {
  return Number(u.userId);
}

/* ------------------------------------------------------------------ */
/*  路由处理                                                           */
/* ------------------------------------------------------------------ */

interface MockRequest { method: string; url: string; body: unknown; headers: Record<string, string>; }
type MockResponse = { status: number; json: () => Promise<unknown> };

async function route(req: MockRequest): Promise<MockResponse> {
  const { method, url, body } = req;
  const path = url.split('?')[0];
  const q = new URLSearchParams(url.split('?')[1] ?? '');
  const u = path.replace(/^\/api\/v1/, '');
  const user = userOf(req);

  const needAuth = () => { if (!user) return err(401, '未登录', 401); return null; };

  // ---- auth ----
  if (u === '/auth/login' && method === 'POST') {
    const { email, password } = body as LoginRequest;
    const found = users.find((x) => x.email === email);
    if (!found || found.password !== password) return err(400, '邮箱或密码错误');
    const at = `mock-at-${found.userId}-${Date.now()}`;
    const rt = `mock-rt-${found.userId}-${Date.now()}`;
    sessions.set(at, found.userId); sessions.set(rt, found.userId);
    const vo: LoginVO = { accessToken: at, refreshToken: rt, userId: found.userId, userName: found.userName, role: found.role };
    return ok(vo);
  }
  if (u === '/auth/register' && method === 'POST') {
    const { userName: un, email, password, role } = body as RegisterRequest;
    if (users.some((x) => x.email === email)) return err(400, '该邮箱已注册');
    const nu: MockUser = { userId: String(++seqUser), userName: un, email, password, role: (role ?? 'user') as UserRole, banStatus: 'NORMAL' };
    users.push(nu);
    messages.push({ messageId: ++seqMsg, userId: nu.userId, type: 'welcome', content: `欢迎注册秒杀系统，${un}！`, activityId: null, isRead: false, createdAt: isoStr(now()) });
    const at = `mock-at-${nu.userId}-${Date.now()}`; const rt = `mock-rt-${nu.userId}-${Date.now()}`;
    sessions.set(at, nu.userId); sessions.set(rt, nu.userId);
    const vo: LoginVO = { accessToken: at, refreshToken: rt, userId: nu.userId, userName: nu.userName, role: nu.role };
    return ok(vo);
  }
  if (u === '/auth/refresh' && method === 'POST') {
    const { refreshToken } = body as { refreshToken: string };
    const uid = sessions.get(refreshToken);
    if (!uid) return err(401, 'refresh token 无效', 401);
    const found = users.find((x) => x.userId === uid)!;
    const at = `mock-at-${uid}-${Date.now()}`; const rt = `mock-rt-${uid}-${Date.now()}`;
    sessions.set(at, uid); sessions.set(rt, uid);
    return ok({ accessToken: at, refreshToken: rt, userId: uid, userName: found.userName, role: found.role } as LoginVO);
  }
  if (u === '/auth/logout' && method === 'POST') return ok(null);
  if (u === '/user/me' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const info: UserInfoVO = { userId: user!.userId, userName: user!.userName, email: user!.email, role: user!.role, banStatus: user!.banStatus };
    return ok(info);
  }

  // ---- activity ----
  if (u === '/activity' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const page = Number(q.get('page') ?? 1); const pageSize = Number(q.get('pageSize') ?? 10);
    let list = activities.slice();
    if (user!.role === 'merchant') list = list.filter((a) => a.merchantId === uidNum(user!));
    else if (user!.role === 'user') list = list.filter((a) => ['preheating', 'running', 'ended'].includes(a.status));
    const total = list.length;
    const records = list.slice((page - 1) * pageSize, page * pageSize).map(toActivityVO);
    return ok({ records, total, page, pageSize } as PageVO<ActivityVO>);
  }
  if (u === '/activity' && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'merchant') return err(403, '仅商家可创建活动');
    const r = body as CreateActivityRequest;
    const a: MockActivity = {
      activityId: ++seqAct, activityName: r.activityName, merchantId: uidNum(user!), status: 'draft',
      startTime: r.startTime, endTime: r.endTime, description: r.description ?? '',
      createdAt: isoStr(now()), rejectReason: null,
      goods: (r.seckillGoodsList ?? []).map((it) => {
        const g = goods.find((x) => x.goodsId === it.goodsId);
        return { seckillGoodsId: ++seqSg, goodsId: it.goodsId, goodsName: g?.goodsName ?? `商品${it.goodsId}`, seckillPrice: it.seckillPrice, stock: it.stock, origStock: it.stock, limitNum: it.limitNum, merchantId: uidNum(user!) };
      }),
    };
    activities.unshift(a);
    return ok(toActivityVO(a));
  }
  const actMatch = u.match(/^\/activity\/(\d+)$/);
  if (actMatch && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const a = activities.find((x) => x.activityId === Number(actMatch[1]));
    if (!a) return err(400, '活动不存在');
    return ok(toActivityVO(a));
  }
  const submitMatch = u.match(/^\/activity\/(\d+)\/submit$/);
  if (submitMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    const a = activities.find((x) => x.activityId === Number(submitMatch[1]));
    if (!a) return err(400, '活动不存在');
    if (a.status !== 'draft') return err(400, '仅草稿可提交审核');
    a.status = 'pending';
    return ok(toActivityVO(a));
  }
  const approveMatch = u.match(/^\/activity\/(\d+)\/approve$/);
  if (approveMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'admin') return err(403, '仅管理员可审核活动');
    const a = activities.find((x) => x.activityId === Number(approveMatch[1]));
    if (!a) return err(400, '活动不存在');
    if (a.status !== 'pending') return err(400, '仅待审核活动可通过');
    a.status = 'preheating';
    // 通知商家
    messages.push({ messageId: ++seqMsg, userId: String(a.merchantId), type: 'approval_result', content: `您创建的"${a.activityName}"活动已通过审批，将按时开始秒杀。`, activityId: a.activityId, isRead: false, createdAt: isoStr(now()) });
    // 通知全体用户
    for (const usr of users.filter((x) => x.role === 'user')) {
      const names = a.goods.map((g) => g.goodsName).join('、');
      messages.push({ messageId: ++seqMsg, userId: usr.userId, type: 'approval_result', content: `${users.find((m) => m.userId === String(a.merchantId))?.userName ?? '商家'}将于${a.startTime}创办"${a.activityName}"秒杀活动，包含以下商品：${names}。`, activityId: a.activityId, isRead: false, createdAt: isoStr(now()) });
    }
    return ok(toActivityVO(a));
  }
  const rejectMatch = u.match(/^\/activity\/(\d+)\/reject$/);
  if (rejectMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'admin') return err(403, '仅管理员可驳回活动');
    const a = activities.find((x) => x.activityId === Number(rejectMatch[1]));
    if (!a) return err(400, '活动不存在');
    const reason = (body as { reason?: string })?.reason ?? '';
    a.status = 'draft'; a.rejectReason = reason;
    messages.push({ messageId: ++seqMsg, userId: String(a.merchantId), type: 'approval_result', content: `您的活动"${a.activityName}"被驳回。理由：${reason || '无'}`, activityId: a.activityId, isRead: false, createdAt: isoStr(now()) });
    return ok(toActivityVO(a));
  }

  // ---- goods ----
  if (u === '/goods' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'merchant') return err(403, '仅商家可操作');
    return ok(goods.filter((g) => g.merchantId === uidNum(user!)).map(toGoodsVO));
  }
  if (u === '/goods' && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'merchant') return err(403, '仅商家可操作');
    const r = body as CreateGoodsRequest;
    const g: MockGoods = { goodsId: ++seqGoods, goodsName: r.goodsName, price: r.price, status: 1, stock: r.stock, createdAt: isoStr(now()), merchantId: uidNum(user!) };
    goods.push(g);
    return ok(toGoodsVO(g));
  }
  const goodsUpd = u.match(/^\/goods\/(\d+)$/);
  if (goodsUpd && method === 'PUT') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'merchant') return err(403, '仅商家可操作');
    const g = goods.find((x) => x.goodsId === Number(goodsUpd[1]) && x.merchantId === uidNum(user!));
    if (!g) return err(400, '商品不存在');
    const r = body as UpdateGoodsRequest;
    g.goodsName = r.goodsName; g.price = r.price; g.stock = r.stock;
    return ok(toGoodsVO(g));
  }
  const goodsInfo = u.match(/^\/goods\/(\d+)$/);
  if (goodsInfo && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const g = goods.find((x) => x.goodsId === Number(goodsInfo[1]));
    if (!g) return err(400, '商品不存在');
    return ok({ goodsId: g.goodsId, goodsName: g.goodsName, price: g.price, stock: g.stock });
  }
  const listMatch = u.match(/^\/goods\/(\d+)\/list$/);
  if (listMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    const g = goods.find((x) => x.goodsId === Number(listMatch[1]) && x.merchantId === uidNum(user!));
    if (!g) return err(400, '商品不存在');
    g.status = 1; return ok(toGoodsVO(g));
  }
  const delistMatch = u.match(/^\/goods\/(\d+)\/delist$/);
  if (delistMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    const g = goods.find((x) => x.goodsId === Number(delistMatch[1]) && x.merchantId === uidNum(user!));
    if (!g) return err(400, '商品不存在');
    g.status = 0; return ok(toGoodsVO(g));
  }

  // ---- seckill ----
  if (u === '/seckill/execute' && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.banStatus === 'BANNED') return err(403, '账号已被封禁，无法参与秒杀');
    await delay(400);
    const r = body as SeckillRequest;
    const a = activities.find((x) => x.activityId === r.activityId);
    if (!a) return err(400, '活动不存在');
    if (a.status !== 'running') return err(400, '活动未开始或已结束');
    const sg = a.goods.find((g) => g.seckillGoodsId === r.seckillGoodsId);
    if (!sg) return err(400, '秒杀商品不存在');
    if (r.buyCount > sg.limitNum) return err(400, '超过限购数量');
    const key = `${a.activityId}:${sg.seckillGoodsId}:${user!.userId}`;
    if (buyers.has(key)) return err(400, '请勿重复购买');
    if (sg.stock <= 0) return err(400, '商品已售罄');
    sg.stock -= r.buyCount;
    buyers.add(key);
    const orderToken = `tok-${a.activityId}-${sg.seckillGoodsId}-${user!.userId}-${Date.now()}`;
    const orderNo = String(++seqOrderNo);
    orders.push({
      orderNo, orderToken, userId: user!.userId, activityId: a.activityId, seckillGoodsId: sg.seckillGoodsId,
      buyCount: r.buyCount, totalAmount: sg.seckillPrice * r.buyCount, status: null, createdAt: isoStr(now()), polls: 0,
    });
    const resp: SeckillResponse = { orderToken };
    return ok(resp);
  }

  // ---- order ----
  if (u === '/order/status' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const token = q.get('token') ?? '';
    const o = orders.find((x) => x.orderToken === token && x.userId === user!.userId);
    if (!o) return ok({ status: null, orderNo: null } as OrderStatusVO);
    o.polls += 1;
    // 模拟异步建单：前 2 次轮询仍为 null，第 3 次起返回 UNPAID
    if (o.polls < 3 && o.status === null) return ok({ status: null, orderNo: null } as OrderStatusVO);
    if (o.status === null) o.status = 'UNPAID';
    return ok({ status: o.status, orderNo: o.orderNo } as OrderStatusVO);
  }
  if (u === '/order/cancel' && method === 'POST') {
    const e = needAuth(); if (e) return e;
    const { orderNo } = body as { orderNo: string };
    const o = orders.find((x) => x.orderNo === orderNo && x.userId === user!.userId);
    if (!o) return err(400, '订单不存在');
    if (o.status !== 'UNPAID') return err(400, '订单状态不可取消');
    o.status = 'CANCELLED';
    // 回补库存
    const a = activities.find((x) => x.activityId === o.activityId);
    const sg = a?.goods.find((g) => g.seckillGoodsId === o.seckillGoodsId);
    if (sg) sg.stock += o.buyCount;
    buyers.delete(`${o.activityId}:${o.seckillGoodsId}:${o.userId}`);
    return ok(null);
  }

  // ---- payment ----
  if (u === '/payment/pay' && method === 'POST') {
    const e = needAuth(); if (e) return e;
    const { orderNo } = body as { orderNo: string; userId: string };
    const o = orders.find((x) => x.orderNo === orderNo && x.userId === user!.userId);
    if (!o) return err(400, '订单不存在');
    if (o.status === 'PAID') return err(400, '订单已支付');
    if (o.status !== 'UNPAID') return err(400, '订单已取消，无法支付');
    await delay(600);
    o.status = 'PAID';
    const resp: PayResponse = { success: true, message: '支付成功' };
    return ok(resp);
  }

  // ---- messages ----
  if (u === '/messages' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const page = Number(q.get('page') ?? 1); const size = Number(q.get('size') ?? 20);
    const list = messages.filter((m) => m.userId === user!.userId).sort((a, b) => b.messageId - a.messageId);
    const records = list.slice((page - 1) * size, page * size).map(toMessageVO);
    return ok(records);
  }
  if (u === '/messages/unread/count' && method === 'GET') {
    const e = needAuth(); if (e) return e;
    const c = messages.filter((m) => m.userId === user!.userId && !m.isRead).length;
    return ok(c);
  }
  const readMatch = u.match(/^\/messages\/(\d+)\/read$/);
  if (readMatch && method === 'PUT') {
    const e = needAuth(); if (e) return e;
    const m = messages.find((x) => x.messageId === Number(readMatch[1]) && x.userId === user!.userId);
    if (m) m.isRead = true;
    return ok(null);
  }

  // ---- admin ----
  const banMatch = u.match(/^\/admin\/users\/(\d+)\/(ban|unban)$/);
  if (banMatch && method === 'POST') {
    const e = needAuth(); if (e) return e;
    if (user!.role !== 'admin') return err(403, '仅管理员可操作');
    const target = users.find((x) => x.userId === banMatch[1]);
    if (!target) return err(400, '用户不存在');
    target.banStatus = banMatch[2] === 'ban' ? 'BANNED' : 'NORMAL';
    messages.push({ messageId: ++seqMsg, userId: target.userId, type: 'ban_info', content: banMatch[2] === 'ban' ? '您的账号已被管理员封禁。' : '您的账号已被解封，恢复正常使用。', activityId: null, isRead: false, createdAt: isoStr(now()) });
    return ok(null);
  }

  return err(404, `mock 未实现: ${method} ${u}`);
}

/* ------------------------------------------------------------------ */
/*  安装                                                               */
/* ------------------------------------------------------------------ */

let installed = false;

export function isMockEnabled(): boolean {
  if (import.meta.env.VITE_USE_MOCK === 'true') return true;
  try {
    if (localStorage.getItem('seckill_mock') === '1') return true;
    if (typeof window !== 'undefined' && new URLSearchParams(window.location.search).get('mock') === '1') return true;
  } catch { /* noop */ }
  return false;
}

export function installMockFetch() {
  if (installed || typeof window === 'undefined') return;
  const realFetch = window.fetch.bind(window);
  installed = true;
  window.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    if (!url.includes('/api/v1/')) {
      return realFetch(input as RequestInfo, init);
    }
    const method = (init?.method ?? 'GET').toUpperCase();
    let body: unknown = undefined;
    const rawBody = init?.body;
    if (rawBody && typeof rawBody === 'string') {
      try { body = JSON.parse(rawBody); } catch { body = rawBody; }
    }
    const headers: Record<string, string> = {};
    const h = init?.headers;
    if (h) {
      if (h instanceof Headers) h.forEach((v, k) => { headers[k] = v; });
      else if (Array.isArray(h)) h.forEach(([k, v]) => { headers[k] = v; });
      else Object.assign(headers, h);
    }
    await delay(120 + Math.random() * 160);
    const resp = await route({ method, url, body, headers });
    return resp as unknown as Response;
  }) as typeof window.fetch;
}

export function mockInfo() {
  return {
    users: [
      { email: 'user@seckill.com', password: '123456', role: '用户' },
      { email: 'merchant@seckill.com', password: '123456', role: '商家' },
      { email: 'admin@seckill.com', password: '123456', role: '管理员' },
    ],
  };
}
