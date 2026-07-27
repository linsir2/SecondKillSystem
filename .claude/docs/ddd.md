# DDD 领域驱动设计分析

> 基于 prd.md + sql.md + 注意事项.md，v1 版本。

---

## 1. 业务事件

### 用户上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 用户已注册 | `UserRegistered` | 注册成功 | 内部 |
| 用户已登录 | `UserLoggedIn` | 登录成功 | 内部 |
| 用户资料已更新 | `UserProfileUpdated` | 修改个人信息 | 内部 |
| 用户已被封禁 | `UserBanned` | 管理员封禁 | **发布**（Seckill + Notification 需感知） |
| 用户已解封 | `UserUnbanned` | 管理员解封 | **发布** |

### 商品上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 商品已创建 | `GoodsCreated` | 商家创建商品 | 内部 |
| 商品信息已更新 | `GoodsUpdated` | 商家修改商品 | 内部 |
| 商品已上架 | `GoodsListed` | status 0→1 | **发布** |
| 商品已下架 | `GoodsDelisted` | status 1→0 | **发布** |

### 活动上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 活动草稿已保存 | `ActivityDrafted` | 商家保存草稿 | 内部 |
| 活动已提交审核 | `ActivitySubmittedForReview` | status draft→pending | **发布**（通知管理员） |
| 活动审核已通过 | `ActivityApproved` | 管理员审批通过 | **发布**（通知商家+用户、触发 Inventory 分配库存） |
| 活动审核已驳回 | `ActivityRejected` | 管理员驳回 | **发布**（通知商家） |
| 活动已进入预热 | `ActivityPreheated` | pending→preheating | **发布**（触发 Inventory 预热到 Redis） |
| 活动已开始 | `ActivityStarted` | preheating→running | **发布** |
| 活动已结束 | `ActivityEnded` | running→ended（自动/手动） | **发布**（触发 Inventory 回补 goods.stock） |
| 活动已被强制下架 | `ActivityForceEnded` | 管理员强制下架 | **发布** |

### 抢购上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 库存已预扣 | `StockPreDeducted` | Redis Lua 返回 1 | **发布**（发 MQ 到 Order） |
| 抢购已被拒绝 | `SeckillRejected` | Lua 返回 -1/-2 或限流/黑名单拦截 | 内部 |
| 削峰消息发送失败 | `PeakShavingMessageSendFailed` | MQ 发送异常 | 内部（触发 Inventory 即时补偿） |

### 订单上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 订单已创建 | `OrderCreated` | DB 插入成功，status=UNPAID | **发布**（通知用户、前端轮询感知） |
| 订单已支付 | `OrderPaid` | 支付回调确认，status UNPAID→PAID | **发布** |
| 订单已超时取消 | `OrderTimedOut` | 延时关单消费成功，status UNPAID→CANCELLED | **发布**（触发 Inventory 回补 Redis） |
| 订单已主动取消 | `OrderCancelled` | 用户主动取消（v1 可能有，先列出） | **发布**（触发 Inventory 回补 Redis） |
| 延时关单消息投递失败 | `OrderTimeoutMessageDeliveryFailed` | message_log retry_count ≥ 3 | **发布**（通知管理员告警） |
| 悬空死账已发现 | `OrphanDeductionFound` | SchedulerX ZRANGEBYSCORE 扫描到 | 内部（触发 Lua 原子回滚） |

### 支付上下文

| 事件 | 英文名 | 触发时机 | 发布范围 |
|---|---|---|---|
| 支付已发起 | `PaymentInitiated` | 用户点击支付 | 内部 |
| 支付已确认 | `PaymentConfirmed` | 第三方回调验证通过 | **发布**（Order 上下文消费 → 状态变 PAID） |

### 库存上下文（协调层）

库存无独立领域事件，它**响应**其他上下文的事件：

