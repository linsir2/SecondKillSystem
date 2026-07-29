package com.seckill.module.stock.listener;

import com.seckill.module.order.model.dto.OrderPaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付成功 Redis 清理监听器 —— 监听 {@link OrderPaidEvent}，从 pending ZSET 移除 orderToken。
 *
 * <p>使用 {@link TransactionPhase#AFTER_COMMIT}，确保 DB 订单已支付提交后再清理 Redis
 * （与 {@link OrderCancelledEventListener} / {@link OrderTimedOutEventListener} 同一范式）。</p>
 */
@Component
public class OrderPaidEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEventListener.class);

    private final StringRedisTemplate redisTemplate;

    public OrderPaidEventListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaid(OrderPaidEvent event) {
        if (event == null) {
            log.warn("Received null OrderPaidEvent, skipping");
            return;
        }

        String pendingKey = "seckill:pending:" + event.activityId();
        try {
            redisTemplate.opsForZSet().remove(pendingKey, event.orderToken());
            log.debug("Cleaned pending ZSET for orderToken={} activityId={}", event.orderToken(), event.activityId());
        } catch (Exception e) {
            log.error("Failed to clean pending ZSET for orderToken={}, error={}",
                    event.orderToken(), e.getMessage(), e);
        }
    }
}
