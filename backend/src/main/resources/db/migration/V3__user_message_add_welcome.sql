-- ============================================================
-- 秒杀系统 — 消息类型扩展：增加 welcome 枚举值
-- Flyway migration: V3__user_message_add_welcome.sql
-- ============================================================
-- 设计说明：
--   - user_message.msg_type 新增 'welcome' 值，用于新用户注册欢迎通知
--   - MySQL ENUM 不支持原子增删，MODIFY 整列定义
--   - 生产环境大表需谨慎（此处为练习项目，直接 MODIFY）
-- ============================================================

ALTER TABLE user_message
    MODIFY COLUMN msg_type
    ENUM('approval_result', 'ban_info', 'sent_error', 'welcome')
    NOT NULL COMMENT '消息类别';
