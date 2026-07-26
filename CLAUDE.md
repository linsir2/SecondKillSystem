# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

秒杀系统（Seckill / Flash Sale System）—— 高并发、高可用秒杀架构练习项目。

完整需求文档见 `.claude/docs/prd.md`，数据库设计见 `.claude/docs/sql.md`。
DDD 领域分析见 `.claude/docs/ddd.md`，开发注意事项见 `.claude/docs/注意事项.md`。

## 技术栈

| 层       | 选型                   |
| -------- | ---------------------- |
| 后端     | Spring Boot（Java）    |
| 前端     | React + Vite           |
| 缓存     | Redis（Docker）         |
| 消息队列 | RocketMQ（Docker）      |
| 数据库   | MySQL（Docker）         |
| 密码哈希 | Argon2id               |
| 定时任务 | SchedulerX             |
| 静态托管 | Nginx（Docker）         |

> Redis / RocketMQ / MySQL 等中间件统一通过 docker-compose 启动，与应用代码分离。

## 核心链路

```text
用户点击【立即抢购】
  │
  ▼
① 网关层：黑名单校验（Redis）+ 单用户 1s 限流
  │
  ▼
② Redis + Lua 原子脚本：检查重复购买 → 检查库存 → DECRBY + SADD
  │
  ├─ 失败（-1 重复 / -2 售罄）→ 直接拦截
  └─ 成功 → 生成 orderToken，ZADD 到 pending 队列
      │
      ▼
③ 发普通 RocketMQ 消息（削峰）← 发送失败则即时反向补偿（恢复库存+SREM）
  │
  ▼
④ Order Service 消费 MQ → 本地事务插入 seckill_order + message_log
  唯一索引 uk_order_token 保证幂等
  │
  ├─ 成功 → 发 1 分钟延时关单消息（可靠投递 via message_log 扫描线程）
  └─ 失败 → 重试 3 次，兜底 SchedulerX 定时任务补偿
      │
      ▼
⑤ 前端轮询 /api/v1/order/status → 订单生成后跳转支付页
```

## 目录结构

```
秒杀系统/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/seckill/
│   │   ├── SeckillApplication.java   # 入口 @MapperScan
│   │   ├── common/                   # 公共层（constant/exception/result/util）
│   │   ├── config/                   # @Configuration 类
│   │   │   ├── redis/                #  RedisTemplate 序列化
│   │   │   ├── mq/                   #  RocketMQ 配置
│   │   │   └── web/                  #  CORS 过滤器
│   │   ├── filter/                   # 网关拦截（限流、黑名单）
│   │   └── module/                   # 业务模块
│   │       ├── user/                 #  用户（注册登录、信息管理）
│   │       ├── goods/                #  普通商品
│   │       ├── activity/             #  秒杀活动（状态机、审核）
│   │       ├── order/                #  订单（状态机、支付）
│   │       ├── stock/                #  库存（预留）
│   │       └── message/              #  消息中心（点对点+WebSocket）
│   ├── src/main/resources/
│   │   ├── application.yml           #  基础配置 + 占位符
│   │   ├── application-dev.yml       #  本地开发配置
│   │   ├── application-prod.yml      #  生产环境配置
│   │   ├── db/migration/             #  Flyway 迁移脚本
│   │   └── mapper/                   #  MyBatis XML
│   ├── pom.xml                       #  Spring Boot 3.2 + MP + RocketMQ + Flyway
│   └── Dockerfile                    #  多阶段构建
├── frontend/                         # React + Vite 前端
│   ├── index.html
│   ├── vite.config.ts                #  dev proxy → localhost:8080
│   ├── tsconfig.json
│   ├── package.json
│   ├── default.conf                  #  生产 Nginx 配置
│   ├── Dockerfile                    #  build → nginx:alpine
│   └── src/
│       ├── main.tsx                  #  React 入口
│       ├── App.tsx                   #  路由根组件
│       ├── api/                      #  API 调用封装
│       ├── components/               #  通用组件
│       ├── pages/                    #  页面
│       ├── hooks/                    #  自定义 Hook
│       └── utils/                    #  工具函数
├── docker/
│   ├── docker-compose.yml            #  MySQL + Redis + RocketMQ
│   ├── rocketmq/broker.conf
│   └── mysql/init.d/
├── sql/                              # DDL 脚本（备查）
├── .gitignore
└── CLAUDE.md
```

每个业务模块内按 controller / service / mapper / model(entity, dto, vo) 分层。