- 监听 `ActivityApproved` → 分配库存：`goods.stock`↓，`seckill_goods.stock`↑
- 监听 `ActivityPreheated` → 预热到 Redis：`SET seckill:stock:{activityId}:{seckillGoodsId}`
- 监听 `PeakShavingMessageSendFailed` → 即时反向补偿：`INCRBY` + `SREM`
- 监听 `OrderTimedOut` / `OrderCancelled` → 回补 Redis：`INCRBY` + `SREM`
- 监听 `ActivityEnded` → 等 1 分钟后按 Redis 真实剩余值回补 `goods.stock`

### 通知上下文

通知上下文也主要是**响应**型：

| 事件 | 英文名 | 触发时机 |
|---|---|---|
| 私信已送达 | `PrivateMessageDelivered` | `user_message` 入库 |
| 活动广播已推送 | `ActivityBroadcastPushed` | WebSocket 广播成功 |
| 管理员告警已触发 | `AdminAlertTriggered` | 消息投递失败 ≥ 3 次 |

---

## 2. 限界上下文

### 2.1 总览

```text
┌──────────┐   ┌──────────┐   ┌──────────┐
│ Identity  │   │ Product  │   │ Activity │
│   用户    │   │   商品   │   │   活动   │
└────┬─────┘   └────┬─────┘   └────┬─────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
     ┌──────────────┴──────────────────────────┐
     │            核心交易域 (Core)              │
     │                                          │
     │  ┌──────────┐  MQ (PL)  ┌──────────┐    │
     │  │ Seckill  │──────────▶│  Order   │    │
     │  │  抢购    │           │  订单    │    │
     │  └────┬─────┘           └────┬─────┘    │
     │       │                      │          │
     │       │   事件驱动            │ 事件驱动  │
     │       ▼                      ▼          │
     │  ┌──────────────────────────────────┐   │
     │  │           Inventory              │   │
     │  │            库存（协调层）          │   │
     │  └──────────────────────────────────┘   │
     │                                          │
     │  ┌──────────┐                            │
     │  │ Payment  │◀─── 监听 Order 事件 ────────┘
     │  │  支付    │
     │  └──────────┘
     └──────────────┬──────────────────────────┘
                    │
              ┌─────┴─────┐
              │Notification│  ── ACL ──▶  WebSocket / 外部 MQ
              │   通知     │
              └───────────┘
```

### 2.2 各上下文职责与拆分理由

#### 用户上下文（Identity）— 通用子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 注册、登录、角色管理（admin/merchant/user）、封禁/解封 |
| **聚合根** | `SysUser` |
| **拆分理由** | 用户身份是独立子域，其他上下文只消费 `user_id` + `role` + 封禁状态。与秒杀核心逻辑无关，按"身份与访问"通用子域自然分离 |

#### 商品上下文（Product）— 支撑子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 商品 CRUD、上下架、商家商品归属管理 |
| **聚合根** | `Goods` |
| **拆分理由** | 商品生命周期独立于秒杀活动。一个商品可以存在但从未参于活动。商家管理自有商品是独立业务能力 |

#### 活动上下文（Activity）— 核心子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 活动状态机（draft→pending→preheating→running→ended）、审核流、秒杀商品绑定（`SeckillGoods`）、活动启停 |
| **聚合根** | `Activity`（含 `SeckillGoods` 实体） |
| **拆分理由** | 活动有自己的生命周期和业务规则（状态机、审批流），与"如何抢购"是不同关注点。商家创建活动 vs 用户参与抢购是两个完全不同的用例 |

#### 抢购上下文（Seckill）— 核心子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 网关防护（黑名单 Redis + 1s 限流）、Redis+Lua 原子预扣（DECRBY+SADD）、生成 orderToken、ZADD pending zset、发削峰 MQ、发送失败即时反向补偿 |
| **聚合根** | 无传统聚合根——以 `SeckillDomainService` 形态存在，操作 Redis 数据结构 |
| **拆分理由** | 高并发入口（瞬时万级 QPS），技术方案截然不同（纯 Redis + Lua + MQ，不碰 DB）。需要独立演进和容量规划。与 Order 通过 MQ 异步解耦，无共享 DB 事务 |

