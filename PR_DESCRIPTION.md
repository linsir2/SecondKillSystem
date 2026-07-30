# [codex] 完整前端 UI、H2/Redis 本地部署适配及精度修复

## 变更概要

本次 PR 为秒杀系统补齐了完整的前端界面，并将后端改造为可在仅具备 H2 + Redis 的环境中一键启动与部署，同时修复了前后端交互中的大整数精度问题。

## 主要改动

### 前端（`frontend/`）

- **全角色页面与路由流转**
  - 登录 / 注册
  - 活动广场（用户） / 活动详情 / 秒杀下单 / 订单支付
  - 商家活动列表 / 创建活动 / 商品管理
  - 消息中心 / 个人中心
  - 管理员审核后台
- **新增组件**：`Layout`、`icons`、`ui` 等基础组件，统一紫色品牌 + 玫瑰红 urgency 风格
- **状态与路由**：`authStore` 增加 `isLoading`、`AuthGuard` 增加 loading spinner；路由按角色懒加载与鉴权
- **Mock 支持**：`frontend/src/mock/server.ts` 可在无后端时模拟完整秒杀链路（注册 → 登录 → 秒杀 → 异步建单 → 支付）
- **类型修复**：`userId`、`orderNo` 全部使用 `string`，避免 JS 大整数精度丢失
- **请求与测试**：修复 `request` 拦截器、token 处理、测试用例兼容

### 后端（`backend/`）

- **新增 `local` profile**：`application-local.yml` + `schema-local.sql` + `data-local.sql`
  - H2 内存数据库替代 MySQL
  - Redis 替代 RocketMQ 作为库存扣减与事件驱动
  - `SeckillPreheatRunner` 预热活动库存到 Redis
  - `SeckillDeductedEventListener` 监听库存已扣减事件并异步建单
- **默认激活 local profile**：`application.yml` 中 `spring.profiles.active=local`，方便沙箱/本地一键启动
- **精度修复**：`LoginVO`、`UserInfoVO`、`OrderStatusVO` 中的 `userId` / `orderNo` 添加 `@JsonFormat(shape = STRING)`，避免 JSON 序列化为 Number 后前端失真
- **密码哈希修复**：`data-local.sql` 中 admin / merchant / user 默认账号使用 Argon2 正确编码的密文，默认密码 `123456` 可直接登录
- **依赖与测试**：`pom.xml` 增加 H2 驱动等本地依赖；修复相关单元测试

## 验证结果

已在沙箱完成端到端验证，覆盖以下链路：

1. 新用户注册
2. 用户 / 商家 / 管理员 登录
3. 活动列表与详情浏览
4. 秒杀下单（库存扣减）
5. 订单创建与支付
6. 消息通知
7. 商家创建活动、商品管理
8. 管理员审核活动

## 截图验证

截图已保存在 `screenshots/` 目录：

| 序号 | 页面 |
|------|------|
| 01-login.png | 登录页 |
| 02-register.png | 注册页 |
| 03-activity-list.png | 用户活动广场 |
| 04-activity-detail.png | 活动详情页 |
| 05-order-unpaid.png | 待支付订单 |
| 06-order-paid.png | 已支付订单 |
| 07-messages.png | 消息中心 |
| 08-profile.png | 个人中心 |
| 09-merchant-activity-list.png | 商家活动管理 |
| 10-create-activity.png | 商家创建活动 |
| 11-goods-manage.png | 商家商品管理 |

## 部署方式（local profile）

```bash
# 1. 启动 Redis（默认 6379）
redis-server

# 2. 启动后端
cd backend
./mvnw -DskipTests -P local spring-boot:run
# 或直接使用 jar
java -jar target/seckill-system-1.0.0-SNAPSHOT.jar --spring.profiles.active=local

# 3. 启动前端开发服务器
cd frontend
npm install
npm run dev

# 4. 生产构建
cd frontend
npm run build
# 构建产物在 frontend/dist/
```

## 默认账号（local 环境）

| 角色 | 邮箱 | 密码 |
|------|------|------|
| 管理员 | admin@seckill.com | 12345678 |
| 商家 | merchant@seckill.com | 12345678 |
| 普通用户 | user@seckill.com | 12345678 |

## 注意事项

- `application-local.yml`、`schema-local.sql`、`data-local.sql` 仅用于本地/H2 环境，生产环境请切回 `prod` / `dev` profile 并配置真实 MySQL、RocketMQ。
- 前端 `vite.config.ts` 已配置代理到 `http://localhost:8080`，开发时后端需运行在 8080 端口。
