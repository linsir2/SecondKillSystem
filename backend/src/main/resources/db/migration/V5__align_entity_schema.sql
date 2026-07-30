-- ============================================================
-- 修复实体与数据库结构不一致
-- Flyway migration: V5__align_entity_schema.sql
-- ============================================================
-- 问题：
--   1. mybatis-plus 全局配置 logic-delete-field: deleted
--      但所有表都缺少 deleted 列 → SELECT WHERE deleted = 0 报错
--   2. Activity.entity.rejectReason 已在实体和使用代码中存在，
--      但 activity 表未建 reject_reason 列 → UPDATE 写入报错
--   3. payment 表缺少 updated_at 列（其他表都有）
-- ============================================================

-- 1. 所有表增加 deleted 列（默认 0 = 未删除，匹配 logic-not-delete-value: 0）
ALTER TABLE sys_user       ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删';
ALTER TABLE goods          ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE activity       ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE seckill_goods  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE seckill_order  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE user_message   ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE message_log    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE payment        ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';

-- 2. activity 表补 reject_reason 列
ALTER TABLE activity ADD COLUMN reject_reason VARCHAR(500) DEFAULT NULL COMMENT '驳回理由（审核驳回时写入）';

-- 3. payment 表补 updated_at 列
ALTER TABLE payment ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
