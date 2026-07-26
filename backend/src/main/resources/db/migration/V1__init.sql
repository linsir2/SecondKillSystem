-- ============================================================
-- 秒杀系统 — 基础表结构
-- Flyway migration: V1__init.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS user (
    user_id    BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    user_name  VARCHAR(25)  NOT NULL UNIQUE COMMENT '用户名',
    email      VARCHAR(255)          UNIQUE COMMENT '邮箱',
    password   VARCHAR(255) NOT NULL COMMENT 'Argon2id 密码哈希',
    role       ENUM('admin', 'merchant', 'user') NOT NULL DEFAULT 'user' COMMENT '角色',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人信息表';

CREATE TABLE IF NOT EXISTS goods (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '主键ID',
    goods_id    BIGINT       NOT NULL COMMENT '雪花算法商品ID',
    goods_name  VARCHAR(255) NOT NULL COMMENT '商品名',
    merchant_id BIGINT       NOT NULL COMMENT '商家ID(外键user.user_id)',
    price       DECIMAL(10,2) NOT NULL COMMENT '日常售价',
    status      TINYINT(4)   NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    stock       INT(11)      NOT NULL DEFAULT 0 COMMENT '总库存',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_goods_merchant (goods_id, merchant_id),
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通商品表';

CREATE TABLE IF NOT EXISTS activity (
    activity_id   BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    activity_name VARCHAR(255) NOT NULL COMMENT '活动名称',
    merchant_id   BIGINT       NOT NULL COMMENT '创建商家ID',
    status        ENUM('draft', 'pending', 'preheating', 'running', 'ended') NOT NULL DEFAULT 'draft' COMMENT '活动状态',
    start_time    DATETIME     NOT NULL COMMENT '开始时间',
    end_time      DATETIME     NOT NULL COMMENT '结束时间',
    description   VARCHAR(500) DEFAULT NULL COMMENT '活动规则说明',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_status (status),
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

CREATE TABLE IF NOT EXISTS seckill_goods (
    seckill_goods_id BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    activity_id      BIGINT       NOT NULL COMMENT '活动ID',
    goods_id         BIGINT       NOT NULL COMMENT '商品ID',
    seckill_price    DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    stock            INT(11)      NOT NULL COMMENT '秒杀库存(须 ≤ goods.stock)',
    limit_num        INT(11)      NOT NULL DEFAULT 1 COMMENT '单用户限购数量',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_goods (activity_id, goods_id),
    INDEX idx_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品表';

CREATE TABLE IF NOT EXISTS seckill_order (
    order_no        BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法订单号',
    order_token     VARCHAR(64)  NOT NULL COMMENT '排队凭证/幂等键',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    activity_id     BIGINT       NOT NULL COMMENT '活动ID',
    seckill_goods_id BIGINT      NOT NULL COMMENT '秒杀商品ID',
    buy_count       INT(11)      NOT NULL DEFAULT 1 COMMENT '购买数量',
    total_amount    DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status          ENUM('UNPAID', 'PAID', 'CANCELLED') NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态',
    pay_time        DATETIME     DEFAULT NULL COMMENT '支付成功时间',
    cancel_time     DATETIME     DEFAULT NULL COMMENT '取消时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_token (order_token),
    UNIQUE KEY uk_user_activity_goods (user_id, activity_id, seckill_goods_id),
    INDEX idx_activity (activity_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单表';

CREATE TABLE IF NOT EXISTS user_message (
    message_id  BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    user_id     BIGINT       NOT NULL COMMENT '接收用户ID',
    msg_type    ENUM('approval_result', 'ban_info', 'sent_error') NOT NULL COMMENT '消息类别',
    content     TEXT         NOT NULL COMMENT '消息内容(按模板填充)',
    activity_id BIGINT       DEFAULT NULL COMMENT '关联活动ID',
    is_read     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息中心表';

CREATE TABLE IF NOT EXISTS message_log (
    msg_id      BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花算法ID',
    biz_type    VARCHAR(32)  NOT NULL COMMENT '业务类型(seckill_order / order_timeout)',
    biz_id      VARCHAR(128) NOT NULL COMMENT '业务唯一键(order_token)',
    topic       VARCHAR(128) NOT NULL COMMENT 'MQ Topic',
    tag         VARCHAR(128) DEFAULT NULL COMMENT 'MQ Tag',
    body        TEXT         DEFAULT NULL COMMENT '消息体JSON',
    status      ENUM('INIT', 'SENT', 'FAIL') NOT NULL DEFAULT 'INIT' COMMENT '发送状态',
    retry_count INT(11)      NOT NULL DEFAULT 0 COMMENT '重试次数',
    send_time   DATETIME     DEFAULT NULL COMMENT '实际发送时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz (biz_type, biz_id),
    INDEX idx_status_retry (status, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表(事务性outbox)';
