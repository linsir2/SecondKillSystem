package com.seckill.module.payment.model.dto;

/**
 * 支付请求。
 *
 * @param orderNo 订单号（不可 null）
 * @param userId  买家用户 ID（不可 null）
 */
public record PayRequest(Long orderNo, Long userId) {
}
