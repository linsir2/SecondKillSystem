package com.seckill.module.stock.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.config.mq.SeckillProducerConfig;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀库存扣减领域服务。
 *
 * <p>封装 Redis+Lua 原子预扣，成功后生成 orderToken 并 ZADD 到 pending 队列，
 * 同时同步发送 {@link SeckillDeductedEvent} 到 MQ 触发订单创建。
 * MQ 发送失败时通过补偿 Lua 原子恢复 Redis 状态。</p>
 *
 * <p>对应 DDD 抢购上下文（Seckill）的核心域名服务。</p>
 */
@Service
public class SeckillStockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> compensateScript;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public SeckillStockService(StringRedisTemplate redisTemplate,
                               ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;

        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setLocation(new ClassPathResource("lua/seckill_deduct.lua"));
        this.deductScript.setResultType(Long.class);

        this.compensateScript = new DefaultRedisScript<>();
        this.compensateScript.setLocation(new ClassPathResource("lua/seckill_compensate.lua"));
        this.compensateScript.setResultType(Long.class);
    }

    /**
     * 执行秒杀库存预扣。
     *
     * @param activityId     活动 ID（不可 null）
     * @param seckillGoodsId 秒杀商品 ID（不可 null）
     * @param userId         用户 ID（不可 null）
     * @param buyCount       购买数量（必须 &gt; 0）
     * @return 扣减结果
     */
    public SeckillDeductResult deduct(Long activityId, Long seckillGoodsId, Long userId, int buyCount) {
        if (activityId == null) throw new IllegalArgumentException("activityId must not be null");
        if (seckillGoodsId == null) throw new IllegalArgumentException("seckillGoodsId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        if (buyCount <= 0) throw new IllegalArgumentException("buyCount must be > 0, got: " + buyCount);

        String stockKey = redisKey("stock", activityId, seckillGoodsId);
        String usersKey = redisKey("users", activityId, seckillGoodsId);
        String limitKey = redisKey("limit", activityId, seckillGoodsId);
        String pendingKey = redisKey("pending", activityId, null);

        // ---- 1. 执行 Lua 原子预扣 ----
        Long result = redisTemplate.execute(
                deductScript,
                List.of(stockKey, usersKey, limitKey),
                String.valueOf(userId),
                String.valueOf(buyCount));

        if (result == null || result == -2) {
            return SeckillDeductResult.soldOut();
        }
        if (result == -1) {
            return SeckillDeductResult.duplicate();
        }
        if (result == -3) {
            return SeckillDeductResult.overLimit();
        }

        // ---- 2. 成功：生成 orderToken + ZADD 到 pending 队列 ----
        String orderToken = UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(pendingKey, orderToken, System.currentTimeMillis());

        // ---- 2b. 写入 meta（SchedulerX 兜底补偿用） ----
        redisTemplate.opsForValue().set(
                "seckill:pending:meta:" + orderToken,
                userId + ":" + seckillGoodsId + ":" + buyCount,
                Duration.ofMinutes(15));

        // ---- 3. 通知订单服务创建订单 ----
        SeckillDeductedEvent event = new SeckillDeductedEvent(orderToken, userId, activityId, seckillGoodsId, buyCount);
        if (rocketMQTemplate != null) {
            try {
                rocketMQTemplate.syncSend(SeckillProducerConfig.DESTINATION_STOCK_DEDUCTED, event);
            } catch (Exception e) {
                // 发送失败（超时/异常），立即补偿
                compensate(activityId, seckillGoodsId, userId, buyCount, orderToken,
                        stockKey, usersKey, pendingKey);
                throw new BusinessException("系统繁忙，请重试");
            }
        } else {
            // Local / 无 MQ 环境：通过 Spring 事件同步触发订单创建（仍保持业务解耦）
            eventPublisher.publishEvent(event);
        }

        return SeckillDeductResult.success(orderToken, buyCount);
    }

    /**
     * 执行 Redis 补偿：恢复库存 + 移除用户 + 清除排队凭证。
     */
    private void compensate(Long activityId, Long seckillGoodsId, Long userId,
                            int buyCount, String orderToken,
                            String stockKey, String usersKey, String pendingKey) {
        redisTemplate.execute(
                compensateScript,
                List.of(stockKey, usersKey, pendingKey),
                String.valueOf(userId),
                String.valueOf(buyCount),
                orderToken);

        // 清理 meta（补偿完成后无需保留）
        redisTemplate.delete("seckill:pending:meta:" + orderToken);
    }

    /**
     * 超时关单回补 Redis 库存（公开方法，供事件监听器调用）。
     *
     * <p>执行 Lua 原子操作：INCRBY 恢复库存 + SREM 移除用户 + ZREM 移除排队凭证。</p>
     *
     * @param activityId     活动 ID（不可 null）
     * @param seckillGoodsId 秒杀商品 ID（不可 null）
     * @param userId         用户 ID
     * @param buyCount       购买数量（必须 > 0）
     * @param orderToken     排队凭证（不可 null）
     */
    public void compensateByTimeout(Long activityId, Long seckillGoodsId, Long userId,
                                    int buyCount, String orderToken) {
        if (activityId == null) throw new IllegalArgumentException("activityId must not be null");
        if (seckillGoodsId == null) throw new IllegalArgumentException("seckillGoodsId must not be null");
        if (buyCount <= 0) throw new IllegalArgumentException("buyCount must be > 0, got: " + buyCount);
        if (orderToken == null) throw new IllegalArgumentException("orderToken must not be null");

        String stockKey = redisKey("stock", activityId, seckillGoodsId);
        String usersKey = redisKey("users", activityId, seckillGoodsId);
        String pendingKey = redisKey("pending", activityId, null);

        redisTemplate.execute(
                compensateScript,
                List.of(stockKey, usersKey, pendingKey),
                String.valueOf(userId),
                String.valueOf(buyCount),
                orderToken);

        // 清理 meta
        redisTemplate.delete("seckill:pending:meta:" + orderToken);
    }

    private static String redisKey(String prefix, Long activityId, Long seckillGoodsId) {
        if (seckillGoodsId == null) {
            return "seckill:" + prefix + ":" + activityId;
        }
        return "seckill:" + prefix + ":" + activityId + ":" + seckillGoodsId;
    }
}
