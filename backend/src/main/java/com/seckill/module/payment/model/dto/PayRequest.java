package com.seckill.module.payment.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 支付请求。
 *
 * @param orderNo 订单号
 */
@Schema(description = "支付请求")
public record PayRequest(
        @NotNull @Min(1)
        @Schema(description = "订单号") Long orderNo
) {}