#### 订单上下文（Order）— 核心子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 消费 MQ 创建订单（`uk_order_token` 幂等）、订单状态机（UNPAID→PAID/CANCELLED）、`message_log` 投递延时关单消息、SchedulerX 悬空死账扫描 |
| **聚合根** | `SeckillOrder`、`MessageLog`（两个独立聚合） |
| **拆分理由** | 订单生命周期长于抢购瞬间（1 分钟支付窗口 + 延时关单 + 补偿定时任务）。与抢购的"极速拒绝/通过"不同节奏，需要独立事务边界和重试策略 |

> **为什么 `MessageLog` 是独立聚合？** 尽管与 `SeckillOrder` 在同一 DB 事务写入，但 `MessageLog` 有独立的发送状态机和生命周期（INIT→SENT/FAIL），且扫描线程按 `idx_status_retry` 拉取，不应通过订单聚合根访问。

#### 支付上下文（Payment）— 支撑子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 发起支付、接收第三方回调、验证签名、发布 `PaymentConfirmed` |
| **聚合根** | `Payment`（v1 可选，支付流水表待建） |
| **拆分理由** | 支付对接外部第三方网关，天然需要防腐层。与订单内部逻辑隔离，支付渠道变更不应影响订单核心 |

#### 库存上下文（Inventory）— 核心子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 响应事件协调 Redis / MySQL 库存一致性：分配（审批通过）、预热、即时补偿（MQ 发送失败）、回补（关单/活动结束）、悬空死账回滚 |
| **聚合根** | 无传统聚合根——以 `InventoryDomainService` + 事件处理器形态存在 |
| **拆分理由** | 库存操作散布在活动、抢购、订单三个上下文中。提取为独立的**协调层**，避免跨上下文直接操作同一份数据。库存最终一致性策略需要独立设计和测试 |

#### 通知上下文（Notification）— 通用子域

| 维度 | 内容 |
|---|---|
| **核心职责** | 点对点私信入库（`user_message`）、WebSocket 活动广播（不落库）、按预置模板填充参数、管理员告警 |
| **聚合根** | `UserMessage` |
| **拆分理由** | 通知是典型通用子域，有独立技术方案（入库 vs WebSocket），消费多种事件（审核结果、封禁、活动发布、错误告警） |

---

## 3. 多义词汇

以下词汇在不同上下文中有不同含义，开发时必须区分。

| 词汇 | Identity | Product | Activity | Seckill | Order | Inventory |
|---|---|---|---|---|---|---|
| **库存 Stock** | — | `goods.stock`：商家拥有的普通库存总量 | `seckill_goods.stock`：分配给活动的秒杀库存（≤ goods.stock） | `seckill:stock:{aId}:{gId}`：Redis 中可扣减的预热计数 | — | 跨 Redis/MySQL 的一致性视图，协调分配/补偿/回补 |
| **状态 Status** | `role` + 封禁标记 | 0下架/1上架 | draft/pending/preheating/running/ended | — | UNPAID/PAID/CANCELLED | — |
| **活动 Activity** | — | — | 有时限、有审核流程的秒杀事件 | 一个正在运行、可抢购的会话（按 activityId 寻址 Redis key） | 订单关联的归属维度（有索引按活动查订单） | 库存预热/回补的触发源 |
| **商品 Goods** | — | 商家创建的普通商品（日常售价、总库存） | `SeckillGoods`：绑定到活动、有秒杀价和限购数的实体 | 被抢购的标的（按 seckillGoodsId 隔离 Redis key） | 订单行关联的秒杀商品 | 库存分配/回补的单位 |
| **用户 User** | 已注册的账号 | 商品的拥有者（merchant_id） | 活动的创建者（merchant_id） | 参与抢购的买家（userId） | 下单的买家（user_id） | 需 SREM 的 Redis set 成员 |
| **消息 Message** | — | — | 审核结果通知消息 | MQ 消息体（SeckillDeductedEvent） | MQ 消息 + 延时关单消息 + message_log 记录 | — |
| **Token** | JWT / Session token | — | — | `orderToken`：排队凭证（UUID，zset score=timestamp） | `order_token`：幂等键（UK）、订单状态查询凭据 | — |

