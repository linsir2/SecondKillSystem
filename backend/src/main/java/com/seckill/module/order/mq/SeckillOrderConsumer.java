package com.seckill.module.order.mq;

import com.seckill.common.exception.BusinessException;
import com.seckill.module.order.service.OrderService;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 MQ 消费者 —— 消费库存预扣成功通知，触发订单创建。
 *
 * <p>监听 {@code seckill_order:stock_deducted}，委托 {@link OrderService#createOrder}
 * 完成事务性订单入库 + 消息表写入。</p>
 *
 * <ul>
 *   <li>{@link BusinessException} / {@link IllegalArgumentException} → ack（永久错误，不重试）</li>
 *   <li>{@link RuntimeException} → 传播异常，RocketMQ 重试</li>
 * </ul>
 */
@Component
@RocketMQMessageListener(
        consumerGroup = "seckill-order-consumer",
        topic = "seckill_order",
        selectorExpression = "stock_deducted"
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillDeductedEvent> {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final OrderService orderService;

    public SeckillOrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(SeckillDeductedEvent event) {
        try {
            orderService.createOrder(event);
        } catch (BusinessException e) {
            log.warn("Permanent failure consuming stock_deducted, acking: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Invalid message body, acking: {}", e.getMessage());
        }
    }
}
