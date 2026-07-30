-- Local demo data
INSERT INTO sys_user (user_id, user_name, email, password, role, ban_status) VALUES
(1001, '张买家', 'user@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$fHuqGw5uszTukpx+DHDiPQ$7actzabrsD6XghdZ5vZP5A8pNqIfsT82oEVpE6YHdTw', 'user', 'normal'),
(1002, '极客数码', 'merchant@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$jqv67h8lgkWV7dv64se1Rg$qpK87uhFn3i1+dF53ukObaGJIbujiL2PPC6PKRNuTEE', 'merchant', 'normal'),
(1003, '管理员', 'admin@seckill.com', '$argon2id$v=19$m=65536,t=3,p=1$aVDLQgOS0GFMSUYOTWn5YQ$VUes+RCzVV9zDx5PLyM/KwQmU2JguIJOY6Sm5bom6mY', 'admin', 'normal');

INSERT INTO goods (goods_id, goods_name, merchant_id, price, status, stock) VALUES
(5001, 'iPhone 16 Pro 256G', 1002, 8999.00, 1, 500),
(5002, 'Sony WH-1000XM5 头戴耳机', 1002, 2899.00, 1, 300),
(5003, '客制化机械键盘', 1002, 699.00, 1, 1000),
(5004, '4K 显示器 27寸', 1002, 1799.00, 1, 200);

INSERT INTO activity (activity_id, activity_name, merchant_id, status, start_time, end_time, description) VALUES
(101, '618 数码狂欢夜', 1002, 'running', TIMESTAMPADD(MINUTE, -10, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 50, CURRENT_TIMESTAMP), '年度最低价，全场数码爆款限时秒杀'),
(102, '午间限时秒杀', 1002, 'preheating', TIMESTAMPADD(MINUTE, 2, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, 32, CURRENT_TIMESTAMP), '午休时段精选好物'),
(103, '开学季特惠', 1002, 'ended', TIMESTAMPADD(MINUTE, -120, CURRENT_TIMESTAMP), TIMESTAMPADD(MINUTE, -60, CURRENT_TIMESTAMP), '开学装备一站式补齐'),
(104, '双11 预热专场', 1002, 'draft', TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 3, TIMESTAMPADD(HOUR, 2, CURRENT_TIMESTAMP)), '双11 筹备中'),
(105, '夏日清仓甩卖', 1002, 'pending', TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 1, TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP)), '清凉一夏');

INSERT INTO seckill_goods (seckill_goods_id, activity_id, goods_id, seckill_price, stock, limit_num) VALUES
(9001, 101, 5001, 6999.00, 50, 1),
(9002, 101, 5002, 1999.00, 80, 2),
(9003, 102, 5003, 399.00, 100, 1),
(9004, 103, 5002, 2099.00, 0, 1),
(9005, 104, 5004, 1299.00, 150, 1),
(9006, 105, 5003, 299.00, 60, 2);

INSERT INTO user_message (message_id, user_id, msg_type, content, activity_id, is_read) VALUES
(1, 1001, 'approval_result', '欢迎来到秒杀系统！立即去活动广场参与抢购吧。', NULL, 0),
(2, 1002, 'approval_result', '欢迎商家入驻！可前往「创建活动」发布秒杀。', NULL, 1),
(3, 1003, 'approval_result', '管理员后台已就绪，可审核活动与管理用户。', NULL, 1);