---

## 4. 统一语言术语表

| 中文术语 | 英文代码名 | 所在上下文 | 定义 |
|---|---|---|---|
| 用户 | `SysUser` | Identity | 已注册的系统账户，拥有 role（admin/merchant/user）和封禁状态 |
| 商家 | Merchant | Identity / Product / Activity | role=merchant 的用户，可创建商品和活动。CLAUDE.md 角色表中"商家"的代码对应 |
| 管理员 | Admin | Identity / Activity | role=admin 的用户，审核活动、封禁用户、强制下架活动 |
| 买家 | Buyer | Identity / Seckill / Order | role=user 的用户，参与抢购。等同于"普通用户" |
| 封禁 | Banned | Identity | 被管理员禁止参与抢购的用户状态。网关层通过 Redis 黑名单校验 |
| 商品 | `Goods` | Product | 商家创建的普通商品。有雪花 goods_id、日常售价 price、总库存 stock |
| 秒杀商品 | `SeckillGoods` | Activity | 绑定到活动的商品。是 Activity 聚合的内部实体，有秒杀价、秒杀库存、限购数量 |
| 活动 | `Activity` | Activity | 有时限、有状态机的秒杀事件。聚合根 |
| 活动状态 | `ActivityStatus` | Activity | draft / pending / preheating / running / ended |
| 审核 | Review | Activity | 管理员对 pending 状态活动的审批操作。通过→preheating，驳回→通知商家 |
| 预热 | Preheating | Activity / Inventory | 活动开始前将秒杀库存加载到 Redis：`SET seckill:stock:{activityId}:{seckillGoodsId}` |
| 抢购 | Seckill | Seckill | 用户点击"立即抢购"触发的核心流程：网关校验→Lua 预扣→生成 token→发 MQ |
| 预扣 | Pre-deduction | Seckill / Inventory | Redis Lua 原子执行 `DECRBY stock` + `SADD users`。此时只扣 Redis，DB 未扣 |
| 排队凭证 | `orderToken` | Seckill / Order | Lua 成功后生成的 UUID。写入 Redis zset `seckill:pending:{activityId}`（score=timestamp），同时作为订单幂等键 |
| 削峰 | Peak Shaving | Seckill → Order | 将预扣成功请求通过 RocketMQ 异步投递，避免 DB 瞬时压力 |
| 即时补偿 | Immediate Compensation | Seckill / Inventory | MQ 发送失败时立即执行：`INCRBY` 恢复库存 + `SREM` 移除用户 |
| 订单 | `SeckillOrder` | Order | 抢购成功后生成的订单。聚合根，有独立状态机 |
| 订单状态 | `OrderStatus` | Order | UNPAID / PAID / CANCELLED |
| 幂等 | Idempotent | Order | `uk_order_token` 保证同一 orderToken 重复消费 MQ 不产生重复订单 |
| 延时关单 | Delayed Order Cancellation | Order | 订单创建后 1 分钟未支付自动取消。通过 `message_log` + 扫描线程可靠投递延时消息 |
| 本地消息表 | `message_log` | Order | 事务性 outbox 表。与 `seckill_order` 同事务写入，后台线程扫描 INIT 记录投递 MQ |
| 悬空死账 | Orphan Deduction | Order / Inventory | Redis 已扣但 DB 未生成订单的异常。SchedulerX 每 3 分钟扫描 pending zset 比对 MySQL，Lua 原子回滚 |
| 支付 | Payment | Payment | 对 UNPAID 订单发起支付，第三方回调确认后发布 `PaymentConfirmed` |
| 支付确认 | `PaymentConfirmed` | Payment → Order | 第三方回调验证通过后发布的事件。Order 监听并执行 UNPAID→PAID |
| 库存回补 | Stock Recovery | Inventory | 关单/活动结束后将剩余秒杀库存加回 Redis（`INCRBY`+`SREM`）或 `goods.stock` |
| 库存一致性 | Stock Consistency | Inventory | 最终一致性模型——允许短暂 Redis/MySQL 不一致，通过即时补偿 + 延时关单回补 + SchedulerX 兜底保证收敛 |
| 私信 | `UserMessage` | Notification | 点对点通知，入库 `user_message`。类型：approval_result / ban_info / sent_error |
| 广播 | Broadcast | Notification | 商家发布活动时通过 WebSocket 向全体在线用户推送，不落库 |
| 消息模板 | Message Template | Notification | 预置模板，按参数填充。如"您于{时间}创建的"{活动名}"活动已通过审批" |

