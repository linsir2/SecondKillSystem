package com.seckill.module.payment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 支付请求。
 *
 * @param orderNo 订单号（不可 null）
 * @param userId  买家用户 ID（不可 null）
 */
@Schema(description = "支付请求")
public record PayRequest(
        @Schema(description = "订单号") Long orderNo,
        @Schema(description = "买家用户 ID") Long userId
) {}
