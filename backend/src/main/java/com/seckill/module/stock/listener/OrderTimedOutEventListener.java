package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderTimedOutEvent;
import com.seckill.module.stock.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 超时关单 Redis 补偿监听器 —— 监听 {@link OrderTimedOutEvent}，恢复 Redis 库存。
 *
 * <p>Redis 补偿是 best-effort 操作，异常仅 log 不传播，
 * 避免 MQ 消费者因此重试导致死循环。</p>
 */
@Component
public class OrderTimedOutEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimedOutEventListener.class);

    private final SeckillStockService seckillStockService;

    public OrderTimedOutEventListener(SeckillStockService seckillStockService) {
        this.seckillStockService = seckillStockService;
    }

    @EventListener
    public void handleOrderTimedOut(OrderTimedOutEvent event) {
        try {
            seckillStockService.compensateByTimeout(
                    event.activityId(),
                    event.seckillGoodsId(),
                    event.userId(),
                    event.buyCount(),
                    event.orderToken());
        } catch (Exception e) {
            log.error("Redis compensate failed for orderToken={}, error={}",
                    event.orderToken(), e.getMessage(), e);
        }
    }
}
