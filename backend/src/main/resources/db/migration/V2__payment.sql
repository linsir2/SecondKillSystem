-- ============================================================
-- 秒杀系统 — 支付流水表
-- Flyway migration: V2__payment.sql
-- ============================================================
-- 设计说明：
--   - 支付流水与订单解耦，即使第三方支付（预留）也是独立表
--   - uk_payment_order_no 保证幂等，重复支付请求静默成功
--   - status 为 REFUND 预留退款场景（v2+）
-- ============================================================

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT        NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    order_no   BIGINT        NOT NULL COMMENT '订单号（逻辑关联 seckill_order.order_no）',
    user_id    BIGINT        NOT NULL COMMENT '买家ID（逻辑关联 sys_user.user_id）',
    amount     DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status     ENUM('SUCCESS', 'REFUND') NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS成功 REFUND退款(预留)',
    pay_time   DATETIME      NOT NULL COMMENT '支付时间',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_order_no (order_no) COMMENT '幂等约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';
