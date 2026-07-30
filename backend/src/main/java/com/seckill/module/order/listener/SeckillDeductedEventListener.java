package com.seckill.module.order.listener;

import com.seckill.module.order.service.OrderService;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀预扣成功事件监听器 —— 无 RocketMQ 时通过 Spring 事件创建订单。
 */
@Profile("local")
@Component
public class SeckillDeductedEventListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillDeductedEventListener.class);

    private final OrderService orderService;

    public SeckillDeductedEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @EventListener
    public void handle(SeckillDeductedEvent event) {
        try {
            orderService.createOrder(event);
            log.debug("Order created for orderToken={}", event.orderToken());
        } catch (Exception e) {
            log.error("Failed to create order for orderToken={}", event.orderToken(), e);
            throw e;
        }
    }
}