---

## 5. 上下文映射

### 5.1 映射关系总表

| 上游 U | 下游 D | 映射模式 | 说明 |
|---|---|---|---|
| Identity | Product, Activity, Order | **Open Host Service + Published Language** | Identity 提供统一 API 查询用户身份/角色/封禁状态。下游按 `user_id` 消费，不关心内部实现。共享内核：`user_id` 值的格式 |
| Identity | Seckill | **Shared Kernel** | 网关黑名单校验直接读 Redis 封禁集合，与 Identity 共享"封禁用户 Set"模型。双方须同步维护 |
| Product | Activity | **Customer-Supplier** | Activity 创建时校验 goods 存在且属于当前商家（`goods.merchant_id = currentMerchantId`）。Product 是上游，定义 goods_id 规则；Activity 遵从 |
| Activity | Seckill | **Customer-Supplier** | Seckill 的 Redis key 按 `activityId` + `seckillGoodsId` 寻址。活动时间窗口、running 状态由 Activity 决定。Seckill 是下游，完全遵从 Activity 模型 |
| Seckill | Order | **Published Language（via MQ）** | `SeckillDeductedEvent` 消息体是双方约定的 PL。Order 是 Conformist（完全遵从 Seckill 消息格式，不反向要求）。MQ 是传输通道 |
| Seckill | Inventory | **Shared Kernel / 事件驱动** | Seckill 执行 Lua 预扣直接操作 Redis key——与 Inventory 共享 Redis 库存模型。发送失败时 Seckill 自驱即时补偿（属于 Inventory 职责但由 Seckill 直接执行以降低延迟） |
| Order | Inventory | **事件驱动** | Order 发布 `OrderTimedOut` / `OrderCancelled`，Inventory 监听并执行 Redis 回补。不直接 RPC 调用 |
| Order | Payment | **Customer-Supplier** | Payment 需要 Order 提供 `orderNo`、`totalAmount`、`orderToken`。Order 上游，Payment 下游 |
| Payment | Order | **事件驱动** | Payment 发布 `PaymentConfirmed`，Order 监听并执行 `UNPAID→PAID`（乐观锁保证并发安全） |
| Activity | Notification | **事件驱动** | Activity 发布 `ActivityApproved` / `ActivityRejected`，Notification 监听并按模板发私信/广播 |
| Activity | Inventory | **事件驱动** | Activity 发布 `ActivityApproved` → Inventory 分配库存；`ActivityEnded` → Inventory 回补 goods.stock |
| Identity | Notification | **事件驱动** | Identity 发布 `UserBanned`，Notification 监听并发送封禁通知私信 |
| Order | Notification | **事件驱动** | Order 发布 `OrderTimeoutMessageDeliveryFailed`，Notification 监听并告警管理员 |
| Notification | 外部基础设施 | **Anti-Corruption Layer** | WebSocket 连接管理、MQ 投递细节封装在 ACL 内。Notification 核心只关心"发什么内容给谁"，不关心传输协议 |
| Payment | 第三方支付网关 | **Anti-Corruption Layer** | 支付渠道 API 的差异（签名算法、回调格式）封装在 ACL 内，Payment 领域只操作 `Payment` 聚合 |

### 5.2 关键设计决策

**为什么 Seckill 和 Order 之间用 MQ（Published Language）而不是直接 RPC？**

抢购入口瞬时万级 QPS，订单创建是 DB 写入（受限于连接池、行锁、事务吞吐）。如果同步 RPC，抢购的吞吐量被订单 DB 直接拖死。MQ 削峰让抢购以 Redis 速度拒绝/通过请求，订单按自己节奏消费。

