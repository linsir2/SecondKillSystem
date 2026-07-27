package com.seckill.module.stock.service;

import com.seckill.module.stock.model.dto.SeckillDeductResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 秒杀库存扣减领域服务。
 *
 * <p>封装 Redis+Lua 原子预扣，成功后生成 orderToken 并 ZADD 到 pending 队列。
 * 对应 DDD 抢购上下文（Seckill）的核心域名服务。
 */
@Service
public class SeckillStockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> deductScript;

    public SeckillStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.deductScript = new DefaultRedisScript<>();
        this.deductScript.setLocation(new ClassPathResource("lua/seckill_deduct.lua"));
        this.deductScript.setResultType(Long.class);
    }

    /**
     * 执行秒杀库存预扣。
     *
     * @param activityId     活动 ID（不可 null）
     * @param seckillGoodsId 秒杀商品 ID（不可 null）
     * @param userId         用户 ID（不可 null）
     * @return 扣减结果（成功含 orderToken）
     */
    public SeckillDeductResult deduct(Long activityId, Long seckillGoodsId, Long userId) {
        // null 参数会构造 "seckill:stock:null:200" 这类非法 key → 提前拒绝
        if (activityId == null) throw new IllegalArgumentException("activityId must not be null");
        if (seckillGoodsId == null) throw new IllegalArgumentException("seckillGoodsId must not be null");
        if (userId == null) throw new IllegalArgumentException("userId must not be null");

        String stockKey = redisKey("stock", activityId, seckillGoodsId);
        String usersKey = redisKey("users", activityId, seckillGoodsId);
        String pendingKey = redisKey("pending", activityId, null);

        // 1. 执行 Lua 原子预扣
        Long result = redisTemplate.execute(
                deductScript,
                List.of(stockKey, usersKey),
                String.valueOf(userId));

        // 2. 处理返回码
        if (result == null || result == -2) {
            return SeckillDeductResult.soldOut();
        }
        if (result == -1) {
            return SeckillDeductResult.duplicate();
        }

        // 3. 成功：生成 orderToken + ZADD 到 pending 队列
        String orderToken = UUID.randomUUID().toString();
        redisTemplate.opsForZSet().add(pendingKey, orderToken, System.currentTimeMillis());

        return SeckillDeductResult.success(orderToken);
    }

    private static String redisKey(String prefix, Long activityId, Long seckillGoodsId) {
        if (seckillGoodsId == null) {
            return "seckill:" + prefix + ":" + activityId;
        }
        return "seckill:" + prefix + ":" + activityId + ":" + seckillGoodsId;
    }
}
