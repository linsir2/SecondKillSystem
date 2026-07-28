package com.seckill.module.order.model.dto;

/**
 * 订单已取消领域事件 —— cancel() 成功后由 Service 发布。
 *
 * @param orderNo    订单号
 * @param orderToken 幂等键
 */
public record OrderCancelledEvent(Long orderNo, String orderToken) {
}