**为什么 Inventory 单独成上下文？**

库存操作源有多个：活动审批（分配）、抢购（预扣）、MQ 故障（即时补偿）、关单（回补）、活动结束（回补）、SchedulerX（悬空死账回滚）。如果嵌在任意一个上下文里，其他上下文需要跨边界直接操作库存数据。独立 Inventory 作为协调层，所有库存事件都发到这里统一处理。

**为什么 `message_log` 是独立聚合，不是 `SeckillOrder` 的一部分？**

尽管同一事务写入，但：
1. `message_log` 有独立的状态机（INIT→SENT/FAIL）和生命周期（扫描→投递→重试），不随订单状态变化
2. 扫描线程按 `idx_status_retry` 直接拉取，不应通过订单聚合根
3. 未来可能有其他业务类型（非订单）也使用 `message_log`

---

## 6. 聚合设计要点

### 6.1 聚合根与不变量

| 聚合根 | 上下文 | 内部实体/值对象 | 必须维护的不变量 |
|---|---|---|---|
| `SysUser` | Identity | — | `userName` 唯一；`email` 唯一；被封禁用户不能参与抢购 |
| `Goods` | Product | — | `stock ≥ 0`；只有拥有者（`merchant_id`）可修改 |
| `Activity` | Activity | `SeckillGoods`（1:N 实体）、`ActivityStatus`（值对象）、`TimeRange`（值对象） | 状态转换必须合法（→状态机）；`startTime < endTime`；`SeckillGoods` 只能在 draft 状态增删改；`SeckillGoods.stock ≤ goods.stock`（分配时校验） |
| `SeckillOrder` | Order | `OrderStatus`（值对象） | `orderToken` 唯一；`userId+activityId+seckillGoodsId` 唯一；状态只能 `UNPAID→PAID` 或 `UNPAID→CANCELLED`；**修改状态必须带乐观锁条件 `WHERE status='UNPAID'`** |
| `MessageLog` | Order | `SendStatus`（值对象） | `bizType+bizId` 唯一；`retryCount ≤ 3` |
| `UserMessage` | Notification | `MessageType`（值对象）、`Content`（值对象） | `userId` 必须存在（应用层逻辑关联校验） |

### 6.2 "禁止跨聚合直接修改" 的具体规则

以下操作在代码层面**禁止**：

1. **SeckillOrder 直接改 Goods.stock** ❌ → 通过 Inventory 事件处理器
2. **Activity 直接改 SeckillOrder.status** ❌ → 活动强制下架时只改 Activity 自己的状态；已有订单按自己的规则继续（用户仍可支付，支付窗口不变）
3. **Payment 直接改 SeckillOrder 表字段** ❌ → 发布 `PaymentConfirmed`，Order 上下文自行处理 `order.pay()`
4. **Seckill 直接写 seckill_order 表** ❌ → 通过 MQ 投递 `SeckillDeductedEvent`，Order 上下文消费写入

### 6.3 仓储接口（Repository）边界约束

```java
// ✅ 只返回聚合根
interface ActivityRepository {
    Activity findById(ActivityId id);
    void save(Activity activity);
    // ❌ 不暴露内部实体：SeckillGoods findSeckillGoodsById(...);
}

interface OrderRepository {
    SeckillOrder findByOrderToken(OrderToken token);
    void save(SeckillOrder order);
    // SchedulerX 扫描用——返回 token 列表，不直接返回实体集合
    List<OrderToken> findTokensInPendingWindow(long minScore, long maxScore);
}
```

### 6.4 业务规则在领域对象中（不在 Service）

