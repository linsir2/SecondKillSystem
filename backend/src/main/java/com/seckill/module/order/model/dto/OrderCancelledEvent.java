package com.seckill.module.order.model.dto;

/**
 * 订单已取消领域事件 —— cancel() 成功后由 Service 发布。
 *
 * <p>Inventory 上下文监听此事件，执行 Redis 补偿（恢复库存 + SREM 用户 + ZREM 排队凭证）。</p>
 *
 * @param orderNo        订单号
 * @param orderToken     幂等键
 * @param activityId     活动 ID（Redis 补偿用）
 * @param seckillGoodsId 秒杀商品 ID（Redis 补偿用）
 * @param userId         买家 ID（Redis 补偿用）
 * @param buyCount       购买数量（Redis 补偿用）
 */
public record OrderCancelledEvent(
        Long orderNo,
        String orderToken,
        Long activityId,
        Long seckillGoodsId,
        Long userId,
        int buyCount
) {}
