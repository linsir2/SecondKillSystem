package com.seckill.module.stock.model.dto;

/**
 * 库存预扣成功事件 —— 抢购上下文发往订单上下文的 Published Language。
 *
 * <p>Order 上下文消费该事件创建订单，不反查 Seckill 的 Redis 状态。
 * 金额由 Order 自行查询 {@code seckill_goods.seckill_price} 计算，事件体不含金额。</p>
 *
 * @param orderToken     排队凭证 / 订单幂等键
 * @param userId         买家 ID
 * @param activityId     秒杀活动 ID
 * @param seckillGoodsId 秒杀商品 ID
 * @param buyCount       购买数量
 */
public record SeckillDeductedEvent(
        String orderToken,
        Long userId,
        Long activityId,
        Long seckillGoodsId,
        int buyCount
) {}
