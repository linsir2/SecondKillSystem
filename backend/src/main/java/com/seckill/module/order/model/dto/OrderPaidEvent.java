package com.seckill.module.order.model.dto;

/**
 * 订单已支付领域事件 —— pay() 成功后由 Service 发布。
 *
 * @param orderNo    订单号
 * @param orderToken 幂等键
 * @param activityId 活动 ID（用于清理 pending ZSET）
 */
public record OrderPaidEvent(Long orderNo, String orderToken, Long activityId) {
}
