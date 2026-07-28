package com.seckill.module.stock.service;

import com.seckill.common.exception.BusinessException;
import com.seckill.config.mq.SeckillProducerConfig;
import com.seckill.module.stock.model.dto.SeckillDeductResult;
import com.seckill.module.stock.model.dto.SeckillDeductedEvent;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 秒杀库存扣减领域服务。
 *
 * <p>封装 Redis+Lua 原子预扣，成功后生成 orderToken 并 ZADD 到 pending 队列，
 * 同时异步发送 {@link SeckillDeductedEvent} 到 MQ 触发订单创建。
 * MQ 发送失败时通过补偿 Lua 原子恢复 Redis 状态。</p>
 *
 * <p>对应 DDD 抢购上下文（Seckill）的核心域名服务。</p>
 */
@Service
public class SeckillStockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;
    private final DefaultRedisScript<Long> compensateScript;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public SeckillStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

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

        // ---- 3. 异步发送 MQ 通知订单服务（无 rocketMQTemplate 时静默跳过） ----
        if (rocketMQTemplate != null) {
            try {
                rocketMQTemplate.asyncSend(
                        SeckillProducerConfig.DESTINATION_STOCK_DEDUCTED,
                        new SeckillDeductedEvent(orderToken, userId, activityId, seckillGoodsId, buyCount),
                        new SendCallback() {
                            @Override
                            public void onSuccess(SendResult sendResult) {
                                // MQ 投递成功，无需处理
                            }

                            @Override
                            public void onException(Throwable e) {
                                compensate(activityId, seckillGoodsId, userId, buyCount, orderToken,
                                        stockKey, usersKey, pendingKey);
                            }
                        });
            } catch (Exception e) {
                // 同步异常（如 Broker 不可达），立即补偿
                compensate(activityId, seckillGoodsId, userId, buyCount, orderToken,
                        stockKey, usersKey, pendingKey);
                throw new BusinessException("系统繁忙，请重试");
            }
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
    }

    private static String redisKey(String prefix, Long activityId, Long seckillGoodsId) {
        if (seckillGoodsId == null) {
            return "seckill:" + prefix + ":" + activityId;
        }
        return "seckill:" + prefix + ":" + activityId + ":" + seckillGoodsId;
    }
}
