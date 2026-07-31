-- ============================================================
-- 秒杀系统 — 演示数据种子（仅在空库时填充）
-- Flyway migration: V6__seed_demo_accounts.sql
-- ============================================================
-- 说明：
--   - 仅在 sys_user 表为空时一次性插入演示账号与商品/活动/消息
--   - 避免覆盖已注册用户，避免在已有数据的数据库中产生外键不一致
--   - dev/MySQL 环境默认不加载 data-local.sql，空库时通过此迁移保证演示可用
-- ============================================================

SET @empty = (SELECT COUNT(*) FROM sys_user) = 0;

-- 演示账号（默认密码：12345678）
INSERT INTO sys_user (user_id, user_name, email, password, role, ban_status)
SELECT 1001, '张买家', 'user@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$ShgUQw8eVB0HK4MOVgCSFQ$OEg6aGYhwHoBL5UG5nLuxAIGdUNC2Iy7FCUrfEskkbU', 'user', 'normal'
WHERE @empty;

INSERT INTO sys_user (user_id, user_name, email, password, role, ban_status)
SELECT 1002, '极客数码', 'merchant@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$ExHjNVmk4i0Y2zhzCDMxUw$z/ezU6G3rPZc6wiudrfdj4limj7vNQ1jaLzQ0SEC8A4', 'merchant', 'normal'
WHERE @empty;

INSERT INTO sys_user (user_id, user_name, email, password, role, ban_status)
SELECT 1003, '管理员', 'admin@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$2orvC0AvUc3IQo3HQwzFzg$D+Y5WFuljoNk6F62/G7eB/gcG/OVxW3yTlh0ayCf6BM', 'admin', 'normal'
WHERE @empty;

-- 演示商品（空库时填充）
INSERT INTO goods (goods_id, goods_name, merchant_id, price, status, stock)
SELECT 5001, 'iPhone 16 Pro 256G', 1002, 8999.00, 1, 500 WHERE @empty;

INSERT INTO goods (goods_id, goods_name, merchant_id, price, status, stock)
SELECT 5002, 'Sony WH-1000XM5 头戴耳机', 1002, 2899.00, 1, 300 WHERE @empty;

INSERT INTO goods (goods_id, goods_name, merchant_id, price, status, stock)
SELECT 5003, '客制化机械键盘', 1002, 699.00, 1, 1000 WHERE @empty;

INSERT INTO goods (goods_id, goods_name, merchant_id, price, status, stock)
SELECT 5004, '4K 显示器 27寸', 1002, 1799.00, 1, 200 WHERE @empty;

-- 演示活动（空库时填充）
INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description)
SELECT 101, '618 数码狂欢夜', 1002, 'running', TIMESTAMPADD(MINUTE, -10, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 50, CURRENT_TIMESTAMP), '年度最低价，全场数码爆款限时秒杀' WHERE @empty;

INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description)
SELECT 102, '午间限时秒杀', 1002, 'preheating', TIMESTAMPADD(MINUTE, 2, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 32, CURRENT_TIMESTAMP), '午休时段精选好物' WHERE @empty;

INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description)
SELECT 103, '开学季特惠', 1002, 'ended', TIMESTAMPADD(MINUTE, -120, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, -60, CURRENT_TIMESTAMP), '开学装备一站式补齐' WHERE @empty;

INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description)
SELECT 104, '双11 预热专场', 1002, 'draft', TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 3, TIMESTAMPADD(HOUR, 2, CURRENT_TIMESTAMP)), '双11 筹备中' WHERE @empty;

INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description)
SELECT 105, '夏日清仓甩卖', 1002, 'pending', TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 1, TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP)), '清凉一夏' WHERE @empty;

-- 演示秒杀商品（空库时填充）
INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9001, 101, 5001, 6999.00, 50, 1 WHERE @empty;

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9002, 101, 5002, 1999.00, 80, 2 WHERE @empty;

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9003, 102, 5003, 399.00, 100, 1 WHERE @empty;

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9004, 103, 5002, 2099.00, 0, 1 WHERE @empty;

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9005, 104, 5004, 1299.00, 150, 1 WHERE @empty;

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num)
SELECT 9006, 105, 5003, 299.00, 60, 2 WHERE @empty;

-- 演示消息（空库时填充）
INSERT INTO user_message (message_id, user_id, msg_type, content, activity_id, is_read)
SELECT 1, 1001, 'welcome', '欢迎来到秒杀系统！立即去活动广场参与抢购吧。', NULL, 0 WHERE @empty;

INSERT INTO user_message (message_id, user_id, msg_type, content, activity_id, is_read)
SELECT 2, 1002, 'welcome', '欢迎商家入驻！可前往「创建活动」发布秒杀。', NULL, 1 WHERE @empty;

INSERT INTO user_message (message_id, user_id, msg_type, content, activity_id, is_read)
SELECT 3, 1003, 'welcome', '管理员后台已就绪，可审核活动与管理用户。', NULL, 1 WHERE @empty;
