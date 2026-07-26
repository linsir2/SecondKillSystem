-- ============================================================
-- 秒杀系统 — 基础表结构
-- Flyway migration: V1__init.sql
-- ============================================================
-- 设计说明：
--   - 外键约束在应用层保障，不使用 DB FOREIGN KEY（秒杀场景高并发写入，
--     FK 带来额外锁开销，且 DDL 不灵活）
--   - 所有主键均使用雪花算法 ID（BIGINT），由应用层生成
--   - 不使用 INT(N) 显示宽度语法（MySQL 8.0.17+ 已废弃）
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    user_id    BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    user_name  VARCHAR(25)  NOT NULL UNIQUE COMMENT '用户名',
    email      VARCHAR(255) NOT NULL UNIQUE COMMENT '邮箱（必填，用于登录/找回密码）',
    password   VARCHAR(255) NOT NULL COMMENT 'Argon2id 密码哈希',
    role       ENUM('admin', 'merchant', 'user') NOT NULL DEFAULT 'user' COMMENT '角色',
    ban_status ENUM('normal', 'banned') NOT NULL DEFAULT 'normal' COMMENT '封禁状态',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（原名 user，因 MySQL 保留字改名）';

CREATE TABLE IF NOT EXISTS goods (
    goods_id    BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法商品ID（同时做主键）',
    goods_name  VARCHAR(255) NOT NULL COMMENT '商品名',
    merchant_id BIGINT       NOT NULL COMMENT '商家ID（逻辑关联 sys_user.user_id，应用层校验 role=merchant）',
    price       DECIMAL(10,2) NOT NULL COMMENT '日常售价',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    stock       INT          NOT NULL DEFAULT 0 COMMENT '总库存',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通商品表';

CREATE TABLE IF NOT EXISTS activity (
    activity_id   BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    activity_name VARCHAR(255) NOT NULL COMMENT '活动名称',
    merchant_id   BIGINT       NOT NULL COMMENT '创建商家ID（逻辑关联 sys_user.user_id）',
    status        ENUM('draft', 'pending', 'preheating', 'running', 'ended') NOT NULL DEFAULT 'draft' COMMENT '活动状态',
    start_time    DATETIME     NOT NULL COMMENT '开始时间',
    end_time      DATETIME     NOT NULL COMMENT '结束时间',
    description   VARCHAR(500) DEFAULT NULL COMMENT '活动规则说明',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动表';

CREATE TABLE IF NOT EXISTS seckill_goods (
    seckill_goods_id BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    activity_id      BIGINT       NOT NULL COMMENT '活动ID（逻辑关联 activity.activity_id）',
    goods_id         BIGINT       NOT NULL COMMENT '商品ID（逻辑关联 goods.goods_id）',
    seckill_price    DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock            INT          NOT NULL COMMENT '秒杀库存（须 ≤ goods.stock，应用层校验）',
    limit_num        INT          NOT NULL DEFAULT 1 COMMENT '单用户限购数量',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_goods (activity_id, goods_id),
    INDEX idx_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

CREATE TABLE IF NOT EXISTS seckill_order (
    order_no        BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法订单号',
    order_token     VARCHAR(64)  NOT NULL COMMENT '排队凭证/幂等键',
    user_id         BIGINT       NOT NULL COMMENT '用户ID（逻辑关联 sys_user.user_id）',
    activity_id     BIGINT       NOT NULL COMMENT '活动ID（逻辑关联 activity.activity_id）',
    seckill_goods_id BIGINT      NOT NULL COMMENT '秒杀商品ID（逻辑关联 seckill_goods.seckill_goods_id）',
    buy_count       INT          NOT NULL DEFAULT 1 COMMENT '购买数量',
    total_amount    DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status          ENUM('UNPAID', 'PAID', 'CANCELLED') NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态',
    pay_time        DATETIME     DEFAULT NULL COMMENT '支付成功时间',
    cancel_time     DATETIME     DEFAULT NULL COMMENT '取消时间（超时/主动取消）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_token (order_token),
    UNIQUE KEY uk_user_activity_goods (user_id, activity_id, seckill_goods_id),
    INDEX idx_activity (activity_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

CREATE TABLE IF NOT EXISTS user_message (
    message_id  BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID（逻辑关联 sys_user.user_id）',
    msg_type    ENUM('approval_result', 'ban_info', 'sent_error') NOT NULL COMMENT '消息类别',
    content     TEXT         NOT NULL COMMENT '消息内容（按模板填充）',
    activity_id BIGINT       DEFAULT NULL COMMENT '关联活动ID（逻辑关联 activity.activity_id）',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息中心表';

CREATE TABLE IF NOT EXISTS message_log (
    msg_id      BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    biz_type    VARCHAR(32)  NOT NULL COMMENT '业务类型（seckill_order / order_timeout）',
    biz_id      VARCHAR(128) NOT NULL COMMENT '业务唯一键（order_token）',
    topic       VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    tag         VARCHAR(128) DEFAULT NULL COMMENT 'MQ Tag',
    body        TEXT         DEFAULT NULL COMMENT '消息体JSON',
    status      ENUM('INIT', 'SENT', 'FAIL') NOT NULL DEFAULT 'INIT' COMMENT '发送状态',
    retry_count INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    send_time   DATETIME     DEFAULT NULL COMMENT '实际发送时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz (biz_type, biz_id),
    INDEX idx_status_retry (status, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（事务性 outbox，用于可靠投递延时关单消息）';
