package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderTimedOutEvent;
import com.seckill.module.stock.service.SeckillStockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 超时关单 Redis 补偿监听器 —— 监听 {@link OrderTimedOutEvent}，恢复 Redis 库存。
 *
 * <p>使用 {@link TransactionPhase#AFTER_COMMIT}，确保 DB 事务已提交再执行 Redis 补偿，
 * 避免 DB 回滚导致 Redis 与 MySQL 不一致风险。</p>
 */
@Component
public class OrderTimedOutEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimedOutEventListener.class);

    private final SeckillStockService seckillStockService;

    public OrderTimedOutEventListener(SeckillStockService seckillStockService) {
        this.seckillStockService = seckillStockService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
