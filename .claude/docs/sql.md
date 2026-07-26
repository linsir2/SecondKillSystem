## 数据库表

### 个人信息表 —— sys_user

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| user_id | 唯一, BIGINT | 雪花算法 |
| user_name | 唯一，varchar(25) | 不可重复 |
| email | 唯一，Unique, varchar(255) |  |
| password | varchar(255) | Argon2id 密码哈希 |
| role | ENUM[’admin’, ‘merchant’, ‘user’] | 角色 |
| ban_status | ENUM('normal', 'banned') | 封禁状态，默认 normal。封禁后无法参与抢购 |
| created_at | datetime |  |
| updated_at | datetime |  |

### 普通商品表 —— goods

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| id | 主键, BIGINT | 雪花算法 |
| goods_id | 全局唯一, 雪花算法，BIGINT | 雪花算法，unique(goods_id, merchant_id) |
| goods_name | varchar(255) | 商品名 |
| merchant_id | 外键user_id from user, BIGINT | 必须是商人merchant, unique(goods_id, merchant_id) |
| price | decimal(10, 2) | 日常售价 |
| status | 0/1, tinyint(4) | 0下架，1上架 |
| stock | int(11) | 总库存 |
| created_at | datetime, default current_timestamp |  |
| updated_at | datetime, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |  |

### 秒杀商品表 —— seckill_goods（未上架无法当秒杀商品）

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| seckill_goods_id | 主键ID, BIGINT | 雪花算法 |
| activity_id | 外键activity_id from activity, BIGINT | 活动ID, unique(activity_id, goods_id) |
| goods_id | 关联goods表，外键goods_id, BIGINT | unique(activity_id, goods_id |
| seckill_price | decimal(10, 2) | 秒杀价 |
| stock | int(11) | 秒杀商品库存,seckill_goods.stock < goods_stock |
| limit_num | int(11) | 限购数量 |
| created_at | datetime, DEFAULT CURRENT_TIMESTAMP |  |
| updated_at | datetime, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |  |

### 活动表 —— activity

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| activity_id | 活动ID，主键, BIGINT | 雪花算法 |
| activity_name |  | 活动名字 |
| merchant_id | BIGINT，外联user表user_id | 创建商家ID |
| status | ENUM[‘draft’、‘pending’、‘preheating’、‘running’、‘ended’] | 活动状态:草稿/待审核/预热中/进行中/已结束 |
| start_time | datetime | 开始时间 |
| end_time | datetime | 结束时间 |
| description | varchar(500) | 活动规则 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### 订单表 —— seckill_order

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| order_no | 雪花算法，主键，BIGINT | 订单号 |
| order_token | varchar(64), UNIQUE | 排队凭证/幂等键，对应流程中的 orderToken |
| user_id | 外键关联user表, role必须为user | 用户id |
| activity_id | 关联activity表； 再建一个普通索引，用于查询一个活动下的所有订单 | 活动ID |
| seckill_goods_id | 关联activity表 | 秒杀商品ID |
| buy_count |  | 购买商品数量 |
| total_amount |  | 订单总金额 |
| status | ENUM[‘UNPAID’、‘PAID’、‘CANCELLED’] | 订单状态:待支付/已支付/已取消 |
| pay_time | datetime | 支付成功时间 |
| cancel_time |  | 取消时间，包括超时取消/主动取消 |
| created_at | datetime，CURRENT_TIMESTAMP | 创建时间 |
| updated_at | datetime, CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

索引：UNIQUE(order_token), UNIQUE(user_id, activity_id, seckill_goods_id)

### 消息中心表 —— user_message

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| message_id | ID，主键 | 雪花算法 |
| user_id | 外联user表user_id | 接收到消息的用户ID |
| msg_type | ENUM[‘approval_result’、‘ban_info’、‘sent_error’] | 消息类别，顺便充当消息标题 |
| content | text | 消息详细内容(预制好模板，按参数填充) |
| activity_id | 外键关联activity表，activity_id |  |
| is_read | 0/1 | 是否已读 |
| created_at | datetime | 创建时间 |

### 本地消息表 —— message_log

> 用途：配合本地消息表方案，记录需要发送的 MQ 消息，由后台线程扫描并投递。
>
> 在本系统中，它主要承担：订单创建成功后，可靠地投递“1 分钟超时关单”延时消息。

| 字段名 | 约束 | 说明 |
| --- | --- | --- |
| msg_id | 主键，BIGINT | 雪花算法 |
| biz_type | varchar(32) | 业务类型，如 seckill_order / order_timeout |
| biz_id | varchar(128) | 业务唯一键，如 order_token |
| topic | varchar(128) | MQ Topic |
| tag | varchar(128) | MQ Tag |
| body | text | 消息体 JSON |
| status | ENUM('INIT', 'SENT', 'FAIL') | 发送状态 |
| retry_count | int(11) | 重试次数 |
| send_time | datetime | 实际发送时间 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

索引：idx_status_retry_count, UNIQUE(biz_type, biz_id)