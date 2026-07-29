package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderCancelledEvent;
import com.seckill.module.stock.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 手动取消订单 Redis 补偿监听器 —— 监听 {@link OrderCancelledEvent}，恢复 Redis 库存。
 *
 * <p>与 {@link OrderTimedOutEventListener} 逻辑相同，均为 best-effort 补偿，
 * 异常仅 log 不传播。</p>
 */
@Component
public class OrderCancelledEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledEventListener.class);

    private final SeckillStockService seckillStockService;

    public OrderCancelledEventListener(SeckillStockService seckillStockService) {
        this.seckillStockService = seckillStockService;
    }

    @EventListener
    public void handleOrderCancelled(OrderCancelledEvent event) {
        try {
            seckillStockService.compensateByTimeout(
                    event.activityId(),
                    event.seckillGoodsId(),
                    event.userId(),
                    event.buyCount(),
                    event.orderToken());
        } catch (Exception e) {
            log.error("Redis compensate failed for manual cancel orderToken={}, error={}",
                    event.orderToken(), e.getMessage(), e);
        }
    }
}
