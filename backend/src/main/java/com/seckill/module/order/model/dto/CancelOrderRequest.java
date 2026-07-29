package com.seckill.module.order.model.dto;

/**
 * 取消订单请求。
 *
 * @param orderNo 订单号
 */
public record CancelOrderRequest(Long orderNo) {
}
