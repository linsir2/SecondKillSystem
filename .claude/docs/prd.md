| 角色 | 权限 |
| --- | --- |
| 用户 | 抢购，支付，个人信息管理，订单查询 |
| 商家 | 申请秒杀活动，绑定商品，查看活动销售数据 |
| 管理员 | 活动审核/驳回、黑名单用户封禁、活动强制下架 |

## 数据流：

### 创建活动：

```
商家创建活动 -[写入活动表]-> 管理员审批 -[通过]-> 通知商家/用户 -> 前端活动列表该活动同步开启倒计时
                                      |-[驳回]-> 警告商家(消息中心)

商品表有goods表和seckill_goods表，商家创建活动后如果管理员审批成功的话，goods 减去对应库存，seckill_goods 增加对应库存

备注：
1. 商家创建活动时的可选商品量要连接具体的库存表，可以选择多个商品，但必须是商人自己所有商品，做成一个下拉列表
2. 管理员审批通过之后同时通过钩子通知商家和用户
- 商家得知审批通过
- 用户得知有这个活动
- 钩子通过预设两种模板填充对应参数数据即可
  - 【商家版】：您于{时间}创建的“{活动名}”活动已通过审批，将于{开始时间}准时开始秒杀
  - 【用户版】：{商家名}将于{开始时间}创办“{活动名}”秒杀活动，包含以下商品：{商品名1}、{商品名2}...
```

### 活动与商品详情展示：

```
用户点击进入活动页 -> 读取activity_id，从activity表中查到活动相关数据，并根据绑定的goods_id查seckill_goods表查到秒杀活动商品相关信息 -> 返回所有数据到前端，渲染前端

备注：
1. 要有倒计时，在活动页
2. 第二版再让管理员可以创建活动，并让多个商人申请入驻秒杀活动，填写相关秒杀商品
```

### 高并发核心扣减与削峰流（第一阶段：Redis, Lua, 普通RocketMQ消息队列）

```
[用户点击【立即抢购】]
  │
  ▼
1. [网关 / 拦截器防护]
   ├── 黑名单校验：拦截封禁 ID
   └── 接口限流：单用户 1 秒只能请求 1 次
   |
   ▼ (通过校验)
2. [执行 Redis + Lua 脚本 (原子操作)]
   │
   ├── 注意：Key 必须按活动/商品隔离，不能全局共用
   │      seckill:stock:{activityId}:{seckillGoodsId}   库存计数
   │      seckill:users:{activityId}:{seckillGoodsId}   已购买用户集合
   |
   ├── 检查 A: 是否在 seckill:users:{activityId}:{seckillGoodsId} 集合中？ ➔ 在则返回 -1 (重复购买)
   ├── 检查 B: seckill:stock:{activityId}:{seckillGoodsId} 是否 > 0？       ➔ 否则返回 -2 (已售罄)
   └── 执行 C: DECRBY seckill:stock:{activityId}:{seckillGoodsId} 1 （Lua 脚本）
              并  SADD seckill:users:{activityId}:{seckillGoodsId} userId ➔ 返回 1 (成功)
  │
  ├──────────────────────────────────────────┐
  │ [Lua 返回 -1 / -2]                       │ [Lua 返回 1 (预扣成功)]
  ▼                                          ▼
网关直接拦截，返回前端                    生成全局排队凭证 orderToken，额外写入带过期时间的 zset:ZADD seckill:pending:{actId} 当前时间戳 orderToken
“已售罄”或“请勿重复抢购”                       │
                                             ▼
                                3. [发送普通 RocketMQ 消息]
                                   { userId, activityId, seckillGoodsId, orderToken }
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       │ 发送成功                                   │ 发送失败/超时 (Catch 异常)
                       ▼                                           ▼
            向前端返回 orderToken                   [应用层即时反向补偿]
            前端显示“排队中...”，                     - Redis 加回库存: INCRBY 1
            开启定时轮询接口                         - Redis 移出用户: SREM userId
            GET /api/v1/order/status?token=xxx     - 给前端返回“系统繁忙，请重试”
```

### 异步订单生成与事务消息流（第二阶段）