```java
// ✅ Activity 聚合根内部
public class Activity {
    public void approve() {
        if (this.status != ActivityStatus.PENDING) {
            throw new IllegalStateException("只能审核待审核状态的活动");
        }
        this.status = ActivityStatus.PREHEATING;
        registerEvent(new ActivityApproved(this));
    }

    public void addSeckillGoods(Goods goods, BigDecimal seckillPrice, int stock, int limitNum) {
        if (this.status != ActivityStatus.DRAFT) {
            throw new IllegalStateException("只有草稿状态可以修改秒杀商品");
        }
        // 不变量：秒杀库存不能超过原商品库存
        if (stock > goods.getStock()) {
            throw new IllegalArgumentException("秒杀库存不能超过商品总库存");
        }
        this.seckillGoodsList.add(new SeckillGoods(goods.getGoodsId(), seckillPrice, stock, limitNum));
    }
}

// ✅ SeckillOrder 聚合根内部
public class SeckillOrder {
    public void pay() {
        if (this.status != OrderStatus.UNPAID) {
            throw new IllegalStateException("只能支付待支付状态的订单");
        }
        this.status = OrderStatus.PAID;
        this.payTime = LocalDateTime.now();
        registerEvent(new OrderPaid(this));
    }

    public void cancel() {
        if (this.status != OrderStatus.UNPAID) {
            throw new IllegalStateException("只能取消待支付状态的订单");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelTime = LocalDateTime.now();
        registerEvent(new OrderTimedOut(this));
    }
}
```

---

## 7. 核心链路在 DDD 视角下的穿行

```text
  用户点击【立即抢购】
       │
       ▼
  ┌─────────────────────────────────────────────┐
  │ Seckill 上下文（纯 Redis，不碰 DB）           │
  │                                              │
  │ 1. 网关：Identity 共享的黑名单 Set + 限流计数器│
  │ 2. Lua 原子预扣（操作 Inventory 共享模型）     │
  │    ├─ 返回 -1/-2 → 直接终止（SeckillRejected） │
  │    └─ 返回 1 → 生成 orderToken + ZADD pending │
  │ 3. 发 MQ（SeckillDeductedEvent）               │
  │    └─ 发送失败 → 即时补偿（Inventory 监听）     │
  └──────────────────┬──────────────────────────┘
                     │ MQ (Published Language)
                     ▼
  ┌─────────────────────────────────────────────┐
  │ Order 上下文（开始碰 DB）                     │
  │                                              │
  │ 4. 消费 MQ → 一个本地 DB 事务：               │
  │    - SeckillOrder 聚合创建（uk_order_token 幂等│
  │    - MessageLog 聚合创建（bizType=order_timeout│
  │    - 发布 OrderCreated                        │
  │ 5. MessageLog 扫描线程 → 投递 1min 延时关单    │
  │    - 成功 → message_log.status = SENT          │
  │    - 失败 3 次 → FAIL + 通知管理员告警          │
  └──────────────────┬──────────────────────────┘
                     │
         ┌───────────┼───────────┐
         ▼                       ▼
  ┌─────────────┐        ┌─────────────┐
  │Payment 上下文│        │Notification │
  │             │        │  上下文      │
  │ 发起支付    │        │             │
  │ 回调→验证   │        │ 前端轮询感知 │
  │ Payment     │        │ OrderCreated │
  │ Confirmed   │        │ 跳转支付页   │
  └──────┬──────┘        └─────────────┘
         │
         ▼
  ┌─────────────────────────────────────────────┐
  │ Order 上下文（再次消费）                      │
  │                                              │
  │ 6. 监听 PaymentConfirmed →                    │
  │    order.pay()  // UNPAID→PAID (乐观锁)       │
  │    发布 OrderPaid                             │
  │                                              │
  │ 7. 延时关单消息到期 →                          │
  │    order.cancel() // UNPAID→CANCELLED (乐观锁) │
  │    发布 OrderTimedOut                         │
  └──────────────────┬──────────────────────────┘
                     │ 事件
                     ▼
  ┌─────────────────────────────────────────────┐
  │ Inventory 上下文（事件处理器）                 │
  │                                              │
  │ 8. 监听 OrderTimedOut →                      │
  │    Redis Lua: INCRBY + SREM                  │
  │                                              │
  │ 9. 监听 ActivityEnded →                      │
  │    等 1min → GET Redis 真实剩余 →            │
  │    UPDATE goods SET stock = stock + 剩余      │
  └─────────────────────────────────────────────┘
```