## 角色与权限

| 角色   | 权限 |
| ------ | ---- |
| 用户   | 抢购、支付、个人信息管理、订单查询 |
| 商家   | 申请秒杀活动、绑定商品（下拉选择自有商品）、查看活动销售数据 |
| 管理员 | 活动审核/驳回、黑名单用户封禁、活动强制下架 |

## 活动状态机

`draft`（草稿） → `pending`（待审核） → `preheating`（预热中） → `running`（进行中） → `ended`（已结束）

审核通过后 `goods` 减对应库存、`seckill_goods` 加对应库存。活动结束后等待 1 分钟，秒杀剩余库存按 Redis 真实值恢复回 `goods`。

## 订单状态机

`UNPAID`（待支付） → `PAID`（已支付） / `CANCELLED`（已取消）

1 分钟内未支付 → 延时关单 + Redis 库存回补（INCRBY + SREM）。
SchedulerX 兜底悬空死账：ZRANGEBYSCORE 按窗口扫描 → 比对 MySQL → Lua 原子回滚。

## 数据库表

- **sys_user** — 雪花算法 ID，Argon2id 密码哈希，角色 ENUM(admin, merchant, user)（原名 `user`，因 MySQL 保留字改名）
- **goods** — 普通商品，unique(goods_id, merchant_id)，stock 总库存
- **seckill_goods** — 秒杀商品，unique(activity_id, goods_id)，seckill_price, limit_num
- **activity** — 秒杀活动，status ENUM(draft, pending, preheating, running, ended)
- **seckill_order** — 订单，UK(order_token)，UK(user_id, activity_id, seckill_goods_id)
- **user_message** — 点对点私信，msg_type ENUM(approval_result, ban_info, sent_error)
- **message_log** — 本地消息表（事务性 outbox），UNIQUE(biz_type, biz_id)

## 消息中心

- **点对点**（入库 `user_message`）：审核结果、封禁通知、发送失败的告警
- **广播**（WebSocket，不落库）：商家发布秒杀活动通知全体用户
- **通知模板**（预设，按参数填充）：
  - 商家版：您于{时间}创建的"{活动名}"活动已通过审批，将于{开始时间}准时开始秒杀
  - 用户版：{商家名}将于{开始时间}创办"{活动名}"秒杀活动，包含以下商品：{商品名1}、{商品名2}...

## 高并发场景 Redis Key 设计

```text
seckill:stock:{activityId}:{seckillGoodsId}    库存计数（预热后 DECRBY）
seckill:users:{activityId}:{seckillGoodsId}    已抢购用户集合（SADD/SREM）
seckill:pending:{activityId}                   排队凭证 zset（score=时间戳）
```

## 设计原则

- 高知识密度，不做冗余封装
- 最小必要代码，不预支抽象
- 触及架构/安全/库存一致性时主动刹车确认

## TDD 铁律

**RED → GREEN → REFACTOR，严格顺序，不可颠倒。**

```
1. RED:  先写测试（只写测试文件，产品代码不存在）→ mvn test 必须失败
2. GREEN: 写最少产品代码让测试通过 → mvn test 全绿
3. REFACTOR: 清理代码结构，测试保持全绿 → 下一轮
```

**规则：**

- 绝不先写产品代码再补测试。测试文件创建时，被测类/Lua脚本应尚未存在
- 一个测试方法只断一个语义（正常路径 / 边界 / 异常）
- 测试不用 Spring 上下文就尽量不用——纯 Jedis/纯 JUnit 跑得快
- Redis 相关测试直连 docker-compose Redis（`localhost:6379`），`@BeforeEach FLUSHALL` 保证隔离
- Testcontainers 暂不可用（docker-java ↔ Docker Engine 29.4.0 不兼容），待升级后切回

**Lua 脚本 TDD 特例：**
- Lua 脚本从 classpath 加载（`src/main/resources/lua/`）
- 测试通过 `Jedis.eval(script, keys, args)` 直接执行 `src/main/resources/lua/*.lua` 源文件

## 常用命令

### 启动中间件（Docker）

```bash
cd docker && docker compose up -d
```

### 后端

```bash
# 本地启动（依赖 docker compose 已启动）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 打包
mvn clean package -DskipTests

# 运行测试
mvn test
```

### 前端

```bash
cd frontend
npm install
npm run dev          # 开发模式（热更新，proxy → localhost:8080）
npm run build        # 生产构建
```
