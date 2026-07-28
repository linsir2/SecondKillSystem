package com.seckill.module.order.mq;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.model.dto.OrderTimeoutMessage;
import com.seckill.module.order.service.OrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 延时关单 MQ 消费者 —— 消费 1 分钟超时关单通知，取消未支付订单。
 *
 * <p>监听 {@code seckill_order:order_timeout}，委托 {@link OrderService#cancelByTimeout}
 * 完成订单取消 + 乐观锁 + 事件发布。</p>
 *
 * <ul>
 *   <li>{@link BusinessException} / {@link IllegalArgumentException} → ack（永久错误，不重试）</li>
 *   <li>{@link RuntimeException} → 传播异常，RocketMQ 重试</li>
 * </ul>
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "seckill-order-timeout-consumer",
        topic = "seckill_order",
        selectorExpression = "order_timeout"
)
public class OrderTimeoutConsumer implements RocketMQListener<OrderTimeoutMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(OrderTimeoutMessage message) {
        try {
            orderService.cancelByTimeout(message.orderToken());
        } catch (BusinessException e) {
            log.warn("Permanent failure consuming order_timeout, acking: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid message body, acking: {}", e.getMessage());
        }
    }
}