```
[订单服务 Order Service] 消费普通 MQ 中的削峰消息 (SeckillDeductedEvent)
  │
  ▼
1. 执行【本地 MySQL 事务】(@Transactional)
   - 插入 seckill_order 表，状态设为【待支付】
   - 插入 message_log 表，状态为 INIT，记录待发送的 1 分钟超时关单消息
   - 唯一索引校验 uk_order_token (保障 MQ 消费幂等性，防止重复插入)
  │
  ├──────────────────────────────────────────┐
  │ 本地 DB 事务成功                          │ 本地 DB 事务失败 (如 uk冲突、DB锁超时、字段异常)
  ▼                                          ▼
2A. DB 自动提交 (COMMIT)                   2B. DB 自动回滚 (ROLLBACK)
  │                                          │
  |                                          ├─► catch (DuplicateKeyException e) {} 唯一索引冲突，不发补偿消息，直接 ACK
  |                                          |
  |                                          ├─► catch (Exception e) {consumer.nack();} 让 mq 重试最多三次 
  |                                          |
  |                                           ├─► 向 MQ 返回 ACK 信号 (消费跳过，防止无限死循环重试)
  │                                          │
  ├─► 向 MQ 返回 ACK 信号，结束本次消费        └─► 交给 SchedulerX Job 兜底
  |
  ▼                                               
3. 后台消息扫描线程（Message Sender）每隔 5~10 秒执行
   │
   ▼
   拉取 message_log 中 status = 'INIT' 且 retry_count < 3 的记录
   │
   ▼
   发送【1分钟超时关单延时消息】到 MQ（如 RocketMQ 延时级别对应 1min）
   │
   ├─► 发送成功：UPDATE message_log SET status = 'SENT', send_time = now() WHERE msg_id = ?
   │
   └─► 发送失败：UPDATE message_log SET retry_count = retry_count + 1 WHERE msg_id = ?
                  达到最大重试次数后可置为 FAIL，向管理员告警处理，预制消息模板，填充参数
   │
   ▼
4. 前端轮询接口 GET /api/v1/order/status
   查到 orderNo 生成成功，页面自动跳转【订单支付页】
   等待用户付款...

======================= 【后续业务与异常兜底数据流】 =======================

▶ 流转 A：用户在 1 分钟内支付成功 (正向终态)
[支付网关/回调接口] 收到第三方支付成功通知
  │
  ▼
执行【本地 MySQL 事务】(利用行锁防止并发更新)
  - UPDATE seckill_order SET status = 'PAID' WHERE order_token = ? AND status = 'UNPAID'
  - (若需要，在此处记录支付流水)

▶ 流转 B：用户 1 分钟未支付 (逆向终态)
[延时关单消费者] 消费到 1 分钟前发出的超时通知消息
  │
  ▼
执行【本地 MySQL 事务】(利用行锁严格校验)
  - 执行：UPDATE seckill_order SET status = 'CANCELLED' WHERE order_token = ? AND status = 'UNPAID'
  │
  ├─► 受影响行数 = 0 (说明卡点被支付了，状态已变) ➔ 直接丢弃该消息，不作处理
  │
  └─► 受影响行数 = 1 (说明确实未支付，关单成功) 
      └── 触发 Redis 补偿：
          - 恢复库存：INCRBY seckill:stock:{activityId}:{seckillGoodsId} 1
          - 清除标记：SREM seckill:users:{activityId}:{seckillGoodsId} userId

▶ 异常兜底 C：服务器极度异常导致的“悬空死账” (终极防线)
  (场景：如果在上面 2B 步骤中，准备发补偿消息的那一瞬间机器断电，导致 Redis 扣了但 DB 没订单，且补偿消息没发出去)
[SchedulerX 定时任务] 每隔 3 分钟执行
  │
  ▼
每次只扫描窗口：3min < 当前时间 - score < 10min（只筛查这段时间内预扣成功的订单凭证）
使用 ZRANGEBYSCORE 按时间窗口分片增量查询，不会拉取全量数据
遍历拿到一批 orderToken，批量查询 MySQL：WHERE uk_order_token IN (...)
筛选：Redis 存在 orderToken，MySQL 无对应订单 = 悬空死账
执行 Lua 原子回滚：恢复库存 + SREM 用户 + ZREM 移除 pending 记录
```

- 消息中心
    - 点对点私信，写入user_message
        - 管理员对某个用户封禁 → 用户消息中心通知
        - 管理员对某个活动驳回 → 活动驳回理由等，商家消息中心通知
        - 后台消息扫描线程发现某个订单发送超过三次失败 → 通知管理员
    - 全局系统广播：商家发布秒杀活动 → 不写库，通过websocket广播
- 秒杀抢购
    - 资格校验 —— 禁止封禁id用户抢购，在网关层利用redis挡掉
    - 限流 —— 单用户一秒只能请求一次，用redis；一个用户只能有一个订单
    - 库存预扣 —— 预热到redis中
    - 排队 —— 分配redis中的令牌，拿到token则到rocketmq中排队异步等订单创建，然后支付
    - 防止redis扣了但mysql没扣 —— schedulerX 定时任务
- 订单
    - 订单创建
    - 状态机 —— 待支付/已支付 / 已取消
- 活动
    - 商家创建活动，设置活动开始时间，结束时间，商品，商品量，秒杀价，单人允许秒杀商品量
    - 规则配置 —— 倒计时
    - 商品绑定 —— 设置商品量 ，核对库存
    - 活动状态机 —— 草稿 / 待审核 / 预热中 / 进行中 / 已结束
- 库存 —— 数据最终一致性
    - 数据回补
    - 商品库存 —— 核对是否够创建秒杀活动 / 异步扣库存
    - 活动结束后【**等待1分钟】**，秒杀库存如果没卖完，要将其恢复到goods普通库存中，【读取秒杀的 redis 来获取最真实的剩余库存】，然后加回去
        
        > 注意：读取 Redis 库存时，若 Key 不存在（nil）应按 0 处理，避免空指针异常。
        > 
- 支付
    - 1分钟未点击则订单作废 → 支付失败/超时取消订单回补库存
    - 支付成功 → 订单换状态，扣库存，前端显示已成功
- 用户
    - 注册登录
    - 用户基本信息
- 前端动静分离
    - 倒计时以服务器的为准，后端写一个轻量接口，只返回当前服务器时间和活动开始时间，`{ "serverTime": 1700000000000, "startTime": 1700003600000 }`。 —— JS以此在浏览器本地跑倒计时
    - 静态内容，前端静态文件由 nginx托管，让他接管静态请求

---