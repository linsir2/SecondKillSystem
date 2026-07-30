-- H2 schema for local profile (MySQL compatibility mode)
CREATE TABLE IF NOT EXISTS sys_user (
    user_id    BIGINT       NOT NULL PRIMARY KEY,
    user_name  VARCHAR(25)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       ENUM('admin', 'merchant', 'user') NOT NULL DEFAULT 'user',
    ban_status ENUM('normal', 'banned') NOT NULL DEFAULT 'normal',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS goods (
    goods_id    BIGINT       NOT NULL PRIMARY KEY,
    goods_name  VARCHAR(255) NOT NULL,
    merchant_id BIGINT       NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    stock       INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_goods_merchant ON goods(merchant_id);

CREATE TABLE IF NOT EXISTS activity (
    activity_id   BIGINT       NOT NULL PRIMARY KEY,
    activity_name VARCHAR(255) NOT NULL,
    merchant_id   BIGINT       NOT NULL,
    status        ENUM('draft', 'pending', 'preheating', 'running', 'ended') NOT NULL DEFAULT 'draft',
    start_time    DATETIME     NOT NULL,
    end_time      DATETIME     NOT NULL,
    description   VARCHAR(500) DEFAULT NULL,
    reject_reason VARCHAR(500) DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_activity_merchant ON activity(merchant_id);
CREATE INDEX IF NOT EXISTS idx_activity_status ON activity(status);
CREATE INDEX IF NOT EXISTS idx_activity_start_time ON activity(start_time);

CREATE TABLE IF NOT EXISTS seckill_goods (
    seckill_goods_id BIGINT       NOT NULL PRIMARY KEY,
    activity_id      BIGINT       NOT NULL,
    goods_id         BIGINT       NOT NULL,
    seckill_price    DECIMAL(10,2) NOT NULL,
    stock            INT          NOT NULL,
    limit_num        INT          NOT NULL DEFAULT 1,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_goods (activity_id, goods_id)
);
CREATE INDEX IF NOT EXISTS idx_sg_activity ON seckill_goods(activity_id);

CREATE TABLE IF NOT EXISTS seckill_order (
    order_no         BIGINT       NOT NULL PRIMARY KEY,
    order_token      VARCHAR(64)  NOT NULL UNIQUE,
    user_id          BIGINT       NOT NULL,
    activity_id      BIGINT       NOT NULL,
    seckill_goods_id BIGINT       NOT NULL,
    buy_count        INT          NOT NULL DEFAULT 1,
    total_amount     DECIMAL(10,2) NOT NULL,
    status           ENUM('UNPAID', 'PAID', 'CANCELLED') NOT NULL DEFAULT 'UNPAID',
    pay_time         DATETIME     DEFAULT NULL,
    cancel_time      DATETIME     DEFAULT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_activity_goods (user_id, activity_id, seckill_goods_id)
);
CREATE INDEX IF NOT EXISTS idx_order_activity ON seckill_order(activity_id);
CREATE INDEX IF NOT EXISTS idx_order_user ON seckill_order(user_id);

CREATE TABLE IF NOT EXISTS user_message (
    message_id  BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    msg_type    ENUM('approval_result', 'ban_info', 'sent_error') NOT NULL,
    content     TEXT         NOT NULL,
    activity_id BIGINT       DEFAULT NULL,
    is_read     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_user ON user_message(user_id);
CREATE INDEX IF NOT EXISTS idx_msg_unread ON user_message(user_id, is_read);

CREATE TABLE IF NOT EXISTS message_log (
    msg_id      BIGINT       NOT NULL PRIMARY KEY,
    biz_type    VARCHAR(32)  NOT NULL,
    biz_id      VARCHAR(128) NOT NULL,
    topic       VARCHAR(128) NOT NULL,
    tag         VARCHAR(128) DEFAULT NULL,
    body        TEXT         DEFAULT NULL,
    status      ENUM('INIT', 'SENT', 'FAIL') NOT NULL DEFAULT 'INIT',
    retry_count INT          NOT NULL DEFAULT 0,
    send_time   DATETIME     DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz (biz_type, biz_id)
);
CREATE INDEX IF NOT EXISTS idx_ml_status_retry ON message_log(status, retry_count);

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT       NOT NULL PRIMARY KEY,
    order_no   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    amount     DECIMAL(10,2) NOT NULL,
    status     ENUM('SUCCESS', 'FAILED') NOT NULL DEFAULT 'SUCCESS',
    pay_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_order (order_no)
);
CREATE INDEX IF NOT EXISTS idx_payment_user ON payment(user_id);
