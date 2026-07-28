package com.seckill.config.mq;

/**
 * 秒杀相关 MQ 主题/标签常量。
 *
 * <p>spring-cloud-starter-stream-rocketmq / RocketMQ Spring Boot 自动配置
 * 负责创建 RocketMQTemplate Bean，本类仅声明路由常量。</p>
 */
public final class SeckillProducerConfig {

    private SeckillProducerConfig() {}

    /** 秒杀订单相关消息 Topic。 */
    public static final String TOPIC = "seckill_order";

    /** 库存预扣成功 Tag。 */
    public static final String TAG_STOCK_DEDUCTED = "stock_deducted";

    /** 完整的路由表达式 "{topic}:{tag}"。 */
    public static final String DESTINATION_STOCK_DEDUCTED = TOPIC + ":" + TAG_STOCK_DEDUCTED;
}
