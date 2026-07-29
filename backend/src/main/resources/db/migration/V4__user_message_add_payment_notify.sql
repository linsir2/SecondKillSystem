-- ============================================================
-- 秒杀系统 — 消息类型扩展：增加 payment_notify 枚举值
-- Flyway migration: V4__user_message_add_payment_notify.sql
-- ============================================================
-- 设计说明：
--   - user_message.msg_type 新增 'payment_notify_merchant' 和
--     'payment_notify_user'，用于支付成功后的通知
--   - MySQL ENUM 不支持原子增删，MODIFY 整列定义
-- ============================================================

ALTER TABLE user_message
    MODIFY COLUMN msg_type
    ENUM('approval_result', 'ban_info', 'sent_error', 'welcome',
         'payment_notify_merchant', 'payment_notify_user')
    NOT NULL COMMENT '消息类别';
