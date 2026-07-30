package com.seckill.module.order.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 取消订单请求。
 *
 * @param orderNo 订单号
 */
@Schema(description = "取消订单请求")
public record CancelOrderRequest(@Schema(description = "订单号") Long orderNo) {
}
